package com.sony.ebs.octopus3.microservices.cadcsourceservice.validators

import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaSheet
import org.junit.Before
import org.junit.Test

class RequestValidatorTest {

    RequestValidator validator
    Delta delta
    DeltaSheet deltaSheet

    @Before
    void before() {
        validator = new RequestValidator()
        delta = new Delta(publication: "SCORE", locale: "en_GB", cadcUrl: "http://aaa/bbb", since: "2014-07-05T00:00:00.000Z")
        deltaSheet = new DeltaSheet(publication: "SCORE", locale: "en_GB", url: "//a")
    }

    @Test
    void "validate all"() {
        assert !validator.validateDelta(delta)
    }

    @Test
    void "validate url no protocol"() {
        delta.cadcUrl = "//bbb"
        assert !validator.validateDelta(delta)
    }

    @Test
    void "validate url no host"() {
        delta.cadcUrl = "/bbb"
        assert validator.validateDelta(delta) == ["cadcUrl parameter is invalid"]
    }

    @Test
    void "validate url null"() {
        delta.cadcUrl = null
        assert validator.validateDelta(delta) == ["cadcUrl parameter is invalid"]
    }

    @Test
    void "validate url empty"() {
        delta.cadcUrl = ""
        assert validator.validateDelta(delta) == ["cadcUrl parameter is invalid"]
    }

    @Test
    void "validate since value null"() {
        delta.since = null
        assert !validator.validateDelta(delta)
    }

    @Test
    void "validate since value empty"() {
        delta.since = ""
        assert !validator.validateDelta(delta)
    }

    @Test
    void "validate since value all"() {
        delta.since = "All"
        assert !validator.validateDelta(delta)
    }

    @Test
    void "validate since value invalid"() {
        delta.since = "2014-07-05T00-00:00.000Z"
        assert validator.validateDelta(delta) == ["since parameter is invalid"]
    }

    @Test
    void "validate since value short and invalid"() {
        delta.since = "2014-07-05T00:00:00"
        assert validator.validateDelta(delta) == ["since parameter is invalid"]
    }

    @Test
    void "validate locale null"() {
        delta.locale = null
        assert validator.validateDelta(delta) == ["locale parameter is invalid"]
    }

    @Test
    void "validate locale empty"() {
        delta.locale = ""
        assert validator.validateDelta(delta) == ["locale parameter is invalid"]
    }

    @Test
    void "validate locale invalid"() {
        delta.locale = "tr_T"
        assert validator.validateDelta(delta) == ["locale parameter is invalid"]
    }

    @Test
    void "validate publication null"() {
        delta.publication = null
        assert validator.validateDelta(delta) == ["publication parameter is invalid"]
    }

    @Test
    void "validate publication empty"() {
        delta.publication = ""
        assert validator.validateDelta(delta) == ["publication parameter is invalid"]
    }

    @Test
    void "validate publication invalid"() {
        delta.publication = "?aa"
        assert validator.validateDelta(delta) == ["publication parameter is invalid"]
    }

    @Test
    void "validate publication with dash"() {
        delta.publication = "SCORE-EDITORIAL"
        assert !validator.validateDelta(delta)
    }

    @Test
    void "validate publication with underscore"() {
        delta.publication = "SCORE_EDITORIAL"
        assert !validator.validateDelta(delta)
    }

    @Test
    void "validate publication alphanumeric"() {
        delta.publication = "SONY1"
        assert !validator.validateDelta(delta)
    }

    @Test
    void "validate sheet"() {
        assert !validator.validateDeltaSheet(deltaSheet)
    }

    @Test
    void "validate sheet invalid url"() {
        deltaSheet.url = "/a"
        assert validator.validateDeltaSheet(deltaSheet) == ["url parameter is invalid"]
    }

    @Test
    void "validate sheet invalid publication"() {
        deltaSheet.publication = null
        assert validator.validateDeltaSheet(deltaSheet) == ["publication parameter is invalid"]
    }

    @Test
    void "validate sheet invalid locale"() {
        deltaSheet.locale = null
        assert validator.validateDeltaSheet(deltaSheet) == ["locale parameter is invalid"]
    }
}
