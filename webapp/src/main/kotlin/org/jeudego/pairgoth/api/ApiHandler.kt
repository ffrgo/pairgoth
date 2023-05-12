package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.web.ApiException
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

interface ApiHandler {

    fun route(request: HttpServletRequest, response: HttpServletResponse) =
        when (request.method) {
            "GET" -> get(request)
            "POST" -> post(request)
            "PUT" -> put(request)
            "DELETE" -> delete(request)
            else -> notImplemented()
        }

    fun get(request: HttpServletRequest): Json {
        notImplemented()
    }

    fun post(request: HttpServletRequest): Json {
        notImplemented()
    }

    fun put(request: HttpServletRequest): Json {
        notImplemented()
    }

    fun delete(request: HttpServletRequest): Json {
        notImplemented()
    }

    fun notImplemented(): Nothing {
        throw ApiException(HttpServletResponse.SC_BAD_REQUEST)
    }

    fun getPayload(request: HttpServletRequest): Json {
        return request.getAttribute(PAYLOAD_KEY) as Json ?: throw ApiException(HttpServletResponse.SC_BAD_REQUEST, "no payload")
    }

    fun getSelector(request: HttpServletRequest): String? {
        return request.getAttribute(SELECTOR_KEY) as String?
    }

    companion object {
        const val PAYLOAD_KEY = "PAYLOAD"
        const val SELECTOR_KEY = "SELECTOR"
        val logger = LoggerFactory.getLogger("api")
        fun badRequest(msg: String = "bad request"): Nothing = throw ApiException(HttpServletResponse.SC_BAD_REQUEST, msg)
    }
}
