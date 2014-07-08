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

    rx.Observable<String> retrieveDelta(String publication, String locale, String since, String cadcUrl) {
        observableHelper.createObservable({
            String relUrl = deltaUrlBuilder.createUrl(publication, locale, since)
            "$cadcUrl$relUrl"
        }).flatMap {
            httpClient.getFromCadc(it)
        }
    }

    rx.Observable<Delta> parseDelta(String publication, String locale, String content) {
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

    rx.Observable importSingleProduct(product, sheetUrl, synch) {
        def importUrl = "$importSheetUrl?synch=$synch&product=$product&url=$sheetUrl"
        httpClient.getLocal(importUrl).flatMap({
            rx.Observable.from([success: true, product: product])
        }).onErrorReturn({
            log.error "error in $product", it
            [success: false, product: product]
        })
    }

    rx.Observable<Map> importProducts(Delta delta, boolean synch) {
        log.info "starting import for $delta"
        rx.Observable.zip(
                delta?.urlMap?.collect { product, sheetUrl ->
                    importSingleProduct(product, sheetUrl, synch)
                }
        ) { result ->
            log.info "import finished with result $result"
            def error = [], success = []
            result.each { it.success ? success << it.product : error << it.product }
            return [error: error, success: success]
        }
    }

    rx.Observable<Map> deltaFlow(String publication, String locale, String since, String cadcUrl, boolean synch) {
        retrieveDelta(publication, locale, since, cadcUrl)
                .flatMap({ String result ->
            parseDelta(publication, locale, result)
        }).flatMap({ Delta delta ->
            importProducts(delta, synch)
        }).doOnError({
            log.error "error in delta import", it
        })
    }

}

