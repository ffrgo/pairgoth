package org.jeudego.pairgoth.web

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Logs client-side errors.
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
