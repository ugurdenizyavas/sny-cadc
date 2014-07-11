package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.date.ISODateUtils
import com.sony.ebs.octopus3.commons.file.FileUtils
import groovy.util.logging.Slf4j
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

@Slf4j
@Service
public class DeltaUrlBuilder {

    @Value('${octopus3.sourceservice.storageFolder}')
    String storageFolder

    String createUrl(String publication, String locale, String sincePrm) {
        String since = !sincePrm ? createSinceFromFS(publication, locale) : (!sincePrm.equalsIgnoreCase("all") ? sincePrm : null)
        String url = createUrlInner(locale, since)
        log.info "url created for publication $publication locale $locale since $sincePrm is $url"
        url
    }

    private String createUrlInner(String locale, String since) {
        def url = !since ? "/$locale" : "/changes/$locale?since=$since"
        log.info "url inner for locale $locale and since $since is $url"
        url
    }

    Path createDeltaFilePath(String publication, String locale) {
        Paths.get("$storageFolder/$publication/$locale/_productlist")
    }

    private String createSinceFromFS(String publication, String locale) {
        def path = createDeltaFilePath(publication, locale)
        if (Files.exists(path)) {
            def lastModifiedTime = Files.readAttributes(path, BasicFileAttributes.class)?.lastModifiedTime()?.toMillis()
            def str = ISODateUtils.toISODateString(new DateTime(lastModifiedTime))
            log.info "lastModifiedTime for $publication and $locale is $str"
            str
        } else {
            null
        }
    }

    void storeDelta(String publication, String locale, String text) {
        def path = createDeltaFilePath(publication, locale)
        log.info "starting storing $path"
        FileUtils.writeFile(path, text.getBytes("UTF-8"), true, true)
        log.info "finished storing $path"
    }

    String readDelta(String publication, String locale) {
        def path = createDeltaFilePath(publication, locale)
        log.info "starting reading $path"
        def result = new String(Files.readAllBytes(path), "UTF-8")
        log.info "finished reading $path"
        result
    }

    void deleteDelta(String publication, String locale) {
        def path = createDeltaFilePath(publication, locale)
        log.info "starting deleting $path"
        FileUtils.delete(path)
        log.info "finished deleting $path"
    }

    String getSkuFromUrl(String url) {
        def sku = url?.lastIndexOf('/') >= 0 && !url?.endsWith("/") ? url.substring(url.lastIndexOf('/') + 1) : null
        log.info "sku for $url is $sku"
        sku
    }

}
