package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.cadcsourceservice.delta.ProductServiceResult
import com.sony.ebs.octopus3.microservices.cadcsourceservice.delta.DeltaService
import groovy.util.logging.Slf4j
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j(value = "activity", category = "activity")
@Component
@org.springframework.context.annotation.Lazy
class DeltaHandler extends GroovyHandler {

    @Autowired
    DeltaService deltaService

    @Autowired
    RequestValidator validator

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            CadcDelta delta = new CadcDelta(type: DeltaType.global_sku, processId: new ProcessIdImpl(), publication: pathTokens.publication,
                    locale: pathTokens.locale, since: request.queryParams.since, cadcUrl: request.queryParams.cadcUrl)
            activity.info "starting {}", delta

            List productServiceResults = []
            List errors = validator.validateCadcDelta(delta)
            if (errors) {
                activity.error "error validating {} : {}", delta, errors
                response.status(400)
                render json(status: 400, errors: errors, delta: delta)
            } else {
                def startTime = new DateTime()
                deltaService.process(delta).finallyDo({
                    def endTime = new DateTime()
                    def timeStats = HandlerUtil.getTimeStats(startTime, endTime)
                    if (delta.errors) {
                        activity.error "finished {} with errors: {}", delta, delta.errors
                        response.status(500)
                        render json(status: 500, timeStats: timeStats, errors: delta.errors, delta: delta)
                    } else {
                        activity.info "finished {} with success", delta
                        response.status(200)
                        render json(status: 200, timeStats: timeStats, result: createDeltaResult(delta, productServiceResults), delta: delta)
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

    Map createDeltaResult(CadcDelta delta, List productServiceResults) {
        def createSuccess = {
            productServiceResults.findAll({ it.success }).collect({ it.repoUrl })
        }
        def createErrors = {
            Map errorMap = [:]
            productServiceResults.findAll({ !it.success }).each { ProductServiceResult serviceResult ->
                serviceResult.errors.each { error ->
                    if (errorMap[error] == null) errorMap[error] = []
                    errorMap[error] << serviceResult.cadcUrl
                }
            }
            errorMap
        }
        [
                stats  : [
                        "number of delta products": delta.urlList?.size(),
                        "number of success"       : productServiceResults?.findAll({
                            it.success
                        }).size(),
                        "number of errors"        : productServiceResults?.findAll({
                            !it.success
                        }).size()
                ],
                success: createSuccess(),
                errors : createErrors()
        ]
    }

}
