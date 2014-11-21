package com.sony.ebs.octopus3.microservices.cadcsourceservice.service

import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.encoding.MaterialNameEncoder
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcProduct
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
class ProductService {

    final JsonSlurper jsonSlurper = new JsonSlurper()

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Autowired
    @Qualifier("localHttpClient")
    Oct3HttpClient localHttpClient

    @Autowired
    @Qualifier("cadcHttpClient")
    Oct3HttpClient cadcHttpClient

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

    def getUrlWithProcessId(String url, String processId) {
        def urlBuilder = new URIBuilder(url)
        if (processId) {
            urlBuilder.addParameter("processId", processId)
        }
        urlBuilder.toString()
    }

    rx.Observable<String> process(CadcProduct product) {
        byte[] jsonBytes
        String repoUrl
        rx.Observable.just("starting").flatMap({
            cadcHttpClient.doGet(product.url)
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("getting sheet from cadc", product.errors)
        }).flatMap({ Oct3HttpResponse response ->
            observe(execControl.blocking({
                jsonBytes = response.bodyAsBytes
                product.materialName = getMaterialName(jsonBytes)
            }))
        }).flatMap({
            repoUrl = repositoryFileServiceUrl.replace(":urn", product.urn?.toString())
            localHttpClient.doPost(getUrlWithProcessId(repoUrl, product.processId), jsonBytes)
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("saving sheet to repo", product.errors)
        }).map({
            log.info "{} finished successfully", product
            [urn: product.urn?.toString(), repoUrl: repoUrl]
        })
    }

}
