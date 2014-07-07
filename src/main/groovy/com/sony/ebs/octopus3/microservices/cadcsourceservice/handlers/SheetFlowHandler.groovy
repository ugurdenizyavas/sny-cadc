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
            String product = request.queryParams['product']
            String url = request.queryParams['url']
            boolean synch = Boolean.parseBoolean(request.queryParams['synch'])

            def finish = { message ->
                log.info "$message for product $product, url $url"
                response.status(202)
                render json(status: 202, message: message, product: product, url: url)
            }
            if (synch) {
                sheetRetriever.sheetFlow(product, url).subscribe {
                    finish "sheet import finished"
                }
            } else {
                sheetRetriever.sheetFlow(product, url).subscribe {
                    log.info "sheet import finished for product $product, url $url"
                }
                finish "sheet import started"
            }

        }
    }

}
