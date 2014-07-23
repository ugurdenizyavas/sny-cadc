package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

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
        execController.start {
            sheetService.sheetFlow(deltaSheet).subscribe({
                result.set(it)
            }, {
                log.error "error in flow", it
                result.set("error")
            })
        }
        assert result.get() == expected
    }


    @Test
    void "success"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from("eee")
            }
            doPost(1) { url, data ->
                assert url == "http://cadcsource/save/urn:global_sku:score:en_gb:p"
                assert data == "eee"
                rx.Observable.from("aaa")
            }
        }
        runFlow(null, "success for DeltaSheet(urnStr:urn:global_sku:score:en_gb:p, url:http://cadc/p)")
    }

    @Test
    void "success with process id"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from("eee")
            }
            doPost(1) { url, data ->
                assert url == "http://cadcsource/save/urn:global_sku:score:en_gb:p?processId=123"
                rx.Observable.from("aaa")
            }
        }
        runFlow("123", "success for DeltaSheet(urnStr:urn:global_sku:score:en_gb:p, url:http://cadc/p, processId:123)")
    }

    @Test
    void "error in get"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                throw new Exception("exp in doGet")
            }
        }
        runFlow(null, "error")
    }

    @Test
    void "error in post"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from("eee")
            }
            doPost(1) { url, data ->
                throw new Exception("exp in doPost")
            }
        }
        runFlow(null, "error")
    }
}
