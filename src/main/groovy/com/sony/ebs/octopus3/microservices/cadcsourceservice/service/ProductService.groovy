package com.sony.ebs.octopus3.microservices.cadcsourceservice.service

import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.encoding.MaterialNameEncoder
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcProduct
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
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

    rx.Observable<String> process(CadcProduct product, ProductResult productResult) {
        byte[] jsonBytes
        String outputUrn, outputUrl
        rx.Observable.just("starting").flatMap({
            productResult.inputUrl = product.url
            cadcHttpClient.doGet(product.url)
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("getting product from cadc", productResult.errors)
        }).flatMap({ Oct3HttpResponse response ->
            observe(execControl.blocking({
                jsonBytes = response.bodyAsBytes
                product.materialName = getMaterialName(jsonBytes)
                outputUrn = product.urn?.toString()
                outputUrl = repositoryFileServiceUrl.replace(":urn", outputUrn)
                HandlerUtil.addProcessId(outputUrl, product.processId)
            }))
        }).flatMap({ url ->
            localHttpClient.doPost(url, jsonBytes)
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("saving product to repo", productResult.errors)
        }).map({
            productResult.outputUrn = outputUrn
            productResult.outputUrl = outputUrl
            log.info "{} finished successfully", product
            "success"
        })
    }

}
