package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.DeltaRetriever
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j
@Component
class DeltaFlowHandler extends GroovyHandler {

    @Autowired
    DeltaRetriever deltaRetriever

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            def publication = pathTokens['publication']
            def locale = pathTokens['locale']
            def since = request.queryParams['since']
            def cadcUrl = request.queryParams['cadcUrl']

            deltaRetriever.deltaFlow(publication, locale, since, cadcUrl).subscribe({ List products ->
                log.info "delta import finished for publication $publication, locale $locale, since $since, cadcUrl $cadcUrl with products $products"
            })
            log.info "delta import started for publication $publication, locale $locale, since $since, cadcUrl $cadcUrl"
            render json([publication: publication, locale: locale, since: since, cadcUrl: cadcUrl, message: "delta import started"])
        }
    }

}
