package com.sony.ebs.octopus3.microservices.cadcsourceservice.model

import org.junit.Before
import org.junit.Test

class DeltaSheetTest {

    DeltaSheet deltaSheet

    @Before
    void before() {
        deltaSheet = new DeltaSheet(publication: "SCORE", locale: "en_GB", url: "//")
    }

    @Test
    void "setUrnStr"() {
        deltaSheet.assignUrnStr("aaa")
        assert deltaSheet.urnStr == "urn:global_sku:score:en_gb:aaa"
    }

}
