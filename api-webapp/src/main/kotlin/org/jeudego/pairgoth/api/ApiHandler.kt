package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.server.ApiException
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

interface ApiHandler {

    fun route(request: HttpServletRequest, response: HttpServletResponse) =
        // for now, only get() needed the response object ; other methods shall be reengineered as well if needed
        when (request.method) {
            "GET" -> get(request, response)
            "POST" -> post(request)
            "PUT" -> put(request)
            "DELETE" -> delete(request)
            else -> notImplemented()
        }

    fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
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
        return request.getAttribute(PAYLOAD_KEY) as Json? ?: throw ApiException(HttpServletResponse.SC_BAD_REQUEST, "no payload")
    }

    fun getObjectPayload(request: HttpServletRequest): Json.Object {
        val json = getPayload(request)
        if (!json.isObject) badRequest("expecting a json object")
        return json.asObject()
    }

    fun getArrayPayload(request: HttpServletRequest): Json.Array {
        val json = getPayload(request)
        if (!json.isArray) badRequest("expecting a json array")
        return json.asArray()
    }

    fun getSelector(request: HttpServletRequest): String? {
        return request.getAttribute(SELECTOR_KEY) as String?
    }

    fun getSubSelector(request: HttpServletRequest): String? {
        return request.getAttribute(SUBSELECTOR_KEY) as String?
    }

    companion object {
        const val PAYLOAD_KEY = "PAYLOAD"
        const val SELECTOR_KEY = "SELECTOR"
        const val SUBSELECTOR_KEY = "SUBSELECTOR"
        val logger = LoggerFactory.getLogger("api")
        fun badRequest(msg: String = "bad request"): Nothing = throw ApiException(HttpServletResponse.SC_BAD_REQUEST, msg)
    }
}
