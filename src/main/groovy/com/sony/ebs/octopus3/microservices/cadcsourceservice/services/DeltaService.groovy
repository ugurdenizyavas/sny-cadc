package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.microservices.cadcsourceservice.http.HttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class DeltaService {

    @Autowired
    @Qualifier("ningHttpClient")
    HttpClient httpClient

    @Autowired
    ObservableHelper observableHelper

    @Autowired
    DeltaUrlBuilder deltaUrlBuilder

    @Value('${octopus3.sourceservice.importSheetUrl}')
    String importSheetUrl

    private rx.Observable<String> retrieveDelta(String publication, String locale, String since, String cadcUrl) {
        observableHelper.createObservable({
            String relUrl = deltaUrlBuilder.createUrl(publication, locale, since)
            "$cadcUrl$relUrl"
        }).flatMap {
            httpClient.getFromCadc(it)
        }
    }

    private rx.Observable<Delta> parseDelta(String publication, String locale, String content) {
        observableHelper.createObservable {
            def result = new JsonSlurper().parseText(content)
            def urlMap = [:]
            result.skus[locale].each {
                def product = deltaUrlBuilder.getProductFromUrl(it)
                urlMap[product] = it
            }
            def delta = new Delta(publication: publication, locale: locale, urlMap: urlMap)
            log.info "parsed delta: $delta"
            delta
        }
    }

    private rx.Observable<String> importSingleProduct(product, sheetUrl) {
        def importUrl = "$importSheetUrl?product=$product&url=$sheetUrl"
        httpClient.getLocal(importUrl).flatMap({
            rx.Observable.from("success for $product")
        }).onErrorReturn({
            log.error "error in $product", it
            "error in $product"
        })
    }

    private rx.Observable<String> importProducts(Delta delta) {
        log.info "starting import for $delta"
        rx.Observable.zip(
                delta?.urlMap?.collect { product, sheetUrl ->
                    importSingleProduct(product, sheetUrl)
                }
        ) { result ->
            log.info "import finished with result $result"
            "$result"
        }
    }

    rx.Observable<String> deltaFlow(String publication, String locale, String since, String cadcUrl) {
        retrieveDelta(publication, locale, since, cadcUrl)
                .flatMap({ String result ->
            parseDelta(publication, locale, result)
        }).flatMap({ Delta delta ->
            importProducts(delta)
        }).doOnError({
            log.error "error in delta import", it
        })
    }

}

