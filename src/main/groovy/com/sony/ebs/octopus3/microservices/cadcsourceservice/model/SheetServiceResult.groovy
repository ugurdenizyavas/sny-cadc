package com.sony.ebs.octopus3.microservices.cadcsourceservice.model

import com.fasterxml.jackson.annotation.JsonInclude
import groovy.transform.EqualsAndHashCode
import groovy.transform.Sortable
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
@Sortable(includes = ['urn', 'success', 'statusCode'])
@EqualsAndHashCode(includes = ['urn', 'success', 'statusCode', 'errors', 'jsonUrl'])
@JsonInclude(JsonInclude.Include.NON_NULL)
class SheetServiceResult {

    String urn
    int statusCode
    boolean success
    List errors
    String jsonUrl

}
