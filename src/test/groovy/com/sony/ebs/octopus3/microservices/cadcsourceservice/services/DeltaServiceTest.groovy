package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient

import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.SheetServiceResult
import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.apache.http.client.utils.URIBuilder
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

@Slf4j
class DeltaServiceTest {

    DeltaService deltaService

    @Before
    void before() {
        deltaService = new DeltaService()
    }

    @Test
    void "test success"() {
        def response = new MockNingResponse(_statusCode: 200, _responseBody: '{"result" : { "repoUrl" : "http://repo" } }')
        def result = deltaService.createServiceResult(response, "http://cadc")
        result.with {
            assert success == true
            assert statusCode == 200
            assert cadcUrl == "http://cadc"
            assert repoUrl == "http://repo"
        }
    }

    @Test
    void "test failure"() {
        def response = new MockNingResponse(_statusCode: 500, _responseBody: '{"errors" : [ "err1", "err2" ] }')
        def result = deltaService.createServiceResult(response, "http://cadc")
        result.with {
            assert success == false
            assert statusCode == 500
            assert cadcUrl == "http://cadc"
            assert errors == ["err1", "err2"]
        }
    }

    @Test
    void "test error"() {
        def result = deltaService.createServiceResultOnError("err1", "http://cadc")
        result.with {
            assert success == false
            assert cadcUrl == "http://cadc"
            assert errors == ["err1"]
        }
    }
}
