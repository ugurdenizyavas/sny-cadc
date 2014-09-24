package com.sony.ebs.octopus3.microservices.cadcsourceservice.delta

import com.fasterxml.jackson.annotation.JsonInclude
import groovy.transform.EqualsAndHashCode
import groovy.transform.Sortable
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
@Sortable(includes = ['cadcUrl', 'success', 'statusCode'])
@EqualsAndHashCode(includes = ['cadcUrl', 'success', 'statusCode', 'errors', 'repoUrl'])
@JsonInclude(JsonInclude.Include.NON_NULL)
class DeltaItemServiceResult {

    String cadcUrl
    boolean success
    int statusCode
    List errors
    String repoUrl

}
