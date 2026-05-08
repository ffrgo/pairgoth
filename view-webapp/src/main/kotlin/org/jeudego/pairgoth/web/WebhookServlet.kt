package org.jeudego.pairgoth.web

import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.servlet.ServletOutputStream
import javax.servlet.WriteListener
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponseWrapper

class WebhookServlet : HttpServlet() {

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        val sub = subPath(request) ?: return notConfigured(response)
        when {
            // GET /players/{code} — pull registered players from the consumer.
            playersRoute.matches(sub) -> forward("GET", sub, null, null, response)
            else -> notFound(response, "GET", sub)
        }
    }

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        val sub = subPath(request) ?: return notConfigured(response)
        when {
            sub.startsWith("/publish/") -> handlePublish(request, response, sub.removePrefix("/publish/"))
            else -> notFound(response, "POST", sub)
        }
    }

    private fun notFound(response: HttpServletResponse, method: String, sub: String) {
        response.status = HttpServletResponse.SC_NOT_FOUND
        response.contentType = "application/json; charset=UTF-8"
        val msg = "unknown webhook route: $method $sub".replace("\"", "\\\"")
        response.writer.write("""{"status":false,"message":"$msg"}""")
    }

    private fun handlePublish(request: HttpServletRequest, response: HttpServletResponse, spec: String) {
        // spec = "{view}/{code}/{round}"
        val parts = spec.split('/')
        if (parts.size != 3) {
            response.status = HttpServletResponse.SC_BAD_REQUEST
            response.contentType = "application/json; charset=UTF-8"
            response.writer.write("""{"status":false,"message":"expected /publish/{view}/{code}/{round}"}""")
            return
        }
        val (view, code, round) = parts
        // Body source: pairings/results render server-side from $tour state; standings receives the
        // browser-scraped HTML in the request body (Fomantic-styled UI tables, can't easily re-render).
        val rendered: String = when (view) {
            "pairings", "results" -> {
                val tourId = request.getParameter("id") ?: run {
                    response.status = HttpServletResponse.SC_BAD_REQUEST
                    response.contentType = "application/json; charset=UTF-8"
                    response.writer.write("""{"status":false,"message":"missing id parameter"}""")
                    return
                }
                render(request, response, view, tourId, round) ?: return
            }
            "standings" ->
                request.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            else -> {
                response.status = HttpServletResponse.SC_BAD_REQUEST
                response.contentType = "application/json; charset=UTF-8"
                response.writer.write("""{"status":false,"message":"unknown view: $view"}""")
                return
            }
        }
        // Consumer route: standings → /standings/{code}/{round}; pairings & results both → /pairings/{code}/{round}.
        val consumerPath = if (view == "standings") "/standings/$code/$round" else "/pairings/$code/$round"
        forward("POST", consumerPath, "text/html; charset=UTF-8",
            wrap(rendered).toByteArray(StandardCharsets.UTF_8), response)
    }

    /** Wraps inner content with the .pairgoth-published div + the consolidated style block. */
    private fun wrap(inner: String): String =
        "<style>@layer pairgoth-published {\n$publishCss\n}</style>\n" +
        "<div class=\"pairgoth-published\">\n$inner\n</div>\n"

    private fun render(request: HttpServletRequest, response: HttpServletResponse, view: String, tourId: String, round: String): String? {
        val target = "/publish/$view?id=$tourId&round=$round"
        return try {
            val capture = CapturingResponse(response)
            request.getRequestDispatcher(target).include(request, capture)
            capture.body()
        } catch (e: Exception) {
            logger.error("publish render failed for $target", e)
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            response.contentType = "application/json; charset=UTF-8"
            val msg = (e.message ?: "render error").replace("\"", "\\\"")
            response.writer.write("""{"status":false,"message":"$msg"}""")
            null
        }
    }

    private fun subPath(request: HttpServletRequest): String? {
        if (webhookUrl.isEmpty()) return null
        val uri = request.requestURI
        if (!uri.startsWith("/api/webhook/")) throw Error("unexpected URI: $uri")
        return uri.substringAfter("/api/webhook")
    }

    private fun notConfigured(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
        response.contentType = "application/json; charset=UTF-8"
        response.writer.write("""{"status":false,"message":"webhook not configured"}""")
    }

    private fun forward(method: String, subPath: String, contentType: String?, body: ByteArray?, response: HttpServletResponse) {
        val target = "${webhookUrl.removeSuffix("/")}$subPath"
        try {
            val conn = URL(target).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.setRequestProperty("X-Pairgoth-Secret", webhookSecret)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            if (body != null) {
                conn.doOutput = true
                contentType?.let { conn.setRequestProperty("Content-Type", it) }
                conn.outputStream.use { it.write(body) }
            }
            val status = conn.responseCode
            response.status = status
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            if (stream != null) {
                response.contentType = conn.contentType ?: "application/json; charset=UTF-8"
                stream.use { input ->
                    response.outputStream.use { output -> input.copyTo(output) }
                }
            } else {
                // Upstream returned no body (e.g. 404 from a missing route). Synthesize JSON so the
                // browser-side parser doesn't choke on Content-Type: application/json + empty body.
                response.contentType = "application/json; charset=UTF-8"
                response.writer.write("""{"status":false,"message":"upstream $status with no body for $target"}""")
            }
        } catch (e: Exception) {
            logger.error("webhook forward failed: $method $target", e)
            response.status = HttpServletResponse.SC_BAD_GATEWAY
            response.contentType = "application/json; charset=UTF-8"
            val msg = (e.message ?: "webhook unreachable").replace("\"", "\\\"")
            response.writer.write("""{"status":false,"message":"$msg"}""")
        }
    }

    /** Wraps a response so the included render output is captured into a StringWriter rather than committed. */
    private class CapturingResponse(orig: HttpServletResponse) : HttpServletResponseWrapper(orig) {
        private val sw = StringWriter()
        private val pw = PrintWriter(sw)
        private val sos = object : ServletOutputStream() {
            override fun write(b: Int) { sw.write(b) }
            override fun isReady() = true
            override fun setWriteListener(listener: WriteListener?) {}
        }
        override fun getWriter(): PrintWriter = pw
        override fun getOutputStream(): ServletOutputStream = sos
        fun body(): String { pw.flush(); return sw.toString() }
    }

    private val publishCss: String by lazy {
        servletContext.getResourceAsStream("/css/publish.css")?.bufferedReader()?.use { it.readText() }
            ?: "/* /css/publish.css missing */"
    }

    companion object {
        private val logger = LoggerFactory.getLogger("webhook")
        private const val TIMEOUT_MS = 30_000

        private val playersRoute = Regex("^/players/[^/]+$")

        private val webhookUrl: String by lazy {
            WebappManager.properties.getProperty("webhook.url")?.trim().orEmpty()
        }
        private val webhookSecret: String by lazy {
            WebappManager.properties.getProperty("webhook.secret").orEmpty()
        }
    }
}
