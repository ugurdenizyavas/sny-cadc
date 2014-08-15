package com.sony.ebs.octopus3.microservices.cadcsourceservice.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true, excludes = ['errors'])
@JsonInclude(JsonInclude.Include.NON_NULL)
class DeltaSheet {

    String urnStr
    String url
    String processId

    @JsonIgnore
    List errors = []

}
