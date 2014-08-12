package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.file.FileAttributesProvider
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

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
            NingHttpClient.isSuccess(response, "updating last modified date")
        }).map({
            "done"
        })
    }

    String createDeltaUrlInner(String cadcUrl, String locale, String since) {
        def url
        if (!since || since.equalsIgnoreCase("all")) {
            url = "$cadcUrl/$locale"
        } else {
            url = "$cadcUrl/changes/$locale?since=" + URLEncoder.encode(since, "UTF-8")
        }
        log.info "url inner for locale $locale and since $since is $url"
        url
    }

    rx.Observable<String> createDeltaUrl(Delta delta) {
        if (delta.since) {
            rx.Observable.just(createDeltaUrlInner(delta.cadcUrl, delta.locale, delta.since))
        } else {
            fileAttributesProvider.getLastModifiedTime(delta.lastModifiedUrn)
                    .flatMap({ result ->
                String since = result.found ? result.value : null
                rx.Observable.just(createDeltaUrlInner(delta.cadcUrl, delta.locale, since))
            })
        }
    }

    String getSkuFromUrl(String url) {
        def sku = url?.lastIndexOf('/') >= 0 && !url?.endsWith("/") ? url.substring(url.lastIndexOf('/') + 1) : null
        log.debug "sku for $url is $sku"
        sku
    }

}
