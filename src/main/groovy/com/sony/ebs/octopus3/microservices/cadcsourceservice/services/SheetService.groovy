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
@org.springframework.context.annotation.Lazy
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

    @Value('${octopus3.sourceservice.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    rx.Observable<String> sheetFlow(DeltaSheet deltaSheet) {
        rx.Observable.from("starting").flatMap({
            cadcHttpClient.doGet(deltaSheet.url)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting sheet json from cadc", deltaSheet.errors)
        }).flatMap({ Response response ->
            log.info "saving sheet"
            deltaSheet.assignUrnStr()
            String postUrl = repositoryFileServiceUrl.replace(":urn", deltaSheet.urnStr)
            if (deltaSheet.processId) postUrl += "?processId=$deltaSheet.processId"

            localHttpClient.doPost(postUrl, response.responseBodyAsStream)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "saving sheet json to repo", deltaSheet.errors)
        }).map({
            log.info "{} finished successfully", deltaSheet
            "success"
        })
    }

}
