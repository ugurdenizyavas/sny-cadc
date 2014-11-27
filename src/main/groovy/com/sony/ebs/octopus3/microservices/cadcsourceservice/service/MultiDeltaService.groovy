package com.sony.ebs.octopus3.microservices.cadcsourceservice.service

import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import groovy.util.logging.Slf4j
import groovyx.net.http.URIBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class MultiDeltaService {

    @Autowired
    ExecControl execControl

    @Autowired
    @Qualifier("longLocalHttpClient")
    Oct3HttpClient localHttpClient

    @Value('${octopus3.sourceservice.cadcsourceDeltaServiceUrl}')
    String cadcsourceDeltaServiceUrl

    public rx.Observable<Object> doDelta(CadcDelta delta) {
        rx.Observable.just("starting").flatMap({
            def initialUrl = cadcsourceDeltaServiceUrl.replace(":publication", delta.publication).replace(":locale", delta.locale)
            def urlBuilder = new URIBuilder(initialUrl)
            urlBuilder.addQueryParam("cadcUrl", delta.cadcUrl)
            if (delta.processId?.id) {
                urlBuilder.addQueryParam("processId", delta.processId?.id)
            }
            if (delta.sdate) {
                urlBuilder.addQueryParam("sdate", delta.sdate)
            }
            localHttpClient.doGet(urlBuilder.toString())
        }).flatMap({ Oct3HttpResponse response ->
            observe(execControl.blocking({
                HandlerUtil.parseOct3ResponseQuiet(response)
            }))
        }).onErrorReturn({
            log.error "error for $delta", it
            def error = HandlerUtil.getErrorMessage(it)
            error
        })
    }

}

