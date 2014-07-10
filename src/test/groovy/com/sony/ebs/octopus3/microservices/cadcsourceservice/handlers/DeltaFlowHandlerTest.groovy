package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.DeltaService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.validators.RequestValidator
import groovy.mock.interceptor.MockFor
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class DeltaFlowHandlerTest {

    MockFor mockDeltaService, mockValidator

    @Before
    void before() {
        mockDeltaService = new MockFor(DeltaService)
        mockValidator = new MockFor(RequestValidator)
    }

    @Test
    void "main flow"() {
        mockDeltaService.demand.with {
            deltaFlow(1) { ProcessId processId, String publication, String locale, String since, String cadcUrl ->
                assert processId != null
                assert publication == "SCORE"
                assert locale == "en_GB"
                assert since == "2014"
                assert cadcUrl == "http://cadc/skus"
                rx.Observable.from("xxx")
            }
        }

        mockValidator.demand.with {
            validatePublication(1) { true }
            validateLocale(1) { true }
            validateUrl(1) { true }
            validateSinceValue(1) { true }
        }

        handle(new DeltaFlowHandler(deltaService: mockDeltaService.proxyInstance(), validator: mockValidator.proxyInstance()), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/?cadcUrl=http://cadc/skus&since=2014"
        }).with {
            assert status.code == 202
            assert rendered(DefaultJsonRender).object.message == "delta import started"
            assert rendered(DefaultJsonRender).object.publication == "SCORE"
            assert rendered(DefaultJsonRender).object.locale == "en_GB"
            assert rendered(DefaultJsonRender).object.since == "2014"
            assert rendered(DefaultJsonRender).object.cadcUrl == "http://cadc/skus"
            assert rendered(DefaultJsonRender).object.status == 202
            assert rendered(DefaultJsonRender).object.processId != null
        }
    }

    @Test
    void "publication parameter is invalid"() {
        mockValidator.demand.with {
            validatePublication(1) { false }
        }
        handle(new DeltaFlowHandler(deltaService: mockDeltaService.proxyInstance(), validator: mockValidator.proxyInstance()), {
            pathBinding([locale: "en_GB"])
            uri "/"
        }).with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.message == "publication parameter is invalid"
        }
    }

    @Test
    void "locale parameter is invalid"() {
        mockValidator.demand.with {
            validatePublication(1) { true }
            validateLocale(1) { false }
        }

        handle(new DeltaFlowHandler(deltaService: mockDeltaService.proxyInstance(), validator: mockValidator.proxyInstance()), {
            pathBinding([publication: "SCORE"])
            uri "/"
        }).with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.message == "locale parameter is invalid"
        }
    }

    @Test
    void "cadcUrl parameter is invalid"() {
        mockValidator.demand.with {
            validatePublication(1) { true }
            validateLocale(1) { true }
            validateUrl(1) { false }
        }

        handle(new DeltaFlowHandler(deltaService: mockDeltaService.proxyInstance(), validator: mockValidator.proxyInstance()), {
            uri "/"
        }).with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.message == "cadcUrl parameter is invalid"
        }
    }

    @Test
    void "since parameter is invalid"() {
        mockValidator.demand.with {
            validatePublication(1) { true }
            validateLocale(1) { true }
            validateUrl(1) { true }
            validateSinceValue(1) { false }
        }

        handle(new DeltaFlowHandler(deltaService: mockDeltaService.proxyInstance(), validator: mockValidator.proxyInstance()), {
            uri "/"
        }).with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.message == "since parameter is invalid"
        }
    }
}
