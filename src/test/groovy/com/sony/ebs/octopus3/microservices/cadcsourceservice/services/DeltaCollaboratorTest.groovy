package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import org.junit.Before
import org.junit.Test

class DeltaCollaboratorTest {

    DeltaCollaborator deltaCollaborator

    @Before
    void before() {
        deltaCollaborator = new DeltaCollaborator(storageFolder: "target/delta")
    }

    @Test
    void "create url"() {
        deltaCollaborator.deleteDelta("GLOBAL", "en_GB")
        assert deltaCollaborator.createUrl("GLOBAL", "en_GB", null) == "/en_GB"
    }

    @Test
    void "create url with since value all"() {
        assert deltaCollaborator.createUrl("GLOBAL", "en_GB", "All") == "/en_GB"
    }

    @Test
    void "create url with since"() {
        assert deltaCollaborator.createUrl("GLOBAL", "en_GB", "s1") == "/changes/en_GB?since=s1"
    }

    @Test
    void "create since with file"() {
        deltaCollaborator.storeDelta("GLOBAL", "fr_FR", "xxx")
        assert deltaCollaborator.createUrl("GLOBAL", "fr_FR", null).contains("since=")
    }

    @Test
    void "get sku for null"() {
        assert deltaCollaborator.getSkuFromUrl(null) == null
    }

    @Test
    void "get sku for empty str"() {
        assert deltaCollaborator.getSkuFromUrl("") == null
    }

    @Test
    void "get sku for no slash"() {
        assert deltaCollaborator.getSkuFromUrl("aa") == null
    }

    @Test
    void "get sku for no sku"() {
        assert deltaCollaborator.getSkuFromUrl("/") == null
    }

    @Test
    void "get sku for only sku"() {
        assert deltaCollaborator.getSkuFromUrl("/x1.c") == "x1.c"
    }

    @Test
    void "get sku for prefix and sku"() {
        assert deltaCollaborator.getSkuFromUrl("aa/x1.c") == "x1.c"
    }

    @Test
    void "store delta"() {
        deltaCollaborator.storeDelta("GLOBAL", "en_GB", "xxx")
        assert deltaCollaborator.readDelta("GLOBAL", "en_GB") == "xxx"
    }

}
