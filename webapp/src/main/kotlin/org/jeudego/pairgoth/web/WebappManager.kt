package org.jeudego.pairgoth.web

import com.republicate.mailer.SmtpLoop
import org.apache.commons.lang3.tuple.Pair
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
        // overcome a Jetty's bug (v9.4.10.v20180503) whereas if a @WebListener is also listed in the descriptor
        // it will be instanciated twice...
        context = sce.servletContext
        logger.info("---------- Starting Web Application ----------")
        context.setAttribute("manager", this)
        webappRoot = context.getRealPath("/")
        try {
            properties.load(context.getResourceAsStream("/WEB-INF/webapp.properties"))
            val submaps: MutableMap<String, MutableMap<String, String>> = HashMap()
            for (prop in properties.stringPropertyNames()) {
                val value = properties.getProperty(prop)

                // filter out missing values and passwords
                if (value.startsWith("\${") || prop.contains("password")) continue
                context.setAttribute(prop, value)

                // also support one level of submaps (TODO - more)
                val dot = prop.indexOf('.')
                if (dot != -1) {
                    val topKey = prop.substring(0, dot)
                    val subKey = prop.substring(dot + 1)
                    if ("password" == subKey) continue
                    var submap = submaps[topKey]
                    if (submap == null) {
                        submap = HashMap()
                        submaps[topKey] = submap
                    }
                    submap[subKey] = value
                }
            }
            for ((key, value) in submaps) {
                context.setAttribute(key, value)
            }
            logger.info("Using profile {}", properties.getProperty("webapp.env"))

            // set system user agent string to empty string
            System.setProperty("http.agent", "")

            // disable (for now ?) the SSL certificate checks, because many sites
            // fail to correctly implement SSL...
            disableSSLCertificateChecks()

            // start smtp loop
            if (properties.containsKey("smtp.host")) {
                registerService("smtp", SmtpLoop(properties))
                startService("smtp")
            }

        } catch (ioe: IOException) {
            logger.error("webapp initialization error", ioe)
        }
    }

    override fun contextDestroyed(sce: ServletContextEvent) {
        logger.info("---------- Stopping Web Application ----------")

        // overcome a Jetty's bug (v9.4.10.v20180503) whereas if a @WebListener is also listed in the descriptor
        // it will be instanciated twice...
        if (context == null) return
        val context = sce.servletContext
        for (service in webServices.keys) stopService(service, true)
        // ??? DriverManager.deregisterDriver(com.mysql.cj.jdbc.Driver ...);
    }

    /* ServletContextAttributeListener interface */
    override fun attributeAdded(event: ServletContextAttributeEvent) {}
    override fun attributeRemoved(event: ServletContextAttributeEvent) {}
    override fun attributeReplaced(event: ServletContextAttributeEvent) {}

    /* HttpSessionListener interface */
    override fun sessionCreated(se: HttpSessionEvent) {}
    override fun sessionDestroyed(se: HttpSessionEvent) {}

    companion object {
        lateinit var webappRoot: String
        lateinit var context: ServletContext
        private val webServices: MutableMap<String?, Pair<Runnable, Thread?>> = TreeMap()
        var logger = LoggerFactory.getLogger(WebappManager::class.java)
        val properties = Properties()
        fun getProperty(prop: String?): String {
            return properties.getProperty(prop)
        }

        val webappURL by lazy { getProperty("webapp.url") }

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
