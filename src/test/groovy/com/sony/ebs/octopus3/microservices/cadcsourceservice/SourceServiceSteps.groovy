package com.sony.ebs.octopus3.microservices.cadcsourceservice

import com.github.dreamhead.moco.HttpServer
import com.github.dreamhead.moco.Moco
import com.github.dreamhead.moco.Runner
import cucumber.api.groovy.EN
import cucumber.api.groovy.Hooks
import groovy.json.JsonBuilder
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

Given(~"Cadc services for locale (.*)") { String locale ->
    def deltaFeed = '{"skus":{"' + locale + '":["http://localhost:12306/a", "http://localhost:12306/c", "http://localhost:12306/b"]}}'
    server.get(by(uri("/delta/$locale"))).response(deltaFeed)
    server.get(by(uri("/a"))).response("a")
    server.get(by(uri("/b"))).response("b")
    server.get(by(uri("/c"))).response("c")
}

Given(~"Cadc delta service error for locale (.*)") { String locale ->
    server.get(by(uri("/delta/$locale"))).response(status(500))
}

Given(~"Repo services for publication (.*) locale (.*) with no errors") { String publication, String locale ->
    def values = "${publication.toLowerCase()}:${locale.toLowerCase()}"

    server.get(by(uri("/repository/fileattributes/urn:global_sku:last_modified:$values"))).response(status(404))
    server.post(by(uri("/repository/file/urn:global_sku:last_modified:$values"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:a"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:b"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:c"))).response(status(200))
}

Given(~"Repo services for publication (.*) locale (.*) with last modified date save error") { String publication, String locale ->
    def values = "${publication.toLowerCase()}:${locale.toLowerCase()}"

    server.get(by(uri("/repository/fileattributes/urn:global_sku:last_modified:$values"))).response(status(404))
    server.post(by(uri("/repository/file/urn:global_sku:last_modified:$values"))).response(status(500))
}

Given(~"Repo services for publication (.*) locale (.*) with save errors") { String publication, String locale ->
    def values = "${publication.toLowerCase()}:${locale.toLowerCase()}"

    server.get(by(uri("/repository/fileattributes/urn:global_sku:last_modified:$values"))).response(status(404))
    server.post(by(uri("/repository/file/urn:global_sku:last_modified:$values"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:a"))).response(status(200))
    server.post(by(uri("/repository/file/urn:global_sku:$values:b"))).response(status(500))
    server.post(by(uri("/repository/file/urn:global_sku:$values:c"))).response(status(200))
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

    assert json.result.stats."number of delta products" == 3
    assert json.result.stats."number of success" == 3
    assert json.result.stats."number of errors" == 0

    assert json.result.list.size() == 3
    assert json.result.list.contains([statusCode: 200, success: true, urn: "urn:global_sku:$values:a".toString()])
    assert json.result.list.contains([statusCode: 200, success: true, urn: "urn:global_sku:$values:b".toString()])
    assert json.result.list.contains([statusCode: 200, success: true, urn: "urn:global_sku:$values:c".toString()])
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

    assert json.errors == ["HTTP 500 error getting delta json from cadc"]
    assert !json.result
}

Then(~"Delta for publication (.*) locale (.*) should get save errors") { String publication, String locale ->
    def values = "${publication.toLowerCase()}:${locale.toLowerCase()}"

    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json.delta.publication == publication
    assert json.delta.locale == locale

    assert json.result.stats."number of delta products" == 3
    assert json.result.stats."number of success" == 2
    assert json.result.stats."number of errors" == 1

    def list = json.result.list
    assert list.size() == 3
    assert list.contains([statusCode: 200, success: true, urn: "urn:global_sku:$values:a".toString()])
    assert list.contains([statusCode: 500, success: false, urn: "urn:global_sku:$values:b".toString(), errors: ["HTTP 500 error saving sheet json to repo"]])
    assert list.contains([statusCode: 200, success: true, urn: "urn:global_sku:$values:c".toString()])
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
* ******************** SHEET SERVICE *************************************************************
* */

Given(~"Cadc sheet (.*)") { name ->
    server.request(by(uri("/cadc/sheet/$name"))).response("sheet $name")
}

Given(~"Repo save service for publication (.*) locale (.*) sku (.*)") { String publication, String locale, String sku ->
    def publicationLC = publication.toLowerCase()
    def localeLC = locale.toLowerCase()
    def skuLC = sku.toLowerCase()
    server.post(by(uri("/repository/file/urn:global_sku:$publicationLC:$localeLC:$skuLC"))).response("")
}

When(~"I import sheet with publication (.*) locale (.*) sku (.*) correctly") { String publication, String locale, String sku ->
    def publicationLC = publication.toLowerCase()
    def localeLC = locale.toLowerCase()
    def skuLC = sku.toLowerCase()
    get("cadcsource/sheet/urn:global_sku:$publicationLC:$localeLC:$skuLC?url=http://localhost:12306/cadc/sheet/$sku")
}

Then(~"Sheet with publication (.*) locale (.*) sku (.*) should be imported successful") { String publication, String locale, String sku ->
    def publicationLC = publication.toLowerCase()
    def localeLC = locale.toLowerCase()
    def skuLC = sku.toLowerCase()

    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json?.deltaSheet.urnStr == "urn:global_sku:$publicationLC:$localeLC:$skuLC"
    assert json?.deltaSheet.url == "http://localhost:12306/cadc/sheet/$sku"
    assert json.result == ["success"]
}

When(~"I import sheet with invalid (.*) parameter") { paramName ->
    if (paramName == "urn") {
        get("cadcsource/sheet/a?url=http://sheet/a")
    } else if (paramName == "url") {
        get("cadcsource/sheet/urn:global_sku:score:en_gb:a?url=/sheet/a")
    }
}

