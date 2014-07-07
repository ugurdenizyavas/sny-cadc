package com.sony.ebs.octopus3.microservices.cadcsourceservice.http

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.junit.Before
import org.junit.Test

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Slf4j
class NingHttpClientIntegrationTest {

    HttpClientConfig httpClientConfig

    NingHttpClient ningHttpClient

    String CADC_URL = "https://origin.uat-cadc-loader-lb.sony.eu/syndication/regional/skus/changes/en_GB?since=2014-06-25T00:00:00.000%2B01:00"

    @Before
    void before() {
        httpClientConfig = new HttpClientConfig(
                proxyHost: "43.194.159.10",
                proxyPort: 10080,
                proxyUser: "TRGAEbaseProxy",
                proxyPassword: "badana01",
                authenticationUser: "eu_octopus_syndication",
                authenticationPassword: "2khj0xwb",
                authenticationHosts: "origin.uat-cadc-loader-lb.sony.eu, b, c"
        )
        def executorService = new ThreadPoolExecutor(5, 10, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>())

        ningHttpClient = new NingHttpClient(httpClientConfig: httpClientConfig, executorService: executorService)
        ningHttpClient.init()

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
        def result = ningHttpClient.getFromCadc(CADC_URL).toBlocking().single()
        validate(result)
    }

}
