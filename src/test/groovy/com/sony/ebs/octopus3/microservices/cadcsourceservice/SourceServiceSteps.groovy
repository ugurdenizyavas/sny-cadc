package com.sony.ebs.octopus3.microservices.cadcsourceservice

import com.github.dreamhead.moco.HttpServer
import com.github.dreamhead.moco.Moco
import com.github.dreamhead.moco.Runner
import cucumber.api.groovy.EN
import cucumber.api.groovy.Hooks
import groovy.json.JsonSlurper
import ratpack.groovy.test.LocalScriptApplicationUnderTest
import ratpack.groovy.test.TestHttpClient
import ratpack.groovy.test.TestHttpClients
import com.jayway.restassured.response.Response

import static com.github.dreamhead.moco.Moco.by
import static com.github.dreamhead.moco.Moco.uri
import static com.github.dreamhead.moco.Moco.status
import static com.github.dreamhead.moco.Moco.with

this.metaClass.mixin(Hooks)
this.metaClass.mixin(EN)

System.setProperty 'environment', 'cucumber'
System.setProperty 'ratpack.port', '12300'

class LocalRatpackWorld {
    LocalScriptApplicationUnderTest aut = new LocalScriptApplicationUnderTest()
    @Delegate
    TestHttpClient client = TestHttpClients.testHttpClient(aut)

    HttpServer server = Moco.httpserver(12306)
    Runner runner = Runner.runner(server)
}

def parseJson = { Response response ->
    def text = response.body.asString()
    new JsonSlurper().parseText(text)
}

def validateError = { Response response, message ->
    assert response.statusCode == 400
    def json = parseJson(response)
    assert json?.status == 400
    assert json?.errors == [message]
}

World {
    new LocalRatpackWorld()
}

Before() {
    runner.start()
}

After() {
    runner.stop()
    aut.stop()
}

/*
* ******************** DELTA SERVICE ***********************************************************
* */

def createProductServiceResponse(sku) {
    """
        {
            "skuName" : "$sku"
        }
    """
}

Given(~"Cadc services for locale (.*) with no errors") { String locale ->
    def deltaFeed = '{"skus":{"' + locale + '":["http://localhost:12306/a", "http://localhost:12306/c", "http://localhost:12306/b", "http://localhost:12306/d"]}}'

    //server.get(and(by(uri("/delta/changes/$locale")), eq(query("since"), "2014-08-27T09%3A31%3A17.000%2B02%3A00"))).response(deltaFeed)
    server.get(by(uri("/delta/changes/$locale"))).response(deltaFeed)
    server.get(by(uri("/a"))).response(createProductServiceResponse("a"))
    server.get(by(uri("/b"))).response(createProductServiceResponse("b"))
    server.get(by(uri("/c"))).response(createProductServiceResponse("c"))
    server.get(by(uri("/d"))).response(createProductServiceResponse("d"))
}

Given(~"Cadc delta service error for locale (.*)") { String locale ->
    server.get(by(uri("/delta/$locale"))).response(status(500))
}

Given(~"Repo services for publication (.*) locale (.*) with no errors") { String publication, String locale ->
    def values = "${publication.toLowerCase()}:${locale.toLowerCase()}"

    server.get(by(uri("/repository/fileattributes/urn:global_sku:last_modified:$values"))).response(with('{"result" : { "lastModifiedTime" : "2014-08-27T09:31:17.000+02:00" }}'), status(200))
    server.post(by(uri("/repository/file/urn:global_sku:last_modified:$values"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:a"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:b"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:c"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:d"))).response(status(200))
}

Given(~"Repo services for publication (.*) locale (.*) with last modified date save error") { String publication, String locale ->
    def values = "${publication.toLowerCase()}:${locale.toLowerCase()}"

    server.get(by(uri("/repository/fileattributes/urn:global_sku:last_modified:$values"))).response(with('{"result" : { "lastModifiedTime" : "2014-08-27T09:31:17.000+02:00" }}'), status(200))
    server.post(by(uri("/repository/file/urn:global_sku:last_modified:$values"))).response(status(500))
}

When(~"I request delta of publication (.*) locale (.*)") { String publication, String locale ->
    get("cadcsource/delta/publication/$publication/locale/$locale?cadcUrl=http://localhost:12306/delta")
}

Then(~"Delta for publication (.*) locale (.*) should be imported successfully") { String publication, String locale ->
    def values = "${publication.toLowerCase()}:${locale.toLowerCase()}"

    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json.delta.publication == publication
    assert json.delta.locale == locale
    assert json.delta.cadcUrl == "http://localhost:12306/delta"

    assert json.result.stats."number of delta products" == 4
    assert json.result.stats."number of successful" == 4
    assert json.result.stats."number of unsuccessful" == 0
    assert json.result.finalStartDate == "2014-08-27T09:31:17.000+02:00"
    assert json.result.finalDeltaUrl == "http://localhost:12306/delta/changes/$locale?since=2014-08-27T09%3A31%3A17.000%2B02%3A00"

    def getRepoUrl = { "http://localhost:12306/repository/file/urn:global_sku:$values:$it".toString() }
    assert json.result.other.outputUrls?.sort() == [getRepoUrl("a"), getRepoUrl("b"), getRepoUrl("c"), getRepoUrl("d")]
}

Then(~"Delta for publication (.*) locale (.*) should get last modified date save error") { String publication, String locale ->
    assert response.statusCode == 500
    def json = parseJson(response)
    assert json.status == 500
    assert json.delta.publication == publication
    assert json.delta.locale == locale

    assert json.errors == ["HTTP 500 error updating last modified date"]
    assert !json.result
}

Then(~"Delta for publication (.*) locale (.*) should get cadc delta service error") { String publication, String locale ->
    assert response.statusCode == 500
    def json = parseJson(response)
    assert json.status == 500
    assert json.delta.publication == publication
    assert json.delta.locale == locale

    assert json.errors == ["HTTP 500 error getting delta from cadc"]
    assert !json.result
}


Given(~"Cadc services for locale (.*) with errors") { String locale ->
    def deltaFeed = '{ "skus" : {"' + locale + '''" : [
                "http://localhost:12306/a",
                "http://localhost:12306/b",
                "http://localhost:12306/c",
                "http://localhost:12306/d",
                "http://localhost:12306/e",
                "http://localhost:12306/f",
                "http://localhost:12306/g"
            ]
        }}
        '''
    server.get(by(uri("/delta/$locale"))).response(deltaFeed)
    server.get(by(uri("/a"))).response(createProductServiceResponse("a"))
    server.get(by(uri("/b"))).response(createProductServiceResponse("b"))
    server.get(by(uri("/c"))).response(createProductServiceResponse("c"))
    server.get(by(uri("/d"))).response(status(404))
    server.get(by(uri("/e"))).response(createProductServiceResponse("e"))
    server.get(by(uri("/f"))).response(status(500))
    server.get(by(uri("/g"))).response(status(404))
}

Given(~"Repo services for publication (.*) locale (.*) with save errors") { String publication, String locale ->
    def values = "${publication.toLowerCase()}:${locale.toLowerCase()}"

    server.get(by(uri("/repository/fileattributes/urn:global_sku:last_modified:$values"))).response(status(404))
    server.post(by(uri("/repository/file/urn:global_sku:last_modified:$values"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:a"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:b"))).response(status(500))
    server.post(by(uri("/repository/file/urn:global_sku:$values:c"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:e"))).response(status(500))
}

Then(~"Delta for publication (.*) locale (.*) should get save errors") { String publication, String locale ->
    def values = "${publication.toLowerCase()}:${locale.toLowerCase()}"

    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json.delta.publication == publication
    assert json.delta.locale == locale

    assert json.result.stats."number of delta products" == 7
    assert json.result.stats."number of successful" == 2
    assert json.result.stats."number of unsuccessful" == 5

    def getCadcUrl = { "http://localhost:12306/$it".toString() }
    def getRepoUrl = { "http://localhost:12306/repository/file/urn:global_sku:$values:$it".toString() }
    assert json.result.other.outputUrls?.sort() == [getRepoUrl("a"), getRepoUrl("c")]

    assert json.result.productErrors?.size() == 3
    assert json.result.productErrors."HTTP 500 error saving product to repo"?.sort() == [getCadcUrl("b"), getCadcUrl("e")]
    assert json.result.productErrors."HTTP 404 error getting product from cadc"?.sort() == [getCadcUrl("d"), getCadcUrl("g")]
    assert json.result.productErrors."HTTP 500 error getting product from cadc" == [getCadcUrl("f")]
}

Given(~"Cadc services for locale (.*) with parse delta error") { String locale ->
    server.get(by(uri("/delta/$locale"))).response('invalid json')
}

Then(~"Delta for publication (.*) locale (.*) should get parse delta error") { String publication, String locale ->
    assert response.statusCode == 500
    def json = parseJson(response)
    assert json.status == 500
    assert json.delta.publication == publication
    assert json.delta.locale == locale

    assert json.errors == ["error parsing delta"]
    assert !json.result
}


When(~"I import delta with invalid (.*) parameter") { paramName ->
    if (paramName == "publication") {
        get("cadcsource/delta/publication/,,/locale/en_GB?cadcUrl=//host/skus")
    } else if (paramName == "locale") {
        get("cadcsource/delta/publication/SCORE/locale/tr_?cadcUrl=//host/skus")
    } else if (paramName == "cadcUrl") {
        get("cadcsource/delta/publication/SCORE/locale/en_GB?cadcUrl=/host/skus")
    } else if (paramName == "since") {
        get("cadcsource/delta/publication/SCORE/locale/en_GB?cadcUrl=http://host/skus&since=s1")
    }
}

Then(~"Import should give (.*) parameter error") { paramName ->
    validateError(response, "$paramName parameter is invalid")
}

/*
* ******************** PRODUCT SERVICE *************************************************************
* */

Given(~"Cadc product (.*)") { name ->
    server.request(by(uri("/cadc/product/$name"))).response(createProductServiceResponse(name))
}

Given(~"Repo save service for publication (.*) locale (.*) sku (.*)") { String publication, String locale, String sku ->
    def publicationLC = publication.toLowerCase()
    def localeLC = locale.toLowerCase()
    def skuLC = sku.toLowerCase()
    def values = "$publicationLC:$localeLC:$skuLC"
    server.post(by(uri("/repository/file/urn:global_sku:$values"))).response(status(200))
}

When(~"I import product with publication (.*) locale (.*) sku (.*) correctly") { String publication, String locale, String sku ->
    get("cadcsource/product/publication/$publication/locale/$locale?url=http://localhost:12306/cadc/product/$sku")
}

Then(~"Product with publication (.*) locale (.*) sku (.*) should be imported successful") { String publication, String locale, String sku ->
    def publicationLC = publication.toLowerCase()
    def localeLC = locale.toLowerCase()
    def skuLC = sku.toLowerCase()
    def skuUrn = "urn:global_sku:$publicationLC:$localeLC:$skuLC"

    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json?.product?.url == "http://localhost:12306/cadc/product/$sku"
    assert json?.product?.publication == publication
    assert json?.product?.locale == locale

    assert json?.result?.sku.equalsIgnoreCase(sku)
    assert json?.result?.inputUrl == "http://localhost:12306/cadc/product/$sku"
    assert json?.result?.outputUrn == skuUrn
    assert json?.result?.outputUrl == "http://localhost:12306/repository/file/$skuUrn"
}

When(~"I import product with invalid (.*) parameter") { paramName ->
    if (paramName == "publication") {
        get("cadcsource/product/publication/,,/locale/en_GB?url=http://product/a")
    } else if (paramName == "locale") {
        get("cadcsource/product/publication/SCORE/locale/--?url=http://product/a")
    } else if (paramName == "url") {
        get("cadcsource/product/publication/SCORE/locale/en_GB?url=/product/a")
    }
}

