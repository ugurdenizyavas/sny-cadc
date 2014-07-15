package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.DeltaCollaborator
import groovy.mock.interceptor.StubFor
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

class SaveFlowHandlerTest {

    final String URN = "urn:global_sku:score:en_gb:a"

    @Test
    void "main flow"() {
        def mockDeltaCollaborator = new StubFor(DeltaCollaborator)
        mockDeltaCollaborator.demand.with {
            storeUrn(1) { urn, text -> }
        }
        handle(new SaveFlowHandler(deltaCollaborator: mockDeltaCollaborator.proxyInstance()), {
            pathBinding([urn: URN])
            uri "/?processId=123"
            body "aaa", "application/json"
        }).with {
            assert status.code == 202
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 202
            assert ren.message == "sheet saved"
            assert ren.urn == URN
            assert ren.processId == "123"
        }
    }
}
