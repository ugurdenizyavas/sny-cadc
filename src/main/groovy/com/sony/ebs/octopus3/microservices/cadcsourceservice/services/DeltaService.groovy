package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaUrnValue
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

    @Value('${octopus3.sourceservice.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Value('${octopus3.sourceservice.cadcsourceSheetServiceUrl}')
    String cadcsourceSheetServiceUrl

    private def setUrlMap(Delta delta, InputStream feedInputStream) throws Exception {
        try {
            log.info "creating url map"
            def json = jsonSlurper.parse(feedInputStream, EncodingUtil.CHARSET_STR)
            def urlMap = [:]
            json.skus[delta.locale].each {
                def sku = deltaUrlHelper.getSkuFromUrl(it)
                URN urn = new URNImpl(DeltaUrnValue.global_sku.toString(), [delta.publication, delta.locale, sku])
                urlMap[urn] = it
            }
            log.info "parsed {} products for {}", urlMap.size(), delta
            delta.urlMap = urlMap
        } catch (JsonException e) {
            throw new Exception("error parsing cadc delta json", e)
        }
    }

    private rx.Observable<String> importSingleSheet(ProcessId processId, URN urn, String cadcUrl) {
        def urnStr = urn.toString()

        rx.Observable.from("starting").flatMap({
            def importUrl = cadcsourceSheetServiceUrl.replace(":urn", urnStr) + "?url=$cadcUrl"
            if (processId?.id) importUrl += "&processId=${processId?.id}"
            localHttpClient.doGet(importUrl)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                boolean success = NingHttpClient.isSuccess(response)
                def sheetServiceResult = new SheetServiceResult(urnStr: urnStr, cadcUrl: cadcUrl, success: success, statusCode: response.statusCode)
                if (!success) {
                    def json = jsonSlurper.parse(response.responseBodyAsStream, EncodingUtil.CHARSET_STR)
                    sheetServiceResult.errors = json?.errors.collect { it.toString() }
                } else {
                    sheetServiceResult.with {
                        repoUrl = repositoryFileServiceUrl.replace(":urn", urnStr)
                    }
                }
                sheetServiceResult
            }))
        }).onErrorReturn({
            log.error "error for $urnStr", it
            def error = HandlerUtil.getErrorMessage(it)
            new SheetServiceResult(urnStr: urnStr, cadcUrl: cadcUrl, success: false, errors: [error])
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
                setUrlMap(delta, response.responseBodyAsStream)
            }))
        }).flatMap({
            deltaUrlHelper.updateLastModified(delta)
        }).flatMap({
            rx.Observable.merge(
                    delta.urlMap?.collect { URN urn, String cadcUrl ->
                        importSingleSheet(delta.processId, urn, cadcUrl)
                    }
                    , 30)
        })
    }

}

