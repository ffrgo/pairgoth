package org.jeudego.pairgoth.web

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Receives client-side errors (window error/unhandledrejection handlers in main.js) so field
 * incidents on odd browsers leave a server-side trace. Session-gated by the AuthFilter like
 * the rest of the view /api; the boot-wide cap keeps a looping client from flooding the log.
 */
class ClientLogServlet : HttpServlet() {

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        response.contentType = "application/json; charset=UTF-8"
        if (counter.incrementAndGet() > MAX_PER_BOOT) {
            response.writer.write("""{"status":false,"message":"client log quota exceeded"}""")
            return
        }
        val body = request.reader.readText().take(MAX_BYTES)
        val userAgent = request.getHeader("User-Agent") ?: "-"
        logger.warn("[{}] {}", userAgent, body.replace('\n', ' ').trim())
        response.writer.write("""{"status":true}""")
    }

    companion object {
        private val logger = LoggerFactory.getLogger("client")
        private val counter = AtomicInteger()
        private const val MAX_PER_BOOT = 1000
        private const val MAX_BYTES = 4000
    }
}
