package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaSheet
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

@Slf4j
@Service
class SheetService {

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient localHttpClient

    @Autowired
    @Qualifier("cadcHttpClient")
    NingHttpClient cadcHttpClient

    @Value('${octopus3.sourceservice.saveRepoUrl}')
    String saveRepoUrl

    rx.Observable<String> sheetFlow(DeltaSheet deltaSheet) {
        rx.Observable.from("starting").flatMap({
            cadcHttpClient.doGet(deltaSheet.url)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).flatMap({ Response response ->
            log.info "saving sheet"
            String postUrl = saveRepoUrl.replace(":urn", deltaSheet.urnStr)
            if (deltaSheet.processId) postUrl += "?processId=$deltaSheet.processId"

            localHttpClient.doPost(postUrl, response.responseBody)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).map({
            log.info "returning success"
            log.debug "save sheet result is $it"
            "success for $deltaSheet"
        })
    }

}
