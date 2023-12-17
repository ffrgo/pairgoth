package org.jeudego.pairgoth.web

import com.republicate.kson.Json
import org.jeudego.pairgoth.ratings.RatingsManager
import org.jeudego.pairgoth.util.Colorizer
import org.jeudego.pairgoth.util.parse
import org.jeudego.pairgoth.util.toString
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class SearchServlet: HttpServlet() {

    public override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        val uri = request.requestURI
        logger.logRequest(request, !uri.contains(".") && uri.length > 1)
        var payload: Json? = null
        var reason = "OK"
        try {
            validateContentType(request)
            val query = request.getAttribute(PAYLOAD_KEY) as Json.Object? ?: throw ApiException(HttpServletResponse.SC_BAD_REQUEST, "no payload")
            val needle =  query.getString("needle") ?: throw ApiException(HttpServletResponse.SC_BAD_REQUEST, "no needle")
            val country = query.getString("countryFilter")
            val aga =  query.getBoolean("aga") ?: false
            val egf =  query.getBoolean("egf") ?: false
            val ffg =  query.getBoolean("ffg") ?: false
            payload = RatingsManager.search(needle, aga, egf, ffg, country)
            setContentType(response)
            payload.toString(response.writer)
        } catch (ioe: IOException) {
            logger.error(Colorizer.red("could not process call"), ioe)
            reason = ioe.message ?: "unknown i/o exception"
            error(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, reason, ioe)
        } finally {
            val builder = StringBuilder()
            builder.append(response.status).append(' ')
                .append(reason)
            if (response.status == HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
                logger.trace(Colorizer.red(">> {}"), builder.toString())
            } else {
                logger.trace(Colorizer.green(">> {}"), builder.toString())
            }

            // CB TODO - should be bufferized and asynchronously written in synchronous chunks
            // so that header lines from parallel requests are not mixed up in the logs ;
            // synchronizing the whole request log is not desirable
            for (header in response.headerNames) {
                val value = response.getHeader(header)
                logger.trace(Colorizer.green(">>     {}: {}"), header, value)
            }
            if (payload != null) {
                try {
                    logger.logPayload(">>     ", payload, false)
                } catch (ioe: IOException) {
                }
            }
        }

    }

    @Throws(ApiException::class)
    protected fun validateContentType(request: HttpServletRequest) {
        // extract content type parts
        val contentType = request.contentType
        if (contentType == null) {
            if (request.method == "GET") return
            throw ApiException(
                HttpServletResponse.SC_BAD_REQUEST,
                "no content type header"
            )
        }
        val sep = contentType.indexOf(';')
        val mimeType: String
        var charset: String? = null
        if (sep == -1) mimeType = contentType else {
            mimeType = contentType.substring(0, sep).trim { it <= ' ' }
            val params =
                contentType.substring(sep + 1).split("=".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            if (params.size == 2 && params[0].lowercase(Locale.getDefault())
                    .trim { it <= ' ' } == "charset"
            ) {
                charset = params[1].lowercase(Locale.getDefault()).trim { it <= ' ' }
                    .replace("-".toRegex(), "")
            }
        }

        // check charset
        if (charset != null && EXPECTED_CHARSET != charset.lowercase(Locale.ROOT).replace("-", "")) throw ApiException(
            HttpServletResponse.SC_BAD_REQUEST,
            "UTF-8 content expected"
        )

        // check content type
        if (isJson(mimeType)) {
            // put Json body as request attribute
            try {
                Json.parse(request.reader)?.let { payload: Json ->
                    request.setAttribute(PAYLOAD_KEY, payload)
                    if (logger.isInfoEnabled) {
                        logger.logPayload("<<     ", payload, true)
                    }
                }
            } catch (ioe: IOException) {
                throw ApiException(HttpServletResponse.SC_BAD_REQUEST, ioe)
            }
        }
        else throw ApiException(
            HttpServletResponse.SC_BAD_REQUEST,
            "JSON content expected"
        )

    }

    protected fun error(
        request: HttpServletRequest,
        response: HttpServletResponse,
        code: Int,
        message: String?,
        cause: Throwable? = null
    ) {
        try {
            if (code == 500 || response.isCommitted) {
                logger.error(
                    "Request {} {} gave error {} {}",
                    request.method,
                    request.requestURI,
                    code,
                    message,
                    cause
                )
            }
            response.status = code
            if (response.isCommitted) return
            val errorPayload = Json.Object(
                "success" to false,
                "error" to (message ?: "unknown error")
            )
            setContentType(response)
            errorPayload.toString(response.writer)
        } catch (ioe: IOException) {
            logger.error("Could not send back error", ioe)
        }
    }

    protected fun setContentType(response: HttpServletResponse) {
        response.contentType = "application/json; charset=UTF-8"
    }

    companion object {
        private var logger = LoggerFactory.getLogger("search")
        private const val EXPECTED_CHARSET = "utf8"
        const val PAYLOAD_KEY = "PAYLOAD"
        fun isJson(mimeType: String) = "text/json" == mimeType || "application/json" == mimeType || mimeType.endsWith("+json")
    }
}
