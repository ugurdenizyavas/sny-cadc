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
            def product = request.queryParams['product']
            def text = request.body.text
            log.debug "saving: $text"
            log.info "product $product saved"

            response.status(202)
            render json(status: 202, message: "product saved", product: product)
        }
    }

}
