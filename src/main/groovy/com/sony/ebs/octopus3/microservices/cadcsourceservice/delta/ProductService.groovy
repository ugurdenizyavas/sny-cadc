package com.sony.ebs.octopus3.microservices.cadcsourceservice.delta

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.encoding.MaterialNameEncoder
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcProduct
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
class ProductService {

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
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting sheet from cadc", product.errors)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                jsonBytes = response.responseBodyAsBytes
                product.materialName = getMaterialName(jsonBytes)
            }))
        }).flatMap({
            repoUrl = repositoryFileServiceUrl.replace(":urn", product.urn?.toString())
            localHttpClient.doPost(getUrlWithProcessId(repoUrl, product.processId), jsonBytes)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "saving sheet to repo", product.errors)
        }).map({
            log.info "{} finished successfully", product
            [urn: product.urn?.toString(), repoUrl: repoUrl]
        })
    }

}
