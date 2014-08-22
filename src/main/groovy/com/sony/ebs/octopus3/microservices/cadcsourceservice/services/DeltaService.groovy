package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaUrnValue
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.SheetServiceResult
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

    private def setUrlMap(Delta delta, InputStream feedInputStream) {
        log.info "creating url map"
        def json = jsonSlurper.parse(feedInputStream, "UTF-8")
        def urlMap = [:]
        json.skus[delta.locale].each {
            def sku = deltaUrlHelper.getSkuFromUrl(it)
            URN urn = new URNImpl(DeltaUrnValue.global_sku.toString(), [delta.publication, delta.locale, sku])
            urlMap[urn] = it
        }
        log.info "parsed {} products for {}", urlMap.size(), delta
        delta.urlMap = urlMap
    }

    private rx.Observable<String> importSingleSheet(ProcessId processId, URN urn, String sheetUrl) {
        def urnStr = urn.toString()

        rx.Observable.from("starting").flatMap({
            def importUrl = cadcsourceSheetServiceUrl.replace(":urn", urnStr) + "?url=$sheetUrl"
            if (processId?.id) importUrl += "&processId=${processId?.id}"
            localHttpClient.doGet(importUrl)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                boolean success = NingHttpClient.isSuccess(response)
                def sheetServiceResult = new SheetServiceResult(urn: urnStr, success: success, statusCode: response.statusCode)
                if (!success) {
                    def json = jsonSlurper.parse(response.responseBodyAsStream, "UTF-8")
                    sheetServiceResult.errors = json?.errors.collect { it.toString() }
                } else {
                    sheetServiceResult.with {
                        jsonUrl = repositoryFileServiceUrl.replace(":urn", urnStr)
                    }
                }
                sheetServiceResult
            }))
        }).onErrorReturn({
            log.error "error for $urnStr", it
            def error = HandlerUtil.getErrorMessage(it)
            new SheetServiceResult(urn: urnStr, success: false, errors: [error])
        })
    }

    rx.Observable deltaFlow(Delta delta) {

        rx.Observable.from("starting").flatMap({
            deltaUrlHelper.createDeltaUrl(delta)
        }).flatMap({ String deltaUrl ->
            log.info "getting delta for {}", deltaUrl
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
                    delta.urlMap?.collect { URN urn, String sheetUrl ->
                        importSingleSheet(delta.processId, urn, sheetUrl)
                    }
                    , 30)
        })
    }

}

