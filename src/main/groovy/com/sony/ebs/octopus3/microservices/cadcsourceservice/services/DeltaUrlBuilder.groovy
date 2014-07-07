package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.date.DateConversionException
import com.sony.ebs.octopus3.commons.date.ISODateUtils
import groovy.util.logging.Slf4j
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

@Slf4j
@Component
public class DeltaUrlBuilder {

    @Value('${octopus3.sourceservice.storageFolder}')
    String storageFolder

    ResourceLoader resourceLoader = new DefaultResourceLoader()

    String createUrl(String publication, String locale, String sincePrm) {
        String since = createSinceValue(publication, locale, sincePrm)
        String url = createUrl(locale, since)
        log.info "created $url for $publication $locale since:$sincePrm"
        url
    }

    String createUrl(String locale, String since) {
        !since ? "/$locale" : "/changes/$locale?since=$since"
    }

    String createSinceValue(String publication, String locale, String sincePrm) throws DateConversionException {
        String since = null
        if (sincePrm) {
            if (!"all".equalsIgnoreCase(sincePrm)) {
                def date = ISODateUtils.toISODate(sincePrm)
                log.debug "sincePrm parsed as $date"
                since = sincePrm
            }
        } else {
            def resource = resourceLoader.getResource("$storageFolder/$publication/$locale/_productlist")
            if (resource?.exists()) {
                def dateTime = new DateTime(resource?.file?.lastModified())
                since = ISODateUtils.toISODateString(dateTime)
            }
        }
        since
    }

    String getProductFromUrl(String url) {
        url?.lastIndexOf('/') >= 0 && !url?.endsWith("/") ? url.substring(url.lastIndexOf('/') + 1) : null
    }

}
