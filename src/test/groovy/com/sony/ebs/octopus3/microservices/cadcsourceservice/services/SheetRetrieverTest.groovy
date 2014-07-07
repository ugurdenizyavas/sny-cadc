package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import org.junit.Before
import org.junit.Test

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class SheetRetrieverTest {

    SheetRetriever sheetRetriever

    @Before
    void before() {
        def executorService = new ThreadPoolExecutor(5, 10, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<Runnable>())
        sheetRetriever = new SheetRetriever(executorService: executorService)
    }

    @Test
    void "post sheet"() {
        //def delta = sheetRetriever.postSheet()
    }

}
