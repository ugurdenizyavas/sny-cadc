package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaItem
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.SheetService
import groovy.mock.interceptor.StubFor
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class SheetFlowHandlerTest {

    final static String PUBLICATION = "SCORE"
    final static String LOCALE = "en_GB"
    final static String SHEET_URL = "http://cadc/a"

    StubFor mockSheetService, mockRequestValidator

    @Before
    void before() {
        mockSheetService = new StubFor(SheetService)
        mockRequestValidator = new StubFor(RequestValidator)
    }

    void runFlow(ProcessId processId, String processIdPostfix) {
        mockSheetService.demand.with {
            sheetFlow(1) { DeltaItem deltaItem ->
                assert deltaItem.publication == PUBLICATION
                assert deltaItem.locale == LOCALE
                assert deltaItem.url == SHEET_URL
                assert deltaItem.processId == processId?.id
                rx.Observable.from("xxx")
            }
        }

        mockRequestValidator.demand.with {
            validateDeltaItem(1) { [] }
        }

        handle(new SheetFlowHandler(sheetService: mockSheetService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([publication: PUBLICATION, locale: LOCALE])
            uri "/?url=$SHEET_URL$processIdPostfix"
        }).with {
            assert status.code == 200
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 200
            assert ren.deltaItem.publication == PUBLICATION
            assert ren.deltaItem.locale == LOCALE
            assert ren.deltaItem.url == SHEET_URL
            assert ren.deltaItem.processId == processId?.id
            assert ren.result == "xxx"
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
            validateDeltaItem(1) { ["error"] }
        }

        handle(new SheetFlowHandler(sheetService: mockSheetService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([publication: PUBLICATION, locale: LOCALE])
            uri "/"
        }).with {
            assert status.code == 400
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 400
            assert ren.errors == ["error"]
            assert ren.deltaItem != null
        }
    }

    @Test
    void "error in sheet flow"() {
        mockSheetService.demand.with {
            sheetFlow(1) { DeltaItem deltaItem ->
                deltaItem.errors << "error in sheet flow"
                rx.Observable.just(null)
            }
        }

        mockRequestValidator.demand.with {
            validateDeltaItem(1) { [] }
        }

        handle(new SheetFlowHandler(sheetService: mockSheetService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([publication: PUBLICATION, locale: LOCALE])
            uri "/?url=$SHEET_URL"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.deltaItem.publication == PUBLICATION
            assert ren.deltaItem.locale == LOCALE
            assert ren.deltaItem.url == SHEET_URL
            assert ren.errors == ["error in sheet flow"]
            assert !ren.result
        }
    }

    @Test
    void "exception in sheet flow"() {
        mockSheetService.demand.with {
            sheetFlow(1) {
                rx.Observable.just("starting").map({
                    throw new Exception("exp in sheet flow")
                })
            }
        }
        mockRequestValidator.demand.with {
            validateDeltaItem(1) { [] }
        }

        handle(new SheetFlowHandler(sheetService: mockSheetService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([publication: PUBLICATION, locale: LOCALE])
            uri "/?url=$SHEET_URL"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.deltaItem.publication == PUBLICATION
            assert ren.deltaItem.locale == LOCALE
            assert ren.deltaItem.url == SHEET_URL
            assert ren.errors == ["exp in sheet flow"]
            assert !ren.result
        }
    }
}
