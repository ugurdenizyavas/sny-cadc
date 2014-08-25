package com.sony.ebs.octopus3.microservices.cadcsourceservice.model

import com.fasterxml.jackson.annotation.JsonInclude
import groovy.transform.EqualsAndHashCode
import groovy.transform.Sortable
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
@Sortable(includes = ['urnStr', 'success', 'statusCode'])
@EqualsAndHashCode(includes = ['urnStr', 'success', 'statusCode', 'errors', 'repoUrl'])
@JsonInclude(JsonInclude.Include.NON_NULL)
class SheetServiceResult {

    String urnStr
    String cadcUrl
    int statusCode
    boolean success
    List errors
    String repoUrl

}
