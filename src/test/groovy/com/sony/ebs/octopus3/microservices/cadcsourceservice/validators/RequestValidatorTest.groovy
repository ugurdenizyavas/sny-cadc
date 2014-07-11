package com.sony.ebs.octopus3.microservices.cadcsourceservice.validators

import org.junit.Before
import org.junit.Test

class RequestValidatorTest {

    RequestValidator validator

    @Before
    void before() {
        validator = new RequestValidator()
    }

    @Test
    void "validate url valid"() {
        assert validator.validateUrl("http://aaa/bbb")
    }

    @Test
    void "validate url no protocol"() {
        assert validator.validateUrl("//bbb")
    }

    @Test
    void "validate url no host"() {
        assert !validator.validateUrl("/bbb")
    }

    @Test
    void "validate url null"() {
        assert !validator.validateUrl(null)
    }

    @Test
    void "validate url empty"() {
        assert !validator.validateUrl("")
    }

    @Test
    void "validate since value null"() {
        assert validator.validateSinceValue(null)
    }

    @Test
    void "validate since value empty"() {
        assert validator.validateSinceValue("")
    }

    @Test
    void "validate since value valid"() {
        assert validator.validateSinceValue("2014-07-05T00:00:00.000Z")
    }

    @Test
    void "validate since value invalid"() {
        assert !validator.validateSinceValue("2014-07-05T00-00:00.000Z")
    }

    @Test
    void "validate since value short and invalid"() {
        assert !validator.validateSinceValue("2014-07-05T00:00:00")
    }

    @Test
    void "create urn"() {
        assert validator.createUrn("urn:a:b")
    }

    @Test
    void "create urn invalid"() {
        assert !validator.createUrn("urn:a")
    }

    @Test
    void "create urn no prefix"() {
        assert !validator.createUrn("a")
    }

    @Test
    void "validate locale"() {
        assert validator.validateLocale("tr_TR")
    }

    @Test
    void "validate locale null"() {
        assert !validator.validateLocale(null)
    }

    @Test
    void "validate locale empty"() {
        assert !validator.validateLocale("")
    }

    @Test
    void "validate locale invalid"() {
        assert !validator.validateLocale("tr_T")
    }

    @Test
    void "validate publication"() {
        assert validator.validatePublication("SCORE")
    }

    @Test
    void "validate publication with dash"() {
        assert validator.validatePublication("SCORE-EDITORIAL")
    }

    @Test
    void "validate publication with underscore"() {
        assert validator.validatePublication("SCORE_EDITORIAL")
    }

    @Test
    void "validate publication alphanumeric"() {
        assert validator.validatePublication("SONY1")
    }

}
