package org.jeudego.pairgoth.web

import com.republicate.kson.Json
import org.apache.commons.lang3.tuple.Pair
import org.jeudego.pairgoth.oauth.OauthHelperFactory
import org.jeudego.pairgoth.ratings.RatingsManager
import org.jeudego.pairgoth.util.ApiClient.JsonApiClient
import org.jeudego.pairgoth.util.ApiClient.header
import org.jeudego.pairgoth.util.Translator
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Paths
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*
import javax.servlet.*
import javax.servlet.annotation.WebListener
import javax.servlet.http.HttpSessionEvent
import javax.servlet.http.HttpSessionListener
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.exists

@WebListener
class WebappManager : BaseWebappManager("View Webapp", "view") {

    /* ServletContextListener interface */
    override fun contextInitialized(sce: ServletContextEvent) {
        super.contextInitialized(sce)

        // create a symlink for external resources access
        properties.getProperty("cwd")?.let { cwd ->
            val target = Paths.get(cwd).resolve("resources")
            if (target.exists()) {
                val source = Paths.get(context.getRealPath("/")).resolve("resources")
                if (!source.exists()) {
                    source.createSymbolicLinkPointingTo(target)
                }
            }
        }

        // publish some properties to the webapp context; for easy access from the template
        context.setAttribute("env", properties.getProperty("env") ?: "dev")
        context.setAttribute("version", properties.getProperty("version") ?: "?")
        val auth = properties.getProperty("auth") ?: "none"
        context.setAttribute("auth", auth)
        when (auth) {
            "none", "sesame" -> {}
            // fail fast: the sessionless redirect and the ticket decryption need them on every request
            "external" -> {
                getMandatoryProperty("auth.external.login_url")
                getMandatoryProperty("auth.external.secret")
            }
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

        // Probe the webhook at startup. A missing secret is a hard misconfiguration (fatal). An
        // unreachable or unhealthy webhook is only warned about, not fatal: a docker peer may simply
        // not be up yet, and runtime pushes/pulls already degrade gracefully — so it must never take
        // pairgoth's own startup down. One-line warning, no stacktrace.
        val webhookUrl = properties.getProperty("webhook.url")?.takeIf { it.isNotBlank() }
        context.setAttribute("webhookConfigured", webhookUrl != null)
        webhookUrl?.let { url ->
            val secret = properties.getProperty("webhook.secret")
                ?: throw Error("webhook.url is set but webhook.secret is missing")
            val healthUrl = "${url.removeSuffix("/")}/health"
            try {
                val resp = JsonApiClient.get(healthUrl, header("X-Pairgoth-Secret", secret)) as Json.Object
                if (resp.getBoolean("status") == true)
                    logger.info("webhook at $url healthy: ${resp.getString("name") ?: "(unnamed)"}")
                else
                    logger.warn("webhook at $url reachable but unhealthy: ${resp.getString("message") ?: "(no message)"}")
            } catch (e: Exception) {
                logger.warn("webhook at $url not reachable at startup (${e.message}); continuing — pushes/pulls will retry at runtime")
            }
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
