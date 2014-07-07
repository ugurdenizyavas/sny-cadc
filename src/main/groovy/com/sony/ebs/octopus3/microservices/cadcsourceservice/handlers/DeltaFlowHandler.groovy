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
            String publication = pathTokens['publication']
            String locale = pathTokens['locale']
            String since = request.queryParams['since']
            String cadcUrl = request.queryParams['cadcUrl']
            boolean synch = request.queryParams['synch']

            if (synch) {
                deltaRetriever.deltaFlow(publication, locale, since, cadcUrl, synch).subscribe({
                    log.info "delta import finished for publication $publication, locale $locale, since $since, cadcUrl $cadcUrl"
                    response.status(202)
                    render json(status: 202, message: "delta import finished", publication: publication, locale: locale, since: since, cadcUrl: cadcUrl)
                })
            } else {
                deltaRetriever.deltaFlow(publication, locale, since, cadcUrl, synch).subscribe({
                    log.info "delta import finished for publication $publication, locale $locale, since $since, cadcUrl $cadcUrl"
                })
                log.info "delta import started for publication $publication, locale $locale, since $since, cadcUrl $cadcUrl"
                response.status(202)
                render json(status: 202, message: "delta import started", publication: publication, locale: locale, since: since, cadcUrl: cadcUrl)
            }
        }
    }

}
