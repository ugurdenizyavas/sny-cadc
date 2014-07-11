package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.HttpClient
import groovy.mock.interceptor.StubFor
import org.apache.http.client.utils.URIBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder

class DeltaServiceTest {

    DeltaService deltaService
    ExecController execController

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        deltaService = new DeltaService(execControl: execController.control, importSheetUrl: "http://import")
    }

    @After
    void after() {
        if (execController) execController.close()
    }

    @Test
    void "delta flow"() {
        def mockDeltaCollaborator = new StubFor(DeltaCollaborator)
        mockDeltaCollaborator.demand.with {
            createUrl(1) { publication, locale, since -> "/delta" }
            getSkuFromUrl(2) { String url ->
                def sku = url.endsWith("a") ? "a" : "b"
                assert url == "http://cadc/$sku"
                sku
            }
            storeDelta(1) { publication, locale, text -> }
        }
        deltaService.deltaCollaborator = mockDeltaCollaborator.proxyInstance()

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

        def finished = new Object()
        execController.start {
            deltaService.deltaFlow(processId, "SCORE", "en_GB", "2014", "http://cadc").subscribe { String result ->
                synchronized (finished) {
                    assert result == "[success for urn:global_sku:score:en_gb:a, success for urn:global_sku:score:en_gb:b]"
                    finished.notifyAll()
                }
            }
        }
        synchronized (finished) {
            finished.wait 5000
        }
    }

}
