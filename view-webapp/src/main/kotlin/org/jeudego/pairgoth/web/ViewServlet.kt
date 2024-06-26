package org.jeudego.pairgoth.web

import org.apache.velocity.Template
import org.apache.velocity.context.Context
import org.apache.velocity.exception.ResourceNotFoundException
import org.apache.velocity.tools.view.ServletUtils
import org.apache.velocity.tools.view.VelocityViewServlet
import org.slf4j.LoggerFactory
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ViewServlet : VelocityViewServlet() {
    private fun fileExists(path: String): Boolean {
        return File(servletContext.getRealPath(path)).exists()
    }

    private fun decodeURI(request: HttpServletRequest): String {
        var uri = request.requestURI
        uri = try {
            URLDecoder.decode(uri, "UTF-8")
        } catch (use: UnsupportedEncodingException) {
            throw RuntimeException("could not decode URI $uri", use)
        }
        return uri
    }

    override fun getTemplate(request: HttpServletRequest, response: HttpServletResponse?): Template = getTemplate(STANDARD_LAYOUT)

    override fun fillContext(context: Context, request: HttpServletRequest) {
        super.fillContext(context, request)
        var uri = decodeURI(request)
        context.put("page", "$uri.html")
        val base = uri.replaceFirst(".html$".toRegex(), "")
        val suffixes = Arrays.asList("js", "css")
        for (suffix in suffixes) {
            val resource = "/$suffix$base.$suffix"
            if (fileExists(resource)) {
                context.put(suffix, resource)
            }
        }
        val lang = request.getAttribute("lang") as String
        /*
        val menu = menuEntries!![uri]
        var title: String? = null
        if (lang != null && menu != null) title = menu.getString(lang)
        if (title != null) context.put("title", title)
        if (lang != null) context.put(
            "dateformat",
            DateFormat.getDateInstance(DateFormat.LONG, Locale.forLanguageTag(lang))
        )
         */
    }

    override fun error(
        request: HttpServletRequest?,
        response: HttpServletResponse,
        e: Throwable?
    ) {
        val path: String = ServletUtils.getPath(request)
        if (response.isCommitted) {
            log.error("An error occured but the response headers have already been sent.")
            log.error("Error processing a template for path '{}'", path, e)
            return
        }
        try {
            log.error("Error processing a template for path '{}'", path, e)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        } catch (e2: Exception) {
            // clearly something is quite wrong.
            // let's log the new exception then give up and
            // throw a runtime exception that wraps the first one
            val msg = "Exception while printing error screen"
            log.error(msg, e2)
            throw RuntimeException(msg, e)
        }
    }

    override fun manageResourceNotFound(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        e: ResourceNotFoundException?
    ) {
        val path = ServletUtils.getPath(request)
        log.debug("Resource not found for path '{}'", path, e)
        val message = e!!.message
        if (!response!!.isCommitted && message != null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, path)
        } else {
            error(request, response, e)
            throw e
        }

    }

    override fun doRequest(request: HttpServletRequest, response: HttpServletResponse ) {
        // val uri = request.requestURI
        logger.logRequest(request) //, !uri.contains(".") && uri.length > 1)
        super.doRequest(request, response)
    }

    companion object {
        private var logger = LoggerFactory.getLogger("view")
        private const val STANDARD_LAYOUT = "/WEB-INF/layouts/standard.html"
    }
}