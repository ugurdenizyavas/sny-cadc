package com.sony.ebs.octopus3.microservices.cadcsourceservice

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import ratpack.exec.ExecControl

@Configuration
@ComponentScan(value = "com.sony.ebs.octopus3.microservices.cadcsourceservice")
@PropertySource(value = ['classpath:/default.properties', 'classpath:/${environment}.properties'], ignoreResourceNotFound = true)
class SpringConfig {

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    @Qualifier("cadcHttpClient")
    @org.springframework.context.annotation.Lazy
    public NingHttpClient cadcHttpClient() {
        new NingHttpClient(execControl,
                proxyHost, proxyPort, proxyUser, proxyPassword, nonProxyHosts,
                authenticationUser, authenticationPassword)
    }

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

    @Value('${octopus3.sourceservice.nonProxyHosts}')
    String nonProxyHosts

    @Bean
    @Qualifier("localHttpClient")
    @org.springframework.context.annotation.Lazy
    public NingHttpClient localHttpClient() {
        new NingHttpClient(execControl)
    }

}

