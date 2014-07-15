package com.sony.ebs.octopus3.microservices.cadcsourceservice.model

import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
class DeltaSheet {

    String urnStr
    String url
    String processId

}
