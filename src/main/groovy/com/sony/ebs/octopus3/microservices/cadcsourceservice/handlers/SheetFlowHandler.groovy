package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.SheetService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.validators.RequestValidator
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

    @Autowired
    RequestValidator validator

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            String urnStr = pathTokens.urn
            String url = request.queryParams.url
            String processIdStr = request.queryParams.processId

            def sendError = { String message ->
                log.error message
                response.status(400)
                render json(status: 400, message: message, urn: urnStr, url: url, processId: processIdStr)
            }
            URN urn = validator.createUrn(urnStr)
            if (!urn) {
                sendError("urn parameter is invalid")
            } else if (!validator.validateUrl(url)) {
                sendError("url parameter is invalid")
            } else {
                ProcessId processId = processIdStr ? new ProcessIdImpl(processIdStr) : null
                sheetService.sheetFlow(urn, url, processId).subscribe {
                    log.info "sheet import finished for urn $urnStr, url $url"
                }
                log.info "import started for urn $urnStr, url $url"
                response.status(202)
                render json(status: 202, message: "sheet import started", urn: urnStr, url: url, processId: processIdStr)
            }
        }
    }

}
