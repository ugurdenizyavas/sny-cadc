package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import groovy.util.logging.Slf4j
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service

import javax.annotation.PostConstruct
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Slf4j
@Lazy
@Service
class ObservableHelper {

    ExecutorService executorService

    rx.Observable createObservable(onNextHandler) {
        rx.Observable.create({ observer ->
            // Schedulers.io().createWorker().schedule({
            executorService.submit {
                try {
                    observer.onNext(onNextHandler())
                    observer.onCompleted()
                } catch (all) {
                    observer.onError all
                }
            }
        } as rx.Observable.OnSubscribe)
    }

    @PostConstruct
    public void init() {
        executorService = new ThreadPoolExecutor(10, 100, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>())
        log.info "created executorService thread pool"
    }

}
