package org.jeudego.pairgoth.web

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.proxy.AsyncProxyServlet;
import javax.servlet.http.HttpServletRequest

class ApiServlet : AsyncProxyServlet() {

    override fun addProxyHeaders(clientRequest: HttpServletRequest, proxyRequest: Request) {
        // proxyRequest.header("X-EGC-User", some user id...)
    }

    override fun rewriteTarget(clientRequest: HttpServletRequest): String {
        val uri = clientRequest.requestURI
        if (!uri.startsWith("/api/")) throw Error("unhandled API uri: $uri")
        val path = uri.substringAfter("/api")
        val qr = clientRequest.queryString?.let { "?$it" } ?: ""
        return "$apiRoot$path$qr"
    }

    companion object {
        private val apiRoot by lazy {
            WebappManager.getProperty("api.external.url")?.let { "${it.removeSuffix("/")}" }
                ?: throw Error("no configured API url")
        }
    }
}
