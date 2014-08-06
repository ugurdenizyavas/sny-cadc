package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
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
    DeltaCollaborator deltaCollaborator

    @Value('${octopus3.sourceservice.importSheetUrl}')
    String importSheetUrl

    private Map createUrlMap(Delta delta, String feed) {
        def json = new JsonSlurper().parseText(feed)
        def urlMap = [:]
        json.skus[delta.locale].each {
            def sku = deltaCollaborator.getSkuFromUrl(it)
            URN urn = new URNImpl(DeltaUrnValue.global_sku.toString(), [delta.publication, delta.locale, sku])
            urlMap[urn] = it
        }
        log.info "parsed ${urlMap.size()} products for $delta"
        urlMap
    }

    private rx.Observable<String> importSingleSheet(ProcessId processId, URN urn, String sheetUrl) {
        rx.Observable.from("starting").flatMap({
            def importUrl = importSheetUrl.replace(":urn", urn.toString()) + "?url=$sheetUrl&processId=$processId.id"
            localHttpClient.doGet(importUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).map({
            "success for $urn"
        }).onErrorReturn({
            log.error "error for $urn", it
            "error for $urn"
        })
    }

    rx.Observable<String> deltaFlow(Delta delta) {
        rx.Observable.from("starting").flatMap({
            observe(execControl.blocking({
                log.info "creating delta url"
                String relUrl = deltaCollaborator.createUrl(delta)
                "$delta.cadcUrl$relUrl"
            }))
        }).flatMap({ String url ->
            log.info "getting delta for $url"
            cadcHttpClient.doGet(url)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                log.info "storing delta"
                def deltaFeed = response.responseBody
                deltaCollaborator.storeDelta(delta, deltaFeed)
                deltaFeed
            }))
        }).flatMap({ String feed ->
            observe(execControl.blocking({
                log.info "creating url map"
                createUrlMap(delta, feed)
            }))
        }).flatMap({ Map urlMap ->
            log.info "starting import for ${urlMap.size()} urls"
            rx.Observable.merge(
                    urlMap?.collect { URN urn, String sheetUrl ->
                        importSingleSheet(delta.processId, urn, sheetUrl)
                    }
            , 30)
        })
    }

}

