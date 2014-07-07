package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import org.junit.Before
import org.junit.Test

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class DeltaRetrieverTest {

    DeltaRetriever deltaRetriever

    @Before
    void before() {
        def executorService = new ThreadPoolExecutor(5, 10, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>())
        deltaRetriever = new DeltaRetriever(executorService: executorService, deltaUrlBuilder: new DeltaUrlBuilder())
    }

    @Test
    void "parse delta"() {
        def text = '{"skus":{"en_GB":["http://h/sku/a", "http://h/sku/b"]}}'
        def delta = deltaRetriever.parseDelta("SCORE", "en_GB", text).toBlocking().single()
        assert delta.publication == 'SCORE'
        assert delta.locale == 'en_GB'
        assert delta.urlMap.size() == 2
        assert delta.urlMap.a == 'http://h/sku/a'
        assert delta.urlMap.b == 'http://h/sku/b'
    }

}
