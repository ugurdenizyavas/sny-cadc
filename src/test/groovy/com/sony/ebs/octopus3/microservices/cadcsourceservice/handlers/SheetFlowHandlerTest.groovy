package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.SheetService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.validators.RequestValidator
import groovy.mock.interceptor.StubFor
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class SheetFlowHandlerTest {

    final static String URN = "urn:global_sku:score:en_gb:a"
    final static String SHEET_URL = "http://cadc/a"


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
        def mockSheetService = new StubFor(SheetService)
        mockSheetService.demand.with {
            sheetFlow(1) { URN urn, String sheetUrl, ProcessId pid ->
                assert urn.toString() == URN
                assert sheetUrl == SHEET_URL
                assert pid?.id == processId?.id
                rx.Observable.from("aa")
            }
        }

        def mockRequestValidator = new StubFor(RequestValidator)
        mockRequestValidator.demand.with {
            createUrn(1) {
                assert it == URN
                new URNImpl(URN)
            }
            validateUrl(1) {
                assert it == SHEET_URL
                true
            }
        }

        handle(new SheetFlowHandler(sheetService: mockSheetService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([urn: URN])
            uri "/?url=$SHEET_URL$processIdPostfix"
        }).with {
            assert status.code == 202
            assert rendered(DefaultJsonRender).object.message == "sheet import started"
            assert rendered(DefaultJsonRender).object.urn == URN
            assert rendered(DefaultJsonRender).object.url == SHEET_URL
            assert rendered(DefaultJsonRender).object.status == 202
            assert rendered(DefaultJsonRender).object.processId == processId?.id
        }
    }

    @Test
    void "url parameter is invalid"() {
        def mockRequestValidator = new StubFor(RequestValidator)
        mockRequestValidator.demand.with {
            createUrn(1) { new URNImpl(URN) }
            validateUrl(1) { false }
        }

        handle(new SheetFlowHandler(validator: mockRequestValidator.proxyInstance()), {
            pathBinding([urn: URN])
            uri "/"
        }).with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.status == 400
            assert rendered(DefaultJsonRender).object.message == "url parameter is invalid"
        }
    }

    @Test
    void "urn parameter is invalid"() {
        def mockRequestValidator = new StubFor(RequestValidator)
        mockRequestValidator.demand.with {
            createUrn(1) {
                assert it == null
                null
            }
        }

        handle(new SheetFlowHandler(validator: mockRequestValidator.proxyInstance()), {
            uri "/?url=//aa"
        }).with {
            assert status.code == 400
            assert rendered(DefaultJsonRender).object.status == 400
            assert rendered(DefaultJsonRender).object.message == "urn parameter is invalid"
        }
    }
}
