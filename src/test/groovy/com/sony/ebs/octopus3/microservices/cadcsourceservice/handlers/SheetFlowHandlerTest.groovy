package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaSheet
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.SheetService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.validators.RequestValidator
import groovy.mock.interceptor.StubFor
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class SheetFlowHandlerTest {

    final static String URN = "urn:global_sku:score:en_gb:a"
    final static String SHEET_URL = "http://cadc/a"

    StubFor mockSheetService, mockRequestValidator

    @Before
    void before() {
        mockSheetService = new StubFor(SheetService)
        mockRequestValidator = new StubFor(RequestValidator)
    }

    void runFlow(ProcessId processId, String processIdPostfix) {
        mockSheetService.demand.with {
            sheetFlow(1) { DeltaSheet deltaSheet ->
                assert deltaSheet.urnStr == URN
                assert deltaSheet.url == SHEET_URL
                assert deltaSheet.processId == processId?.id
                rx.Observable.from("xxx")
            }
        }

        mockRequestValidator.demand.with {
            validateDeltaSheet(1) { [] }
        }

        handle(new SheetFlowHandler(sheetService: mockSheetService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([urn: URN])
            uri "/?url=$SHEET_URL$processIdPostfix"
        }).with {
            assert status.code == 200
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 200
            assert ren.deltaSheet.urnStr == URN
            assert ren.deltaSheet.url == SHEET_URL
            assert ren.deltaSheet.processId == processId?.id
            assert ren.result == ["xxx"]
        }
    }

    @Test
    void "sheet flow"() {
        runFlow((ProcessId) null, "")
    }

    @Test
    void "sheet flow with process id"() {
        ProcessId processId = new ProcessIdImpl()
        runFlow(processId, "&processId=$processId.id")
    }

    @Test
    void "invalid parameter"() {
        mockRequestValidator.demand.with {
            validateDeltaSheet(1) { ["error"] }
        }

        handle(new SheetFlowHandler(sheetService: mockSheetService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
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

    @Test
    void "error in sheet flow"() {
        mockSheetService.demand.with {
            sheetFlow(1) { DeltaSheet deltaSheet ->
                deltaSheet.errors << "error in sheet flow"
                rx.Observable.just(null)
            }
        }

        mockRequestValidator.demand.with {
            validateDeltaSheet(1) { [] }
        }

        handle(new SheetFlowHandler(sheetService: mockSheetService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([urn: URN])
            uri "/?url=$SHEET_URL"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.deltaSheet.urnStr == URN
            assert ren.deltaSheet.url == SHEET_URL
            assert ren.errors == ["error in sheet flow"]
            assert !ren.result
        }
    }

}
