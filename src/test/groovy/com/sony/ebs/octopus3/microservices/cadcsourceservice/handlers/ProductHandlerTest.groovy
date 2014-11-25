package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcProduct
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.cadcsourceservice.service.ProductService
import groovy.mock.interceptor.StubFor
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class ProductHandlerTest {

    final static String PUBLICATION = "SCORE"
    final static String LOCALE = "en_GB"
    final static String DELTA_ITEM_URL = "http://cadc/a"

    StubFor mockProductService, mockRequestValidator
    def deltaResultService

    @Before
    void before() {
        mockProductService = new StubFor(ProductService)
        mockRequestValidator = new StubFor(RequestValidator)
        deltaResultService = new DeltaResultService()
    }

    void runFlow(ProcessId processId, String processIdPostfix) {
        mockProductService.demand.with {
            processProduct(1) { CadcProduct product, ProductResult productResult ->
                assert product.publication == PUBLICATION
                assert product.locale == LOCALE
                assert product.url == DELTA_ITEM_URL
                assert product.processId == processId?.id

                productResult.inputUrl = DELTA_ITEM_URL
                productResult.outputUrn = "urn:global_sku:score:en_gb:a"
                productResult.outputUrl = "/repo/file/urn:global_sku:score:en_gb:a"

                rx.Observable.from("xxx")
            }
        }

        mockRequestValidator.demand.with {
            validateCadcProduct(1) { [] }
        }

        handle(new ProductHandler(
                productService: mockProductService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                deltaResultService: deltaResultService
        ), {
            pathBinding([publication: PUBLICATION, locale: LOCALE])
            uri "/?url=$DELTA_ITEM_URL$processIdPostfix"
        }).with {
            assert status.code == 200
            def ren = rendered(DefaultJsonRender).object

            assert ren.status == 200

            assert ren.product.publication == PUBLICATION
            assert ren.product.locale == LOCALE
            assert ren.product.url == DELTA_ITEM_URL
            assert ren.product.processId == processId?.id

            assert ren.result.inputUrl == DELTA_ITEM_URL
            assert ren.result.outputUrn == "urn:global_sku:score:en_gb:a"
            assert ren.result.outputUrl == "/repo/file/urn:global_sku:score:en_gb:a"
        }
    }

    @Test
    void "delta item flow"() {
        runFlow((ProcessId) null, "")
    }

    @Test
    void "delta item flow with process id"() {
        ProcessId processId = new ProcessIdImpl()
        runFlow(processId, "&processId=$processId.id")
    }

    @Test
    void "invalid parameter"() {
        mockRequestValidator.demand.with {
            validateCadcProduct(1) { ["error"] }
        }

        handle(new ProductHandler(
                productService: mockProductService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                deltaResultService: deltaResultService
        ), {
            pathBinding([publication: PUBLICATION, locale: LOCALE])
            uri "/"
        }).with {
            assert status.code == 400
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 400
            assert ren.errors == ["error"]
            assert ren.product != null
        }
    }

    @Test
    void "error in delta item flow"() {
        mockProductService.demand.with {
            processProduct(1) { CadcProduct product, ProductResult productResult ->
                productResult.inputUrl = DELTA_ITEM_URL
                productResult.errors << "error in delta item flow"
                rx.Observable.just(null)
            }
        }

        mockRequestValidator.demand.with {
            validateCadcProduct(1) { [] }
        }

        handle(new ProductHandler(
                productService: mockProductService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                deltaResultService: deltaResultService
        ), {
            pathBinding([publication: PUBLICATION, locale: LOCALE])
            uri "/?url=$DELTA_ITEM_URL"
        }).with {
            assert status.code == 500

            def ren = rendered(DefaultJsonRender).object

            assert ren.status == 500

            assert ren.product.publication == PUBLICATION
            assert ren.product.locale == LOCALE
            assert ren.product.url == DELTA_ITEM_URL

            assert ren.errors == ["error in delta item flow"]

            assert ren.result.inputUrl == DELTA_ITEM_URL
            assert !ren.result.outputUrn
            assert !ren.result.outputUrl
        }
    }

    @Test
    void "exception in delta item flow"() {
        mockProductService.demand.with {
            processProduct(1) { CadcProduct product, ProductResult productResult ->
                productResult.inputUrl = DELTA_ITEM_URL
                rx.Observable.just("starting").map({
                    throw new Exception("exp in delta item flow")
                })
            }
        }
        mockRequestValidator.demand.with {
            validateCadcProduct(1) { [] }
        }

        handle(new ProductHandler(
                productService: mockProductService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                deltaResultService: deltaResultService
        ), {
            pathBinding([publication: PUBLICATION, locale: LOCALE])
            uri "/?url=$DELTA_ITEM_URL"
        }).with {
            assert status.code == 500

            def ren = rendered(DefaultJsonRender).object

            assert ren.status == 500

            assert ren.product.publication == PUBLICATION
            assert ren.product.locale == LOCALE
            assert ren.product.url == DELTA_ITEM_URL

            assert ren.errors == ["exp in delta item flow"]

            assert ren.result.inputUrl == DELTA_ITEM_URL
            assert !ren.result.outputUrn
            assert !ren.result.outputUrl
        }
    }
}
