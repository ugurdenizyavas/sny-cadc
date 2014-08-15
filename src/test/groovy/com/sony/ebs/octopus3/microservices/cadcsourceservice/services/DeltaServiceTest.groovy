package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.SheetServiceResult
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

    DeltaService deltaService
    StubFor mockDeltaUrlHelper
    MockFor mockHttpClient

    Delta delta

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

        delta = new Delta(publication: "SCORE", locale: "en_GB", since: "2014", cadcUrl: "http://cadc")
    }

    List runFlow() {
        def mockHttpClientPI = mockHttpClient.proxyInstance()
        deltaService.localHttpClient = mockHttpClientPI
        deltaService.cadcHttpClient = mockHttpClientPI
        deltaService.deltaUrlHelper = mockDeltaUrlHelper.proxyInstance()

        def result = new BlockingVariable(5)
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
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: '{"skus":{"en_GB":["http://cadc/a", "http://cadc/c", "http://cadc/b"]}}'))
            }
            doGet(3) { String url ->
                def importUrl = new URIBuilder(url).queryParams[0].value
                def sku = importUrl.substring(importUrl.size() - 1)
                assert url.startsWith("http://import/urn:global_sku:score:en_gb:$sku?url=http://cadc/$sku&processId=123")
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: "success"))
            }
        }

        delta.processId = new ProcessIdImpl("123")
        def result = runFlow().sort()
        assert result.size() == 3
        assert result[0] == new SheetServiceResult(urn: "urn:global_sku:score:en_gb:a", success: true, statusCode: 200)
        assert result[1] == new SheetServiceResult(urn: "urn:global_sku:score:en_gb:b", success: true, statusCode: 200)
        assert result[2] == new SheetServiceResult(urn: "urn:global_sku:score:en_gb:c", success: true, statusCode: 200)
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
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: '{"skus":{"en_GB":[]}}'))
            }
        }
        def result = runFlow().sort()
        assert result.size() == 0
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
                rx.Observable.from(new MockNingResponse(_statusCode: 500))
            }
        }
        def result = runFlow().sort()
        assert result.size() == 0
        assert delta.errors == ["HTTP 500 error getting delta json from cadc"]
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
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: '{"skus":{"en_GB":["http://cadc/a", "http://cadc/c", "http://cadc/b"]}}'))
            }
            doGet(3) { String url ->
                if (url.endsWith("/b")) {
                    rx.Observable.from(new MockNingResponse(_statusCode: 500, _responseBody:  '{ "errors" : ["err1", "err2"]}'))
                } else {
                    rx.Observable.from(new MockNingResponse(_statusCode: 200))
                }
            }
        }
        def result = runFlow().sort()
        assert result.size() == 3
        assert result[0] == new SheetServiceResult(urn: "urn:global_sku:score:en_gb:a", success: true, statusCode: 200)
        assert result[1] == new SheetServiceResult(urn: "urn:global_sku:score:en_gb:b", success: false, statusCode: 500, errors: ["err1", "err2"])
        assert result[2] == new SheetServiceResult(urn: "urn:global_sku:score:en_gb:c", success: true, statusCode: 200)
    }

}
