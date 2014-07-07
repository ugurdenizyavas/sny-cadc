package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.SheetRetriever
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
    SheetRetriever sheetRetriever

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            def product = request.queryParams['product']
            def url = request.queryParams['url']

            sheetRetriever.sheetFlow(product, url)

            response.status(202)
            render json(status: 202, message: "sheet import started", product: product, url: url)
        }
    }

}
