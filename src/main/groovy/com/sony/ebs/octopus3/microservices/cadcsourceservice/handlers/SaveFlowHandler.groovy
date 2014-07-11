package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.DeltaCollaborator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j
@Component
class SaveFlowHandler extends GroovyHandler {

    @Autowired
    DeltaCollaborator deltaCollaborator

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            String urn = pathTokens.urn
            String text = request.body.text
            String processIdStr = request.queryParams.processId

            log.info "starting saving for procesId $processIdStr and urn $urn "
            deltaCollaborator.storeUrn(new URNImpl(urn), text)
            log.info "finished saving for procesId $processIdStr and urn $urn "

            response.status(202)
            render json(status: 202, message: "sheet saved", urn: urn, processId: processIdStr)
        }
    }

}
