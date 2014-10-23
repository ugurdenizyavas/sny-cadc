package com.sony.ebs.octopus3.microservices.cadcsourceservice.spring.config

import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClientFactory
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

    @Bean
    public Oct3HttpClientFactory oct3HttpClientFactory() {
        new Oct3HttpClientFactory()
    }

    @Bean
    @Qualifier("localHttpClient")
    public Oct3HttpClient localHttpClient() {
        oct3HttpClientFactory().createHttpClient(launchConfig, httpClientType,
                localProxyHost, localProxyPort,
                localProxyUser, localProxyPassword, localNonProxyHosts,
                '', '',
                8000, 30000)
    }

    @Bean
    @Qualifier("cadcHttpClient")
    @org.springframework.context.annotation.Lazy
    public Oct3HttpClient cadcHttpClient() {
        oct3HttpClientFactory().createHttpClient(launchConfig, httpClientType,
                cadcProxyHost, cadcProxyPort,
                cadcProxyUser, cadcProxyPassword, cadcNonProxyHosts,
                cadcAuthenticationUser, cadcAuthenticationPassword,
                8000, 30000)
    }

}

