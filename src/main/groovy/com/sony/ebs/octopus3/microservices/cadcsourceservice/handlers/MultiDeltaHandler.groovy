package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.microservices.cadcsourceservice.service.DeltaService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j(value = "activity", category = "activity")
@Component
@org.springframework.context.annotation.Lazy
class MultiDeltaHandler extends GroovyHandler {

    @Autowired
    DeltaService deltaService

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            List deltaServiceResults = []
            rx.Observable.from(pathTokens.locales?.split(",") as List).flatMap({
                deltaService.doDelta(new CadcDelta(type: DeltaType.global_sku, processId: new ProcessIdImpl(), publication: pathTokens.publication,
                        locale: it.toString(), since: request.queryParams.since, cadcUrl: request.queryParams.cadcUrl))
            }).finallyDo({
                render json(result: deltaServiceResults)
            }).subscribe({
                deltaServiceResults << it
                activity.debug "multi delta flow emitted: {}", it
            }, { e ->
                activity.error "error in multi delta handler", e
            })
        }
    }
}
