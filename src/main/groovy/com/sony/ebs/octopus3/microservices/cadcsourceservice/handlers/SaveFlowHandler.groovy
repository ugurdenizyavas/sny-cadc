package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j(value = "activity")
@Component
@org.springframework.context.annotation.Lazy
class SaveFlowHandler extends GroovyHandler {

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            String urn = pathTokens.urn
            String processIdStr = request.queryParams.processId
            response.status(200)
            render json(status: 200, message: "sheet saved", urn: urn, processId: processIdStr)
        }
    }

}
