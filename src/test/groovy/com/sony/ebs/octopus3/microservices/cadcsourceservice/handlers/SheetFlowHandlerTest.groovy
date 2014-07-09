package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.SheetService
import groovy.mock.interceptor.MockFor
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class SheetFlowHandlerTest {

    final String URN = "urn:global_sku:score:en_gb:a"

    @Test
    void "main flow"() {
        def mock = new MockFor(SheetService)
        mock.demand.with {
            sheetFlow(1) { URN urn, String sheetUrl ->
                assert urn.toString() == URN
                assert sheetUrl == "http://cadc/a"
                rx.Observable.from("aa")
            }
        }
        def sheetFlowHandler = new SheetFlowHandler()
        sheetFlowHandler.sheetService = mock.proxyInstance()

        def invocation = handle(sheetFlowHandler) {
            pathBinding([urn: URN])
            uri "/?url=http://cadc/a"
        }
        invocation.with {
            assert status.code == 202
            assert rendered(DefaultJsonRender).object.message == "sheet import started"
            assert rendered(DefaultJsonRender).object.urn == URN
            assert rendered(DefaultJsonRender).object.url == "http://cadc/a"
            assert rendered(DefaultJsonRender).object.status == 202
        }
    }

    @Test
    void "missing parameter"() {
        def invocation = handle(new SheetFlowHandler()) {
            pathBinding([urn: URN])
            uri "/"
        }
        invocation.with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.message == "one of urn, url parameters missing"
            assert rendered(DefaultJsonRender).object.urn == URN
            assert rendered(DefaultJsonRender).object.url == null
            assert rendered(DefaultJsonRender).object.status == 400
        }
    }

}
