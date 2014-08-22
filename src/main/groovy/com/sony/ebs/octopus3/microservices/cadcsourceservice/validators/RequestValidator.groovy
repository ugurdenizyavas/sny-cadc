package com.sony.ebs.octopus3.microservices.cadcsourceservice.validators

import com.sony.ebs.octopus3.commons.date.ISODateUtils
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.DeltaSheet
import groovy.util.logging.Slf4j
import org.apache.commons.lang.LocaleUtils
import org.apache.http.client.utils.URIBuilder
import org.springframework.stereotype.Component

@Slf4j
@Component
@org.springframework.context.annotation.Lazy
class RequestValidator {

    /**
     * Validates all delta params
     * @param delta
     * @return
     */
    List validateDelta(Delta delta) {
        def errors = []

        if (!(delta.publication ==~ /[a-zA-Z0-9\-\_]+/)) {
            errors << "publication parameter is invalid"
        }
        try {
            if (!delta.locale) {
                errors << "locale parameter is invalid"
            } else {
                LocaleUtils.toLocale(delta.locale)
            }
        } catch (e) {
            errors << "locale parameter is invalid"
        }
        try {
            if (delta.since && !delta.since.equalsIgnoreCase("all")) {
                ISODateUtils.toISODate(delta.since)
            }
        } catch (e) {
            errors << "since parameter is invalid"
        }
        if (!validateUrl(delta.cadcUrl)) {
            errors << "cadcUrl parameter is invalid"
        }
        errors
    }

    /**
     * Validates all deltaSheet params
     * @param deltaSheet
     * @return
     */
    List validateDeltaSheet(DeltaSheet deltaSheet) {
        def errors = []
        if (!validateUrl(deltaSheet.url)) {
            errors << "url parameter is invalid"
        }
        try {
            new URNImpl(deltaSheet.urnStr)
        } catch (e) {
            errors << "urn parameter is invalid"
        }
        errors
    }

    /**
     * The url needs to be valid and should have a host
     * @param url
     * @return
     */
    private boolean validateUrl(String url) {
        if (url) {
            URIBuilder uriBuilder
            try {
                uriBuilder = new URIBuilder(url)
            } catch (e) {
                log.error "invalid url value $url", e
                return false
            }
            uriBuilder.host
        } else {
            false
        }
    }

}
