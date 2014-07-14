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
        execController = LaunchConfigBuilder.noBaseDir().build().execController

        ningHttpClient = new NingHttpClient(execController.control,
                "43.194.159.10", 10080, "TRGAEbaseProxy", "badana01", "eu_octopus_syndication", "2khj0xwb")
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
            ningHttpClient.doGet(CADC_URL).subscribe { String result ->
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
