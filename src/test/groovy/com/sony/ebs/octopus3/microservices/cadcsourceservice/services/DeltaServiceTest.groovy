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
            getProductFromUrl(1) {
                assert it == "http://cadc/a"
                "a"
            }
            getProductFromUrl(1) {
                assert it == "http://cadc/b"
                "b"
            }
        }
        deltaService.deltaUrlBuilder = mockDeltaUrlBuilder.proxyInstance()

        def mockHttpClient = new MockFor(HttpClient)
        mockHttpClient.demand.with {
            getFromCadc(1) {
                assert it == "http://cadc/delta"
                rx.Observable.from('{"skus":{"en_GB":["http://cadc/a", "http://cadc/b"]}}')
            }
            getLocal(1) {
                assert it == "http://import?product=a&url=http://cadc/a"
                rx.Observable.from("aa")
            }
            getLocal(1) {
                assert it == "http://import?product=b&url=http://cadc/b"
                rx.Observable.from("bb")
            }
        }
        deltaService.httpClient = mockHttpClient.proxyInstance()

        def result = deltaService.deltaFlow("SCORE", "en_GB", "2014", "http://cadc").toBlocking().single()
        assert result == "[success for a, success for b]"
    }

}
