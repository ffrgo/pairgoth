package org.jeudego.pairgoth.web

import com.republicate.mailer.SmtpLoop
import org.apache.commons.lang3.tuple.Pair
import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.IllegalAccessError
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import java.util.IllegalFormatCodePointException
import javax.net.ssl.*
import javax.servlet.*
import javax.servlet.annotation.WebListener
import javax.servlet.http.HttpSessionEvent
import javax.servlet.http.HttpSessionListener

abstract class BaseWebappManager(val webappName: String, loggerName: String) : ServletContextListener, ServletContextAttributeListener, HttpSessionListener {

    val logger = LoggerFactory.getLogger(loggerName)

    protected fun disableSSLCertificateChecks() {
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

    private val webServices: MutableMap<String?, Pair<Runnable, Thread?>> = TreeMap()

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

    /* ServletContextListener interface */
    override fun contextInitialized(sce: ServletContextEvent) {
        context = sce.servletContext
        logger.info("---------- Starting $webappName ----------")
        webappRoot = context.getRealPath("/")
        try {
            // load default properties
            properties.load(context.getResourceAsStream("/WEB-INF/pairgoth.default.properties"))
            // override with system properties after stripping off the 'pairgoth.' prefix
            System.getProperties().filter { (key, value) -> key is String && key.startsWith(PAIRGOTH_PROPERTIES_PREFIX)
            }.forEach { (key, value) ->
                properties[(key as String).removePrefix(PAIRGOTH_PROPERTIES_PREFIX)] = value
            }

            // set system user agent string to empty string
            System.setProperty("http.agent", "")

            // disable (for now ?) the SSL certificate checks, because many sites
            // fail to correctly implement SSL...
            disableSSLCertificateChecks()

            logger.info("webapp ${webappName} root filesystem path: ${context.getRealPath("")}")

        } catch (ioe: IOException) {
            logger.error("webapp initialization error", ioe)
        }
    }

    override fun contextDestroyed(sce: ServletContextEvent) {
        logger.info("---------- Stopping $webappName ----------")
        for (service in webServices.keys) stopService(service, true)
    }

    /* ServletContextAttributeListener interface */
    override fun attributeAdded(event: ServletContextAttributeEvent) {}
    override fun attributeRemoved(event: ServletContextAttributeEvent) {}
    override fun attributeReplaced(event: ServletContextAttributeEvent) {}

    /* HttpSessionListener interface */
    override fun sessionCreated(se: HttpSessionEvent) {}
    override fun sessionDestroyed(se: HttpSessionEvent) {}

    companion object {
        const val PAIRGOTH_PROPERTIES_PREFIX = "pairgoth."
        lateinit var webappRoot: String
        lateinit var context: ServletContext
        val properties = Properties()
    }
}
