package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.HttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.NingHttpClient
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

    rx.Observable<String> sheetFlow(URN urn, String sheetUrl, ProcessId processId) {
        cadcHttpClient.doGet(sheetUrl)
                .flatMap({ String sheetContent ->
            String postUrl = "$saveRepoUrl/$urn"
            if (processId) postUrl += "?processId=$processId.id"
            localHttpClient.doPost(postUrl, sheetContent)
        }).doOnError({
            log.error "error in sheet flow for url $sheetUrl", it
        })
    }

}
