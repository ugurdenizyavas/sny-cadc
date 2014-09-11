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

    def runCreateSinceValue(String since) {
        deltaUrlHelper.fileAttributesProvider = mockFileAttributesProvider.proxyInstance()

        def delta = new Delta(publication: "SCORE", locale: "fr_BE", cadcUrl: "http://cadc", since: since)

        def result = new BlockingVariable<String>(5)
        boolean valueSet = false
        execController.start {
            deltaUrlHelper.createSinceValue(delta).subscribe({
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
    void "create since with last modified time"() {
        mockFileAttributesProvider.demand.with {
            getLastModifiedTime(1) { URN urn ->
                assert urn.toString() == "urn:global_sku:last_modified:score:fr_be"
                rx.Observable.just(new FileAttribute(found: true, value: "s1"))
            }
        }
        assert runCreateSinceValue(null) == "s1"
    }

    @Test
    void "create since no last modified time"() {
        mockFileAttributesProvider.demand.with {
            getLastModifiedTime(1) { URN urn ->
                assert urn.toString() == "urn:global_sku:last_modified:score:fr_be"
                rx.Observable.just(new FileAttribute(found: false))
            }
        }
        assert runCreateSinceValue(null) == ""
    }

    @Test
    void "create delta with value"() {
        assert runCreateSinceValue("2014-07-17T14:35:25.089+03:00") == "2014-07-17T14:35:25.089+03:00"
    }

    @Test
    void "create since with value all"() {
        assert runCreateSinceValue("All") == "All"
    }

    def runCreateDeltaUrl(String cadcUrl, String locale, String since) {
        deltaUrlHelper.fileAttributesProvider = mockFileAttributesProvider.proxyInstance()

        def result = new BlockingVariable<String>(5)
        boolean valueSet = false
        execController.start {
            deltaUrlHelper.createDeltaUrl(cadcUrl, locale, since).subscribe({
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
    void "create delta url with since"() {
        assert runCreateDeltaUrl("http://cadc/delta", "fr_BE", "s1") == "http://cadc/delta/changes/fr_BE?since=s1"
    }

    @Test
    void "create delta url null since"() {
        assert runCreateDeltaUrl("http://cadc/delta", "fr_BE", null) == "http://cadc/delta/fr_BE"
    }

    @Test
    void "create delta url empty since"() {
        assert runCreateDeltaUrl("http://cadc/delta", "fr_BE", "") == "http://cadc/delta/fr_BE"
    }

    @Test
    void "create delta url since encoded"() {
        assert runCreateDeltaUrl("http://cadc/delta", "fr_BE", "2014-07-17T14:35:25.089+03:00") == "http://cadc/delta/changes/fr_BE?since=2014-07-17T14%3A35%3A25.089%2B03%3A00"
    }

}
