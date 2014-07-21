package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.sony.ebs.octopus3.commons.date.ISODateUtils
import com.sony.ebs.octopus3.commons.file.FileUtils
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.Delta
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
public class DeltaCollaborator {

    @Value('${octopus3.sourceservice.storageFolder}')
    String storageFolder

    String createUrl(Delta delta) {
        String since = !delta.since ? createSinceFromFS(delta) : (!delta.since.equalsIgnoreCase("all") ? delta.since : null)
        String url = createUrlInner(delta.locale, since)
        log.info "url created for $delta is $url"
        url
    }

    private String createUrlInner(String locale, String since) {
        def url = !since ? "/$locale" : "/changes/$locale?since=" + URLEncoder.encode(since, "UTF-8")
        log.info "url inner for locale $locale and since $since is $url"
        url
    }

    private Path createDeltaFilePath(Delta delta) {
        Paths.get("$storageFolder/${delta.deltaUrn.toPath()}")
    }

    private String createSinceFromFS(Delta delta) {
        def path = createDeltaFilePath(delta)
        if (Files.exists(path)) {
            def lastModifiedTime = Files.readAttributes(path, BasicFileAttributes.class)?.lastModifiedTime()?.toMillis()
            def str = ISODateUtils.toISODateString(new DateTime(lastModifiedTime))
            log.info "lastModifiedTime for $delta is $str"
            str
        } else {
            null
        }
    }

    void storeDelta(Delta delta, String text) {
        def path = createDeltaFilePath(delta)
        log.info "starting storing $path"
        FileUtils.writeFile(path, text.getBytes("UTF-8"), true, true)
        log.info "finished storing $path"
    }

    void storeUrn(URN urn, String text) {
        def path = Paths.get(storageFolder + urn.toPath())
        log.info "starting storing $urn at $path"
        FileUtils.writeFile(path, text.getBytes("UTF-8"), true, true)
        log.info "finished storing $urn at $path"
    }

    String readDelta(Delta delta) {
        def path = createDeltaFilePath(delta)
        log.info "starting reading $path"
        def result = new String(Files.readAllBytes(path), "UTF-8")
        log.info "finished reading $path"
        result
    }

    void deleteDelta(Delta delta) {
        def path = createDeltaFilePath(delta)
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
