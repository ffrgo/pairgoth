package org.jeudego.pairgoth.server

import com.republicate.mailer.SmtpLoop
import org.jeudego.pairgoth.web.BaseWebappManager
import javax.servlet.*
import javax.servlet.annotation.WebListener

@WebListener
class WebappManager : BaseWebappManager("API Server","api") {

    /* ServletContextListener interface */
    override fun contextInitialized(sce: ServletContextEvent) {
        super.contextInitialized(sce)

        logger.info("pairgoth server ${properties["version"]} with profile ${properties["env"]}")

        // start smtp loop
        if (properties.containsKey("smtp.host")) {
            logger.info("Launching SMTP loop")
            registerService("smtp", SmtpLoop(properties))
            startService("smtp")
        }
    }

    companion object {
        val properties get() = BaseWebappManager.properties
        fun getMandatoryProperty(prop: String) = properties.getProperty(prop) ?: throw Error("missing property: $prop")
        val context get() = BaseWebappManager.context
    }
}
