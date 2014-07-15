package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaSheet
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
        "run sheet flow"("", "")
    }

    @Test
    void "sheet flow with process id"() {
        "run sheet flow"("123", "?processId=123")
    }

    void "run sheet flow"(String processId, String processIdPostfix) {
        String SHEET_URL = 'http://cadc/p', SHEET_RESULT = 'eee', SAVE_RESULT = 'aaa'
        String URN = "urn:global_sku:score:en_gb:p"
        String POST_URL = "$SAVE_REPO_URL/$URN$processIdPostfix"

        DeltaSheet deltaSheet = new DeltaSheet(url: SHEET_URL, urnStr: URN, processId: processId)

        def mock = new StubFor(NingHttpClient)
        mock.demand.with {
            doGet(1) {
                assert it == SHEET_URL
                rx.Observable.from(SHEET_RESULT)
            }
            doPost(1) { url, data ->
                assert url == POST_URL
                assert data == SHEET_RESULT
                rx.Observable.from(SAVE_RESULT)
            }
        }
        sheetService.localHttpClient = mock.proxyInstance()
        sheetService.cadcHttpClient = mock.proxyInstance()

        def result = sheetService.sheetFlow(deltaSheet).toBlocking().single()
        assert result == SAVE_RESULT
    }

}
