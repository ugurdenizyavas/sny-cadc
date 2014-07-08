package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import org.junit.Before
import org.junit.Test

class SheetServiceTest {

    SheetService sheetService

    @Before
    void before() {
        sheetService = new SheetService()
    }

    @Test
    void "post sheet"() {
        //def delta = sheetService.postSheet()
    }

}
