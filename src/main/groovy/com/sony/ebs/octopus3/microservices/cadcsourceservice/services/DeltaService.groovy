package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.SheetServiceResult
import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class DeltaService {

    final JsonSlurper jsonSlurper = new JsonSlurper()

    @Autowired
    @Lazy
    ExecControl execControl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient localHttpClient

    @Autowired
    @Qualifier("cadcHttpClient")
    NingHttpClient cadcHttpClient

    @Autowired
    DeltaUrlHelper deltaUrlHelper

    @Value('${octopus3.sourceservice.cadcsourceSheetServiceUrl}')
    String cadcsourceSheetServiceUrl

    private def setUrlList(Delta delta, InputStream feedInputStream) throws Exception {
        try {
            log.debug "starting creating url list"
            def json = jsonSlurper.parse(feedInputStream, EncodingUtil.CHARSET_STR)
            delta.urlList = json.skus[delta.locale].collect { it }
            log.debug "finished creating url list {}", delta.urlList
        } catch (JsonException e) {
            throw new Exception("error parsing cadc delta json", e)
        }
    }

    private rx.Observable<String> importSingleSheet(Delta delta, String cadcUrl) {
        rx.Observable.from("starting").flatMap({
            def importUrl = cadcsourceSheetServiceUrl.replace(":publication", delta.publication).replace(":locale", delta.locale) + "?url=$cadcUrl"
            if (delta.processId?.id) importUrl += "&processId=${delta.processId?.id}"
            localHttpClient.doGet(importUrl)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                boolean success = NingHttpClient.isSuccess(response)
                def sheetServiceResult = new SheetServiceResult(cadcUrl: cadcUrl, success: success, statusCode: response.statusCode)
                def json = jsonSlurper.parse(response.responseBodyAsStream, EncodingUtil.CHARSET_STR)
                if (!success) {
                    sheetServiceResult.errors = json?.errors.collect { it.toString() }
                } else {
                    sheetServiceResult.with {
                        repoUrl = json?.result?.repoUrl
                    }
                }
                sheetServiceResult
            }))
        }).onErrorReturn({
            log.error "error for $cadcUrl", it
            def error = HandlerUtil.getErrorMessage(it)
            new SheetServiceResult(cadcUrl: cadcUrl, success: false, errors: [error])
        })
    }

    rx.Observable deltaFlow(Delta delta) {

        rx.Observable.from("starting").flatMap({
            deltaUrlHelper.createSinceValue(delta)
        }).flatMap({ String since ->
            delta.finalSince = since
            deltaUrlHelper.createDeltaUrl(delta.cadcUrl, delta.locale, since)
        }).flatMap({ String deltaUrl ->
            delta.finalCadcUrl = deltaUrl
            cadcHttpClient.doGet(deltaUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting delta json from cadc", delta.errors)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                setUrlList(delta, response.responseBodyAsStream)
            }))
        }).flatMap({
            deltaUrlHelper.updateLastModified(delta)
        }).flatMap({
            rx.Observable.merge(
                    delta.urlList?.collect {
                        importSingleSheet(delta, it)
                    }
                    , 30)
        })
    }

}

