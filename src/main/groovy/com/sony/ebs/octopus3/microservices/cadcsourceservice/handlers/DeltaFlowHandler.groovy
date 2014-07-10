package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.DeltaService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.validators.RequestValidator
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
    DeltaService deltaService

    @Autowired
    RequestValidator validator

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            String publication = pathTokens.publication
            String locale = pathTokens.locale
            String since = request.queryParams.since
            String cadcUrl = request.queryParams.cadcUrl

            def sendError = { String message ->
                log.error message
                response.status(400)
                render json(status: 400, message: message, publication: publication, locale: locale, since: since, cadcUrl: cadcUrl)
            }
            if (!validator.validatePublication(publication)) {
                sendError("publication parameter is invalid")
            } else if (!validator.validateLocale(locale)) {
                sendError("locale parameter is invalid")
            } else if (!validator.validateUrl(cadcUrl)) {
                sendError("cadcUrl parameter is invalid")
            } else if (!validator.validateSinceValue(since)) {
                sendError("since parameter is invalid")
            } else {
                ProcessId processId = new ProcessIdImpl()
                deltaService.deltaFlow(processId, publication, locale, since, cadcUrl).subscribe({ result ->
                    log.info "delta import finished for publication $publication, locale $locale, since $since, cadcUrl $cadcUrl, reuslt: $result"
                })
                log.info "delta import started for publication $publication, locale $locale, since $since, cadcUrl $cadcUrl"
                response.status(202)
                render json(status: 202, processId: processId.id, message: "delta import started", publication: publication, locale: locale, since: since, cadcUrl: cadcUrl)
            }
        }
    }

}
