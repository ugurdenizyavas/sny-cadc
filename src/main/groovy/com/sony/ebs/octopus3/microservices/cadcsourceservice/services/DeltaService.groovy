package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
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

    private rx.Observable<String> retrieveDelta(Delta delta) {
        observe(execControl.blocking {
            String relUrl = deltaCollaborator.createUrl(delta.publication, delta.locale, delta.since)
            "$delta.cadcUrl$relUrl"
        }).flatMap({
            cadcHttpClient.doGet(it)
        }).flatMap { String text ->
            observe(execControl.blocking {
                deltaCollaborator.storeDelta(delta.publication, delta.locale, text)
                text
            })
        }
    }

    private rx.Observable<Map> createUrlMap(Delta delta, String content) {
        observe(execControl.blocking {
            def result = new JsonSlurper().parseText(content)
            def urlMap = [:]
            result.skus[delta.locale].each {
                def sku = deltaCollaborator.getSkuFromUrl(it)
                URN urn = new URNImpl("global_sku", [delta.publication, delta.locale, sku])
                urlMap[urn] = it
            }
            log.info "parsed delta: $urlMap for $delta"
            urlMap
        })
    }

    private rx.Observable<String> importSingleSheet(ProcessId processId, URN urn, String sheetUrl) {
        def importUrl = "$importSheetUrl/$urn?url=$sheetUrl&processId=$processId.id"
        localHttpClient.doGet(importUrl).flatMap({
            rx.Observable.from("success for $urn")
        }).onErrorReturn({
            log.error "error in $urn", it
            "error in $urn"
        })
    }

    private rx.Observable<String> importSheets(Delta delta) {
        log.info "starting import for $delta"
        rx.Observable.zip(
                delta?.urlMap?.collect { URN urn, String sheetUrl ->
                    importSingleSheet(delta.processId, urn, sheetUrl)
                }
        ) { result ->
            log.info "import finished with result $result"
            "$result"
        }
    }

    rx.Observable<String> deltaFlow(Delta delta) {
        retrieveDelta(delta)
                .flatMap({ String result ->
            createUrlMap(delta, result)
        }).flatMap({ Map urlMap ->
            delta.urlMap = urlMap
            importSheets(delta)
        }).doOnError({
            log.error "error in delta import", it
        })
    }

}

