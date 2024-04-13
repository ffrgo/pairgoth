package org.jeudego.pairgoth.web

import org.apache.commons.lang3.tuple.Pair
import org.jeudego.pairgoth.oauth.OauthHelperFactory
import org.jeudego.pairgoth.ratings.RatingsManager
import org.jeudego.pairgoth.util.Translator
import org.slf4j.LoggerFactory
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*
import javax.servlet.*
import javax.servlet.annotation.WebListener
import javax.servlet.http.HttpSessionEvent
import javax.servlet.http.HttpSessionListener

@WebListener
class WebappManager : BaseWebappManager("View Webapp", "view") {

    /* ServletContextListener interface */
    override fun contextInitialized(sce: ServletContextEvent) {
        super.contextInitialized(sce)

        // publish some properties to the webapp context; for easy access from the template
        context.setAttribute("env", properties.getProperty("env") ?: "dev")
        context.setAttribute("version", properties.getProperty("version") ?: "?")
        val auth = properties.getProperty("auth") ?: "none"
        context.setAttribute("auth", auth)
        when (auth) {
            "none", "sesame" -> {}
            "oauth" -> {
                properties.getProperty("oauth.providers")?.let {
                    val providers = it.split(Regex("\\s*,\\s*"))
                    context.setAttribute("oauthProviders", providers)
                    providers.forEach { provider ->
                        context.setAttribute("${provider}Provider", OauthHelperFactory.getHelper(provider))
                    }
                }
            }
            else -> throw Error("Unhandled auth: $auth")
        }

        logger.info("")
        logger.info("*********************************************")
        logger.info("*                                           *")
        logger.info("*  Pairgoth web server is ready.            *");
        logger.info("*  Open a browser on http://localhost:8080  *")
        logger.info("*  Press control-c to stop the server.      *")
        logger.info("*                                           *")
        logger.info("*********************************************")
        logger.info("")

        registerService("ratings", RatingsManager)
        startService("ratings")
    }

    override fun contextDestroyed(sce: ServletContextEvent) {
        super.contextDestroyed(sce)
        Translator.notifyExiting()
    }

    companion object {
        val properties get() = BaseWebappManager.properties
        val context get() = BaseWebappManager.context
        fun getMandatoryProperty(prop: String) = properties.getProperty(prop) ?: throw Error("missing property: $prop")
    }
}
