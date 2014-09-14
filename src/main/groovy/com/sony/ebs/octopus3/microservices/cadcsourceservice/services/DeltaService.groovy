package com.sony.ebs.octopus3.microservices.cadcsourceservice.services

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.AbstractDeltaService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaUrlHelper
import com.sony.ebs.octopus3.microservices.cadcsourceservice.model.SheetServiceResult
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class DeltaService extends AbstractDeltaService {

    final JsonSlurper jsonSlurper = new JsonSlurper()

    @Autowired
    public DeltaService(ExecControl execControl,
                        @Qualifier("localHttpClient") NingHttpClient localHttpClient,
                        @Qualifier("cadcHttpClient") NingHttpClient cadcHttpClient,
                        DeltaUrlHelper deltaUrlHelper,
                        @Value('${octopus3.sourceservice.cadcsourceSheetServiceUrl}') String cadcsourceSheetServiceUrl
    ) {
        super.setDeltaUrlHelper(deltaUrlHelper)
        super.setExecControl(execControl)
        super.setCadcHttpClient(cadcHttpClient)
        super.setLocalHttpClient(localHttpClient)
        super.setCadcsourceSheetServiceUrl(cadcsourceSheetServiceUrl)
    }

    public DeltaService() {
    }

    @Override
    Object createServiceResult(Response response, String cadcUrl) {
        boolean success = NingHttpClient.isSuccess(response)
        def sheetServiceResult = new SheetServiceResult(cadcUrl: cadcUrl, success: success, statusCode: response.statusCode)
        def json = jsonSlurper.parse(response.responseBodyAsStream, EncodingUtil.CHARSET_STR)
        if (!success) {
            sheetServiceResult.errors = json?.errors.collect { it.toString() }
        } else {
            sheetServiceResult.with {
                repoUrl = json?.result?.repoUrl
            }
        }
        sheetServiceResult
    }

    @Override
    Object createServiceResultOnError(String error, String cadcUrl) {
        new SheetServiceResult(cadcUrl: cadcUrl, success: false, errors: [error])
    }
}

