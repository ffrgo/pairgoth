package org.jeudego.pairgoth.web.sse

import info.macias.sse.EventBroadcast
import info.macias.sse.servlet3.ServletEventTarget
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class SSEServlet: HttpServlet() {
    companion object {
        private val logger = LoggerFactory.getLogger("sse")
    }
    private val broadcast = EventBroadcast()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse?) {
        logger.trace("<< new channel")
        broadcast.addSubscriber(ServletEventTarget(req), req.getHeader("Last-Event-Id"))
    }
}
