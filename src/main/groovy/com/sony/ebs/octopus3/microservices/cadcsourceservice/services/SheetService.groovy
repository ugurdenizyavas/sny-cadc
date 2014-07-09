package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.HttpClient
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class SheetService {

    @Autowired
    @Qualifier("ningHttpClient")
    HttpClient httpClient

    @Value('${octopus3.sourceservice.saveRepoUrl}')
    String saveRepoUrl

    rx.Observable<String> sheetFlow(URN urn, String sheetUrl) {
        httpClient.getFromCadc(sheetUrl)
                .flatMap({ String sheetContent ->
            String postUrl = "$saveRepoUrl/$urn"
            httpClient.postLocal(postUrl, sheetContent)
        }).doOnError({
            log.error "error in sheet flow for url $sheetUrl", it
        })
    }

}
