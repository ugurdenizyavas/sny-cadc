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
    void "get sku for null"() {
        assert deltaSheet.getSkuFromUrl(null) == null
    }

    @Test
    void "get sku for empty str"() {
        assert deltaSheet.getSkuFromUrl("") == null
    }

    @Test
    void "get sku for no slash"() {
        assert deltaSheet.getSkuFromUrl("aa") == null
    }

    @Test
    void "get sku for no sku"() {
        assert deltaSheet.getSkuFromUrl("/") == null
    }

    @Test
    void "get sku for only sku"() {
        assert deltaSheet.getSkuFromUrl("/x1.c") == "x1.c"
    }

    @Test
    void "get sku for prefix and sku"() {
        assert deltaSheet.getSkuFromUrl("aa/x1.c") == "x1.c"
    }

}
