package org.jeudego.pairgoth.server

import info.macias.sse.EventBroadcast
import info.macias.sse.events.MessageEvent
import info.macias.sse.servlet3.ServletEventTarget
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    private val keepAlive = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "sse-keepalive").apply { isDaemon = true } }

    override fun init() {
        // periodic keep-alive comments stop the (proxied) long-lived connections from idling out,
        // and prune subscribers that have silently gone away
        keepAlive.scheduleAtFixedRate({
            try { broadcast.keepAlive() } catch (t: Throwable) { logger.warn("sse keep-alive failed", t) }
        }, 15, 15, TimeUnit.SECONDS)
    }

    override fun destroy() {
        keepAlive.shutdownNow()
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse?) {
        logger.trace("<< new channel")
        broadcast.addSubscriber(ServletEventTarget(req), req.getHeader("Last-Event-Id"))
    }

    internal fun broadcast(message: MessageEvent) = broadcast.broadcast(message)
}
