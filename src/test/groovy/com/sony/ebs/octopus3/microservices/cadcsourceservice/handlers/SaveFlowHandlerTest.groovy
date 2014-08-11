package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class SaveFlowHandlerTest {

    final String URN = "urn:global_sku:score:en_gb:a"

    @Test
    void "main flow"() {
        handle(new SaveFlowHandler(), {
            pathBinding([urn: URN])
            uri "/?processId=123"
            body "aaa", "application/json"
        }).with {
            assert status.code == 200
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 200
            assert ren.message == "sheet saved"
            assert ren.urn == URN
            assert ren.processId == "123"
        }
    }
}
