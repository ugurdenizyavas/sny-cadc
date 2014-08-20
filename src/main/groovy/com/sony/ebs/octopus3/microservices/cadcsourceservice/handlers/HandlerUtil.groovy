package com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers

class HandlerUtil {

    static String getErrorMessage(Throwable t) {
        (t.message ?: t.cause?.message)?.toString()
    }
}
