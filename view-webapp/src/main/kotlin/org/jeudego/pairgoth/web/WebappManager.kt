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
class WebappManager : ServletContextListener, ServletContextAttributeListener, HttpSessionListener {
    private fun disableSSLCertificateChecks() {
        // see http://www.nakov.com/blog/2009/07/16/disable-certificate-validation-in-java-ssl-connections/
        try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate>? {
                    return null
                }

                @Suppress("TrustAllX509TrustManager")
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                @Suppress("TrustAllX509TrustManager")
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            }
            )

            // Install the all-trusting trust manager
            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)

            // Create all-trusting host name verifier
            val allHostsValid = HostnameVerifier { hostname, session -> true }

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
        } catch (e: Exception) {
            logger.error("could not disable SSL certificate checks", e)
        }
    }

    /* ServletContextListener interface */
    override fun contextInitialized(sce: ServletContextEvent) {
        context = sce.servletContext
        logger.info("---------- Starting $WEBAPP_NAME ----------")
        logger.info("info level is active")
        logger.debug("debug level is active")
        logger.trace("trace level is active")
        webappRoot = context.getRealPath("/")
        try {
            // load default properties
            properties.load(context.getResourceAsStream("/WEB-INF/pairgoth.default.properties"))
            // override with system properties after stripping off the 'pairgoth.' prefix
            System.getProperties().filter { (key, value) -> key is String && key.startsWith(PAIRGOTH_PROPERTIES_PREFIX)
            }.forEach { (key, value) ->
                properties[(key as String).removePrefix(PAIRGOTH_PROPERTIES_PREFIX)] = value
            }

            logger.info("pairgoth server ${properties["version"]} with profile ${properties["env"]}")

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

            // set system user agent string to empty string
            System.setProperty("http.agent", "")

            // disable (for now ?) the SSL certificate checks, because many sites
            // fail to correctly implement SSL...
            disableSSLCertificateChecks()

            registerService("ratings", RatingsManager)
            startService("ratings")

        } catch (ioe: IOException) {
            logger.error("webapp initialization error", ioe)
        }
    }

    override fun contextDestroyed(sce: ServletContextEvent) {
        logger.info("---------- Stopping $WEBAPP_NAME ----------")

        Translator.notifyExiting()

        val context = sce.servletContext
        for (service in webServices.keys) stopService(service, true)
        // ??? DriverManager.deregisterDriver(com.mysql.cj.jdbc.Driver ...);

        logger.info("---------- Stopped $WEBAPP_NAME ----------")
    }

    /* ServletContextAttributeListener interface */
    override fun attributeAdded(event: ServletContextAttributeEvent) {}
    override fun attributeRemoved(event: ServletContextAttributeEvent) {}
    override fun attributeReplaced(event: ServletContextAttributeEvent) {}

    /* HttpSessionListener interface */
    override fun sessionCreated(se: HttpSessionEvent) {}
    override fun sessionDestroyed(se: HttpSessionEvent) {}

    companion object {
        const val WEBAPP_NAME = "Pairgoth Web Client"
        const val PAIRGOTH_PROPERTIES_PREFIX = "pairgoth."
        lateinit var webappRoot: String
        lateinit var context: ServletContext
        private val webServices: MutableMap<String?, Pair<Runnable, Thread?>> = TreeMap()
        var logger = LoggerFactory.getLogger(WebappManager::class.java)
        val properties = Properties()
        fun getProperty(prop: String): String? {
            return properties.getProperty(prop)
        }
        fun getMandatoryProperty(prop: String): String {
            return getProperty(prop) ?: throw Error("missing property: ${prop}")
        }

        val webappURL by lazy { getProperty("webapp.external.url") }

        private val services = mutableMapOf<String, Pair<Runnable, Thread>>()

        @JvmOverloads
        fun registerService(name: String?, task: Runnable, initialStatus: Boolean? = null) {
            if (webServices.containsKey(name)) {
                logger.warn("service {} already registered")
                return
            }
            logger.debug("registered service {}", name)
            webServices[name] =
                Pair.of(task, null)
        }

        fun startService(name: String?) {
            val service = webServices[name]!!
            if (service.right != null && service.right!!.isAlive) {
                logger.warn("service {} is already running", name)
                return
            }
            logger.debug("starting service {}", name)
            val thread = Thread(service.left, name)
            thread.start()
            webServices[name] =
                Pair.of(
                    service.left,
                    thread
                )
        }

        @JvmOverloads
        fun stopService(name: String?, webappClosing: Boolean = false) {
            val service = webServices[name]!!
            val thread = service.right
            if (thread == null || !thread.isAlive) {
                logger.warn("service {} is already stopped", name)
                return
            }
            logger.debug("stopping service {}", name)
            thread.interrupt()
            try {
                thread.join()
            } catch (ie: InterruptedException) {
            }
            if (!webappClosing) {
                webServices[name] = Pair.of(service.left, null)
            }
        }
    }
}
