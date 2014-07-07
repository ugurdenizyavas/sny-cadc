
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

Then(~"the response should be (.*)") { String expected ->
    assert expected == response.body.asString()
}

Given(~"Cadc sheet (.*)") { name ->
    server.request(by(uri("/cadc/sheet/$name"))).response("sheet $name")
}

When(~"I import sheet (.*)") { product ->
    get("import/sheet?product=$product&url=http://localhost:12306/cadc/sheet/$product")
}

Then(~"sheet (.*) should be imported") { product ->
    def json = parseJson(response)
    assert json?.product == product
    assert json?.url == "http://localhost:12306/cadc/sheet/$product"
    assert json?.message == "sheet import started"
}

When(~"I save sheet (.*)") { product ->
    post("save/repo?product=$product")
}

Then(~"sheet (.*) should be saved") { product ->
    def json = parseJson(response)
    assert json?.product == product
    assert json?.message == "product saved"
}

Given(~"Cadc returns (.*) products for locale (.*)") { int count, String locale ->
    def str = new JsonBuilder({
        startDate 's1'
        endDate 'e1'
        skus {
            "$locale"((1..count).collect { "http://localhost:12306/cadc/sheet/p$it" })
        }
    }).toString()
    server.request(by(uri("/skus/changes/$locale"))).response(str)

    def callSheet = { HttpServer s1, int c1 ->
        c1.times {
            def name = "p${it + 1}"
            s1.request(by(uri("/cadc/sheet/$name"))).response("sheet${it + 1}")
        }
    }
    callSheet(server, count)
}

When(~"I request delta of publication (.*) and locale (.*) since (.*)") { publication, locale, since ->
    get("import/delta/publication/$publication/locale/$locale?since=$since&cadcUrl=http://localhost:12306/skus")
}

Then(~"(.*) products should be imported with publication (.*) and locale (.*) since (.*)") { int count, publication, locale, since ->
    def json = parseJson(response)
    assert json.publication == publication
    assert json.locale == locale
    assert json.since == since
    assert json.cadcUrl == "http://localhost:12306/skus"
    assert json.message == "delta import started"
}

