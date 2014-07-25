package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
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

            List errors = validator.validateDelta(delta)
            if (errors) {
                activity.error "error validating $delta : $errors"
                response.status(400)
                render json(status: 400, errors: errors, delta: delta)
            } else {
                deltaService.deltaFlow(delta).subscribe({ result ->
                    activity.info "$result for ${delta}"
                }, { e ->
                    activity.error "error in $delta", e
                })
                activity.info "$delta started"
                response.status(202)
                render json(status: 202, message: "delta started", delta: delta)
            }
        }
    }

}
