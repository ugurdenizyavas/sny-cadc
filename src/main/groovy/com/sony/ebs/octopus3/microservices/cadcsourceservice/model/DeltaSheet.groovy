package com.sony.ebs.octopus3.microservices.cadcsourceservice.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.transform.ToString
import groovy.util.logging.Slf4j

@ToString(includeNames = true, includePackage = false, ignoreNulls = true, includes = ['publication', 'locale', 'processId', 'url'])
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
class DeltaSheet {

    String publication
    String locale
    String url
    String processId

    String urnStr

    @JsonIgnore
    List errors = []

    void assignUrnStr(String materialName) {
        urnStr = new URNImpl(DeltaUrnValue.global_sku.toString(), [publication, locale, materialName]).toString()
    }

}
