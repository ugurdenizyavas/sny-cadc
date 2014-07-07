package com.sony.ebs.octopus3.microservices.cadcsourceservice.http

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class HttpClientConfig {

    @Value('${octopus3.sourceservice.proxyHost}')
    String proxyHost

    @Value('${octopus3.sourceservice.proxyPort}')
    int proxyPort

    @Value('${octopus3.sourceservice.proxyUser}')
    String proxyUser

    @Value('${octopus3.sourceservice.proxyPassword}')
    String proxyPassword

    @Value('${octopus3.sourceservice.authenticationUser}')
    String authenticationUser

    @Value('${octopus3.sourceservice.authenticationPassword}')
    String authenticationPassword

    @Value('${octopus3.sourceservice.authenticationHosts}')
    String authenticationHosts

}
