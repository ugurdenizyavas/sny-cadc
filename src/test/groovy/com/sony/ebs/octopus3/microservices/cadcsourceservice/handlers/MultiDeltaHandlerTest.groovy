package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

import com.github.dreamhead.moco.Runner
import com.sony.ebs.octopus3.microservices.cadcsourceservice.service.DeltaService
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.commons.lang.math.RandomUtils
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import ratpack.groovy.test.TestHttpClient
import ratpack.groovy.test.TestHttpClients
import ratpack.jackson.JacksonModule
import ratpack.test.embed.EmbeddedApplication

import static com.github.dreamhead.moco.Moco.*
import static junit.framework.Assert.assertEquals
import static ratpack.groovy.test.embed.EmbeddedApplications.embeddedApp

/**
 * @author trbabalh
 */

@Slf4j
class MultiDeltaHandlerTest {

    static Runner externalResourceServerRunner
    static String externalResourceServerUrl

    EmbeddedApplication ratpackApp
    TestHttpClient ratpackClient

    @BeforeClass
    static void initOnce() {
        def server = httpserver(8000 + RandomUtils.nextInt(999))

        server.request(by(uri("/cadcsource/delta/publication/global/locales/en_GB"))).response("""
                {
                    "status": 200,
                    "timeStats": {
                        "start": "2014-11-13, 10:05:28.755, +0100",
                        "end": "2014-11-13, 10:06:10.824, +0100",
                        "duration": "00:42:069"
                    },
                    "result": {
                    "stats": {
                        "number of delta products": 741,
                        "number of success": 728,
                        "number of errors": 13
                    },
                    "success": [],
                    "errors": {}
                    },
                    "delta": {}
                }
            """)

        server.request(by(uri("/cadcsource/delta/publication/global/locales/de_DE"))).response("""
                {
                    "status": 404,
                    "timeStats": {
                        "start": "2014-11-13, 10:05:28.755, +0100",
                        "end": "2014-11-13, 10:06:10.824, +0100",
                        "duration": "00:42:069"
                    },
                    "result": {
                    "stats": {
                        "number of delta products": 0,
                        "number of success": 0,
                        "number of errors": 0
                    },
                    "success": [],
                    "errors": {}
                    },
                    "delta": {}
                }
            """)

        externalResourceServerRunner = Runner.runner(server)
        externalResourceServerRunner.start()
        externalResourceServerUrl = "http://localhost:${server.port()}"
    }

    @AfterClass
    static void tearDownOnce() {
        externalResourceServerRunner.stop()
    }

    def runMultiDeltaHandler(List locales) {
        ratpackApp = embeddedApp {
            bindings {
                add new JacksonModule()
                def execControl = launchConfig.execController.control

                def deltaService = new DeltaService(execControl: execControl,
                        cadcsourceDeltaServiceUrl: externalResourceServerUrl + "/cadcsource/delta/publication/:publication/locale/:locale")

                bind(DeltaService, deltaService)
            }

            handlers { DeltaService deltaService->

                def multiDeltaHandler = new MultiDeltaHandler(
                        deltaService: deltaService
                )
                get("cadcsource/delta/publication/:publication/locales/:locales", multiDeltaHandler)
            }
        }

        ratpackClient = TestHttpClients.testHttpClient(ratpackApp)
        log.info "Started multi delta handler test server on {}", ratpackApp.address

        def response = ratpackClient.getText("cadcsource/delta/publication/global/locales/" + locales?.join(','))
        log.info response
        def responseJson = new JsonSlurper().parseText(response)
        responseJson.result
    }

    @Test
    void "test multiDeltaHandler with one locale"() {
        assertEquals runMultiDeltaHandler(['en_GB']).size(), 1
    }

    @Test
    void "test multiDeltaHandler with multi locale"() {
        assertEquals runMultiDeltaHandler(['en_GB', 'de_DE']).size(), 2
    }
}
