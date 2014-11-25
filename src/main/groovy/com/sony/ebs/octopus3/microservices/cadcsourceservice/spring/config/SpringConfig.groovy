package com.sony.ebs.octopus3.microservices.cadcsourceservice.spring.config

import com.sony.ebs.octopus3.commons.ratpack.file.FileAttributesProvider
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaUrlHelper
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
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

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Autowired
    @Qualifier("localHttpClient")
    @org.springframework.context.annotation.Lazy
    Oct3HttpClient localHttpClient

    @Bean
    public RequestValidator requestValidator() {
        new RequestValidator()
    }

    @Value('${octopus3.sourceservice.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Bean
    @org.springframework.context.annotation.Lazy
    public DeltaUrlHelper deltaUrlHelper() {
        new DeltaUrlHelper(execControl: execControl,
                repositoryFileServiceUrl: repositoryFileServiceUrl,
                httpClient: localHttpClient,
                fileAttributesProvider: attributesProvider()
        )
    }

    @Value('${octopus3.sourceservice.repositoryFileAttributesServiceUrl}')
    String repositoryFileAttributesServiceUrl

    @Bean
    @org.springframework.context.annotation.Lazy
    public FileAttributesProvider attributesProvider() {
        new FileAttributesProvider(execControl: execControl,
                repositoryFileAttributesServiceUrl: repositoryFileAttributesServiceUrl,
                httpClient: localHttpClient)
    }


    @Bean
    @Qualifier('fileStorage')
    @org.springframework.context.annotation.Lazy
    public ResponseStorage responseStorage(
            @Value('${octopus3.sourceservice.repositoryFileServiceUrl}') String saveUrl) {
        new ResponseStorage(
                httpClient: localHttpClient,
                saveUrl: saveUrl
        )
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public DeltaResultService deltaResultService() {
        new DeltaResultService()
    }

}