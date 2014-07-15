import com.sony.ebs.octopus3.microservices.cadcsourceservice.SpringConfig
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.DeltaFlowHandler
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.ErrorHandler
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.SaveFlowHandler
import com.sony.ebs.octopus3.microservices.cadcsourceservice.handlers.SheetFlowHandler
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

            RxRatpack.initialize()
        }
    }

    handlers {
        post("save/repo/:urn", saveFlowHandler)

        get("import/sheet/:urn", sheetFlowHandler)

        get("import/delta/publication/:publication/locale/:locale", deltaFlowHandler)
    }
}
