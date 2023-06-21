package org.jeudego.pairgoth.application

import org.apache.commons.io.FileUtils
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.SecureRequestCustomizer
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.SslConnectionFactory
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.webapp.WebAppContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.net.JarURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import java.util.jar.JarFile
import java.util.regex.Pattern


fun main(vararg args: String) {
    try {
        // register a shutdown hook for any global necessary cleanup
        Runtime.getRuntime().addShutdownHook(object: Thread() {
            override fun run() { cleanup() }
        })

        // read default properties and provided ones, if any
        readProperties()
        // extract war files from main archive
        extractWarFiles()
        // launch web server
        launchServer()
    } catch (t: Throwable) {
        t.printStackTrace(System.err)
    }
}

private val tmp = System.getProperty("java.io.tmpdir")
private val webapps = Path.of("${tmp}/pairgoth/webapps")
private val version = "1.0-SNAPSHOT" // TODO CB

private fun cleanup() {
    FileUtils.deleteDirectory(webapps.toFile())
}

private fun readProperties() {
    val defaultProps = getResource("/server.default.properties") ?: throw Error("missing default server properties")
    defaultProps.openStream().use {
        serverProps.load(InputStreamReader(it, StandardCharsets.UTF_8))
    }
    val properties = File("./pairgoth.properties")
    if (properties.exists()) {
        serverProps.load(FileReader(properties))
        serverProps.entries.forEach { entry ->
            val property = entry.key as String
            val value = entry.value as String
            if (!property.startsWith("webapp.ssl.")) {
                // do not propagate ssl properties further
                System.setProperty("pairgoth.$property", value)
            }
        }
    }
    // we want colorized output on linux
    if (System.getProperty("os.name") == "Linux")
    {
        System.setProperty("org.eclipse.jetty.logging.appender.MESSAGE_ESCAPE", "false");
    }
}

private fun extractWarFiles() {
    // val jarLocation = object{}::class.java.protectionDomain.codeSource.location
    // prepare output directory
    FileUtils.deleteDirectory(webapps.toFile())
    Files.createDirectories(webapps)

    // extract wars
    val webappsFolderURL = getResource("/META-INF/webapps") ?: throw Error("webapps not found")
    val jarConnection = webappsFolderURL.openConnection() as JarURLConnection
    val jarFile: JarFile = jarConnection.jarFile
    jarFile.entries().toList().filter { entry ->
        entry.name.startsWith(jarConnection.entryName)
    }.forEach { entry ->
        if (!entry.isDirectory) {
            jarFile.getInputStream(entry).use { entryInputStream ->
                Files.copy(entryInputStream, webapps.resolve(entry.name.removePrefix("META-INF/webapps/")))
            }
        }
    }
}
private val mainClass = object{}::class.java.enclosingClass
private val jarPath = mainClass.protectionDomain.codeSource.location.path.let { URLDecoder.decode(it, "UTF-8") }
private val serverProps = Properties()
private fun getResource(resource: String) = mainClass.getResource(resource)

private fun getResourceProperty(key: String) = serverProps.getProperty(key)?.let { property ->
    val url = property.replace("\$jar", jarPath)
    if (!Resource.newResource(url).exists()) throw Error("resource not found: $url")
    URL(url)
} ?: throw Error("missing property: $key")

private fun launchServer() {
    // create webapps contexts
    val webAppContexts = mutableListOf<WebAppContext>()
    val mode = serverProps["mode"] ?: throw Error("missing property: mode")
    if (mode == "server" || mode == "standalone") webAppContexts.add(createContext("api", "/api"))
    if (mode == "client" || mode == "standalone") webAppContexts.add(createContext("view", "/"))

    // special handling for logger properties
    serverProps.stringPropertyNames().asSequence().filter { propName ->
        propName.startsWith("logger.")
    }.forEach { propName ->
        val key = "webapp-slf4j-logger.${propName.substring(7)}"
        val value = serverProps.getProperty(propName)
        webAppContexts.forEach { context ->
            context.setInitParameter(key, value)
        }
    }

    val webappUrl = serverProps.getProperty("webapp.url")?.let { URL(it) } ?: throw Error("missing property webapp.url")
    val secure = webappUrl.protocol == "https"

    // create server
    val server =
        if (secure) Server()
        else Server(webappUrl.port)

    server.apply {
        // register webapps
        handler = ContextHandlerCollection(*webAppContexts.toTypedArray())
        if (secure) {
            val connector = buildSecureConnector(server, webappUrl.port)
            addConnector(connector)
        }
        // launch server
        start()
        join()
    }
}

private fun createContext(webapp: String, contextPath: String) = WebAppContext().also { context ->
    context.war = "$tmp/pairgoth/webapps/$webapp-webapp-$version.war"
    context.contextPath = contextPath
}

private fun buildSecureConnector(server: Server, port: Int): ServerConnector {
    // set up http/2
    val httpConfig = HttpConfiguration().apply {
        addCustomizer(SecureRequestCustomizer())
    }
    val http11 = HttpConnectionFactory(httpConfig)
    val h2 = HTTP2ServerConnectionFactory(httpConfig)
    val alpn = ALPNServerConnectionFactory().apply {
        defaultProtocol = http11.protocol
    }
    val cert = getResourceProperty("webapp.ssl.cert").readBytes()
    val key = getResourceProperty("webapp.ssl.key").readText().let {
        val encodedKey =
            Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*").matcher(it).replaceFirst("$1")
        Base64.getDecoder().decode(encodedKey.replace("\n", ""))
    }
    val pass = serverProps.getProperty("webapp.ssl.pass") ?: "foobar"

    val keyFactory = KeyFactory.getInstance("RSA")
    val keySpec = PKCS8EncodedKeySpec(key)
    val privKey = keyFactory.generatePrivate(keySpec)

    val certificateFactory = CertificateFactory.getInstance("X.509")
    val store = KeyStore.getInstance("JKS").apply {
        load(null)
        setCertificateEntry(
            "certificate",
            certificateFactory.generateCertificate(ByteArrayInputStream(cert)) as X509Certificate
        )
        setKeyEntry(
            "key",
            privKey,
            pass.toCharArray(),
            arrayOf(certificateFactory.generateCertificate(ByteArrayInputStream(cert)))
        )
    }
    val sslContextFactory = SslContextFactory.Server().apply {
        keyStoreType = "JKS"
        keyStore = store
        keyStorePassword = pass
    }

    val tls = SslConnectionFactory(sslContextFactory, alpn.protocol)
    val connector = ServerConnector(server, tls, alpn, h2, http11)
    connector.port = port
    return connector
}