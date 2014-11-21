package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcProduct
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.cadcsourceservice.service.ProductService
import groovy.util.logging.Slf4j
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

@Slf4j(value = "activity", category = "activity")
@Component
@org.springframework.context.annotation.Lazy
class ProductHandler extends GroovyHandler {

    @Autowired
    ProductService productService

    @Autowired
    RequestValidator validator

    @Autowired
    DeltaResultService deltaResultService

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            CadcProduct product = new CadcProduct(type: RepoValue.global_sku, publication: pathTokens.publication,
                    locale: pathTokens.locale, url: request.queryParams.url, processId: request.queryParams.processId)
            activity.debug "starting {}", product

            def startTime = new DateTime()

            def result
            List errors = validator.validateCadcProduct(product)
            if (errors) {
                activity.error "error validating {} : {}", product, errors
                response.status(400)
                render deltaResultService.createProductResultInvalid(product, errors)
            } else {
                productService.process(product).finallyDo({
                    def endTime = new DateTime()
                    if (product.errors) {
                        activity.error "finished {} with errors: {}", product, product.errors
                        response.status(500)
                        render deltaResultService.createProductResultWithErrors(product, product.errors, startTime, endTime)
                    } else {
                        activity.debug "finished {} with success", product
                        response.status(200)
                        render deltaResultService.createProductResult(product, result, startTime, endTime)
                    }
                }).subscribe({
                    result = it
                    activity.debug "delta item flow for {} emitted: {}", product, result
                }, { e ->
                    product.errors << HandlerUtil.getErrorMessage(e)
                    activity.error "error in $product", e
                })
            }
        }
    }

}
