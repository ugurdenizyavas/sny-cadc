package com.sony.ebs.octopus3.microservices.cadcsourceservice.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class Delta {

    ProcessId processId
    String publication
    String locale
    String since
    String cadcUrl

    @JsonIgnore
    Map<URN, String> urlMap = [:]

    @JsonIgnore
    URN getDeltaUrn() {
        new URNImpl(DeltaUrnValue.delta.toString(), [publication, locale])
    }

}
