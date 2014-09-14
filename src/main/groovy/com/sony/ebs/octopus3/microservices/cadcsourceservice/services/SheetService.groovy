package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.encoding.MaterialNameEncoder
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaItem
import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
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

    String getMaterialName(byte[] jsonBytes) throws Exception {
        try {
            def json = jsonSlurper.parse(new ByteArrayInputStream(jsonBytes), EncodingUtil.CHARSET_STR)
            def skuName = json.skuName
            MaterialNameEncoder.encode(skuName)
        } catch (JsonException e) {
            throw new Exception("error parsing cadc delta json", e)
        }
    }

    rx.Observable<String> sheetFlow(DeltaItem deltaItem) {
        byte[] jsonBytes
        String repoUrl
        rx.Observable.from("starting").flatMap({
            cadcHttpClient.doGet(deltaItem.url)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting sheet json from cadc", deltaItem.errors)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                jsonBytes = response.responseBodyAsBytes
                def materialName = getMaterialName(jsonBytes)
                deltaItem.assignUrnStr(materialName)
            }))
        }).flatMap({
            repoUrl = repositoryFileServiceUrl.replace(":urn", deltaItem.urnStr)
            def repoSaveUrl = repoUrl
            if (deltaItem.processId) repoSaveUrl += "?processId=$deltaItem.processId"

            localHttpClient.doPost(repoSaveUrl, jsonBytes)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "saving sheet json to repo", deltaItem.errors)
        }).map({
            log.info "{} finished successfully", deltaItem
            [urn: deltaItem.urnStr, repoUrl: repoUrl]
        })
    }

}
