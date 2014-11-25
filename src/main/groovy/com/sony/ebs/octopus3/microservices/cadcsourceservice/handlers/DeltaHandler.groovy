package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.cadcsourceservice.service.DeltaService
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

@Slf4j(value = "activity", category = "activity")
@Component
@org.springframework.context.annotation.Lazy
class DeltaHandler extends GroovyHandler {

    @Autowired
    DeltaService deltaService

    @Autowired
    RequestValidator validator

    @Autowired
    ResponseStorage responseStorage

    @Autowired
    DeltaResultService deltaResultService

    def storeResponse(CadcDelta delta, responseJson) {
        responseStorage.store(delta,JsonOutput.toJson(responseJson.object))
    }

    @Override
    protected void handle(GroovyContext context) {
        CadcDelta delta = new CadcDelta(
                type: RepoValue.global_sku,
                processId: new ProcessIdImpl(),
                publication: context.pathTokens.publication,
                locale: context.pathTokens.locale,
                since: context.request.queryParams.since,
                cadcUrl: context.request.queryParams.cadcUrl
        )
        activity.info "starting {}", delta

        List errors = validator.validateCadcDelta(delta)
        if (errors) {
            activity.error "error validating {} : {}", delta, errors
            context.response.status(400)
            def responseJson = deltaResultService.createDeltaResultInvalid(delta, errors)
            storeResponse(delta, responseJson)
            context.render responseJson
        } else {
            def startTime = new DateTime()
            DeltaResult deltaResult = new DeltaResult()
            List<ProductResult> productResults = []
            deltaService.processDelta(delta, deltaResult).finallyDo({
                if (deltaResult.errors) {
                    activity.error "finished {} with errors: {}", delta, deltaResult.errors
                    context.response.status(500)
                } else {
                    activity.info "finished {} with success", delta
                    context.response.status(200)
                    enhanceDeltaResult(deltaResult, productResults)
                }
                def endTime = new DateTime()
                def responseJson = deltaResultService.createDeltaResult(delta, deltaResult, startTime, endTime)
                storeResponse(delta, responseJson)
                context.render responseJson
            }).subscribe({
                productResults << it
                activity.debug "delta flow emitted: {}", it
            }, { e ->
                deltaResult.errors << HandlerUtil.getErrorMessage(e)
                activity.error "error in $delta", e
            })
        }
    }

    def enhanceDeltaResult(DeltaResult deltaResult, List<ProductResult> productResults) {
        Map pErrors = [:]
        productResults.findAll({ !it.success }).each { ProductResult serviceResult ->
            serviceResult.errors.each { error ->
                if (pErrors[error] == null) pErrors[error] = []
                pErrors[error] << serviceResult.inputUrl
            }
        }
        deltaResult.with {
            productErrors = pErrors
            deltaUrns = deltaResult.deltaUrls
            successfulUrns = productResults.findAll({ it.success }).collect({ it.inputUrl })
            unsuccessfulUrns = productResults.findAll({ !it.success }).collect({ it.inputUrl })
            other = [
                    outputUrls: (productResults.findAll({ it.success }).collect({ it.outputUrl }))
            ]
        }
    }

}
