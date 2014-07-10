package com.sony.ebs.octopus3.microservices.cadcsourceservice.validators

import com.sony.ebs.octopus3.commons.date.ISODateUtils
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.util.logging.Slf4j
import org.apache.commons.lang.LocaleUtils
import org.apache.http.client.utils.URIBuilder
import org.springframework.stereotype.Component

@Slf4j
@Component
class RequestValidator {

    /**
     * The since string should be a valid iso date if not of value 'all'
     * @param since
     * @return
     */
    boolean validateSinceValue(String since) {
        if (since && !"all".equalsIgnoreCase(since)) {
            try {
                ISODateUtils.toISODate(since)
            } catch (e) {
                log.error "invalid since value $since", e
                return false
            }
        }
        true
    }
    /**
     * The url needs to be valid and should have a host
     * @param url
     * @return
     */
    boolean validateUrl(String url) {
        if (url) {
            URIBuilder uriBuilder
            try {
                uriBuilder = new URIBuilder(url)
                log.debug "$uriBuilder is {uriBuilder.toString()} for $url"
            } catch (e) {
                log.error "invalid url value $url", e
                return false
            }
            uriBuilder.host
        } else {
            false
        }
    }

    /**
     * @param locale
     * @return true if locale is valid
     */
    boolean validateLocale(String locale) {
        try {
            LocaleUtils.toLocale(locale)
        } catch (e) {
            log.error "invalid locale $locale", e
            false
        }
    }

    /**
     * @param locale
     * @return true if publication matches its regex
     */
    boolean validatePublication(String publication) {
        publication ==~ /[a-zA-Z0-9\-\_]+/
    }

    /**
     * The urn needs to be valid and should have a host
     * @param url
     * @return
     */
    URN createUrn(String urnStr) {
        def urn
        try {
            urn = new URNImpl(urnStr)
            log.debug "urn is $urn for $urnStr"
        } catch (e) {
            log.error "invalid urn value $urnStr", e
        }
        urn
    }
}
