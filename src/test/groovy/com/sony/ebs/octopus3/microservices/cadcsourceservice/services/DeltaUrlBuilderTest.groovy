package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import org.junit.Before
import org.junit.Test

class DeltaUrlBuilderTest {

    DeltaUrlBuilder deltaUrlBuilder

    @Before
    void before() {
        deltaUrlBuilder = new DeltaUrlBuilder(storageFolder: "target/delta")
    }

    @Test
    void "create url"() {
        deltaUrlBuilder.deleteDelta("GLOBAL", "en_GB")
        assert deltaUrlBuilder.createUrl("GLOBAL", "en_GB", null) == "/en_GB"
    }

    @Test
    void "create url with since value all"() {
        assert deltaUrlBuilder.createUrl("GLOBAL", "en_GB", "All") == "/en_GB"
    }

    @Test
    void "create url with since"() {
        assert deltaUrlBuilder.createUrl("GLOBAL", "en_GB", "s1") == "/changes/en_GB?since=s1"
    }

    @Test
    void "create since with file"() {
        deltaUrlBuilder.storeDelta("GLOBAL", "fr_FR", "xxx")
        assert deltaUrlBuilder.createUrl("GLOBAL", "fr_FR", null).contains("since=")
    }

    @Test
    void "get sku for null"() {
        assert deltaUrlBuilder.getSkuFromUrl(null) == null
    }

    @Test
    void "get sku for empty str"() {
        assert deltaUrlBuilder.getSkuFromUrl("") == null
    }

    @Test
    void "get sku for no slash"() {
        assert deltaUrlBuilder.getSkuFromUrl("aa") == null
    }

    @Test
    void "get sku for no sku"() {
        assert deltaUrlBuilder.getSkuFromUrl("/") == null
    }

    @Test
    void "get sku for only sku"() {
        assert deltaUrlBuilder.getSkuFromUrl("/x1.c") == "x1.c"
    }

    @Test
    void "get sku for prefix and sku"() {
        assert deltaUrlBuilder.getSkuFromUrl("aa/x1.c") == "x1.c"
    }

    @Test
    void "store delta"() {
        deltaUrlBuilder.storeDelta("GLOBAL", "en_GB", "xxx")
        assert deltaUrlBuilder.readDelta("GLOBAL", "en_GB") == "xxx"
    }

}
