package com.sony.ebs.octopus3.microservices.cadcsourceservice.service

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcProduct
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

@Slf4j
class ProductServiceTest {

    ProductService productService
    StubFor mockLocalHttpClient, mockCadcHttpClient
    CadcProduct product
    ProductResult productResult

    static ExecController execController

    @BeforeClass
    static void beforeClass() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
    }

    @AfterClass
    static void afterClass() {
        if (execController) execController.close()
    }

    @Before
    void before() {
        productService = new ProductService(execControl: execController.control,
                repositoryFileServiceUrl: "http://repo/:urn",
                repositoryCopyServiceUrl: "http://repo/copy/source/:source/destination/:destination"
        )
        mockLocalHttpClient = new StubFor(Oct3HttpClient)
        mockCadcHttpClient = new StubFor(Oct3HttpClient)
        product = new CadcProduct(type: RepoValue.global_sku, publication: "SCORE", locale: "en_GB", url: "http://cadc/p", processId: "123")
        productResult = new ProductResult()
    }

    def runFlow() {
        productService.cadcHttpClient = mockCadcHttpClient.proxyInstance()
        productService.localHttpClient = mockLocalHttpClient.proxyInstance()

        def result = new BlockingVariable(5)
        boolean valueSet = false
        execController.start {
            productService.process(product, productResult).subscribe({
                valueSet = true
                result.set(it)
            }, {
                log.error "error in flow", it
                result.set("error")
            }, {
                if (!valueSet) result.set("outOfFlow")
            })
        }
        result.get()
    }

    def createProductServiceResponse(sku) {
        """
        {
            "skuName" : "$sku"
        }
        """.bytes
    }

    @Test
    void "success"() {
        def sku = "p+p/p.ceh"
        def productServiceResponse = createProductServiceResponse(sku)
        mockCadcHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: productServiceResponse))
            }
        }
        mockLocalHttpClient.demand.with {
            doPost(1) { url, data ->
                assert url == "http://repo/urn:global_sku:score:en_gb:p_2bp_2fp.ceh?processId=123"
                assert data == productServiceResponse
                rx.Observable.from(new Oct3HttpResponse(statusCode: 200))
            }
        }

        runFlow() == "success"

        productResult.with {
            assert !inputUrn
            assert inputUrl == "http://cadc/p"
            assert outputUrn == "urn:global_sku:score:en_gb:p_2bp_2fp.ceh"
            assert outputUrl == "http://repo/urn:global_sku:score:en_gb:p_2bp_2fp.ceh"
        }
    }

    @Test
    void "delta item not found"() {
        mockCadcHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new Oct3HttpResponse(statusCode: 404))
            }
        }
        assert runFlow() == "outOfFlow"
        assert productResult.errors == ["HTTP 404 error getting product from cadc"]
    }

    @Test
    void "delta item could not be saved"() {
        def productServiceResponse = createProductServiceResponse("p")
        mockCadcHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: productServiceResponse))
            }
        }
        mockLocalHttpClient.demand.with {
            doPost(1) { url, data ->
                assert url == "http://repo/urn:global_sku:score:en_gb:p?processId=123"
                assert data == productServiceResponse
                rx.Observable.from(new Oct3HttpResponse(statusCode: 500))
            }
        }
        assert runFlow() == "outOfFlow"
        assert productResult.errors == ["HTTP 500 error saving product to repo"]
    }

}
