package com.sony.ebs.octopus3.microservices.cadcsourceservice.service

import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import groovy.util.logging.Slf4j
import org.apache.http.client.utils.URIBuilder
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
    @Qualifier("localHttpClient")
    Oct3HttpClient localHttpClient

    @Value('${octopus3.sourceservice.cadcsourceDeltaServiceUrl}')
    String cadcsourceDeltaServiceUrl

    def createDeltaResult(Oct3HttpResponse response, String cadcUrl) {
        def productResult = new ProductResult(inputUrl: cadcUrl, success: response.success, statusCode: response.statusCode)
        def json = HandlerUtil.parseOct3ResponseQuiet(response)
        productResult.errors = json?.errors
        productResult
    }

    public rx.Observable<Object> doDelta(CadcDelta delta) {
        rx.Observable.just("starting").flatMap({
            def deltaHandlerUrl = cadcsourceDeltaServiceUrl.replace(":publication", delta.publication).replace(":locale", delta.locale)
            def urlBuilder = new URIBuilder(deltaHandlerUrl)
            urlBuilder.addParameter("cadcUrl", delta.cadcUrl)
            if (delta.processId?.id) {
                urlBuilder.addParameter("processId", delta.processId?.id)
            }
            localHttpClient.doGet(urlBuilder.toString())
        }).flatMap({ Oct3HttpResponse response ->
            observe(execControl.blocking({
                createDeltaResult(response, delta.cadcUrl)
            }))
        }).onErrorReturn({
            log.error "error for $delta", it
            def error = HandlerUtil.getErrorMessage(it)
            error
        })
    }

}

