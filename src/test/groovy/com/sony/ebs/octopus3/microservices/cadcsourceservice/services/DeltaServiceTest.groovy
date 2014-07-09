package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.HttpClient
import groovy.mock.interceptor.MockFor
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
        def mockDeltaUrlBuilder = new MockFor(DeltaUrlBuilder)
        mockDeltaUrlBuilder.demand.with {
            createUrl(1) { publication, locale, since -> "/delta" }
            getProductFromUrl(2) { String url ->
                def product = url.endsWith("a") ? "a" : "b"
                assert url == "http://cadc/$product"
                product
            }
        }
        deltaService.deltaUrlBuilder = mockDeltaUrlBuilder.proxyInstance()

        def mockHttpClient = new MockFor(HttpClient)
        mockHttpClient.demand.with {
            getFromCadc(1) {
                assert it == "http://cadc/delta"
                rx.Observable.from('{"skus":{"en_GB":["http://cadc/a", "http://cadc/b"]}}')
            }
            getLocal(2) { String url ->
                def product = url.endsWith("a") ? "a" : "b"
                assert url == "http://import?product=$product&url=http://cadc/$product"
                rx.Observable.from("$product$product")
            }
        }
        deltaService.httpClient = mockHttpClient.proxyInstance()

        def result = deltaService.deltaFlow("SCORE", "en_GB", "2014", "http://cadc").toBlocking().single()
        assert result == "[success for a, success for b]"
    }

}
