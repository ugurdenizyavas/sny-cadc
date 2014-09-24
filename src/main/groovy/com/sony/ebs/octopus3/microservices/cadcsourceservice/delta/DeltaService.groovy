package com.sony.ebs.octopus3.microservices.cadcsourceservice.delta

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.Delta
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
    NingHttpClient localHttpClient

    @Autowired
    @Qualifier("cadcHttpClient")
    NingHttpClient cadcHttpClient

    @Value('${octopus3.sourceservice.cadcsourceDeltaItemServiceUrl}')
    String cadcsourceDeltaItemServiceUrl

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

    Object createServiceResult(Response response, String cadcUrl) {
        boolean success = NingHttpClient.isSuccess(response)
        def deltaItemServiceResult = new DeltaItemServiceResult(cadcUrl: cadcUrl, success: success, statusCode: response.statusCode)
        def json = jsonSlurper.parse(response.responseBodyAsStream, EncodingUtil.CHARSET_STR)
        if (!success) {
            deltaItemServiceResult.errors = json?.errors.collect { it.toString() }
        } else {
            deltaItemServiceResult.with {
                repoUrl = json?.result?.repoUrl
            }
        }
        deltaItemServiceResult
    }

    private rx.Observable<Object> doDeltaItem(Delta delta, String cadcUrl) {
        rx.Observable.just("starting").flatMap({
            def initialUrl = cadcsourceDeltaItemServiceUrl.replace(":publication", delta.publication).replace(":locale", delta.locale)
            def urlBuilder = new URIBuilder(initialUrl)
            urlBuilder.addParameter("url", cadcUrl)
            if (delta.processId?.id) {
                urlBuilder.addParameter("processId", delta.processId?.id)
            }
            localHttpClient.doGet(urlBuilder.toString())
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                createServiceResult(response, cadcUrl)
            }))
        }).onErrorReturn({
            log.error "error for $cadcUrl", it
            def error = HandlerUtil.getErrorMessage(it)
            new DeltaItemServiceResult(cadcUrl: cadcUrl, success: false, errors: [error])
        })
    }

    rx.Observable<Object> process(Delta delta) {
        def lastModifiedUrn = delta.lastModifiedUrn
        rx.Observable.just("starting").flatMap({
            deltaUrlHelper.createSinceValue(delta.since, lastModifiedUrn)
        }).flatMap({ String since ->
            delta.finalSince = since
            deltaUrlHelper.createCadcDeltaUrl(delta.cadcUrl, delta.locale, since)
        }).flatMap({ String deltaUrl ->
            delta.finalCadcUrl = deltaUrl
            cadcHttpClient.doGet(deltaUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting delta from cadc", delta.errors)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                delta.urlList = parseDelta(delta.locale, response.responseBodyAsStream)
            }))
        }).flatMap({
            deltaUrlHelper.updateLastModified(lastModifiedUrn, delta.errors)
        }).flatMap({
            def list = delta.urlList.collect({ doDeltaItem(delta, it) })
            rx.Observable.merge(list, 30)
        })
    }

}

