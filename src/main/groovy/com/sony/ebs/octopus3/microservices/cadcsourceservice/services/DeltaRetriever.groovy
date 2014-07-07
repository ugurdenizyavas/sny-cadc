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
                    def urlMap = [:]
                    result.skus[locale].each {
                        def product = deltaUrlBuilder.getProductFromUrl(it)
                        urlMap[product] = it
                    }
                    def delta = new Delta(publication: publication, locale: locale, urlMap: urlMap)
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
        rx.Observable.zip(
                delta?.urlMap?.collect { product, sheetUrl ->
                    def importUrl = "$importSheetUrl?product=$product&url=$sheetUrl"
                    httpClient.getLocal(importUrl).onErrorReturn({
                        log.error "error in $product", it
                        "error in $product"
                    })
                }
        ) { result ->
            def message = "import finished for ${result.size()} products"
            log.info message
            return message
        }
    }

    void deltaFlow(String publication, String locale, String since, String cadcUrl) {
        retrieveDelta(publication, locale, since, cadcUrl)
                .flatMap({ String result ->
            parseDelta(publication, locale, result)
        }).flatMap({ Delta delta ->
            importProducts(delta)
        }).doOnError({
            log.error "error in delta import", it
        }).subscribe({
            log.info "delta import finished for publication $publication, locale $locale, since $since, cadcUrl $cadcUrl"
        })
        log.info "delta import started for publication $publication, locale $locale, since $since, cadcUrl $cadcUrl"
    }

}

