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
        sheetService = new SheetService(saveRepoUrl: SAVE_REPO_URL, execControl: execController.control)
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    void runFlow(String processId, String expected) {
        DeltaSheet deltaSheet = new DeltaSheet(url: "http://cadc/p", urnStr: "urn:global_sku:score:en_gb:p", processId: processId)

        sheetService.localHttpClient = mockNingHttpClient.proxyInstance()
        sheetService.cadcHttpClient = mockNingHttpClient.proxyInstance()


        def result = new BlockingVariable<String>(5)
        boolean valueSet = false
        execController.start {
            sheetService.sheetFlow(deltaSheet).subscribe({
                valueSet = true
                result.set(it)
            }, {
                log.error "error in flow", it
                result.set("error")
            }, {
                if (!valueSet)result.set("outOfFlow")
            })
        }
        assert result.get() == expected
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
                assert data == "eee"
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        runFlow(null, "success for DeltaSheet(urnStr:urn:global_sku:score:en_gb:p, url:http://cadc/p)")
    }

    @Test
    void "success with process id"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: "eee"))
            }
            doPost(1) { url, data ->
                assert url == "http://cadcsource/save/urn:global_sku:score:en_gb:p?processId=123"
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        runFlow("123", "success for DeltaSheet(urnStr:urn:global_sku:score:en_gb:p, url:http://cadc/p, processId:123)")
    }

    @Test
    void "sheet not found"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
        }
        runFlow(null, "outOfFlow")
    }

    @Test
    void "sheet could not be saved"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: "eee"))
            }
            doPost(1) { url, data ->
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
        }
        runFlow(null, "outOfFlow")
    }
}
