package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcProduct
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.cadcsourceservice.delta.ProductService
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j(value = "activity", category = "activity")
@Component
@org.springframework.context.annotation.Lazy
class ProductHandler extends GroovyHandler {

    @Autowired
    ProductService productService

    @Autowired
    RequestValidator validator

    @Autowired
    ResponseStorage responseStorage

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            CadcProduct product = new CadcProduct(type: DeltaType.global_sku, publication: pathTokens.publication,
                    locale: pathTokens.locale, url: request.queryParams.url, processId: request.queryParams.processId)
            activity.debug "starting {}", product

            def result
            List errors = validator.validateCadcProduct(product)
            if (errors) {
                activity.error "error validating {} : {}", product, errors
                response.status(400)

                def responseJson = json(status: 400, errors: errors, product: product)

                responseStorage.store(product.processId, ["cadc", "product", product.publication, product.locale, product.processId], JsonOutput.toJson(responseJson.object))
                render responseJson
            } else {
                productService.process(product).finallyDo({
                    if (product.errors) {
                        activity.error "finished {} with errors: {}", product, product.errors
                        response.status(500)

                        def responseJson = json(status: 500, errors: product.errors, product: product)

                        responseStorage.store(
                                product.processId,
                                ["cadc", "product", product.publication, product.locale, product.processId],
                                JsonOutput.toJson(responseJson.object)
                        )
                        render responseJson
                    } else {
                        activity.debug "finished {} with success", product
                        response.status(200)

                        def responseJson = json(status: 200, result: result, product: product)

                        responseStorage.store(
                                product.processId,
                                ["cadc", "product", product.publication, product.locale, product.processId],
                                JsonOutput.toJson(responseJson.object)
                        )

                        render responseJson
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
