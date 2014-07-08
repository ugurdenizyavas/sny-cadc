package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class SaveFlowHandlerTest {

    SaveFlowHandler saveFlowHandler

    @Before
    void before() {
        saveFlowHandler = new SaveFlowHandler()
    }

    @Test
    void "main flow"() {
        def invocation = handle(saveFlowHandler) {
            uri "/save?product=a"
            body "aaa", "application/json"
        }
        invocation.with {
            status.code == 202
            rendered(DefaultJsonRender).object.message == "product saved"
            rendered(DefaultJsonRender).object.product == "a"
            rendered(DefaultJsonRender).object.status == 202
        }
    }
}
