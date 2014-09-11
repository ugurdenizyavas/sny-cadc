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
        deltaService = new DeltaService(execControl: execController.control
                , cadcsourceSheetServiceUrl: "http://cadcsource/sheet/publication/:publication/locale/:locale"
                , repositoryFileServiceUrl: "http://repo/file/:urn")
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
                result.set([it.message])
            })
        }
        result.get()
    }

    def createDeltaResponse() {
        """
        {
            "skus" : {
                "en_GB" : [
                    "http://cadc/a",
                    "http://cadc/c",
                    "http://cadc/b"
                ]
            }
        }
        """
    }

    def createSheetResponse(sku) {
        """
        {
            "deltaSheet" : {
                "urnStr" : "urn:global_sku:score:en_gb:$sku"
            }
        }
        """
    }

    def getSkuFromUrl(url) {
        def importUrl = new URIBuilder(url).queryParams[0].value
        def sku = importUrl.substring(importUrl.size() - 1)
        sku
    }

    @Test
    void "success"() {
        mockDeltaUrlHelper.demand.with {
            createSinceValue(1) {
                assert it == delta
                rx.Observable.just("s1")
            }
            createDeltaUrl(1) { cadcUrl, locale, since ->
                assert cadcUrl == "http://cadc"
                assert locale == "en_GB"
                assert since == "s1"
                rx.Observable.just("http://cadc/delta")
            }
            updateLastModified(1) {
                rx.Observable.just("done")
            }
        }

        mockHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "http://cadc/delta"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: createDeltaResponse()))
            }
            doGet(3) { String url ->
                def sku = getSkuFromUrl(url)
                assert url == "http://cadcsource/sheet/publication/SCORE/locale/en_GB?url=http://cadc/$sku&processId=123"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: createSheetResponse(sku)))
            }
        }

        delta.processId = new ProcessIdImpl("123")
        List<SheetServiceResult> result = runFlow().sort()
        assert result.size() == 3

        assert result[0] == new SheetServiceResult(cadcUrl: "http://cadc/a", repoUrl: "http://repo/file/urn:global_sku:score:en_gb:a", success: true, statusCode: 200)
        assert result[1] == new SheetServiceResult(cadcUrl: "http://cadc/b", repoUrl: "http://repo/file/urn:global_sku:score:en_gb:b", success: true, statusCode: 200)
        assert result[2] == new SheetServiceResult(cadcUrl: "http://cadc/c", repoUrl: "http://repo/file/urn:global_sku:score:en_gb:c", success: true, statusCode: 200)

        assert delta.finalCadcUrl == "http://cadc/delta"
        assert delta.finalSince == "s1"
    }

    @Test
    void "no products to import"() {
        mockDeltaUrlHelper.demand.with {
            createSinceValue(1) {
                rx.Observable.just("s1")
            }
            createDeltaUrl(1) { cadcUrl, locale, since ->
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
        def result = runFlow()
        assert result.size() == 0
        assert delta.finalCadcUrl == "http://cadc/delta"
    }

    @Test
    void "error getting delta"() {
        mockDeltaUrlHelper.demand.with {
            createSinceValue(1) {
                rx.Observable.just("s1")
            }
            createDeltaUrl(1) { cadcUrl, locale, since ->
                rx.Observable.just("http://cadc/delta")
            }
        }
        mockHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 500))
            }
        }
        def result = runFlow()
        assert result.size() == 0
        assert delta.errors == ["HTTP 500 error getting delta json from cadc"]
    }

    @Test
    void "error parsing cadc delta json"() {
        mockDeltaUrlHelper.demand.with {
            createSinceValue(1) {
                rx.Observable.just("s1")
            }
            createDeltaUrl(1) { cadcUrl, locale, since ->
                rx.Observable.just("http://cadc/delta")
            }
        }
        mockHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "http://cadc/delta"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: 'invalid json'))
            }
        }
        def result = runFlow()
        assert result == ["error parsing cadc delta json"]
    }

    @Test
    void "error updating last modified date"() {
        mockDeltaUrlHelper.demand.with {
            createSinceValue(1) {
                rx.Observable.just("s1")
            }
            createDeltaUrl(1) { cadcUrl, locale, since ->
                rx.Observable.just("http://cadc/delta")
            }
            updateLastModified(1) { Delta delta ->
                rx.Observable.just("error").filter({
                    delta.errors << "error updating last modified date"
                    false
                })
            }
        }
        mockHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "http://cadc/delta"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: '{"skus":{"en_GB":["http://cadc/a", "http://cadc/c", "http://cadc/b"]}}'))
            }
        }
        def result = runFlow()
        assert result.size() == 0
        assert delta.errors == ["error updating last modified date"]
    }

    @Test
    void "one sheet is not imported"() {
        mockDeltaUrlHelper.demand.with {
            createSinceValue(1) {
                rx.Observable.just("s1")
            }
            createDeltaUrl(1) { cadcUrl, locale, since ->
                rx.Observable.just("http://cadc/delta")
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
                def sku = getSkuFromUrl(url)
                assert url == "http://cadcsource/sheet/publication/SCORE/locale/en_GB?url=http://cadc/$sku"
                if (sku == "b") {
                    rx.Observable.from(new MockNingResponse(_statusCode: 500, _responseBody: '{ "errors" : ["err1", "err2"]}'))
                } else {
                    rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: createSheetResponse(sku)))
                }
            }
        }
        def result = runFlow().sort()
        assert result.size() == 3
        assert result[0] == new SheetServiceResult(cadcUrl: "http://cadc/a", success: true, statusCode: 200, repoUrl: "http://repo/file/urn:global_sku:score:en_gb:a")
        assert result[1] == new SheetServiceResult(cadcUrl: "http://cadc/b", success: false, statusCode: 500, errors: ["err1", "err2"])
        assert result[2] == new SheetServiceResult(cadcUrl: "http://cadc/c", success: true, statusCode: 200, repoUrl: "http://repo/file/urn:global_sku:score:en_gb:c")

        assert delta.finalCadcUrl == "http://cadc/delta"
        assert delta.finalSince == "s1"
    }

}
