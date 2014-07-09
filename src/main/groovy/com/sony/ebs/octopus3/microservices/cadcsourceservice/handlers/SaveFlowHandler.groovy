package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j
@Component
class SaveFlowHandler extends GroovyHandler {

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            String urn = pathTokens.urn
            String text = request.body.text

            log.debug "saving: $text"
            log.info "$urn saved"

            response.status(202)
            render json(status: 202, message: "sheet saved", urn: urn)
        }
    }

}
