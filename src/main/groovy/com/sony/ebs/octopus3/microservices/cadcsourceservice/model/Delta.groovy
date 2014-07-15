package com.sony.ebs.octopus3.microservices.cadcsourceservice.model

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
class Delta {

    ProcessId processId
    String publication
    String locale
    String since
    String cadcUrl

    Map<URN, String> urlMap = [:]

    URN createUrn() {
        new URNImpl("delta", [publication, locale])
    }

}
