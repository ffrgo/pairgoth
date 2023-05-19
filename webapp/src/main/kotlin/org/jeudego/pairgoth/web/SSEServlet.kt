package org.jeudego.pairgoth.web

import info.macias.sse.EventBroadcast
import info.macias.sse.events.MessageEvent
import info.macias.sse.servlet3.ServletEventTarget
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class SSEServlet: HttpServlet() {
    companion object {
        private val logger = LoggerFactory.getLogger("sse")
        private var zeInstance: SSEServlet? = null
        internal fun getInstance(): SSEServlet = zeInstance ?: throw Error("SSE servlet not ready")
    }
    init {
        if (zeInstance != null) throw Error("Multiple instances of SSE servlet found!")
        zeInstance = this
    }
    private val broadcast = EventBroadcast()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse?) {
        logger.trace("<< new channel")
        broadcast.addSubscriber(ServletEventTarget(req), req.getHeader("Last-Event-Id"))
    }

    internal fun broadcast(message: MessageEvent) = broadcast.broadcast(message)
}
