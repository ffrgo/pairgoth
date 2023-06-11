package org.jeudego.pairgoth.view

import com.republicate.kson.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.EMPTY_REQUEST

class ApiTool {
    companion object {
        const val JSON = "application/json"
        val apiRoot = System.getProperty("pairgoth.webapp.url").let { base ->
            if (base.endsWith('/')) "${base}api/"
            else "${base}/api/"
        }
    }
    private val client = OkHttpClient()
    private fun prepare(url: String) = Request.Builder().url("$apiRoot$url").header("Accept", JSON)
    private fun Json.toRequestBody() = toString().toRequestBody(JSON.toMediaType())
    private fun Request.Builder.process(): Json {
        client.newCall(build()).execute().use { response ->
            if (response.isSuccessful) {
                when (response.body?.contentType()?.subtype) {
                    null -> throw Error("null body or content type")
                    "json" -> return Json.parse(response.body!!.string()) ?: throw Error("could not parse json")
                    else -> throw Error("unhandled content type: ${response.body!!.contentType()}")
                }
            } else throw Error("api call failed: ${response.code} ${response.message}")
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
}
