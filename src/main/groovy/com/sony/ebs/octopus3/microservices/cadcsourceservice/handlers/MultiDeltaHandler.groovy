package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.flows.FlowTypeEnum
import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.flows.ServiceTypeEnum
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
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
        List deltaServiceResults = []
        rx.Observable.from(context.pathTokens.locales?.split(",") as List).flatMap({ locale ->
            def delta = new CadcDelta(
                    flow: FlowTypeEnum.CADC,
                    service: ServiceTypeEnum.DELTA,
                    type: RepoValue.global_sku,
                    processId: new ProcessIdImpl(),
                    publication: context.pathTokens.publication,
                    locale: locale,
                    sdate: context.request.queryParams.sdate,
                    cadcUrl: context.request.queryParams.cadcUrl,
                    upload: true
            )
            multiDeltaService.doDelta(delta)
        }).finallyDo({
            context.render json(result: deltaServiceResults)
        }).subscribe({
            deltaServiceResults << it
            activity.debug "multi delta flow emitted: {}", it
        }, { e ->
            activity.error "error in multi delta handler", e
        })
    }
}
