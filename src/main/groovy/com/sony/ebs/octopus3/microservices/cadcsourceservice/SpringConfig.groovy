package com.sony.ebs.octopus3.microservices.cadcsourceservice

import com.sony.ebs.octopus3.commons.ratpack.file.FileAttributesProvider
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
import ratpack.launch.LaunchConfig

@Configuration
@ComponentScan(value = "com.sony.ebs.octopus3.microservices.cadcsourceservice")
@PropertySource(value = ['classpath:/default.properties', 'classpath:/${environment}.properties'], ignoreResourceNotFound = true)
class SpringConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Autowired
    @org.springframework.context.annotation.Lazy
    LaunchConfig launchConfig

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

    @Bean
    @Qualifier("localHttpClient")
    @org.springframework.context.annotation.Lazy
    public NingHttpClient localHttpClient() {
        new NingHttpClient(launchConfig,
                localProxyHost, localProxyPort, localProxyUser, localProxyPassword, localNonProxyHosts, "", "", 5000, 15000)
    }

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
    @Qualifier("cadcHttpClient")
    @org.springframework.context.annotation.Lazy
    public NingHttpClient cadcHttpClient() {
        new NingHttpClient(launchConfig,
                cadcProxyHost, cadcProxyPort, cadcProxyUser, cadcProxyPassword, cadcNonProxyHosts,
                cadcAuthenticationUser, cadcAuthenticationPassword, 8000, 30000)
    }

    @Value('${octopus3.flix.repositoryFileAttributesServiceUrl}')
    String repositoryFileAttributesServiceUrl

    @Bean
    @org.springframework.context.annotation.Lazy
    public FileAttributesProvider attributesProvider() {
        new FileAttributesProvider(execControl: execControl,
                repositoryFileAttributesServiceUrl: repositoryFileAttributesServiceUrl,
                httpClient: localHttpClient())
    }


}

