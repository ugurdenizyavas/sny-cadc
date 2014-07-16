import com.sony.ebs.octopus3.microservices.cadcsourceservice.SpringConfig
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.DeltaFlowHandler
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.ErrorHandler
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.SaveFlowHandler
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.SheetFlowHandler
import com.sony.ebs.octopus3.microservices.cadcsourceservice.services.MonitoringService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import ratpack.error.ClientErrorHandler
import ratpack.error.ServerErrorHandler
import ratpack.jackson.JacksonModule
import ratpack.rx.RxRatpack

import static ratpack.groovy.Groovy.ratpack
import static ratpack.jackson.Jackson.json

Logger log = LoggerFactory.getLogger("ratpack");

ratpack {

    SheetFlowHandler sheetFlowHandler
    DeltaFlowHandler deltaFlowHandler
    SaveFlowHandler saveFlowHandler
    MonitoringService monitoringService

    bindings {
        add new JacksonModule()
        bind ClientErrorHandler, new ErrorHandler()
        bind ServerErrorHandler, new ErrorHandler()
        init {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class)
            ctx.beanFactory.registerSingleton "launchConfig", launchConfig
            ctx.beanFactory.registerSingleton "execControl", launchConfig.execController.control

            deltaFlowHandler = ctx.getBean(DeltaFlowHandler.class)
            sheetFlowHandler = ctx.getBean(SheetFlowHandler.class)
            saveFlowHandler = ctx.getBean(SaveFlowHandler.class)
            monitoringService = ctx.getBean MonitoringService.class

            RxRatpack.initialize()
        }
    }

    handlers {

        get("amazon/healthcheck") {
            def params = [:]

            params.enabled = request.queryParams.enabled

            if (params.enabled) {
                def action = params.enabled.toBoolean()
                if (action) {
                    monitoringService.up()
                    response.status(200)
                    render json(status: 200, message: "App is up for the eyes of LB!")
                } else {
                    monitoringService.down()
                    response.status(200)
                    render json(status: 200, message: "App is down for the eyes of LB!")
                }
            } else {
                if (monitoringService.checkStatus()) {
                    response.status(200)
                    render json(status: 200, message: "Ticking!")
                } else {
                    response.status(404)
                    render json(status: 404, message: "App is down!")
                }
            }
        }
        post("save/repo/:urn", saveFlowHandler)

        get("import/sheet/:urn", sheetFlowHandler)

        get("import/delta/publication/:publication/locale/:locale", deltaFlowHandler)
    }
}
