package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

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
            deltaFlow(1) { String publication, String locale, String since, String cadcUrl ->
                rx.Observable.from("xxx")
            }
        }
        def deltaFlowHandler = new DeltaFlowHandler()
        deltaFlowHandler.deltaService = mock.proxyInstance()

        def invocation = handle(deltaFlowHandler) {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/?cadcUrl=http://cadc/skus&since=s1"
        }
        invocation.with {
            status.code == 202
            rendered(DefaultJsonRender).object.message == "delta import started"
            rendered(DefaultJsonRender).object.publication == "SCORE"
            rendered(DefaultJsonRender).object.locale == "en_GB"
            rendered(DefaultJsonRender).object.since == "s1"
            rendered(DefaultJsonRender).object.cadcUrl == "http://cadc/skus"
            rendered(DefaultJsonRender).object.status == 202
        }
    }
}
