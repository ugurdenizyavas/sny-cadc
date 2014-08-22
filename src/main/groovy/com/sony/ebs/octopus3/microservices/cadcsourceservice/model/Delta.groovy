package com.sony.ebs.octopus3.microservices.cadcsourceservice.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true, includes = ['processId', 'publication', 'locale', 'since', 'cadcUrl'])
@JsonInclude(JsonInclude.Include.NON_NULL)
class Delta {

    ProcessId processId
    String publication
    String locale
    String since
    String cadcUrl

    @JsonIgnore
    Map urlMap

    @JsonIgnore
    List errors = []

    @JsonIgnore
    URN getBaseUrn() {
        new URNImpl(DeltaUrnValue.global_sku.toString(), [publication, locale])
    }

    @JsonIgnore
    URN getLastModifiedUrn() {
        new URNImpl(DeltaUrnValue.global_sku.toString(), [DeltaUrnValue.last_modified.toString(), publication, locale])
    }
}
