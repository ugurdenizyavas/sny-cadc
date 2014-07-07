package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.HttpClient
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Slf4j
@Component
@org.springframework.context.annotation.Lazy
class SheetRetriever {

    @Autowired
    @Qualifier("ningHttpClient")
    HttpClient httpClient

    @Value('${octopus3.sourceservice.saveRepoUrl}')
    String saveRepoUrl

    rx.Observable<String> sheetFlow(String product, String sheetUrl) {
        httpClient.getFromCadc(sheetUrl)
                .flatMap({ String sheetContent ->
            String postUrl = "$saveRepoUrl?product=$product"
            httpClient.postLocal(postUrl, sheetContent)
        }).doOnError({
            log.error "error in sheet flow for url $sheetUrl", it
        })
    }

}
