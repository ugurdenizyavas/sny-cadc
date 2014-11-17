import com.sony.ebs.octopus3.commons.ratpack.handlers.ErrorHandler
import com.sony.ebs.octopus3.commons.ratpack.handlers.HealthCheckHandler
import com.sony.ebs.octopus3.commons.ratpack.monitoring.MonitoringService
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.DeltaHandler
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.MultiDeltaHandler
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.MultiProductHandler
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.ProductHandler
import com.sony.ebs.octopus3.microservices.cadcsourceservice.spring.config.SpringConfig
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import ratpack.error.ClientErrorHandler
import ratpack.error.ServerErrorHandler
import ratpack.jackson.JacksonModule
import ratpack.rx.RxRatpack

import static ratpack.groovy.Groovy.ratpack

ratpack {

    ProductHandler productHandler
    MultiProductHandler multiProductHandler
    DeltaHandler deltaHandler
    MultiDeltaHandler multiDeltaHandler
    HealthCheckHandler healthCheckHandler

    bindings {
        add new JacksonModule()
        bind ClientErrorHandler, new ErrorHandler()
        bind ServerErrorHandler, new ErrorHandler()
        init {
            RxRatpack.initialize()

            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class)
            ctx.beanFactory.registerSingleton "launchConfig", launchConfig
            ctx.beanFactory.registerSingleton "execControl", launchConfig.execController.control

            deltaHandler = ctx.getBean(DeltaHandler.class)
            productHandler = ctx.getBean(ProductHandler.class)
            multiProductHandler = ctx.getBean(MultiProductHandler.class)
            multiDeltaHandler = ctx.getBean(MultiDeltaHandler.class)
            healthCheckHandler = new HealthCheckHandler(monitoringService: new MonitoringService())
        }
    }

    handlers {
        get("healthcheck", healthCheckHandler)
        get("cadcsource/sheet/publication/:publication/locale/:locale", productHandler)
        get("cadcsource/sheet/publication/:publication/locales/:locale", multiProductHandler)
        get("cadcsource/delta/publication/:publication/locale/:locale", deltaHandler)
        get("cadcsource/delta/publication/:publication/locales/:locales", multiDeltaHandler)
    }
}
