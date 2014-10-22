package com.sony.ebs.octopus3.microservices.cadcsourceservice.spring.config

import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingOct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.ratpack.RatpackOct3HttpClient
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ratpack.launch.LaunchConfig

@Slf4j
@Configuration
@org.springframework.context.annotation.Lazy
class HttpClientConfig {

    @Autowired
    LaunchConfig launchConfig

    @Value('${http.client.type}')
    String httpClientType

    @Value('${octopus3.sourceservice.local.proxy.host}')
    String localProxyHost

    @Value('${octopus3.sourceservice.local.proxy.port}')
    int localProxyPort

    @Value('${octopus3.sourceservice.local.proxy.user}')
    String localProxyUser

    @Value('${octopus3.sourceservice.local.proxy.password}')
    String localProxyPassword

    @Value('${octopus3.sourceservice.local.proxy.nonProxyHosts}')
    String localNonProxyHosts

    @Value('${octopus3.sourceservice.cadc.proxy.host}')
    String cadcProxyHost

    @Value('${octopus3.sourceservice.cadc.proxy.port}')
    int cadcProxyPort

    @Value('${octopus3.sourceservice.cadc.proxy.user}')
    String cadcProxyUser

    @Value('${octopus3.sourceservice.cadc.proxy.password}')
    String cadcProxyPassword

    @Value('${octopus3.sourceservice.cadc.proxy.nonProxyHosts}')
    String cadcNonProxyHosts

    @Value('${octopus3.sourceservice.cadc.authenticationUser}')
    String cadcAuthenticationUser

    @Value('${octopus3.sourceservice.cadc.authenticationPassword}')
    String cadcAuthenticationPassword


    private Oct3HttpClient createHttpClient(LaunchConfig launchConfig, String proxyHost, int proxyPort,
                                            String proxyUser, String proxyPassword, String nonProxyHosts,
                                            String authenticationUser, String authenticationPassword,
                                            int connectionTimeout, int readTimeout) {
        def httpClient
        if (httpClientType == "ning") {
            httpClient = new NingOct3HttpClient(launchConfig, proxyHost, proxyPort, proxyUser, proxyPassword, nonProxyHosts, authenticationUser, authenticationPassword, connectionTimeout, readTimeout)
        } else {
            httpClient = new RatpackOct3HttpClient(launchConfig)
        }
        log.info "created Http Client typed {} : {}", httpClientType, httpClient
        httpClient
    }

    @Bean
    @Qualifier("localHttpClient")
    @org.springframework.context.annotation.Lazy
    public Oct3HttpClient localHttpClient() {
        createHttpClient(launchConfig, localProxyHost, localProxyPort, localProxyUser, localProxyPassword, localNonProxyHosts, "", "", 8000, 30000)
    }

    @Bean
    @Qualifier("cadcHttpClient")
    @org.springframework.context.annotation.Lazy
    public Oct3HttpClient cadcHttpClient() {
        createHttpClient(launchConfig, cadcProxyHost, cadcProxyPort, cadcProxyUser, cadcProxyPassword, cadcNonProxyHosts, cadcAuthenticationUser, cadcAuthenticationPassword, 8000, 30000)
    }


}

