package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.HttpClient
import groovy.mock.interceptor.StubFor
import org.apache.http.client.utils.URIBuilder
import org.junit.Before
import org.junit.Test

class DeltaServiceTest {

    DeltaService deltaService

    @Before
    void before() {
        def observableHelper = new ObservableHelper()
        observableHelper.init()
        deltaService = new DeltaService(observableHelper: observableHelper, importSheetUrl: "http://import")
    }

    @Test
    void "delta flow"() {
        def mockDeltaUrlBuilder = new StubFor(DeltaUrlBuilder)
        mockDeltaUrlBuilder.demand.with {
            createUrl(1) { publication, locale, since -> "/delta" }
            getSkuFromUrl(2) { String url ->
                def sku = url.endsWith("a") ? "a" : "b"
                assert url == "http://cadc/$sku"
                sku
            }
        }
        deltaService.deltaUrlBuilder = mockDeltaUrlBuilder.proxyInstance()

        ProcessId processId = new ProcessIdImpl()

        def mockHttpClient = new StubFor(HttpClient)
        mockHttpClient.demand.with {
            getFromCadc(1) {
                assert it == "http://cadc/delta"
                rx.Observable.from('{"skus":{"en_GB":["http://cadc/a", "http://cadc/b"]}}')
            }
            getLocal(2) { String url ->
                def importUrl = new URIBuilder(url).queryParams[0].value
                def sku = importUrl.endsWith("a") ? "a" : "b"
                assert url == "http://import/urn:global_sku:score:en_gb:$sku?url=http://cadc/$sku&processId=$processId.id"
                rx.Observable.from("$sku$sku")
            }
        }
        deltaService.httpClient = mockHttpClient.proxyInstance()

        def result = deltaService.deltaFlow(processId, "SCORE", "en_GB", "2014", "http://cadc").toBlocking().single()
        assert result == "[success for urn:global_sku:score:en_gb:a, success for urn:global_sku:score:en_gb:b]"
    }

}
