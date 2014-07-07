package com.sony.ebs.octopus3.microservices.cadcsourceservice.model

import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false)
class Delta {
    String publication
    String locale
    List urls = []
}
