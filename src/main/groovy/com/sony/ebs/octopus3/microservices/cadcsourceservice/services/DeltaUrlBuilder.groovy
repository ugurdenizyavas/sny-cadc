package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.date.DateConversionException
import com.sony.ebs.octopus3.commons.date.ISODateUtils
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

@Slf4j
@Service
public class DeltaUrlBuilder {

    @Value('${octopus3.sourceservice.storageFolder}')
    String storageFolder

    ResourceLoader resourceLoader = new DefaultResourceLoader()

    String createUrl(String publication, String locale, String sincePrm) {
        String since = sincePrm ?: createSinceFromFS(publication, locale)
        String url = createUrlInner(locale, since)
        log.info "created $url for $publication $locale since:$sincePrm"
        url
    }

    private String createUrlInner(String locale, String since) {
        !since ? "/$locale" : "/changes/$locale?since=$since"
    }

    private String createSinceFromFS(String publication, String locale) {
        def resource = resourceLoader.getResource("$storageFolder/$publication/$locale/_productlist")
        if (resource?.exists()) {
            def dateTime = new DateTime(resource?.file?.lastModified())
            ISODateUtils.toISODateString(dateTime)
        } else {
            null
        }
    }

    void storeDelta(String publication, String locale, String text) {
        def directoryPath = "$storageFolder/$publication/$locale"
        def directory = resourceLoader.getResource(directoryPath)
        FileUtils.forceMkdir(directory.file)
        FileUtils.write(resourceLoader.getResource("$directoryPath/_productlist").file, text, "UTF-8")
    }

    String getSkuFromUrl(String url) {
        url?.lastIndexOf('/') >= 0 && !url?.endsWith("/") ? url.substring(url.lastIndexOf('/') + 1) : null
    }

}
