package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.apache.http.client.utils.URIBuilder
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

@Slf4j
class DeltaServiceTest {

    final static String DELTA_FEED = '{"skus":{"en_GB":["http://cadc/a", "http://cadc/c", "http://cadc/b"]}}'

    final static String DELTA_FEED_NO_PRODUCTS = '{"skus":{"en_GB":[]}}'

    DeltaService deltaService
    StubFor mockDeltaUrlHelper
    MockFor mockHttpClient

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
        deltaService = new DeltaService(execControl: execController.control, cadcsourceSheetServiceUrl: "http://import/:urn")
        mockDeltaUrlHelper = new StubFor(DeltaUrlHelper)
        mockHttpClient = new MockFor(NingHttpClient)
    }

    List runFlow() {
        Delta delta = new Delta(processId: new ProcessIdImpl(), publication: "SCORE", locale: "en_GB", since: "2014", cadcUrl: "http://cadc")

        def mockHttpClientPI = mockHttpClient.proxyInstance()
        deltaService.localHttpClient = mockHttpClientPI
        deltaService.cadcHttpClient = mockHttpClientPI
        deltaService.deltaUrlHelper = mockDeltaUrlHelper.proxyInstance()

        def result = new BlockingVariable<List<String>>(5)
        execController.start {
            deltaService.deltaFlow(delta).toList().subscribe({
                result.set(it)
            }, {
                log.error "error in flow", it
                result.set(["error"])
            })
        }
        result.get()
    }

    @Test
    void "success"() {
        mockDeltaUrlHelper.demand.with {
            createDeltaUrl(1) {
                rx.Observable.just("http://cadc/delta")
            }
            getSkuFromUrl(3) { String url ->
                def sku = url.substring(url.size() - 1)
                assert url == "http://cadc/$sku"
                sku
            }
            updateLastModified(1) {
                rx.Observable.just("done")
            }
        }

        mockHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "http://cadc/delta"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: DELTA_FEED))
            }
            doGet(3) { String url ->
                def importUrl = new URIBuilder(url).queryParams[0].value
                def sku = importUrl.substring(importUrl.size() - 1)
                assert url.startsWith("http://import/urn:global_sku:score:en_gb:$sku?url=http://cadc/$sku&processId=")
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: "$sku$sku"))
            }
        }
        assert runFlow().sort() == ["success for urn:global_sku:score:en_gb:a", "success for urn:global_sku:score:en_gb:b", "success for urn:global_sku:score:en_gb:c"]
    }

    @Test
    void "no products to import"() {
        mockDeltaUrlHelper.demand.with {
            createDeltaUrl(1) {
                rx.Observable.just("http://cadc/delta")
            }
            updateLastModified(1) {
                rx.Observable.just("done")
            }
        }
        mockHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "http://cadc/delta"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: DELTA_FEED_NO_PRODUCTS))
            }
        }
        assert runFlow().sort() == ["no products to import for urn:global_sku:score:en_gb"]
    }

    @Test
    void "error getting delta"() {
        mockDeltaUrlHelper.demand.with {
            createDeltaUrl(1) {
                rx.Observable.just("http://cadc/delta")
            }
        }
        mockHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
        }
        assert runFlow() == []
    }

    @Test
    void "one sheet is not imported"() {
        mockDeltaUrlHelper.demand.with {
            createDeltaUrl(1) {
                rx.Observable.just("http://cadc/delta")
            }
            getSkuFromUrl(3) { String url ->
                def sku = url.substring(url.size() - 1)
                assert url == "http://cadc/$sku"
                sku
            }
            updateLastModified(1) {
                rx.Observable.just("done")
            }
        }

        mockHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "http://cadc/delta"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: DELTA_FEED))
            }
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
            doGet(2) {
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }

        assert runFlow().size() == 2
    }

}
