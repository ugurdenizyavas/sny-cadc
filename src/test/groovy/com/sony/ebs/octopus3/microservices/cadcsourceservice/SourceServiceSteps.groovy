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

System.setProperty 'ENV', 'cucumber'
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
    assert json?.message == message
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

def cadcService = { HttpServer server, String locale, int numOfSheets, int numOfErrors ->
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
        server.request(by(uri("/cadc/sheet/$name"))).response("sheet${it + 1}")
    }
}

Given(~"Cadc sheet (.*)") { name ->
    server.request(by(uri("/cadc/sheet/$name"))).response("sheet $name")
}

Given(~"Cadc returns (.*) sheets for locale (.*) successfully") { int numOfSheets, String locale ->
    cadcService(server, locale, numOfSheets, 0)
}

/*
* ******************** SAVE SERVICE *************************************************************
* */

When(~"I save sheet (.*)") { sku ->
    post("save/repo/urn:global_sku:score:en_gb:$sku")
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
    get("import/delta/publication/$publication/locale/$locale?since=$since&cadcUrl=http://localhost:12306/skus")
}

Then(~"Sheets should be imported with publication (.*) locale (.*) since (.*)") { publication, locale, since ->
    assert response.statusCode == 202
    def json = parseJson(response)
    assert json.status == 202
    assert json.message == "delta import started"
    assert json.publication == publication
    assert json.locale == locale
    assert json.since == since
    assert json.cadcUrl == "http://localhost:12306/skus"
}

When(~"I import delta with invalid (.*) parameter") { paramName ->
    if (paramName == "publication") {
        get("import/delta/publication/,,/locale/en_GB")
    } else if (paramName == "locale") {
        get("import/delta/publication/SCORE/locale/tr_")
    } else if (paramName == "cadcUrl") {
        get("import/delta/publication/SCORE/locale/en_GB?cadcUrl=/host/skus")
    } else if (paramName == "since") {
        get("import/delta/publication/SCORE/locale/en_GB?cadcUrl=http://host/skus&since=s1")
    }
}

Then(~"Import should give (.*) parameter error") { paramName ->
    validateError(response, "$paramName parameter is invalid")
}

/*
* ******************** SHEET IMPORT SERVICE *************************************************************
* */
When(~"I import sheet (.*) correctly") { sku ->
    get("import/sheet/urn:global_sku:score:en_gb:$sku?url=http://localhost:12306/cadc/sheet/$sku")
}

Then(~"Sheet import of (.*) should be successful") { sku ->
    assert response.statusCode == 202
    def json = parseJson(response)
    assert json.status == 202
    assert json?.message == "sheet import started"
    assert json?.urn == "urn:global_sku:score:en_gb:$sku"
    assert json?.url == "http://localhost:12306/cadc/sheet/$sku"
}

When(~"I import sheet with invalid (.*) parameter") { paramName ->
    if (paramName == "urn") {
        get("import/sheet/a?url=http://sheet/a")
    } else if (paramName == "url") {
        get("import/sheet/urn:global_sku:score:en_gb:a?url=/sheet/a")
    }
}

