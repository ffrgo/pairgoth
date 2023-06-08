package org.jeudego.pairgoth.application

import org.apache.commons.io.FileUtils
import org.eclipse.jetty.server.Server
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile


fun main(vararg args: String) {
    try {
        extractWarFiles()
        launchServer()
    } catch (t: Throwable) {
        t.printStackTrace(System.err)
    }
}

fun extractWarFiles() {
    // val jarLocation = object{}::class.java.protectionDomain.codeSource.location
    // prepare output directory
    val tmp = System.getProperty("java.io.tmpdir")
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

fun launchServer() {

    // create server
    val server = Server(8080)

    // register webapps
//    server.
}