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

            deltaRetriever.deltaFlow(publication, locale, since, cadcUrl)

            response.status(202)
            render json(status: 202, message: "delta import started", publication: publication, locale: locale, since: since, cadcUrl: cadcUrl)
        }
    }

}
