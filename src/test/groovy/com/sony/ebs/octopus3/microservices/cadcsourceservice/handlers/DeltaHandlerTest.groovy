package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.cadcsourceservice.delta.ProductServiceResult
import com.sony.ebs.octopus3.microservices.cadcsourceservice.delta.DeltaService
import groovy.mock.interceptor.StubFor
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class DeltaHandlerTest {

    StubFor mockDeltaService, mockValidator, mockResponseStorage
    def deltaResultService

    def productServiceResultA = new ProductServiceResult(cadcUrl: "//cadc/a", success: true, repoUrl: "//repo/file/a")
    def productServiceResultB = new ProductServiceResult(cadcUrl: "//cadc/b", success: false, errors: ["err3", "err1"])
    def productServiceResultC = new ProductServiceResult(cadcUrl: "//cadc/c", success: true, repoUrl: "//repo/file/c")
    def productServiceResultD = new ProductServiceResult(cadcUrl: "//cadc/d", success: false, errors: ["err1", "err2"])

    @Before
    void before() {
        mockDeltaService = new StubFor(DeltaService)
        mockValidator = new StubFor(RequestValidator)
        mockResponseStorage = new StubFor(ResponseStorage)
        deltaResultService = new DeltaResultService()
    }

    @Test
    void "main flow"() {
        mockDeltaService.demand.with {
            process(1) { CadcDelta delta ->
                assert delta.processId != null
                assert delta.publication == "SCORE"
                assert delta.locale == "en_GB"
                assert delta.since == "2014"
                assert delta.cadcUrl == "http://cadc/skus"
                delta.urlList = ["/a", "/b", "/c", "/d"]
                rx.Observable.from([productServiceResultC, productServiceResultA, productServiceResultD, productServiceResultB])
            }
        }

        mockValidator.demand.with {
            validateCadcDelta(1) { [] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new DeltaHandler(
                deltaService: mockDeltaService.proxyInstance(),
                validator: mockValidator.proxyInstance(),
                responseStorage: mockResponseStorage.proxyInstance(),
                deltaResultService: deltaResultService
        ), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/?cadcUrl=http://cadc/skus&since=2014"
        }).with {
            assert status.code == 200
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 200
            assert ren.delta.publication == "SCORE"
            assert ren.delta.locale == "en_GB"
            assert ren.delta.since == "2014"
            assert ren.delta.cadcUrl == "http://cadc/skus"
            assert ren.delta.processId != null

            assert ren.result.other.repoUrls?.sort() == ["//repo/file/a", "//repo/file/c"]

            assert ren.result.productErrors?.size() == 3
            assert ren.result.productErrors.err1?.sort() == ["//cadc/b", "//cadc/d"]
            assert ren.result.productErrors.err2 == ["//cadc/d"]
            assert ren.result.productErrors.err3 == ["//cadc/b"]

            assert ren.result.stats."number of delta products" == 4
            assert ren.result.stats."number of successful" == 2
            assert ren.result.stats."number of unsuccessful" == 2
        }
    }

    @Test
    void "invalid parameter"() {
        mockValidator.demand.with {
            validateCadcDelta(1) { ["error"] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new DeltaHandler(
                deltaService: mockDeltaService.proxyInstance(),
                validator: mockValidator.proxyInstance(),
                responseStorage: mockResponseStorage.proxyInstance(),
                deltaResultService: deltaResultService
        ), {
            pathBinding([locale: "en_GB"])
            uri "/"
        }).with {
            assert status.code == 400
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 400
            assert ren.errors == ["error"]
            assert ren.delta != null
        }
    }


    @Test
    void "error in delta flow"() {
        mockDeltaService.demand.with {
            process(1) { CadcDelta delta ->
                delta.errors << "error in delta flow"
                rx.Observable.just(null)
            }
        }

        mockValidator.demand.with {
            validateCadcDelta(1) { [] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new DeltaHandler(
                deltaService: mockDeltaService.proxyInstance(),
                validator: mockValidator.proxyInstance(),
                responseStorage: mockResponseStorage.proxyInstance(),
                deltaResultService: deltaResultService
        ), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/?cadcUrl=http://cadc/skus&since=2014"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.delta.publication == "SCORE"
            assert ren.delta.locale == "en_GB"
            assert ren.errors == ["error in delta flow"]
            assert !ren.result
        }
    }

    @Test
    void "exception in delta flow"() {
        mockDeltaService.demand.with {
            process(1) {
                rx.Observable.just("starting").map({
                    throw new Exception("exp in delta flow")
                })
            }
        }
        mockValidator.demand.with {
            validateCadcDelta(1) { [] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new DeltaHandler(
                deltaService: mockDeltaService.proxyInstance(),
                validator: mockValidator.proxyInstance(),
                responseStorage: mockResponseStorage.proxyInstance(),
                deltaResultService: deltaResultService
        ), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/?cadcUrl=http://cadc/skus&since=2014"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.delta.publication == "SCORE"
            assert ren.delta.locale == "en_GB"
            assert ren.errors == ["exp in delta flow"]
            assert !ren.result
        }
    }
}
