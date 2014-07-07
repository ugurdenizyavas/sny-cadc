package com.sony.ebs.octopus3.microservices.cadcsourceservice.http

import groovy.util.logging.Slf4j
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.nio.client.HttpAsyncClient
import org.apache.http.nio.client.methods.HttpAsyncMethods
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.apache.http.ObservableHttp
import rx.apache.http.ObservableHttpResponse

import javax.annotation.PostConstruct
import java.nio.charset.Charset

@Slf4j
@Component("rxJavaApacheHttpClient")
class RxJavaApacheHttpClient implements HttpClient {

    @Autowired
    HttpClientConfig httpClientConfig

    HttpAsyncClient httpClientWithProxy
    HttpAsyncClient httpClientNoProxy

    @PostConstruct
    void init() {
        def builder = HttpAsyncClients.custom()
        CredentialsProvider credsProvider = new BasicCredentialsProvider();

        if (httpClientConfig.proxyHost && httpClientConfig.proxyUser) {
            credsProvider.setCredentials(
                    new AuthScope(httpClientConfig.proxyHost, httpClientConfig.proxyPort),
                    new UsernamePasswordCredentials(httpClientConfig.proxyUser, httpClientConfig.proxyPassword))
            builder.setProxy(new HttpHost(httpClientConfig.proxyHost, httpClientConfig.proxyPort))
        }
        if (httpClientConfig.authenticationHosts && httpClientConfig.authenticationUser) {
            httpClientConfig.authenticationHosts.split(',').collect { it.trim() }.each { String host ->
                credsProvider.setCredentials(
                        new AuthScope(host, -1),
                        new UsernamePasswordCredentials(httpClientConfig.authenticationUser, httpClientConfig.authenticationPassword))
            }
        }

        builder.setDefaultCredentialsProvider(credsProvider)
        httpClientWithProxy = builder.build()
        httpClientWithProxy.start()

        httpClientNoProxy = HttpAsyncClients.createDefault()
        httpClientNoProxy.start()
    }

    private rx.Observable<String> getInner(String url, HttpAsyncClient httpClient) {
        log.info "starting get $url"
        ObservableHttp.createRequest(HttpAsyncMethods.createGet(url), httpClient).toObservable()
                .flatMap { ObservableHttpResponse response ->
            return response.getContent().map {
                def str = new String(it, Charset.forName("UTF-8"))
                log.info "finished get $url with: $str"
                return str
            }
        }.asObservable()
    }

    private rx.Observable<String> postInner(String url, String data, ContentType contentType, HttpAsyncClient httpClient) {
        log.info "starting post $url"
        ObservableHttp.createRequest(HttpAsyncMethods.createPost(url, data, contentType), httpClient).toObservable()
                .flatMap { ObservableHttpResponse response ->
            return response.getContent().map {
                def str = new String(it, Charset.forName("UTF-8"))
                log.info "finished post $url with: $str"
                return str
            }
        }.asObservable()
    }

    @Override
    rx.Observable<String> getLocal(String url) {
        getInner(url, httpClientNoProxy)
    }

    @Override
    rx.Observable<String> getFromCadc(String url) {
        getInner(url, httpClientWithProxy)
    }

    @Override
    rx.Observable<String> postLocal(String url, String data) {
        postInner(url, data, ContentType.APPLICATION_JSON, httpClientNoProxy)
    }

}