package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaSheet
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

@Slf4j
class SheetServiceTest {

    SheetService sheetService
    StubFor mockNingHttpClient
    String SAVE_REPO_URL = "http://cadcsource/save/:urn"

    static ExecController execController

    @BeforeClass
    static void beforeClass() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
    }

    @AfterClass
    static void afterClass() {
        if (execController) execController.close()
    }

    @Before
    void before() {
        sheetService = new SheetService(repositoryFileServiceUrl: SAVE_REPO_URL, execControl: execController.control)
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    def runFlow(DeltaSheet deltaSheet) {
        sheetService.localHttpClient = mockNingHttpClient.proxyInstance()
        sheetService.cadcHttpClient = mockNingHttpClient.proxyInstance()


        def result = new BlockingVariable(5)
        boolean valueSet = false
        execController.start {
            sheetService.sheetFlow(deltaSheet).subscribe({
                valueSet = true
                result.set(it)
            }, {
                log.error "error in flow", it
                result.set("error")
            }, {
                if (!valueSet) result.set("outOfFlow")
            })
        }
        result.get()
    }


    @Test
    void "success"() {
        DeltaSheet deltaSheet = new DeltaSheet(url: "http://cadc", urnStr: "urn:a:b")
        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: "eee"))
            }
            doPost(1) { url, data ->
                assert url == "http://cadcsource/save/urn:a:b"
                assert data.text == "eee"
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runFlow(deltaSheet) == "success"
    }

    @Test
    void "success with process id"() {
        DeltaSheet deltaSheet = new DeltaSheet(url: "http://cadc/p", urnStr: "urn:a:b", processId: "123")
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: "eee"))
            }
            doPost(1) { url, data ->
                assert url == "http://cadcsource/save/urn:a:b?processId=123"
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runFlow(deltaSheet) == "success"
    }

    @Test
    void "sheet not found"() {
        DeltaSheet deltaSheet = new DeltaSheet(url: "http://cadc", urnStr: "urn:a:b")
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
        }
        assert runFlow(deltaSheet) == "outOfFlow"
        assert deltaSheet.errors == ["HTTP 404 error getting sheet json from cadc"]
    }

    @Test
    void "sheet could not be saved"() {
        DeltaSheet deltaSheet = new DeltaSheet(url: "http://cadc", urnStr: "urn:a:b")
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: "eee"))
            }
            doPost(1) { url, data ->
                rx.Observable.from(new MockNingResponse(_statusCode: 500))
            }
        }
        assert runFlow(deltaSheet) == "outOfFlow"
        assert deltaSheet.errors == ["HTTP 500 error saving sheet json to repo"]
    }
}
