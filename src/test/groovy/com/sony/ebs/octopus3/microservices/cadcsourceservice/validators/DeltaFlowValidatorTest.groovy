package com.sony.ebs.octopus3.microservices.cadcsourceservice.validators

import org.junit.Before
import org.junit.Test

class DeltaFlowValidatorTest {

    DeltaFlowValidator deltaFlowValidator

    @Before
    void before() {
        deltaFlowValidator = new DeltaFlowValidator()
    }

    @Test
    void "validate url valid"() {
        assert deltaFlowValidator.validateUrl("http://aaa/bbb")
    }

    @Test
    void "validate url no protocol"() {
        assert deltaFlowValidator.validateUrl("//bbb")
    }

    @Test
    void "validate url no host"() {
        assert !deltaFlowValidator.validateUrl("/bbb")
    }

    @Test
    void "validate url null"() {
        assert !deltaFlowValidator.validateUrl(null)
    }

    @Test
    void "validate url empty"() {
        assert !deltaFlowValidator.validateUrl("")
    }

    @Test
    void "validate since value all"() {
        assert deltaFlowValidator.validateSinceValue("All")
    }

    @Test
    void "validate since value null"() {
        assert deltaFlowValidator.validateSinceValue(null)
    }

    @Test
    void "validate since value empty"() {
        assert deltaFlowValidator.validateSinceValue("")
    }

    @Test
    void "validate since value valid"() {
        assert deltaFlowValidator.validateSinceValue("2014-07-05T00:00:00.000Z")
    }

    @Test
    void "validate since value invalid"() {
        assert !deltaFlowValidator.validateSinceValue("2014-07-05T00-00:00.000Z")
    }

    @Test
    void "validate since value short and invalid"() {
        assert !deltaFlowValidator.validateSinceValue("2014-07-05T00:00:00")
    }
}
