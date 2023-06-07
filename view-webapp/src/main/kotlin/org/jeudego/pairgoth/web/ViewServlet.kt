package org.jeudego.pairgoth.web

import org.apache.commons.lang3.tuple.Pair
import org.apache.velocity.Template
import org.apache.velocity.context.Context
import org.apache.velocity.tools.view.ServletUtils
import org.apache.velocity.tools.view.VelocityViewServlet
import org.jeudego.pairgoth.util.Translator
import org.jeudego.pairgoth.web.WebappManager
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.Serializable
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors
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

    companion object {
        private const val STANDARD_LAYOUT = "/WEB-INF/layouts/standard.html"
    }
}