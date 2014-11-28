package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.flows.FlowTypeEnum
import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.flows.ServiceTypeEnum
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
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
class MultiProductHandler extends GroovyHandler {

    @Autowired
    DeltaService deltaService

    @Override
    protected void handle(GroovyContext context) {
        List productServiceResults = []
        rx.Observable.from(context.pathTokens.locales?.split(",") as List).flatMap({ locale ->
            def delta = new CadcDelta(
                    flow: FlowTypeEnum.CADC,
                    service: ServiceTypeEnum.DELTA,
                    type: RepoValue.global_sku,
                    processId: new ProcessIdImpl(),
                    publication: context.pathTokens.publication,
                    locale: locale,
                    upload: true
            )
            def inputUrl = context.request.queryParams.url
            deltaService.doProduct(delta, inputUrl)
        }).finallyDo({
            context.render json(result: productServiceResults)
        }).subscribe({
            productServiceResults << it
            activity.debug "multi product flow emitted: {}", it
        }, { e ->
            activity.error "error in multi product handler", e
        })
    }
}
