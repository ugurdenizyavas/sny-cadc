package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaItem
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
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
    String SAVE_REPO_URL = "http://cadcsource/repo/:urn"
    DeltaItem deltaItem

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
        deltaItem = new DeltaItem(type: DeltaType.global_sku, publication: "SCORE", locale: "en_GB", url: "http://cadc/p")
    }

    def runFlow() {
        sheetService.localHttpClient = mockNingHttpClient.proxyInstance()
        sheetService.cadcHttpClient = mockNingHttpClient.proxyInstance()


        def result = new BlockingVariable(5)
        boolean valueSet = false
        execController.start {
            sheetService.sheetFlow(deltaItem).subscribe({
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

    def createSheetResponse(sku) {
        """
        {
            "skuName" : "$sku"
        }
        """
    }

    @Test
    void "success"() {
        def sku = "p+p/p.ceh"
        def sheetResponse = createSheetResponse(sku)
        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: sheetResponse))
            }
            doPost(1) { url, data ->
                assert url == "http://cadcsource/repo/urn:global_sku:score:en_gb:p_2bp_2fp.ceh"
                assert data == sheetResponse?.getBytes("UTF-8")
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runFlow() == [urn: "urn:global_sku:score:en_gb:p_2bp_2fp.ceh", repoUrl: "http://cadcsource/repo/urn:global_sku:score:en_gb:p_2bp_2fp.ceh"]
    }

    @Test
    void "success with process id"() {
        def sheetResponse = createSheetResponse("p")
        deltaItem.processId = "123"
        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: sheetResponse))
            }
            doPost(1) { url, data ->
                assert url == "http://cadcsource/repo/urn:global_sku:score:en_gb:p?processId=123"
                assert data == sheetResponse?.getBytes("UTF-8")
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runFlow() == [urn: "urn:global_sku:score:en_gb:p", repoUrl: "http://cadcsource/repo/urn:global_sku:score:en_gb:p"]
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
        assert deltaItem.errors == ["HTTP 404 error getting sheet json from cadc"]
    }

    @Test
    void "sheet could not be saved"() {
        def sheetResponse = createSheetResponse("p")
        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: sheetResponse))
            }
            doPost(1) { url, data ->
                assert url == "http://cadcsource/repo/urn:global_sku:score:en_gb:p"
                assert data == sheetResponse?.getBytes("UTF-8")
                rx.Observable.from(new MockNingResponse(_statusCode: 500))
            }
        }
        assert runFlow() == "outOfFlow"
        assert deltaItem.errors == ["HTTP 500 error saving sheet json to repo"]
    }

}
