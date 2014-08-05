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

@Slf4j(value = "activity")
@Component
@org.springframework.context.annotation.Lazy
class SheetFlowHandler extends GroovyHandler {

    @Autowired
    SheetService sheetService

    @Autowired
    RequestValidator validator

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            DeltaSheet deltaSheet = new DeltaSheet(urnStr: pathTokens.urn, url: request.queryParams.url, processId: request.queryParams.processId)

            List errors = validator.validateDeltaSheet(deltaSheet)
            if (errors) {
                activity.error "error validating $deltaSheet : $errors"
                response.status(400)
                render json(status: 400, errors: errors, deltaSheet: deltaSheet)
            } else {
                sheetService.sheetFlow(deltaSheet).subscribe({
                    activity.info "$deltaSheet finished"
                }, { e ->
                    activity.error "error in $deltaSheet", e
                })
                activity.info "$deltaSheet started"
                response.status(202)
                render json(status: 202, message: "deltaSheet started", deltaSheet: deltaSheet)
            }
        }
    }

}
