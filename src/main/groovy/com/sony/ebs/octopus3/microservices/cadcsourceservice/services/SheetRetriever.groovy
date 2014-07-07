package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.HttpClient
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import java.util.concurrent.ExecutorService

@Slf4j
@Component
@org.springframework.context.annotation.Lazy
class SheetRetriever {

    @Autowired
    @Qualifier("ningHttpClient")
    HttpClient httpClient

    @Autowired
    @Qualifier("executorService")
    ExecutorService executorService

    @Autowired
    DeltaUrlBuilder deltaUrlBuilder

    @Value('${octopus3.sourceservice.saveRepoUrl}')
    String saveRepoUrl

    rx.Observable<String> retrieveSheet(String sheetUrl) {
        httpClient.getFromCadc(sheetUrl)
    }

    rx.Observable<String> postSheet(String product, String data) {
        String postUrl = "$saveRepoUrl?product=$product"
        httpClient.postLocal(postUrl, data).flatMap {
            rx.Observable.from(product)
        }
    }

    rx.Observable<String> sheetFlow(String product, String sheetUrl) {
        retrieveSheet(sheetUrl)
                .flatMap({ String sheetContent ->
            postSheet(product, sheetContent)
        })
    }

}
