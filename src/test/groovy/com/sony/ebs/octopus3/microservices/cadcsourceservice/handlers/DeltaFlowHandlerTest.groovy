package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.DeltaService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.validators.DeltaFlowValidator
import groovy.mock.interceptor.MockFor
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class DeltaFlowHandlerTest {

    @Test
    void "main flow"() {
        def mockDeltaService = new MockFor(DeltaService)
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
        def mockDeltaFlowValidator = new MockFor(DeltaFlowValidator)
        mockDeltaFlowValidator.demand.with {
            validateUrl(1) {
                assert it == "http://cadc/skus"
                true
            }
            validateSinceValue(1) {
                assert it == "2014"
                true
            }
        }

        def invocation = handle(new DeltaFlowHandler(deltaService: mockDeltaService.proxyInstance(), deltaFlowValidator: mockDeltaFlowValidator.proxyInstance())) {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/?cadcUrl=http://cadc/skus&since=2014"
        }
        invocation.with {
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
    void "publication parameter missing"() {
        def invocation = handle(new DeltaFlowHandler()) {
            pathBinding([locale: "en_GB"])
            uri "/"
        }
        invocation.with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.message == "publication parameter is required"
        }
    }

    @Test
    void "locale parameter missing"() {
        def invocation = handle(new DeltaFlowHandler()) {
            pathBinding([publication: "SCORE"])
            uri "/"
        }
        invocation.with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.message == "locale parameter is required"
        }
    }

    @Test
    void "invalid cadcUrl parameter"() {
        def mockDeltaFlowValidator = new MockFor(DeltaFlowValidator)
        mockDeltaFlowValidator.demand.with {
            validateUrl(1) { false }
        }
        def invocation = handle(new DeltaFlowHandler(deltaFlowValidator: mockDeltaFlowValidator.proxyInstance())) {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/"
        }
        invocation.with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.message == "invalid cadcUrl parameter"
        }
    }

    @Test
    void "invalid since parameter"() {
        def mockDeltaFlowValidator = new MockFor(DeltaFlowValidator)
        mockDeltaFlowValidator.demand.with {
            validateUrl(1) { true }
            validateSinceValue(1) { false }
        }
        def invocation = handle(new DeltaFlowHandler(deltaFlowValidator: mockDeltaFlowValidator.proxyInstance())) {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/"
        }
        invocation.with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.message == "invalid since parameter"
        }
    }
}
