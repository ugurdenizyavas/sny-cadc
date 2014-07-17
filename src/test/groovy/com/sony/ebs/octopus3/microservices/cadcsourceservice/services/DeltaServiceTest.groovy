package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.apache.http.client.utils.URIBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder

@Slf4j
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
        Delta delta = new Delta(processId: new ProcessIdImpl(), publication: "SCORE", locale: "en_GB", since: "2014", cadcUrl: "http://cadc")

        def mockDeltaCollaborator = new StubFor(DeltaCollaborator)
        mockDeltaCollaborator.demand.with {
            createUrl(1) { d -> "/delta" }
            getSkuFromUrl(3) { String url ->
                def sku = url.substring(url.size() - 1)
                assert url == "http://cadc/$sku"
                sku
            }
            storeDelta(1) { d, text -> }
        }
        deltaService.deltaCollaborator = mockDeltaCollaborator.proxyInstance()

        def mockHttpClient = new StubFor(NingHttpClient)
        mockHttpClient.demand.with {
            doGet(4) { String url ->
                if (url.endsWith("/delta")) {
                    assert url == "http://cadc/delta"
                    rx.Observable.from('{"skus":{"en_GB":["http://cadc/a", "http://cadc/c", "http://cadc/b"]}}')
                } else {
                    def importUrl = new URIBuilder(url).queryParams[0].value
                    def sku = importUrl.substring(importUrl.size() - 1)
                    assert url == "http://import/urn:global_sku:score:en_gb:$sku?url=http://cadc/$sku&processId=$delta.processId.id"
                    rx.Observable.from("$sku$sku")
                }
            }
        }
        deltaService.localHttpClient = mockHttpClient.proxyInstance()
        deltaService.cadcHttpClient = mockHttpClient.proxyInstance()

        def finished = new Object()
        def result = [].asSynchronized()
        execController.start {
            deltaService.deltaFlow(delta).subscribe { String res ->
                synchronized (finished) {
                    result << res
                    finished.notifyAll()
                }
            }
        }
        synchronized (finished) {
            finished.wait 5000
        }
        assert result.sort() == ["success for urn:global_sku:score:en_gb:a", "success for urn:global_sku:score:en_gb:b", "success for urn:global_sku:score:en_gb:c"]
    }

}
