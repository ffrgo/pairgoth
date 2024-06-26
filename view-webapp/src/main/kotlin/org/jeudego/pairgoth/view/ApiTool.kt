package org.jeudego.pairgoth.view

import com.republicate.kson.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.EMPTY_REQUEST
import org.jeudego.pairgoth.web.AuthFilter
import org.jeudego.pairgoth.web.WebappManager
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ApiTool {
    companion object {
        const val JSON = "application/json"
        const val XML = "application/xml"
        val apiRoot by lazy {
            WebappManager.properties.getProperty("api.external.url")?.let { "${it.removeSuffix("/")}/" }
                ?: throw Error("no configured API url")
        }
        val logger = LoggerFactory.getLogger("api")
    }
    private lateinit var request: HttpServletRequest
    fun setRequest(req: HttpServletRequest) {
        request = req
    }
    fun getBearer() = AuthFilter.getBearer(request)

    private val client = OkHttpClient()
    private fun prepare(url: String) =
        Request.Builder().url("$apiRoot$url")
            .header("Accept", JSON)
            .header("Authorization", "Bearer ${getBearer()}")

    private fun Json.toRequestBody() = toString().toRequestBody(JSON.toMediaType())
    private fun Request.Builder.process(): Json {
        try {
            val apiReq = build()
            if (logger.isTraceEnabled) {
                logger.trace(">> ${apiReq.method} ${apiReq.url}")
                apiReq.headers.forEach { header ->
                    logger.trace("   ${header.first} ${header.second}")
                }
            }

            logger.trace("   ")
            return client.newCall(apiReq).execute().use { response ->
                if (logger.isTraceEnabled) {
                    logger.trace("<< ${response.code} ${response.message}")
                    response.headers.forEach { header ->
                        logger.trace("   ${header.first} ${header.second}")
                    }
                }
                if (response.isSuccessful) {
                    when (response.body?.contentType()?.subtype) {
                        null -> throw Error("null body or content type")
                        "json" -> Json.parse(response.body!!.string()) ?: throw Error("could not parse json")
                        else -> throw Error("unhandled content type: ${response.body!!.contentType()}")
                    }
                } else {
                    if (response.code == HttpServletResponse.SC_UNAUTHORIZED) {
                        request.session.removeAttribute(AuthFilter.SESSION_KEY_API_TOKEN)
                        request.session.removeAttribute(AuthFilter.SESSION_KEY_USER)
                    }
                    when (response.body?.contentType()?.subtype) {
                        "json" -> Json.parse(response.body!!.string()) ?: throw Error("could not parse error json")
                        else -> throw Error("${response.code} ${response.message}")
                    }
                }
            }
        } catch (e: Throwable) {
            logger.error("api call failed", e)
            return Json.Object("error" to e.message)
        }
    }

    fun get(url: String) = prepare(url).process()
    fun post(url: String, payload: Json) = prepare(url)
        .post(payload.toRequestBody())
        .process()
    fun put(url: String, payload: Json) = prepare(url)
        .put(payload.toRequestBody())
        .process()
    fun delete(url: String, payload: Json? = null) = prepare(url)
        .delete(payload?.toRequestBody() ?: EMPTY_REQUEST)
        .process()

    fun post(url: String, xml: String) =
        prepare(url).post(xml.toRequestBody(XML.toMediaType())).process()
}
