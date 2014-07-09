package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.SheetService
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

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            String product = request.queryParams['product']
            String url = request.queryParams['url']
            if (!product || !url) {
                def message = "one of product, url parameters missing"
                log.error message
                response.status(400)
                render json(status: 400, message: message, product: product, url: url)
            } else {
                sheetService.sheetFlow(product, url).subscribe {
                    log.info "sheet import finished for product $product, url $url"
                }
                log.info "import started for product $product, url $url"
                response.status(202)
                render json(status: 202, message: "sheet import started", product: product, url: url)
            }
        }
    }

}
