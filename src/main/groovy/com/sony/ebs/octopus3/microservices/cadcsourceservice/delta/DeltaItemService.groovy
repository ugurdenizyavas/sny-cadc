package com.sony.ebs.octopus3.microservices.cadcsourceservice.delta

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.encoding.MaterialNameEncoder
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaItem
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class DeltaItemService {

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

    @Value('${octopus3.sourceservice.repositoryCopyServiceUrl}')
    String repositoryCopyServiceUrl

    String getMaterialName(byte[] jsonBytes) throws Exception {
        try {
            def json = jsonSlurper.parse(new ByteArrayInputStream(jsonBytes), EncodingUtil.CHARSET_STR)
            def skuName = json.skuName
            MaterialNameEncoder.encode(skuName)
        } catch (JsonException e) {
            throw new Exception("error parsing delta", e)
        }
    }

    def getUrlWithProcessId = { String url, String processId ->
        def urlBuilder = new URIBuilder(url)
        if (processId) {
            urlBuilder.addParameter("processId", processId)
        }
        urlBuilder.toString()
    }

    rx.Observable<String> process(DeltaItem deltaItem) {
        byte[] jsonBytes
        String repoUrl
        rx.Observable.just("starting").flatMap({
            cadcHttpClient.doGet(deltaItem.url)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting sheet from cadc", deltaItem.errors)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                jsonBytes = response.responseBodyAsBytes
                deltaItem.materialName = getMaterialName(jsonBytes)
            }))
        }).flatMap({
            String copyUrl = repositoryCopyServiceUrl
                    .replace(":source", deltaItem.urn?.toString())
                    .replace(":destination", deltaItem.getUrnForSubType(DeltaType.previous)?.toString())
            localHttpClient.doGet(getUrlWithProcessId(copyUrl, deltaItem.processId))
        }).flatMap({
            repoUrl = repositoryFileServiceUrl.replace(":urn", deltaItem.urn?.toString())
            localHttpClient.doPost(getUrlWithProcessId(repoUrl, deltaItem.processId), jsonBytes)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "saving sheet to repo", deltaItem.errors)
        }).map({
            log.info "{} finished successfully", deltaItem
            [urn: deltaItem.urn?.toString(), repoUrl: repoUrl]
        })
    }

}
