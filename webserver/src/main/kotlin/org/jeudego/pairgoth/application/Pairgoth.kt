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
        // publish properties as system properties
        publishProperties()
        // launch web server
        launchServer()
    } catch (t: Throwable) {
        t.printStackTrace(System.err)
    }
}

private val tmp = System.getProperty("java.io.tmpdir")
private val webapps = Path.of("${tmp}/pairgoth/webapps")

private fun cleanup() {
    FileUtils.deleteDirectory(webapps.toFile())
}

private val allowedModes = setOf("standalone", "server", "client")

private fun readProperties() {
    // do a first pass at determining the final 'mode', since it will influence other default value
    var mode = "standalone"
    val userProperties = File("./pairgoth.properties")
    if (userProperties.exists()) {
        val userProps = Properties()
        userProps.load(FileReader(userProperties))
        if (userProps.contains("mode")) mode = userProps.getProperty("mode")
    }
    val systemMode: String? = System.getProperty("pairgoth.mode")
    if (systemMode != null) {
        mode = systemMode
    }

    if (!allowedModes.contains(mode)) throw Error("invalid mode: $mode")

    // read default properties
    val defaultProps = getResource("/${mode}.default.properties") ?: throw Error("missing default server properties")
    defaultProps.openStream().use {
        serverProps.load(InputStreamReader(it, StandardCharsets.UTF_8))
    }
    // default env depends upon the presence of the pom.xml file
    val env = if (File("./pom.xml").exists()) "dev" else "prod"
    serverProps["env"] = env
    // read user properties
    if (userProperties.exists()) {
        serverProps.load(FileReader(userProperties))
    }
    // read system properties
    System.getProperties().forEach {
        val key = it.key as String
        val value = it.value as String
        if (key.startsWith("pairgoth.")) {
            serverProps[key.removePrefix("pairgoth.")] = value
        }
    }
}

private fun publishProperties() {
    serverProps.entries.forEach { entry ->
        val property = entry.key as String
        val value = entry.value as String
        if (!property.startsWith("webapp.ssl.")) {
            // do not propagate ssl properties further
            System.setProperty("pairgoth.$property", value)
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
    val extractVersion = Regex(".*?-(\\d+\\.\\d+(?:\\.\\d+)?(?:-[^.-]+)?).war")
    var version: String? = null
    jarFile.entries().toList().filter { entry ->
        entry.name.startsWith(jarConnection.entryName)
    }.forEach { entry ->
        if (!entry.isDirectory) {
            val filename = entry.name.removePrefix("META-INF/webapps/")
            val versionMatch = extractVersion.matchEntire(filename)
                ?: throw Error("Could not extract version from filename: $filename")
            val entryVersion = versionMatch.groupValues[1]
            if (version == null) {
                version = entryVersion
                serverProps["version"] = version
            } else if (version != entryVersion) {
                throw Error("Inconsistent versions found: $version and $entryVersion")
            }
            jarFile.getInputStream(entry).use { entryInputStream ->
                Files.copy(entryInputStream, webapps.resolve(filename))
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
    val mode = serverProps["mode"] ?: "standalone"
    if (mode == "server" || mode == "standalone") webAppContexts.add(createContext("api", "/api/tour"))
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

    val webappUrl = when (mode) {
        "client", "standalone" ->
            URL(
                serverProps.getProperty("webapp.protocol") ?: throw Error("missing property webapp.protocol"),
                serverProps.getProperty("webapp.host") ?: throw Error("missing property webapp.host"),
                serverProps.getProperty("webapp.port")?.toInt() ?: 80,
                "/"
            )
        "server" ->
            URL(
                serverProps.getProperty("api.protocol") ?: throw Error("missing property api.protocol"),
                serverProps.getProperty("api.host") ?: throw Error("missing property api.host"),
                serverProps.getProperty("api.port")?.toInt() ?: 80,
                "/"
            )
        else -> throw Error("invalid mode: $mode")
    }
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
    val version = serverProps["version"] ?: throw Error("version not found")
    context.war = "$tmp/pairgoth/webapps/$webapp-webapp-$version.war"
    context.contextPath = contextPath
    if (webapp == "api") {
        context.allowNullPathInfo = true
    }
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
