package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.HttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import java.util.concurrent.ExecutorService

@Slf4j
@Component
@org.springframework.context.annotation.Lazy
class DeltaRetriever {

    @Autowired
    @Qualifier("ningHttpClient")
    HttpClient httpClient

    @Autowired
    @Qualifier("executorService")
    ExecutorService executorService

    @Autowired
    DeltaUrlBuilder deltaUrlBuilder

    @Value('${octopus3.sourceservice.importSheetUrl}')
    String importSheetUrl

    rx.Observable<String> retrieveDelta(String publication, String locale, String since, String cadcUrl) {
        String relUrl = deltaUrlBuilder.createUrl(publication, locale, since)
        def url = "$cadcUrl$relUrl"
        httpClient.getFromCadc(url)
    }

    rx.Observable<Delta> parseDelta(String publication, String locale, String content) {
        rx.Observable.create({ observer ->
            // Schedulers.io().createWorker().schedule({
            executorService.submit {
                try {
                    def result = new JsonSlurper().parseText(content)
                    def delta = new Delta(publication: publication, locale: locale, urls: result.skus[locale])
                    log.info "parsed delta: $delta"
                    observer.onNext(delta)
                    observer.onCompleted()
                } catch (all) {
                    observer.onError all
                }
            }
        } as rx.Observable.OnSubscribe)
    }

    rx.Observable<List> importProducts(Delta delta) {
        log.info "starting import for $delta"
        def jsonSlurper = new JsonSlurper()
        rx.Observable.zip(
                delta?.urls?.collect { sheetUrl ->
                    def importUrl = "$importSheetUrl?url=$sheetUrl"
                    httpClient.getLocal(importUrl)
                }
        ) { result ->
            log.info "done ${result.size()}: ${result.join(',')}"
            return result.collect { jsonSlurper.parseText(it)?.product }
        }
    }

    rx.Observable<List> deltaFlow(String publication, String locale, String since, String cadcUrl) {
        retrieveDelta(publication, locale, since, cadcUrl)
                .flatMap({ String result ->
            parseDelta(publication, locale, result)
        }).flatMap({ Delta delta ->
            importProducts(delta)
        })
    }

}

