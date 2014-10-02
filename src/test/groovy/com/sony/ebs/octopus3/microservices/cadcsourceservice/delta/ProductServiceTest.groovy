package com.sony.ebs.octopus3.microservices.cadcsourceservice.delta

import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.CadcProduct
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
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
        mockLocalHttpClient = new StubFor(NingHttpClient)
        mockCadcHttpClient = new StubFor(NingHttpClient)
        product = new CadcProduct(type: DeltaType.global_sku, publication: "SCORE", locale: "en_GB", url: "http://cadc/p", processId: "123")
    }

    def runFlow() {
        productService.cadcHttpClient = mockCadcHttpClient.proxyInstance()
        productService.localHttpClient = mockLocalHttpClient.proxyInstance()

        def result = new BlockingVariable(5)
        boolean valueSet = false
        execController.start {
            productService.process(product).subscribe({
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
        """
    }

    @Test
    void "success"() {
        def sku = "p+p/p.ceh"
        def productServiceResponse = createProductServiceResponse(sku)
        mockCadcHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: productServiceResponse))
            }
        }
        mockLocalHttpClient.demand.with {
            doPost(1) { url, data ->
                assert url == "http://repo/urn:global_sku:score:en_gb:p_2bp_2fp.ceh?processId=123"
                assert data == productServiceResponse?.getBytes("UTF-8")
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runFlow() == [urn: "urn:global_sku:score:en_gb:p_2bp_2fp.ceh", repoUrl: "http://repo/urn:global_sku:score:en_gb:p_2bp_2fp.ceh"]
    }

    @Test
    void "delta item not found"() {
        mockCadcHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
        }
        assert runFlow() == "outOfFlow"
        assert product.errors == ["HTTP 404 error getting sheet from cadc"]
    }

    @Test
    void "delta item could not be saved"() {
        def productServiceResponse = createProductServiceResponse("p")
        mockCadcHttpClient.demand.with {
            doGet(1) {
                assert it == "http://cadc/p"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: productServiceResponse))
            }
        }
        mockLocalHttpClient.demand.with {
            doPost(1) { url, data ->
                assert url == "http://repo/urn:global_sku:score:en_gb:p?processId=123"
                assert data == productServiceResponse?.getBytes("UTF-8")
                rx.Observable.from(new MockNingResponse(_statusCode: 500))
            }
        }
        assert runFlow() == "outOfFlow"
        assert product.errors == ["HTTP 500 error saving sheet to repo"]
    }

}
