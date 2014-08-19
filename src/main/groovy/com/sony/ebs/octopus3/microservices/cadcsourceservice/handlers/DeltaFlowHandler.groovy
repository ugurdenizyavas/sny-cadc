package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.SheetServiceResult
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.DeltaService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.validators.RequestValidator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j(value = "activity")
@Component
@org.springframework.context.annotation.Lazy
class DeltaFlowHandler extends GroovyHandler {

    @Autowired
    DeltaService deltaService

    @Autowired
    RequestValidator validator

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            Delta delta = new Delta(processId: new ProcessIdImpl(), publication: pathTokens.publication, locale: pathTokens.locale,
                    since: request.queryParams.since, cadcUrl: request.queryParams.cadcUrl)

            List sheetServiceResults = []
            List errors = validator.validateDelta(delta)
            if (errors) {
                activity.error "error validating $delta : $errors"
                response.status(400)
                render json(status: 400, errors: errors, delta: delta)
            } else {
                deltaService.deltaFlow(delta).subscribe({
                    sheetServiceResults << it
                    activity.info "sheet result: $it"
                }, { e ->
                    delta.errors << e.message ?: e.cause?.message
                    activity.error "error in $delta", e
                }, {
                    if (delta.errors) {
                        response.status(500)
                        render json(status: 500, delta: delta, errors: delta.errors)
                    } else {
                        response.status(200)
                        render json(status: 200, delta: delta, result: createDeltaResult(delta, sheetServiceResults))
                    }
                })
            }
        }
    }

    Map createDeltaResult(Delta delta, List sheetServiceResults) {
        def createSuccess = {
            sheetServiceResults.findAll({ it.success }).collect({ it.jsonUrl })
        }
        def createErrors = {
            Map errorMap = [:]
            sheetServiceResults.findAll({ !it.success }).each { SheetServiceResult serviceResult ->
                serviceResult.errors.each { error ->
                    if (errorMap[error] == null) errorMap[error] = []
                    errorMap[error] << serviceResult.urn
                }
            }
            errorMap
        }
        [
                stats  : [
                        "number of delta products": delta.urlMap?.size(),
                        "number of success"       : sheetServiceResults?.findAll({
                            it.success
                        }).size(),
                        "number of errors"        : sheetServiceResults?.findAll({
                            !it.success
                        }).size()
                ],
                success: createSuccess(),
                errors : createErrors()
        ]
    }

}
