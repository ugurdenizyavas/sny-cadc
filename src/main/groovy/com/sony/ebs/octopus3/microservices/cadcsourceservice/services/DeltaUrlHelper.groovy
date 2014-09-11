package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.file.FileAttributesProvider
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.util.CadcSourceUtil
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class DeltaUrlHelper {

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Value('${octopus3.sourceservice.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    @Autowired
    FileAttributesProvider fileAttributesProvider

    rx.Observable<String> updateLastModified(Delta delta) {
        rx.Observable.just("starting").flatMap({
            def url = repositoryFileServiceUrl.replace(":urn", delta.lastModifiedUrn.toString())
            httpClient.doPost(url, "update")
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "updating last modified date", delta.errors)
        }).map({
            "done"
        })
    }

    rx.Observable<String> createDeltaUrl(String cadcUrl, String locale, String since) {
        observe(execControl.blocking({
            def url
            if (!since || since.equalsIgnoreCase("all")) {
                url = "$cadcUrl/$locale"
            } else {
                url = "$cadcUrl/changes/$locale?since=" + URLEncoder.encode(since, CadcSourceUtil.CHARSET_STR)
            }
            log.info "url inner for locale {} and since {} is {}", locale, since, url
            url
        }))
    }

    rx.Observable<String> createSinceValue(Delta delta) {
        if (delta.since) {
            rx.Observable.just(delta.since)
        } else {
            fileAttributesProvider.getLastModifiedTime(delta.lastModifiedUrn)
                    .map({ result ->
                result.found ? result.value : ""
            })
        }
    }

    String getSkuFromUrl(String url) {
        def sku = url?.lastIndexOf('/') >= 0 && !url?.endsWith("/") ? url.substring(url.lastIndexOf('/') + 1) : null
        log.trace "sku for {} is {}", url, sku
        sku
    }

}
