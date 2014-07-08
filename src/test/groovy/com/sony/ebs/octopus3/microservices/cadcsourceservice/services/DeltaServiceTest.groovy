package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import org.junit.Before
import org.junit.Test

class DeltaServiceTest {

    DeltaService deltaService

    @Before
    void before() {
        deltaService = new DeltaService()
    }

    //@Test
    void "parse delta"() {
        def text = '{"skus":{"en_GB":["http://h/sku/a", "http://h/sku/b"]}}'
        def delta = deltaService.parseDelta("SCORE", "en_GB", text).toBlocking().single()
        assert delta.publication == 'SCORE'
        assert delta.locale == 'en_GB'
        assert delta.urlMap.size() == 2
        assert delta.urlMap.a == 'http://h/sku/a'
        assert delta.urlMap.b == 'http://h/sku/b'
    }

}
