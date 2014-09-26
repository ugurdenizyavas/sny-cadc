package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcProduct
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.cadcsourceservice.delta.ProductService
import groovy.mock.interceptor.StubFor
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class ProductHandlerTest {

    final static String PUBLICATION = "SCORE"
    final static String LOCALE = "en_GB"
    final static String DELTA_ITEM_URL = "http://cadc/a"

    StubFor mockProductService, mockRequestValidator, mockResponseStorage

    @Before
    void before() {
        mockProductService = new StubFor(ProductService)
        mockRequestValidator = new StubFor(RequestValidator)
        mockResponseStorage = new StubFor(ResponseStorage)
    }

    void runFlow(ProcessId processId, String processIdPostfix) {
        mockProductService.demand.with {
            process(1) { CadcProduct product ->
                assert product.publication == PUBLICATION
                assert product.locale == LOCALE
                assert product.url == DELTA_ITEM_URL
                assert product.processId == processId?.id
                rx.Observable.from("xxx")
            }
        }

        mockRequestValidator.demand.with {
            validateCadcProduct(1) { [] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new ProductHandler(productService: mockProductService.proxyInstance(), validator: mockRequestValidator.proxyInstance(), responseStorage: mockResponseStorage.proxyInstance()), {
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
            assert ren.result == "xxx"
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

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new ProductHandler(productService: mockProductService.proxyInstance(), validator: mockRequestValidator.proxyInstance(), responseStorage: mockResponseStorage.proxyInstance()), {
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
            process(1) { CadcProduct product ->
                product.errors << "error in delta item flow"
                rx.Observable.just(null)
            }
        }

        mockRequestValidator.demand.with {
            validateCadcProduct(1) { [] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new ProductHandler(productService: mockProductService.proxyInstance(), validator: mockRequestValidator.proxyInstance(), responseStorage: mockResponseStorage.proxyInstance()), {
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
            assert !ren.result
        }
    }

    @Test
    void "exception in delta item flow"() {
        mockProductService.demand.with {
            process(1) {
                rx.Observable.just("starting").map({
                    throw new Exception("exp in delta item flow")
                })
            }
        }
        mockRequestValidator.demand.with {
            validateCadcProduct(1) { [] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new ProductHandler(productService: mockProductService.proxyInstance(), validator: mockRequestValidator.proxyInstance(), responseStorage: mockResponseStorage.proxyInstance()), {
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
            assert !ren.result
        }
    }
}
