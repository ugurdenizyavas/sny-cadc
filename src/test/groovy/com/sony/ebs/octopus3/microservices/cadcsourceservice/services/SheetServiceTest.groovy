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
    DeltaSheet deltaSheet

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
        deltaSheet = new DeltaSheet(publication: "SCORE", locale: "en_GB", url: "http://cadc/p")
    }

    def runFlow() {
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
        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: "eee"))
            }
            doPost(1) { url, data ->
                assert url == "http://cadcsource/save/urn:global_sku:score:en_gb:p"
                assert data.text == "eee"
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runFlow() == "success"
    }

    @Test
    void "success with process id"() {
        deltaSheet.processId = "123"
        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: "eee"))
            }
            doPost(1) { url, data ->
                assert url == "http://cadcsource/save/urn:global_sku:score:en_gb:p?processId=123"
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runFlow() == "success"
    }

    @Test
    void "sheet not found"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
        }
        assert runFlow() == "outOfFlow"
        assert deltaSheet.errors == ["HTTP 404 error getting sheet json from cadc"]
    }

    @Test
    void "sheet could not be saved"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: "eee"))
            }
            doPost(1) { url, data ->
                assert url == "http://cadcsource/save/urn:global_sku:score:en_gb:p"
                rx.Observable.from(new MockNingResponse(_statusCode: 500))
            }
        }
        assert runFlow() == "outOfFlow"
        assert deltaSheet.errors == ["HTTP 500 error saving sheet json to repo"]
    }

}
