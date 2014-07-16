package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import org.springframework.stereotype.Component

/**
 * author: tryavasu
 * date: 15/07/2014
 */
@Component
class MonitoringService {

    boolean appStatus = true

    boolean checkStatus() {
        appStatus
    }

    def down() {
        appStatus = false
    }

    def up() {
        appStatus = true
    }
}
