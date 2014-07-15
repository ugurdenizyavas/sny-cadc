package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaSheet
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
            sheetFlow(1) { DeltaSheet deltaSheet ->
                assert deltaSheet.urnStr == URN
                assert deltaSheet.url == SHEET_URL
                assert deltaSheet.processId == processId?.id
                rx.Observable.from("aa")
            }
        }

        def mockRequestValidator = new StubFor(RequestValidator)
        mockRequestValidator.demand.with {
            validateDeltaSheet(1) { [] }
        }

        handle(new SheetFlowHandler(sheetService: mockSheetService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([urn: URN])
            uri "/?url=$SHEET_URL$processIdPostfix"
        }).with {
            assert status.code == 202
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 202
            assert ren.message == "deltaSheet started"
            assert ren.deltaSheet.urnStr == URN
            assert ren.deltaSheet.url == SHEET_URL
            assert ren.deltaSheet.processId == processId?.id
        }
    }

    @Test
    void "invalid parameter"() {
        def mockRequestValidator = new StubFor(RequestValidator)
        mockRequestValidator.demand.with {
            validateDeltaSheet(1) { ["error"] }
        }

        handle(new SheetFlowHandler(validator: mockRequestValidator.proxyInstance()), {
            pathBinding([urn: URN])
            uri "/"
        }).with {
            assert status.code == 400
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 400
            assert ren.errors == ["error"]
            assert ren.deltaSheet != null
        }
    }

}
