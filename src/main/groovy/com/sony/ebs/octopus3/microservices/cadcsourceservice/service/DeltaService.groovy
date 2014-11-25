package com.sony.ebs.octopus3.microservices.cadcsourceservice.service

import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaUrlHelper
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
class DeltaService {

    final JsonSlurper jsonSlurper = new JsonSlurper()

    @Autowired
    ExecControl execControl

    @Autowired
    @Qualifier("localHttpClient")
    Oct3HttpClient localHttpClient

    @Autowired
    @Qualifier("cadcHttpClient")
    Oct3HttpClient cadcHttpClient

    @Value('${octopus3.sourceservice.cadcsourceProductServiceUrl}')
    String cadcsourceProductServiceUrl

    @Value('${octopus3.sourceservice.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Autowired
    DeltaUrlHelper deltaUrlHelper

    List parseDelta(String locale, InputStream feedInputStream) throws Exception {
        try {
            def json = jsonSlurper.parse(feedInputStream, EncodingUtil.CHARSET_STR)
            def list = json.skus[locale].collect { it }
            log.debug "delta urls: {}", list
            list
        } catch (JsonException e) {
            throw new Exception("error parsing delta", e)
        }
    }

    def createProductResult(Oct3HttpResponse response, String inputUrl) {
        def productResult = new ProductResult(
                inputUrl: inputUrl,
                success: response.success,
                statusCode: response.statusCode
        )
        def json = HandlerUtil.parseOct3ResponseQuiet(response)
        productResult.errors = json?.errors
        if (response.success) {
            productResult.outputUrn = json?.result?.outputUrn
            productResult.outputUrl = json?.result?.outputUrl
        }
        productResult
    }

    def createProductServiceUrl(CadcDelta delta, String inputUrl) {
        def initialUrl = cadcsourceProductServiceUrl.replace(":publication", delta.publication).replace(":locale", delta.locale)
        new URIBuilder(initialUrl).with {
            addParameter("url", inputUrl)
            if (delta.processId?.id) {
                addParameter("processId", delta.processId?.id)
            }
            it.toString()
        }
    }

    rx.Observable<ProductResult> doProduct(CadcDelta delta, String inputUrl) {
        rx.Observable.just("starting").flatMap({
            observe(execControl.blocking({
                createProductServiceUrl(delta, inputUrl)
            }))
        }).flatMap({
            localHttpClient.doGet(it)
        }).flatMap({ Oct3HttpResponse response ->
            observe(execControl.blocking({
                createProductResult(response, inputUrl)
            }))
        }).onErrorReturn({
            log.error "error for $inputUrl", it
            def error = HandlerUtil.getErrorMessage(it)
            new ProductResult(
                    success: false,
                    inputUrl: inputUrl,
                    errors: [error]
            )
        })
    }

    rx.Observable<Object> processDelta(CadcDelta delta, DeltaResult deltaResult) {
        def lastModifiedUrn = delta.lastModifiedUrn
        rx.Observable.just("starting").flatMap({
            deltaUrlHelper.createStartDate(delta.sdate, lastModifiedUrn)
        }).flatMap({ String sdate ->
            deltaResult.finalStartDate = sdate
            deltaUrlHelper.createCadcDeltaUrl(delta.cadcUrl, delta.locale, sdate)
        }).flatMap({ String deltaUrl ->
            deltaResult.finalDeltaUrl = deltaUrl
            cadcHttpClient.doGet(deltaUrl)
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("getting delta from cadc", deltaResult.errors)
        }).flatMap({ Oct3HttpResponse response ->
            observe(execControl.blocking({
                deltaResult.deltaUrls = parseDelta(delta.locale, response.bodyAsStream)
            }))
        }).flatMap({
            deltaUrlHelper.updateLastModified(lastModifiedUrn, deltaResult.errors)
        }).flatMap({
            def list = deltaResult.deltaUrls.collect({ doProduct(delta, it) })
            rx.Observable.merge(list, 30)
        })
    }

}

