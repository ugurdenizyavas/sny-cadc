package com.sony.ebs.octopus3.microservices.cadcsourceservice.service

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaUrlHelper
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import groovyx.net.http.URIBuilder
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

@Slf4j
class DeltaServiceTest {

    final static String DELTA_URL = "http://cadc/delta"
    final static String START_DATE = "s1"

    DeltaService deltaService
    StubFor mockDeltaUrlHelper, mockCadcHttpClient, mockLocalHttpClient

    CadcDelta delta
    DeltaResult deltaResult

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
        deltaService = new DeltaService(
                execControl: execController.control,
                cadcsourceProductServiceUrl: "http://cadcsource/product/publication/:publication/locale/:locale"
        )
        mockDeltaUrlHelper = new StubFor(DeltaUrlHelper)
        mockCadcHttpClient = new StubFor(Oct3HttpClient)
        mockLocalHttpClient = new StubFor(Oct3HttpClient)

        delta = new CadcDelta(type: RepoValue.global_sku, publication: "SCORE", locale: "en_GB", sdate: "2014", cadcUrl: "http://cadc")
        deltaResult = new DeltaResult()
    }

    List runFlow() {
        deltaService.cadcHttpClient = mockCadcHttpClient.proxyInstance()
        deltaService.localHttpClient = mockLocalHttpClient.proxyInstance()
        deltaService.deltaUrlHelper = mockDeltaUrlHelper.proxyInstance()

        def result = new BlockingVariable(5)
        execController.start {
            deltaService.processDelta(delta, deltaResult).toList().subscribe({
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
        """.bytes
    }

    def createProductResponse(sku) {
        """
         {
            "result" : {
                "inputUrl" : "http://cadc/${sku}",
                "outputUrn" : "urn:global_sku:score:en_gb:${sku}",
                "outputUrl" : "//repo/file/urn:global_sku:score:en_gb:${sku}"
            }
         }'
        """
    }

    String getSkuFromUrl(url) {
        String importUrl = new URIBuilder(url).query.url
        def sku = importUrl.substring(importUrl.size() - 1)
        sku
    }

    @Test
    void "success"() {
        mockDeltaUrlHelper.demand.with {
            createStartDate(1) { sdate, lastModifiedUrn ->
                rx.Observable.just(START_DATE)
            }
            createCadcDeltaUrl(1) { cadcUrl, locale, sdate ->
                assert cadcUrl == "http://cadc"
                assert locale == "en_GB"
                assert sdate == START_DATE
                rx.Observable.just(DELTA_URL)
            }
            updateLastModified(1) { lastModifiedUrn, errors ->
                rx.Observable.just("done")
            }
        }
        mockCadcHttpClient.demand.with {
            doGet(1) { url ->
                assert url == DELTA_URL
                rx.Observable.from(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: createDeltaResponse()))
            }
        }
        mockLocalHttpClient.demand.with {
            doGet(3) { String url ->
                def sku = getSkuFromUrl(url)
                assert url == "http://cadcsource/product/publication/SCORE/locale/en_GB?url=http%3A%2F%2Fcadc%2F$sku&processId=123"
                rx.Observable.from(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: createProductResponse(sku).bytes))
            }
        }

        delta.processId = new ProcessIdImpl("123")
        List<ProductResult> result = runFlow().sort({ it.inputUrl })
        assert result.size() == 3

        result[0].with {
            assert inputUrl == "http://cadc/a"
            assert outputUrn == "urn:global_sku:score:en_gb:a"
            assert outputUrl == "//repo/file/urn:global_sku:score:en_gb:a"
            assert success
            assert statusCode == 200
        }
        result[1].with {
            assert inputUrl == "http://cadc/b"
            assert outputUrn == "urn:global_sku:score:en_gb:b"
            assert outputUrl == "//repo/file/urn:global_sku:score:en_gb:b"
            assert success
            assert statusCode == 200
        }
        result[2].with {
            assert inputUrl == "http://cadc/c"
            assert outputUrn == "urn:global_sku:score:en_gb:c"
            assert outputUrl == "//repo/file/urn:global_sku:score:en_gb:c"
            assert success
            assert statusCode == 200
        }

        assert deltaResult.deltaUrls == ["http://cadc/a", "http://cadc/c", "http://cadc/b"]
        assert deltaResult.finalDeltaUrl == DELTA_URL
        assert deltaResult.finalStartDate == START_DATE
    }

    @Test
    void "no products to import"() {
        mockDeltaUrlHelper.demand.with {
            createStartDate(1) { sdate, lastModifiedUrn ->
                rx.Observable.just(START_DATE)
            }
            createCadcDeltaUrl(1) { cadcUrl, locale, sdate ->
                rx.Observable.just(DELTA_URL)
            }
            updateLastModified(1) { lastModifiedUrn, errors ->
                rx.Observable.just("done")
            }
        }
        mockCadcHttpClient.demand.with {
            doGet(1) { String url ->
                rx.Observable.from(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: '{"skus":{"en_GB":[]}}'.bytes))
            }
        }
        def result = runFlow()
        assert result.size() == 0

        assert !deltaResult.deltaUrls
        assert deltaResult.finalDeltaUrl == DELTA_URL
        assert deltaResult.finalStartDate == START_DATE
    }

    @Test
    void "error getting delta"() {
        mockDeltaUrlHelper.demand.with {
            createStartDate(1) { sdate, lastModifiedUrn ->
                rx.Observable.just(START_DATE)
            }
            createCadcDeltaUrl(1) { cadcUrl, locale, sdate ->
                rx.Observable.just(DELTA_URL)
            }
        }
        mockCadcHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new Oct3HttpResponse(statusCode: 500))
            }
        }
        def result = runFlow()
        assert result.size() == 0
        assert deltaResult.errors == ["HTTP 500 error getting delta from cadc"]
        assert !deltaResult.deltaUrls
        assert deltaResult.finalDeltaUrl == DELTA_URL
        assert deltaResult.finalStartDate == START_DATE
    }

    @Test
    void "error parsing delta"() {
        mockDeltaUrlHelper.demand.with {
            createStartDate(1) { sdate, lastModifiedUrn ->
                rx.Observable.just(START_DATE)
            }
            createCadcDeltaUrl(1) { cadcUrl, locale, sdate ->
                rx.Observable.just(DELTA_URL)
            }
        }
        mockCadcHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == DELTA_URL
                rx.Observable.from(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: 'invalid json'.bytes))
            }
        }
        def result = runFlow()
        assert result == ["error parsing delta"]
        assert !deltaResult.deltaUrls
        assert deltaResult.errors == []
        assert deltaResult.finalDeltaUrl == DELTA_URL
        assert deltaResult.finalStartDate == START_DATE
    }

    @Test
    void "error updating last modified date"() {
        mockDeltaUrlHelper.demand.with {
            createStartDate(1) { sdate, lastModifiedUrn ->
                rx.Observable.just(START_DATE)
            }
            createCadcDeltaUrl(1) { cadcUrl, locale, sdate ->
                rx.Observable.just(DELTA_URL)
            }
            updateLastModified(1) { lastModifiedUrn, errors ->
                rx.Observable.just("error").filter({
                    deltaResult.errors << "error updating last modified date"
                    false
                })
            }
        }
        mockCadcHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == DELTA_URL
                rx.Observable.from(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: '{"skus":{"en_GB":["http://cadc/a", "http://cadc/c", "http://cadc/b"]}}'.bytes))
            }
        }
        def result = runFlow()
        assert result.size() == 0
        assert deltaResult.deltaUrls == ["http://cadc/a", "http://cadc/c", "http://cadc/b"]
        assert deltaResult.errors == ["error updating last modified date"]
        assert deltaResult.finalDeltaUrl == DELTA_URL
        assert deltaResult.finalStartDate == START_DATE
    }

    @Test
    void "one delta item is not imported"() {
        mockDeltaUrlHelper.demand.with {
            createStartDate(1) { sdate, lastModifiedUrn ->
                rx.Observable.just(START_DATE)
            }
            createCadcDeltaUrl(1) { cadcUrl, locale, sdate ->
                rx.Observable.just(DELTA_URL)
            }
            updateLastModified(1) { lastModifiedUrn, errors ->
                rx.Observable.just("done")
            }
        }
        mockCadcHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == DELTA_URL
                rx.Observable.from(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: '{"skus":{"en_GB":["http://cadc/a", "http://cadc/c", "http://cadc/b"]}}'.bytes))
            }
        }
        mockLocalHttpClient.demand.with {
            doGet(3) { String url ->
                def sku = getSkuFromUrl(url)
                assert url == "http://cadcsource/product/publication/SCORE/locale/en_GB?url=http%3A%2F%2Fcadc%2F$sku"
                if (sku == "b") {
                    rx.Observable.from(new Oct3HttpResponse(statusCode: 500, bodyAsBytes: '{ "errors" : ["err1", "err2"]}'.bytes))
                } else {
                    rx.Observable.from(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: createProductResponse(sku).bytes))
                }
            }
        }
        List<ProductResult> result = runFlow().sort({ it.inputUrl })
        assert result.size() == 3
        result[0].with {
            assert inputUrl == "http://cadc/a"
            assert outputUrn == "urn:global_sku:score:en_gb:a"
            assert outputUrl == "//repo/file/urn:global_sku:score:en_gb:a"
            assert success
            assert statusCode == 200
        }
        result[1].with {
            assert inputUrl == "http://cadc/b"
            assert !outputUrn
            assert !outputUrl
            assert !success
            assert statusCode == 500
            assert errors == ["err1", "err2"]
        }
        result[2].with {
            assert inputUrl == "http://cadc/c"
            assert outputUrn == "urn:global_sku:score:en_gb:c"
            assert outputUrl == "//repo/file/urn:global_sku:score:en_gb:c"
            assert success
            assert statusCode == 200
        }

        assert deltaResult.deltaUrls == ["http://cadc/a", "http://cadc/c", "http://cadc/b"]
        assert deltaResult.errors == []
        assert deltaResult.finalDeltaUrl == DELTA_URL
        assert deltaResult.finalStartDate == START_DATE
    }

}
