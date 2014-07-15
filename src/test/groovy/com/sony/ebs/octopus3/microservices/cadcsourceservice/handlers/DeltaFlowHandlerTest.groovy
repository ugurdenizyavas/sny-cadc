package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.DeltaService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.validators.RequestValidator
import groovy.mock.interceptor.StubFor
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class DeltaFlowHandlerTest {

    StubFor mockDeltaService, mockValidator

    @Before
    void before() {
        mockDeltaService = new StubFor(DeltaService)
        mockValidator = new StubFor(RequestValidator)
    }

    @Test
    void "main flow"() {
        mockDeltaService.demand.with {
            deltaFlow(1) { Delta delta ->
                assert delta.processId != null
                assert delta.publication == "SCORE"
                assert delta.locale == "en_GB"
                assert delta.since == "2014"
                assert delta.cadcUrl == "http://cadc/skus"
                rx.Observable.from("xxx")
            }
        }

        mockValidator.demand.with {
            validateDelta(1) { [] }
        }

        handle(new DeltaFlowHandler(deltaService: mockDeltaService.proxyInstance(), validator: mockValidator.proxyInstance()), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/?cadcUrl=http://cadc/skus&since=2014"
        }).with {
            assert status.code == 202
            def ren =  rendered(DefaultJsonRender).object
            assert ren.message == "delta started"
            assert ren.status == 202
            assert ren.delta.publication == "SCORE"
            assert ren.delta.locale == "en_GB"
            assert ren.delta.since == "2014"
            assert ren.delta.cadcUrl == "http://cadc/skus"
            assert ren.delta.processId != null
        }
    }

    @Test
    void "invalid parameter"() {
        mockValidator.demand.with {
            validateDelta(1) { ["error"] }
        }
        handle(new DeltaFlowHandler(deltaService: mockDeltaService.proxyInstance(), validator: mockValidator.proxyInstance()), {
            pathBinding([locale: "en_GB"])
            uri "/"
        }).with {
            assert status.code == 400
            def ren =  rendered(DefaultJsonRender).object
            assert ren.status == 400
            assert ren.errors == ["error"]
            assert ren.delta != null
        }
    }
}
