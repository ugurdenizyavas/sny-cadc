package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class SaveFlowHandlerTest {

    final String URN = "urn:global_sku:score:en_gb:a"

    @Test
    void "main flow"() {
        def saveFlowHandler = new SaveFlowHandler()
        def invocation = handle(saveFlowHandler) {
            pathBinding([urn: URN])
            uri "/?processId=123"
            body "aaa", "application/json"
        }
        invocation.with {
            assert status.code == 202
            assert rendered(DefaultJsonRender).object.status == 202
            assert rendered(DefaultJsonRender).object.message == "sheet saved"
            assert rendered(DefaultJsonRender).object.urn == URN
            assert rendered(DefaultJsonRender).object.processId == "123"
        }
    }
}
