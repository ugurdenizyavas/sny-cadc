package com.sony.ebs.octopus3.microservices.cadcsourceservice.http

import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig
import com.ning.http.client.ProxyServer
import com.ning.http.client.Realm
import groovy.util.logging.Slf4j
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.ExecutorService

@Slf4j
@Component("ningHttpClient")
class NingHttpClient implements HttpClient {

    enum RequestType {
        GET_LOCAL, POST_LOCAL, GET_CADC
    }

    @Autowired
    HttpClientConfig httpClientConfig

    AsyncHttpClient httpClientWithProxy
    AsyncHttpClient httpClientNoProxy

    @Autowired
    @Qualifier("executorService")
    ExecutorService executorService

    @PostConstruct
    void init() {
        AsyncHttpClientConfig config
        if (httpClientConfig?.proxyHost) {
            def proxyServer = new ProxyServer(httpClientConfig.proxyHost, httpClientConfig.proxyPort,
                    httpClientConfig.proxyUser, httpClientConfig.proxyPassword)
            config = new AsyncHttpClientConfig.Builder().setProxyServer(proxyServer).build()
        } else {
            config = new AsyncHttpClientConfig.Builder().build()
        }
        httpClientWithProxy = new AsyncHttpClient(config)

        httpClientNoProxy = new AsyncHttpClient()
    }

    String getByNing(RequestType requestType, String urlString, String data = null) {
        def url = new URIBuilder(urlString).toString()

        log.info "starting $requestType for $url"
        def f
        if (RequestType.GET_LOCAL == requestType) {
            f = httpClientNoProxy.prepareGet(url)
                    .addHeader('Accept-Charset', 'UTF-8')
                    .execute()
        } else if (RequestType.GET_CADC == requestType) {
            f = httpClientWithProxy.prepareGet(url)
                    .addHeader('Accept-Charset', 'UTF-8')
                    .setRealm((new Realm.RealmBuilder()).setScheme(Realm.AuthScheme.BASIC).setPrincipal(httpClientConfig.authenticationUser).setPassword(httpClientConfig.authenticationPassword).build())
                    .execute()
        } else if (RequestType.POST_LOCAL == requestType) {
            f = httpClientNoProxy.preparePost(url)
                    .addHeader('Accept-Charset', 'UTF-8')
                    .setBody(data)
                    .execute()
        }
        def response = f.get()

        log.info "finished $requestType for $url"
        if (response.statusCode != 200) {
            def message = "error getting $url with http status code $response.statusCode"
            log.error message
            throw new Exception(message)
        }

        response.responseBody
    }

    rx.Observable<String> getObservableNing(RequestType requestType, String url, String data = null) {
        rx.Observable.create({ observer ->
            executorService.submit {
                try {
                    def result = getByNing(requestType, url, data)
                    observer.onNext(result)
                    observer.onCompleted()
                } catch (all) {
                    observer.onError all
                }
            }
        } as rx.Observable.OnSubscribe)
    }

    @Override
    rx.Observable<String> getLocal(String url) {
        getObservableNing(RequestType.GET_LOCAL, url)
    }

    @Override
    rx.Observable<String> getFromCadc(String url) {
        getObservableNing(RequestType.GET_CADC, url)
    }

    @Override
    rx.Observable<String> postLocal(String url, String data) {
        getObservableNing(RequestType.POST_LOCAL, url, data)
    }
}
