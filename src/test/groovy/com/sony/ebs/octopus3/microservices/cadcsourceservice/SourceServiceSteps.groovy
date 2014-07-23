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

def cadcService = { HttpServer server, String publication, String locale, int numOfSheets, int numOfErrors ->
    def str = new JsonBuilder({
        startDate 's1'
        endDate 'e1'
        skus {
            "$locale"((1..numOfSheets).collect { "http://localhost:12306/cadc/sheet/p$it" })
        }
    }).toString()
    server.request(by(uri("/skus/changes/$locale"))).response(str)

    (numOfSheets - numOfErrors).times {
        def name = "p${it + 1}"
        server.request(by(uri("/cadcsource/sheet/urn:global_sku:$publication:$locale:$name".toLowerCase()))).response("sheet${it + 1}")
    }
}

Given(~"Cadc sheet (.*)") { name ->
    server.request(by(uri("/cadc/sheet/$name"))).response("sheet $name")
}

Given(~"Cadc returns (.*) sheets for publication (.*) locale (.*) successfully") { int numOfSheets, String publication, String locale ->
    cadcService(server, publication, locale, numOfSheets, 0)
}

/*
* ******************** SAVE SERVICE *************************************************************
* */

When(~"I save sheet (.*)") { sku ->
    post("cadcsource/save/urn:global_sku:score:en_gb:$sku")
}

Then(~"sheet (.*) should be saved") { sku ->
    def json = parseJson(response)
    assert json?.urn == "urn:global_sku:score:en_gb:$sku"
    assert json?.message == "sheet saved"
}

/*
* ******************** DELTA IMPORT SERVICE ***********************************************************
* */

When(~"I request delta of publication (.*) locale (.*) since (.*)") { publication, locale, since ->
    get("cadcsource/delta/publication/$publication/locale/$locale?since=$since&cadcUrl=http://localhost:12306/skus")
}

Then(~"Sheets should be imported with publication (.*) locale (.*) since (.*)") { publication, locale, since ->
    assert response.statusCode == 202
    def json = parseJson(response)
    assert json.status == 202
    assert json.message == "delta started"
    assert json.delta.publication == publication
    assert json.delta.locale == locale
    assert json.delta.since == since
    assert json.delta.cadcUrl == "http://localhost:12306/skus"
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
* ******************** SHEET IMPORT SERVICE *************************************************************
* */
When(~"I import sheet (.*) correctly") { sku ->
    get("cadcsource/sheet/urn:global_sku:score:en_gb:$sku?url=http://localhost:12306/cadc/sheet/$sku")
}

Then(~"Sheet import of (.*) should be successful") { sku ->
    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json?.message == "deltaSheet finished"
    assert json?.deltaSheet.urnStr == "urn:global_sku:score:en_gb:$sku"
    assert json?.deltaSheet.url == "http://localhost:12306/cadc/sheet/$sku"
}

When(~"I import sheet with invalid (.*) parameter") { paramName ->
    if (paramName == "urn") {
        get("cadcsource/sheet/a?url=http://sheet/a")
    } else if (paramName == "url") {
        get("cadcsource/sheet/urn:global_sku:score:en_gb:a?url=/sheet/a")
    }
}

