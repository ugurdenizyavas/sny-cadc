package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.encoding.MaterialNameEncoder
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaSheet
import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class SheetService {

    final JsonSlurper jsonSlurper = new JsonSlurper()

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

    String getMaterialName(String jsonStr) throws Exception {
        try {
            def json = jsonSlurper.parseText(jsonStr)
            def skuName = json.skuName
            MaterialNameEncoder.encode(skuName)
        } catch (JsonException e) {
            throw new Exception("error parsing cadc delta json", e)
        }
    }

    rx.Observable<String> sheetFlow(DeltaSheet deltaSheet) {
        String jsonStr
        rx.Observable.from("starting").flatMap({
            cadcHttpClient.doGet(deltaSheet.url)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting sheet json from cadc", deltaSheet.errors)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                jsonStr = IOUtils.toString(response.responseBodyAsStream, EncodingUtil.CHARSET)
                def materialName = getMaterialName(jsonStr)
                deltaSheet.assignUrnStr(materialName)
            }))
        }).flatMap({
            String postUrl = repositoryFileServiceUrl.replace(":urn", deltaSheet.urnStr)
            if (deltaSheet.processId) postUrl += "?processId=$deltaSheet.processId"

            localHttpClient.doPost(postUrl, IOUtils.toInputStream(jsonStr, EncodingUtil.CHARSET))
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "saving sheet json to repo", deltaSheet.errors)
        }).map({
            log.info "{} finished successfully", deltaSheet
            "success"
        })
    }

}
