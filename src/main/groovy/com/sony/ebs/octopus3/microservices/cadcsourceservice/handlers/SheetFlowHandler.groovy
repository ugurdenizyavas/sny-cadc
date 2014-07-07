package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.DeltaUrlBuilder
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

    @Autowired
    DeltaUrlBuilder deltaUrlBuilder

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            def url = request.queryParams['url']

            String product = deltaUrlBuilder.getProductFromUrl(url)
            sheetRetriever.sheetFlow(product, url).subscribe {
                log.info "sheet import finished for product $product, url $url"
            }
            log.info "sheet import started for product $product, url $url"
            render json([product: product, url: url, message: "sheet import started"])
        }
    }


}
