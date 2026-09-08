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
        // welcome event carrying this boot's identity: volatile (not retained) and id-less (leaves the
        // client's replay cursor untouched), it lets even cursor-less subscribers — tabs that only ever
        // saw keep-alive comments — detect a server restart across reconnects and resync
        private val helloEvent = MessageEvent.Builder()
            .setEvent("hello")
            .setData(Event.bootId)
            .setVolatile()
            .build()
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
        // polling transport (HTTP/1.1 clients): same feed as JSON instead of a held SSE connection
        val since = req.getParameter("since")
        if (since != null && resp != null) {
            resp.contentType = "application/json; charset=UTF-8"
            resp.writer.write(Event.eventsSince(since.toLongOrNull()).toString())
            return
        }
        logger.trace("<< new channel")
        broadcast.addSubscriber(ServletEventTarget(req), helloEvent, req.getHeader("Last-Event-Id"))
    }

    internal fun broadcast(message: MessageEvent) = broadcast.broadcast(message)
}
