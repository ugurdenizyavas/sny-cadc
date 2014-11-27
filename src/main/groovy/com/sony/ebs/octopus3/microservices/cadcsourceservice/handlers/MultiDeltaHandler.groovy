package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.service.DeltaService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.service.MultiDeltaService
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
    MultiDeltaService multiDeltaService

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            List deltaServiceResults = []
            rx.Observable.from(pathTokens.locales?.split(",") as List).flatMap({
                multiDeltaService.doDelta(new CadcDelta(type: RepoValue.global_sku, processId: new ProcessIdImpl(), publication: pathTokens.publication,
                        locale: it.toString(), sdate: request.queryParams.sdate, cadcUrl: request.queryParams.cadcUrl))
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