package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import org.junit.Before
import org.junit.Test

class DeltaCollaboratorTest {

    DeltaCollaborator deltaCollaborator

    @Before
    void before() {
        deltaCollaborator = new DeltaCollaborator(storageFolder: "target/oct3")
    }

    @Test
    void "create url"() {
        def delta = new Delta(publication: "GLOBAL", locale: "en_GB")
        deltaCollaborator.deleteDelta(delta)
        assert deltaCollaborator.createUrl(delta) == "/en_GB"
    }

    @Test
    void "create url with since value all"() {
        assert deltaCollaborator.createUrl(new Delta(publication: "GLOBAL", locale: "en_GB", since: "All")) == "/en_GB"
    }

    @Test
    void "create url with since"() {
        assert deltaCollaborator.createUrl(new Delta(publication: "GLOBAL", locale: "en_GB", since: "s1")) == "/changes/en_GB?since=s1"
    }

    @Test
    void "create url with since encoded"() {
        assert deltaCollaborator.createUrl(new Delta(publication: "GLOBAL", locale: "en_GB", since: "2014-07-17T14:35:25.089+03:00")) == "/changes/en_GB?since=2014-07-17T14%3A35%3A25.089%2B03%3A00"
    }

    @Test
    void "create since with file"() {
        def delta = new Delta(publication: "GLOBAL", locale: "fr_FR")
        deltaCollaborator.storeDelta(delta, "xxx")
        assert deltaCollaborator.createUrl(delta).contains("since=")
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
        def delta = new Delta(publication: "GLOBAL", locale: "en_GB")
        deltaCollaborator.storeDelta(delta, "xxx")
        assert deltaCollaborator.readDelta(delta) == "xxx"
    }

}
