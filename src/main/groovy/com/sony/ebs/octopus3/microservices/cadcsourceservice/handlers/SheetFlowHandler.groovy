package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaSheet
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.SheetService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.validators.RequestValidator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j
@Component
class SheetFlowHandler extends GroovyHandler {

    @Autowired
    SheetService sheetService

    @Autowired
    RequestValidator validator

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            DeltaSheet deltaSheet = new DeltaSheet(urnStr: pathTokens.urn, url: request.queryParams.url, processId: request.queryParams.processId)

            def sendError = {
                response.status(400)
                render json(status: 400, errors: it, deltaSheet: deltaSheet)
            }

            List errors = validator.validateDeltaSheet(deltaSheet)
            if (errors) {
                log.error "error validating $deltaSheet : $errors"
                sendError(errors)
            } else {
                sheetService.sheetFlow(deltaSheet).subscribe({
                    log.info "$deltaSheet finished"
                    response.status(200)
                    render json(status: 200, message: "deltaSheet finished", deltaSheet: deltaSheet)
                }, { e ->
                    log.error "error in $deltaSheet", e
                    sendError([e.getMessage()])
                })
            }
        }
    }

}
