package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.ratpack.file.FileAttribute
import com.sony.ebs.octopus3.commons.ratpack.file.FileAttributesProvider
import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
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
class DeltaUrlHelperTest {

    DeltaUrlHelper deltaUrlHelper

    StubFor mockNingHttpClient, mockFileAttributesProvider
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
        deltaUrlHelper = new DeltaUrlHelper()
        deltaUrlHelper = new DeltaUrlHelper(
                execControl: execController.control,
                repositoryFileServiceUrl: "/repository/file/:urn")
        mockNingHttpClient = new StubFor(NingHttpClient)
        mockFileAttributesProvider = new StubFor(FileAttributesProvider)

        delta = new Delta(publication: "SCORE", locale: "fr_BE")
    }

    def runUpdateLastModified() {
        deltaUrlHelper.httpClient = mockNingHttpClient.proxyInstance()

        def result = new BlockingVariable<String>(5)
        boolean valueSet = false
        execController.start {
            deltaUrlHelper.updateLastModified(delta).subscribe({
                valueSet = true
                result.set(it)
            }, {
                log.error "error", it
                result.set("error")
            }, {
                if (!valueSet) result.set("outOfFlow")
            })
        }
        result.get()
    }

    @Test
    void "update last modified"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, String data ->
                assert url == "/repository/file/urn:global_sku:last_modified:score:fr_be"
                assert data == "update"
                rx.Observable.just(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runUpdateLastModified() == "done"
    }

    @Test
    void "update last modified outOfFlow"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, String data ->
                rx.Observable.just(new MockNingResponse(_statusCode: 500))
            }
        }
        assert runUpdateLastModified() == "outOfFlow"
        assert delta.errors == ["HTTP 500 error updating last modified date"]
    }

    @Test
    void "update last modified error"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, String data ->
                throw new Exception("error updating last modified time")
            }
        }
        assert runUpdateLastModified() == "error"
    }

    @Test
    void "get sku for null"() {
        assert deltaUrlHelper.getSkuFromUrl(null) == null
    }

    @Test
    void "get sku for empty str"() {
        assert deltaUrlHelper.getSkuFromUrl("") == null
    }

    @Test
    void "get sku for no slash"() {
        assert deltaUrlHelper.getSkuFromUrl("aa") == null
    }

    @Test
    void "get sku for no sku"() {
        assert deltaUrlHelper.getSkuFromUrl("/") == null
    }

    @Test
    void "get sku for only sku"() {
        assert deltaUrlHelper.getSkuFromUrl("/x1.c") == "x1.c"
    }

    @Test
    void "get sku for prefix and sku"() {
        assert deltaUrlHelper.getSkuFromUrl("aa/x1.c") == "x1.c"
    }

    def runCreateDeltaUrl(String since) {
        deltaUrlHelper.fileAttributesProvider = mockFileAttributesProvider.proxyInstance()

        def delta = new Delta(publication: "SCORE", locale: "fr_BE", cadcUrl: "http://cadc", since: since)

        def result = new BlockingVariable<String>(5)
        boolean valueSet = false
        execController.start {
            deltaUrlHelper.createDeltaUrl(delta).subscribe({
                valueSet = true
                result.set(it)
            }, {
                log.error "error", it
                result.set("error")
            }, {
                if (!valueSet) result.set("outOfFlow")
            })
        }
        result.get()
    }

    @Test
    void "create delta url lmt found"() {
        mockFileAttributesProvider.demand.with {
            getLastModifiedTime(1) { URN urn ->
                assert urn.toString() == "urn:global_sku:last_modified:score:fr_be"
                rx.Observable.just(new FileAttribute(found: true, value: "s1"))
            }
        }
        assert runCreateDeltaUrl(null) == "http://cadc/changes/fr_BE?since=s1"
    }

    @Test
    void "create delta url lmt not found"() {
        mockFileAttributesProvider.demand.with {
            getLastModifiedTime(1) { URN urn ->
                assert urn.toString() == "urn:global_sku:last_modified:score:fr_be"
                rx.Observable.just(new FileAttribute(found: false))
            }
        }
        assert runCreateDeltaUrl(null) == "http://cadc/fr_BE"
    }

    @Test
    void "create delta url since all"() {
        assert runCreateDeltaUrl("All") == "http://cadc/fr_BE"
    }

    @Test
    void "create delta url since value"() {
        assert runCreateDeltaUrl("s2") == "http://cadc/changes/fr_BE?since=s2"
    }

    @Test
    void "create delta url since value encoding"() {
        assert runCreateDeltaUrl("2014-07-17T14:35:25.089+03:00") == "http://cadc/changes/fr_BE?since=2014-07-17T14%3A35%3A25.089%2B03%3A00"
    }

}
