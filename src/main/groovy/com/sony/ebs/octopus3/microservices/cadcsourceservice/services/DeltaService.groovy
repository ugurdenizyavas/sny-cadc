package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaUrnValue
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

    private Map createUrlMap(Delta delta, InputStream feedInputStream) {
        log.info "creating url map"
        def json = jsonSlurper.parse(feedInputStream, "UTF-8")
        def urlMap = [:]
        json.skus[delta.locale].each {
            def sku = deltaUrlHelper.getSkuFromUrl(it)
            URN urn = new URNImpl(DeltaUrnValue.global_sku.toString(), [delta.publication, delta.locale, sku])
            urlMap[urn] = it
        }
        log.info "parsed ${urlMap.size()} products for $delta"
        urlMap
    }

    private rx.Observable<String> importSingleSheet(ProcessId processId, URN urn, String sheetUrl) {

        def importUrl = cadcsourceSheetServiceUrl.replace(":urn", urn.toString()) + "?url=$sheetUrl&processId=$processId.id"

        rx.Observable.from("starting").flatMap({
            localHttpClient.doGet(importUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "calling cadcsource sheet service")
        }).map({
            "success for $urn"
        }).onErrorReturn({
            log.error "error for $importUrl", it
            "error for $urn"
        })
    }

    rx.Observable<String> deltaFlow(Delta delta) {
        Map urlMap
        rx.Observable.from("starting").flatMap({
            deltaUrlHelper.createDeltaUrl(delta)
        }).flatMap({ String deltaUrl ->
            log.info "getting delta for $deltaUrl"
            cadcHttpClient.doGet(deltaUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting delta json from cadc")
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                urlMap = createUrlMap(delta, response.responseBodyAsStream)
            }))
        }).flatMap({
            deltaUrlHelper.updateLastModified(delta)
        }).flatMap({
            if (urlMap) {
                log.info "starting import for ${urlMap.size()} urls"
                rx.Observable.merge(
                        urlMap?.collect { URN urn, String sheetUrl ->
                            importSingleSheet(delta.processId, urn, sheetUrl)
                        }
                        , 30)
            } else {
                def message = "no products to import for $delta.baseUrn"
                log.info message
                rx.Observable.just(message)
            }
        })
    }

}

