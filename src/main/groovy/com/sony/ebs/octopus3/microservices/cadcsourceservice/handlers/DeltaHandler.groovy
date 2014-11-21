package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.ProductServiceResult
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

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            CadcDelta delta = new CadcDelta(type: DeltaType.global_sku, processId: new ProcessIdImpl(), publication: pathTokens.publication,
                    locale: pathTokens.locale, since: request.queryParams.since, cadcUrl: request.queryParams.cadcUrl)
            activity.info "starting {}", delta

            List productServiceResults = []
            delta.errors = validator.validateCadcDelta(delta)
            if (delta.errors) {
                activity.error "error validating {} : {}", delta, delta.errors
                response.status(400)

                def responseJson = deltaResultService.createDeltaResultInvalid(delta, delta.errors)
                responseStorage.store(
                        delta.processId.id,
                        ["cadc", "delta", delta.publication, delta.locale, delta.processId.id],
                        JsonOutput.toJson(responseJson.object)
                )
                render responseJson
            } else {
                def startTime = new DateTime()
                deltaService.process(delta).finallyDo({
                    def endTime = new DateTime()
                    if (delta.errors) {
                        activity.error "finished {} with errors: {}", delta, delta.errors
                        response.status(500)

                        def responseJson = deltaResultService.createDeltaResultWithErrors(delta, delta.errors, startTime, endTime)

                        responseStorage.store(
                                delta.processId.id,
                                ["cadc", "delta", delta.publication, delta.locale, delta.processId.id],
                                JsonOutput.toJson(responseJson.object)
                        )
                        render responseJson
                    } else {
                        activity.info "finished {} with success", delta
                        response.status(200)

                        def responseJson = deltaResultService.createDeltaResult(delta, createDeltaResult(delta, productServiceResults), startTime, endTime)

                        responseStorage.store(
                                delta.processId.id,
                                ["cadc", "delta", delta.publication, delta.locale, delta.processId.id],
                                JsonOutput.toJson(responseJson.object)
                        )
                        render responseJson
                    }
                }).subscribe({
                    productServiceResults << it
                    activity.debug "delta flow emitted: {}", it
                }, { e ->
                    delta.errors << HandlerUtil.getErrorMessage(e)
                    activity.error "error in $delta", e
                })
            }
        }
    }

    DeltaResult createDeltaResult(CadcDelta delta, List<ProductServiceResult> productServiceResults) {
        Map productErrors = [:]
        productServiceResults.findAll({ !it.success }).each { ProductServiceResult serviceResult ->
            serviceResult.errors.each { error ->
                if (productErrors[error] == null) productErrors[error] = []
                productErrors[error] << serviceResult.cadcUrl
            }
        }

        def successfulUrns = productServiceResults.findAll({ it.success }).collect({ it.cadcUrl })
        def unsuccessfulUrns = productServiceResults.findAll({ !it.success }).collect({ it.cadcUrl })

        def repoUrls = productServiceResults.findAll({ it.success }).collect({ it.repoUrl })

        new DeltaResult(
                productErrors: productErrors,
                deltaUrns: delta.urlList,
                successfulUrns: successfulUrns,
                unsuccessfulUrns: unsuccessfulUrns,
                other: [
                        repoUrls: repoUrls
                ]

        )

    }

}
