package org.jeudego.pairgoth.application

import org.apache.commons.io.FileUtils
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.webapp.WebAppContext
import java.io.File
import java.io.FileReader
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile

fun main(vararg args: String) {
    try {
        extractWarFiles()
        launchServer()
    } catch (t: Throwable) {
        t.printStackTrace(System.err)
    }
}

private val tmp = System.getProperty("java.io.tmpdir")
private val version = "1.0-SNAPSHOT" // TODO CB

private fun extractWarFiles() {
    // val jarLocation = object{}::class.java.protectionDomain.codeSource.location
    // prepare output directory
    val targetPath = Path.of("${tmp}/pairgoth/webapps")
    FileUtils.deleteDirectory(targetPath.toFile())
    Files.createDirectories(targetPath)

    // extract wars
    val webappsFolderURL = object{}::class.java.enclosingClass.getResource("/META-INF/webapps") ?: throw Error("webapps not found")
    val jarConnection = webappsFolderURL.openConnection() as JarURLConnection
    val jarFile: JarFile = jarConnection.getJarFile()
    jarFile.entries().toList().filter { entry ->
        entry.name.startsWith(jarConnection.entryName)
    }.forEach { entry ->
        if (!entry.isDirectory) {
            jarFile.getInputStream(entry).use { entryInputStream ->
                Files.copy(entryInputStream, targetPath.resolve(entry.name.removePrefix("META-INF/webapps/")))
            }
        }
    }
}

private fun launchServer() {

    // create server
    val server = Server(8080) // CB TODO port is to be calculated from webapp.url

    // create webapps contexts
    val apiContext = createContext("api", "/api");
    val viewContext = createContext("view", "/");

    // handle properties
    val properties = File("./pairgoth.properties");
    if (properties.exists()) {
        val props = Properties()
        props.load(FileReader(properties));
        props.entries.forEach { entry ->
            val property = entry.key as String
            val value = entry.value as String
            if (property.startsWith("logger.")) {
                // special handling for logger properties
                val webappLoggerPropKey = "webapp-slf4j-logger.${property.substring(7)}"
                apiContext.setInitParameter(webappLoggerPropKey, value);
                viewContext.setInitParameter(webappLoggerPropKey, value);
            } else {
                System.setProperty("pairgoth.$property", value);
            }
        }
    }

    // register webapps
    server.handler = ContextHandlerCollection(apiContext, viewContext);

    // launch server
    server.start()
    server.join()
}

private fun createContext(webapp: String, contextPath: String) = WebAppContext().also { context ->
    context.war = "$tmp/pairgoth/webapps/$webapp-webapp-$version.war"
    context.contextPath = contextPath;
}
