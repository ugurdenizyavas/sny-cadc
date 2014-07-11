package com.sony.ebs.octopus3.microservices.cadcsourceservice.http

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder

@Ignore
@Slf4j
class NingHttpClientIntegrationTest {

    ExecController execController
    NingHttpClient ningHttpClient

    String CADC_URL = "https://origin.uat-cadc-loader-lb.sony.eu/syndication/regional/skus/changes/en_GB?since=2014-06-25T00:00:00.000%2B01:00"

    @Before
    void before() {
        def httpClientConfig = new HttpClientConfig(
                proxyHost: "43.194.159.10",
                proxyPort: 10080,
                proxyUser: "TRGAEbaseProxy",
                proxyPassword: "badana01",
                authenticationUser: "eu_octopus_syndication",
                authenticationPassword: "2khj0xwb",
                authenticationHosts: "origin.uat-cadc-loader-lb.sony.eu, b, c"
        )

        execController = LaunchConfigBuilder.noBaseDir().build().execController

        ningHttpClient = new NingHttpClient(httpClientConfig: httpClientConfig, execControl: execController.control)
        ningHttpClient.init()
    }

    @After
    void after() {
        if (execController) execController.close()
    }

    def validate(String result) {
        log.info "validating $result"
        def json = new JsonSlurper().parseText(result)
        assert json.startDate
        assert json.endDate
        assert json.skus['en_GB'].size() > 0
    }

    @Test
    void "test ningHttpClient"() {
        def finished = new Object()
        execController.start {
            ningHttpClient.getFromCadc(CADC_URL).subscribe { String result ->
                synchronized (finished) {
                    validate(result)
                    finished.notifyAll()
                }
            }
        }
        synchronized (finished) {
            finished.wait 10000
        }
    }

}
