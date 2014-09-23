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
class DeltaItemServiceTest {

    DeltaItemService deltaItemService
    StubFor mockLocalHttpClient, mockCadcHttpClient
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
        deltaItemService = new DeltaItemService(execControl: execController.control,
                repositoryFileServiceUrl: "http://repo/:urn",
                repositoryCopyServiceUrl: "http://repo/copy/source/:source/destination/:destination"
        )
        mockLocalHttpClient = new StubFor(NingHttpClient)
        mockCadcHttpClient = new StubFor(NingHttpClient)
        deltaItem = new DeltaItem(type: DeltaType.global_sku, publication: "SCORE", locale: "en_GB", url: "http://cadc/p", processId: "123")
    }

    def runFlow() {
        deltaItemService.cadcHttpClient = mockCadcHttpClient.proxyInstance()
        deltaItemService.localHttpClient = mockLocalHttpClient.proxyInstance()

        def result = new BlockingVariable(5)
        boolean valueSet = false
        execController.start {
            deltaItemService.deltaItemFlow(deltaItem).subscribe({
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

    def createDeltaItemResponse(sku) {
        """
        {
            "skuName" : "$sku"
        }
        """
    }

    @Test
    void "success"() {
        def sku = "p+p/p.ceh"
        def deltaItemResponse = createDeltaItemResponse(sku)
        mockCadcHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: deltaItemResponse))
            }
        }
        mockLocalHttpClient.demand.with {
            doGet(1) {
                assert it == "http://repo/copy/source/urn:global_sku:score:en_gb:p_2bp_2fp.ceh/destination/urn:global_sku:previous:score:en_gb:p_2bp_2fp.ceh?processId=123"
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
            doPost(1) { url, data ->
                assert url == "http://repo/urn:global_sku:score:en_gb:p_2bp_2fp.ceh?processId=123"
                assert data == deltaItemResponse?.getBytes("UTF-8")
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runFlow() == [urn: "urn:global_sku:score:en_gb:p_2bp_2fp.ceh", repoUrl: "http://repo/urn:global_sku:score:en_gb:p_2bp_2fp.ceh"]
    }

    @Test
    void "delta item not found"() {
        mockCadcHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
        }
        assert runFlow() == "outOfFlow"
        assert deltaItem.errors == ["HTTP 404 error getting sheet from cadc"]
    }

    @Test
    void "delta item could not be saved"() {
        def deltaItemResponse = createDeltaItemResponse("p")
        mockCadcHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: deltaItemResponse))
            }
        }
        mockLocalHttpClient.demand.with {
            doGet(1) {
                assert it == "http://repo/copy/source/urn:global_sku:score:en_gb:p/destination/urn:global_sku:previous:score:en_gb:p?processId=123"
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
            doPost(1) { url, data ->
                assert url == "http://repo/urn:global_sku:score:en_gb:p?processId=123"
                assert data == deltaItemResponse?.getBytes("UTF-8")
                rx.Observable.from(new MockNingResponse(_statusCode: 500))
            }
        }
        assert runFlow() == "outOfFlow"
        assert deltaItem.errors == ["HTTP 500 error saving sheet to repo"]
    }

}
