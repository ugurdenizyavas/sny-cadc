package com.sony.ebs.octopus3.microservices.cadcsourceservice.model

import com.sony.ebs.octopus3.commons.urn.URN
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false)
class Delta {
    String publication
    String locale
    Map<URN, String> urlMap = [:]
}
