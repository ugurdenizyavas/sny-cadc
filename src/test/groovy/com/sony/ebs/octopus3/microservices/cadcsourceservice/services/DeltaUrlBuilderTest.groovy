package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.date.DateConversionException
import org.junit.Before
import org.junit.Test

class DeltaUrlBuilderTest {

    DeltaUrlBuilder deltaUrlBuilder

    @Before
    void before() {
        deltaUrlBuilder = new DeltaUrlBuilder(storageFolder: "classpath:datefiles")
    }

    @Test
    void "create since with file"() {
        def since = deltaUrlBuilder.createSinceValue("GLOBAL", "fr_FR", null)
        assert since != null
    }

    @Test
    void "create since with no file"() {
        def since = deltaUrlBuilder.createSinceValue("GLOBAL", "en_GB", null)
        assert since == null
    }

    @Test
    void "create since with param"() {
        def since = deltaUrlBuilder.createSinceValue("GLOBAL", "fr_FR", "2014-06-20T20:30:00.000Z")
        assert since == "2014-06-20T20:30:00.000Z"
    }

    @Test(expected = DateConversionException.class)
    void "create since with wrong format"() {
        deltaUrlBuilder.createSinceValue("GLOBAL", "fr_FR", "2014-06-20T20:30:00-")
    }

    @Test
    void "create since with all"() {
        def since = deltaUrlBuilder.createSinceValue("GLOBAL", "fr_FR", "All")
        assert since == null
    }

    @Test
    void "create url"() {
        assert deltaUrlBuilder.createUrl("en_GB", null) == "/en_GB"
    }

    @Test
    void "create url with since"() {
        assert deltaUrlBuilder.createUrl("en_GB", "s1") == "/changes/en_GB?since=s1"
    }

    @Test
    void "get product for null"() {
        assert deltaUrlBuilder.getProductFromUrl(null) == null
    }

    @Test
    void "get product for empty str"() {
        assert deltaUrlBuilder.getProductFromUrl("") == null
    }

    @Test
    void "get product for no slash"() {
        assert deltaUrlBuilder.getProductFromUrl("aa") == null
    }

    @Test
    void "get product for no product"() {
        assert deltaUrlBuilder.getProductFromUrl("/") == null
    }

    @Test
    void "get product for only product"() {
        assert deltaUrlBuilder.getProductFromUrl("/x1.c") == "x1.c"
    }

    @Test
    void "get product for prefix and product"() {
        assert deltaUrlBuilder.getProductFromUrl("aa/x1.c") == "x1.c"
    }
}
