import com.sony.ebs.octopus3.commons.ratpack.handlers.ErrorHandler
import com.sony.ebs.octopus3.commons.ratpack.handlers.HealthCheckHandler
import com.sony.ebs.octopus3.commons.ratpack.monitoring.MonitoringService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.SpringConfig
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import ratpack.error.ClientErrorHandler
import ratpack.error.ServerErrorHandler
import ratpack.jackson.JacksonModule
import ratpack.rx.RxRatpack

import static ratpack.groovy.Groovy.ratpack

Logger log = LoggerFactory.getLogger("ratpack");

ratpack {

    SheetFlowHandler sheetFlowHandler
    DeltaFlowHandler deltaFlowHandler
    SaveFlowHandler saveFlowHandler
    HealthCheckHandler healthCheckHandler

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
            healthCheckHandler = new HealthCheckHandler(monitoringService: new MonitoringService())

            RxRatpack.initialize()
        }
    }

    handlers {
        get("healthcheck", healthCheckHandler)
        get("cadcsource/sheet/:urn", sheetFlowHandler)
        get("cadcsource/delta/publication/:publication/locale/:locale", deltaFlowHandler)
        post("cadcsource/save/:urn", saveFlowHandler)
    }
}
