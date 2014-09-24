package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaItem
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.DeltaItemService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j(value = "activity", category = "activity")
@Component
@org.springframework.context.annotation.Lazy
class DeltaItemHandler extends GroovyHandler {

    @Autowired
    DeltaItemService deltaItemService

    @Autowired
    RequestValidator validator

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            DeltaItem deltaItem = new DeltaItem(type: DeltaType.global_sku, publication: pathTokens.publication,
                    locale: pathTokens.locale, url: request.queryParams.url, processId: request.queryParams.processId)
            activity.debug "starting {}", deltaItem

            def result
            List errors = validator.validateDeltaItem(deltaItem)
            if (errors) {
                activity.error "error validating {} : {}", deltaItem, errors
                response.status(400)
                render json(status: 400, errors: errors, deltaItem: deltaItem)
            } else {
                deltaItemService.process(deltaItem).finallyDo({
                    if (deltaItem.errors) {
                        activity.error "finished {} with errors: {}", deltaItem, deltaItem.errors
                        response.status(500)
                        render json(status: 500, errors: deltaItem.errors, deltaItem: deltaItem)
                    } else {
                        activity.debug "finished {} with success", deltaItem
                        response.status(200)
                        render json(status: 200, result: result, deltaItem: deltaItem)
                    }
                }).subscribe({
                    result = it
                    activity.debug "delta item flow for {} emitted: {}", deltaItem, result
                }, { e ->
                    deltaItem.errors << HandlerUtil.getErrorMessage(e)
                    activity.error "error in $deltaItem", e
                })
            }
        }
    }

}
