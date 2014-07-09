package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.DeltaService
import groovy.mock.interceptor.MockFor
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class DeltaFlowHandlerTest {

    @Test
    void "main flow"() {
        def mock = new MockFor(DeltaService)
        mock.demand.with {
            deltaFlow(1) { ProcessId processId, String publication, String locale, String since, String cadcUrl ->
                assert processId != null
                assert publication == "SCORE"
                assert locale == "en_GB"
                assert since == "2014"
                assert cadcUrl == "http://cadc/skus"
                rx.Observable.from("xxx")
            }
        }
        def deltaFlowHandler = new DeltaFlowHandler()
        deltaFlowHandler.deltaService = mock.proxyInstance()

        def invocation = handle(deltaFlowHandler) {
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
    void "missing parameter"() {
        def invocation = handle(new DeltaFlowHandler()) {
            pathBinding([locale: "en_GB"])
            uri "/?cadcUrl=http://cadc/skus"
        }
        invocation.with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.message == "one of publication, locale, cadcUrl parameters missing"
            assert rendered(DefaultJsonRender).object.publication == null
            assert rendered(DefaultJsonRender).object.locale == "en_GB"
            assert rendered(DefaultJsonRender).object.cadcUrl == "http://cadc/skus"
            assert rendered(DefaultJsonRender).object.status == 400
        }
    }

}
