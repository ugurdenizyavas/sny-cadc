package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.HttpClient
import groovy.mock.interceptor.MockFor
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
    void "post sheet"() {
        String SHEET_URL = 'http://cadc/p', PRODUCT = 'p', SHEET_RESULT = 'eee', SAVE_RESULT = 'aaa'
        String POST_URL = "$SAVE_REPO_URL?product=$PRODUCT"

        def mock = new MockFor(HttpClient)
        mock.demand.with {
            getFromCadc(1) { rx.Observable.from(SHEET_RESULT) }
            postLocal(1) { url, data ->
                assert url == POST_URL
                assert data == SHEET_RESULT
                rx.Observable.from(SAVE_RESULT)
            }
        }
        sheetService.httpClient = mock.proxyInstance()

        def result = sheetService.sheetFlow(PRODUCT, SHEET_URL).toBlocking().single()
        assert result == SAVE_RESULT
    }

}
