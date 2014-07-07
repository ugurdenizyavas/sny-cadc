package com.sony.ebs.octopus3.microservices.cadcsourceservice

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer

import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Configuration
@ComponentScan(value = "com.sony.ebs.octopus3.microservices.cadcsourceservice")
@PropertySource(value = ['classpath:/default.properties', 'classpath:/${ENV}.properties'], ignoreResourceNotFound = true)
class SpringConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean(name = "executorService")
    public ExecutorService getExecutorService() {
        new ThreadPoolExecutor(10, 100, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>())
    }




}

