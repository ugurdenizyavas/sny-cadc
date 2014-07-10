package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.HttpClient
import groovy.mock.interceptor.StubFor
import org.junit.Before
import org.junit.Test

class SheetServiceTest {

    SheetService sheetService
    String SAVE_REPO_URL = "http://save/repo"

    @Before
    void before() {
        sheetService = new SheetService(saveRepoUrl: SAVE_REPO_URL)
    }

    @Test
    void "sheet flow"() {
        "run sheet flow"((ProcessId) null, "")
    }

    @Test
    void "sheet flow with process id"() {
        ProcessId processId = new ProcessIdImpl()
        "run sheet flow"(processId, "?processId=$processId.id")
    }

    void "run sheet flow"(ProcessId processId, String processIdPostfix) {
        String SHEET_URL = 'http://cadc/p', SKU = 'p', SHEET_RESULT = 'eee', SAVE_RESULT = 'aaa'
        String URN = "urn:global_sku:score:en_gb:p"
        String POST_URL = "$SAVE_REPO_URL/$URN$processIdPostfix"

        def mock = new StubFor(HttpClient)
        mock.demand.with {
            getFromCadc(1) {
                assert it == SHEET_URL
                rx.Observable.from(SHEET_RESULT)
            }
            postLocal(1) { url, data ->
                assert url == POST_URL
                assert data == SHEET_RESULT
                rx.Observable.from(SAVE_RESULT)
            }
        }
        sheetService.httpClient = mock.proxyInstance()

        def result = sheetService.sheetFlow(new URNImpl(URN), SHEET_URL, processId).toBlocking().single()
        assert result == SAVE_RESULT
    }

}
