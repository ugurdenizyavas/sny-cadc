package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaSheet
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Slf4j
@Service
class SheetService {

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient localHttpClient

    @Autowired
    @Qualifier("cadcHttpClient")
    NingHttpClient cadcHttpClient

    @Value('${octopus3.sourceservice.saveRepoUrl}')
    String saveRepoUrl

    rx.Observable<String> sheetFlow(DeltaSheet deltaSheet) {
        cadcHttpClient.doGet(deltaSheet.url)
                .flatMap({ String sheetContent ->
            String postUrl = "$saveRepoUrl/$deltaSheet.urnStr"
            if (deltaSheet.processId) postUrl += "?processId=$deltaSheet.processId"
            localHttpClient.doPost(postUrl, sheetContent)
        }).doOnError({
            log.error "error in $deltaSheet", it
        })
    }

}
