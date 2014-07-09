package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.SheetService
import groovy.mock.interceptor.MockFor
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class SheetFlowHandlerTest {

    @Test
    void "main flow"() {
        def mock = new MockFor(SheetService)
        mock.demand.with {
            sheetFlow(1) { String product, String sheetUrl ->
                assert product == "a"
                assert sheetUrl == "http://cadc/a"
                rx.Observable.from("aa")
            }
        }
        def sheetFlowHandler = new SheetFlowHandler()
        sheetFlowHandler.sheetService = mock.proxyInstance()

        def invocation = handle(sheetFlowHandler) {
            uri "/?product=a&url=http://cadc/a"
        }
        invocation.with {
            status.code == 202
            rendered(DefaultJsonRender).object.message == "sheet import started"
            rendered(DefaultJsonRender).object.product == "a"
            rendered(DefaultJsonRender).object.url == "http://cadc/a"
            rendered(DefaultJsonRender).object.status == 202
        }
    }
}
