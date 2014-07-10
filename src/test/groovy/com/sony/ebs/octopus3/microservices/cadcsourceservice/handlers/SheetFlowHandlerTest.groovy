package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.SheetService
import groovy.mock.interceptor.MockFor
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class SheetFlowHandlerTest {

    final String URN = "urn:global_sku:score:en_gb:a"

    @Test
    void "sheet flow"() {
        "run sheet flow"((ProcessId) null, "")
    }

    @Test
    void "sheet flow with process id"() {
        ProcessId processId = new ProcessIdImpl()
        "run sheet flow"(processId, "&processId=$processId.id")
    }

    void "run sheet flow"(ProcessId processId, String processIdPostfix) {
        def mock = new MockFor(SheetService)
        mock.demand.with {
            sheetFlow(1) { URN urn, String sheetUrl, ProcessId pid ->
                assert urn.toString() == URN
                assert sheetUrl == "http://cadc/a"
                assert pid?.id == processId?.id
                rx.Observable.from("aa")
            }
        }

        handle(new SheetFlowHandler(sheetService: mock.proxyInstance()), {
            pathBinding([urn: URN])
            uri "/?url=http://cadc/a$processIdPostfix"
        }).with {
            assert status.code == 202
            assert rendered(DefaultJsonRender).object.message == "sheet import started"
            assert rendered(DefaultJsonRender).object.urn == URN
            assert rendered(DefaultJsonRender).object.url == "http://cadc/a"
            assert rendered(DefaultJsonRender).object.status == 202
            assert rendered(DefaultJsonRender).object.processId == processId?.id
        }
    }

    @Test
    void "url parameter missing"() {
        handle(new SheetFlowHandler(), {
            pathBinding([urn: URN])
            uri "/"
        }).with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.status == 400
            assert rendered(DefaultJsonRender).object.message == "url parameter is required"
        }
    }

    @Test
    void "urn parameter missing"() {
        handle(new SheetFlowHandler(), {
            uri "/?url=//aa"
        }).with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.status == 400
            assert rendered(DefaultJsonRender).object.message == "urn parameter is required"
        }
    }
}
