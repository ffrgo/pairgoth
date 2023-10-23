package org.jeudego.pairgoth.server

import com.republicate.kson.Json
import org.jeudego.pairgoth.util.Colorizer.blue
import org.jeudego.pairgoth.util.Colorizer.green
import org.jeudego.pairgoth.util.toString
import org.slf4j.Logger
import java.io.StringWriter
import javax.servlet.http.HttpServletRequest

fun Logger.logRequest(req: HttpServletRequest, logHeaders: Boolean = false) {
    val builder = StringBuilder()
    builder.append(req.method).append(' ')
        .append(req.scheme).append("://")
        .append(req.localName)
    val port = req.localPort
    if (port != 80) builder.append(':').append(port)
    if (!req.contextPath.isEmpty()) {
        builder.append(req.contextPath)
    }
    builder.append(req.requestURI)
    if (req.method == "GET") {
        val qs = req.queryString
        if (qs != null) builder.append('?').append(qs)
    }
    // builder.append(' ').append(req.getProtocol());
    info(blue("<< {}"), builder.toString())
    if (isTraceEnabled && logHeaders) {
        // CB TODO - should be bufferized and asynchronously written in synchronous chunks
        // so that header lines from parallel requests are not mixed up in the logs ;
        // synchronizing the whole request log is not desirable
        val headerNames = req.headerNames
        while (headerNames.hasMoreElements()) {
            val name = headerNames.nextElement()
            val value = req.getHeader(name)
            trace(blue("<<     {}: {}"), name, value)
        }
    }
}

fun Logger.logPayload(prefix: String?, payload: Json, upstream: Boolean) {
    val writer = StringWriter()
    //payload.toPrettyString(writer, "");
    payload.toString(writer)
    if (isTraceEnabled) {
        for (line in writer.toString().split("\n".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()) {
            trace(if (upstream) blue("{}{}") else green("{}{}"), prefix, line)
        }
    } else {
        var line = writer.toString()
        val pos = line.indexOf('\n')
        if (pos != -1) line = line.substring(0, pos)
        if (line.length > 50) line = line.substring(0, 50) + "..."
        debug(if (upstream) blue("{}{}") else green("{}{}"), prefix, line)
    }
}


fun HttpServletRequest.getRemoteAddress(): String? {
    var ip = getHeader("X-Forwarded-For")
    if (ip == null) {
        ip = remoteAddr
    } else {
        val comma = ip.indexOf(',')
        if (comma != -1) {
            ip = ip.substring(0, comma).trim { it <= ' ' } // keep the left-most IP address
        }
    }
    return ip
}
