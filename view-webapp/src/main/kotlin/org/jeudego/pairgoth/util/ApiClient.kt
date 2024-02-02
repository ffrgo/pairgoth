package org.jeudego.pairgoth.util.ApiClient

import org.jeudego.pairgoth.util.parse

import com.republicate.kson.Json
import org.apache.http.*
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.*
import org.apache.http.config.SocketConfig
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.entity.ContentType
import org.apache.http.entity.EntityTemplate
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicNameValuePair
import org.apache.http.ssl.SSLContexts
import org.apache.http.util.EntityUtils
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.StringReader
import java.net.ProtocolException
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory


/**
 * This class implements a basic API client around Apache HTTP client.
 */

val API_CLIENT_TIMEOUT = 60000

// TODO cookieStore ? credentialsProvider ?
// CookieStore cookieStore = new BasicCookieStore();
// CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

private val client = HttpClients.custom()
    .setSSLSocketFactory(
        SSLConnectionSocketFactory(
            SSLContexts.createSystemDefault(), arrayOf("TLSv1.2"),
            null,
            SSLConnectionSocketFactory.getDefaultHostnameVerifier()
        )
    )
    .setConnectionTimeToLive(1, TimeUnit.MINUTES)
    .setDefaultSocketConfig(
        SocketConfig.custom()
            .setSoTimeout(API_CLIENT_TIMEOUT)
            .build()
    )
    .setDefaultRequestConfig(
        RequestConfig.custom()
            .setConnectTimeout(API_CLIENT_TIMEOUT)
            .setSocketTimeout(API_CLIENT_TIMEOUT)
            .setCookieSpec(CookieSpecs.STANDARD)
            .build()
    )
    .build()

// CB TODO - this should go elsewhere
fun ByteArray.reader(charset: Charset) =
    BufferedReader(InputStreamReader(ByteArrayInputStream(this), charset))

fun header(name: String, value: String) = BasicHeader(name, value)

fun param(name: String, value: String) = BasicNameValuePair(name, value)

// Incomplete list of binary content types, that we should avoid to log
fun ContentType.isBinary() = mimeType.startsWith("image/") || mimeType.endsWith("pdf")
fun ContentType.isText() = !isBinary()

data class BinaryData(val contentType: ContentType, val data: ByteArray, val name: String, val filename: String? = null)

fun buildMultiPartBody(payload: Json.Object, binaryData: BinaryData? = null): HttpEntity {
    val builder = MultipartEntityBuilder.create()
    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
    for (entry in payload.entries) {
        builder.addPart(entry.key, StringBody(entry.value.toString(), ContentType.MULTIPART_FORM_DATA))
    }
    binaryData?.let {
        builder.addBinaryBody(it.name, it.data, it.contentType, it.filename ?: "file")
    }
    return builder.build()
}

abstract class BaseApiClient<T> {

    companion object {
        internal var logger = LoggerFactory.getLogger("api")
    }

    private fun <B> build(method: String, url: String, body: B? = null, vararg keyValues: NameValuePair): HttpRequestBase {

        val headers = mutableListOf<Header>()
        val params = mutableListOf<NameValuePair>()
        for (pair in keyValues) {
            when (pair) {
                is Header -> headers.add(pair)
                else -> params.add(pair)
            }
        }

        val req: HttpRequestBase = when (method) {
            "GET" -> {
                val paramsString = params.map { "${it.name}=${it.value}" }.joinToString("&")
                val finalURL = url + (if (url.contains('?')) '&' else '?' ) + paramsString
                HttpGet(finalURL)
            }
            "POST" -> {
                HttpPost(url).also { req ->
                    if (params.isNotEmpty() && body != null) {
                        throw ClientProtocolException("specify POST body or POST parameters but not both")
                    }
                    if (params.isNotEmpty()) {
                        val entity = UrlEncodedFormEntity(params, "UTF-8")
                        req.entity = entity
                    } else if (body != null) {
                        val entity = if (body is HttpEntity) body
                        else StringEntity(body.toString(),
                            if (body is Json) ContentType.APPLICATION_JSON
                            else ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8))
                        req.entity = entity
                    }
                }
            }
            "PATCH" -> {
                HttpPatch(url).also { req ->
                    if (params.isNotEmpty() && body != null) {
                        throw ClientProtocolException("specify PATCH body or PATCH parameters but not both")
                    }
                    if (params.isNotEmpty()) {
                        val entity = UrlEncodedFormEntity(params, "UTF-8")
                        req.entity = entity
                    } else if (body != null) {
                        val entity = if (body is HttpEntity) body
                        else EntityTemplate { outputstream: OutputStream ->
                            outputstream.write(body.toString().toByteArray())
                            outputstream.flush()
                        }.also {
                            it.setContentType(ContentType.APPLICATION_JSON.toString())
                        }
                        req.entity = entity
                    }
                }
            }
            "DELETE" -> {
                HttpDelete(url).also { req ->
                    if (params.isNotEmpty() || body != null) {
                        throw ClientProtocolException("DELETE body or DELETE parameters not supported")
                    }
                }
            }
            else -> throw ClientProtocolException("unhandled method: $method")
        }
        headers.addAll(acceptHeaders())
        headers.forEach {
            req.addHeader(it)
        }
        return req
    }

    private fun submit(req: HttpRequestBase): Pair<ByteArray, ContentType> {
        try {
            val resp: HttpResponse = client.execute(req)
            val statusLine = resp.statusLine
            val status = statusLine.statusCode
            when {
                status in 200..299 -> {
                    if (status != 204 && resp.entity == null) throw ClientProtocolException("Response is empty")
                    val body = resp.entity?.let { EntityUtils.toByteArray(it) }
                    val contentType = resp.entity?.let { ContentType.get(it) }
                    if (logger.isTraceEnabled) traceResponse(resp, body?.toString(contentType?.charset ?: StandardCharsets.UTF_8), contentType)
                    return Pair(body ?: "{}".toByteArray(StandardCharsets.UTF_8), contentType ?: ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8))
                }
                else -> {
                    val body = resp.entity?.let { EntityUtils.toString(it) }
                    val contentType = resp.entity?.let { ContentType.get(it) }
                    if (logger.isTraceEnabled) traceResponse(resp, body, contentType)
                    var message = statusLine.toString()
                    if (body != null) message = "$message - $body"
                    throw IOException(message)
                }
            }
        } finally {
            req.releaseConnection()
        }
    }

    private fun traceRequest(req: HttpRequestBase, body: String? = null) {
        logger.trace(">> ${req.method} ${req.uri}")
        for (header in req.allHeaders) {
            logger.trace(">> $header")
        }
        if (body != null) {
            val contentType = req.allHeaders.firstOrNull { it.name == HttpHeaders.CONTENT_TYPE }?.let {
                ContentType.parse(it.value)
            }
            if (contentType?.isText() ?: true) {
                for (line in body.split(Regex("[\r\n]"))) {
                    logger.trace(">> $line")
                }
            } else {
                logger.trace(">> ${contentType?.toString() ?: "unknown mime type"}, ${body.length} bytes")
            }
        }
    }

    private fun traceResponse(resp: HttpResponse, body: String?, contentType: ContentType?) {
        val statusLine = resp.statusLine
        logger.trace("<< ${statusLine.statusCode} ${statusLine.reasonPhrase}")
        for (header in resp.allHeaders) logger.trace("<< $header")
        if (body != null) {
            val knownContentType = contentType ?: resp.allHeaders.firstOrNull { it.name == HttpHeaders.CONTENT_TYPE }?.let {
                ContentType.parse(it.value)
            }
            if (knownContentType?.isText() ?: true) {
                for (line in body.split(Regex("[\r\n]"))) {
                    logger.trace("<< $line")
                }
            } else {
                logger.trace("<< ${contentType?.toString() ?: "unknown mime type"}, ${body.length} bytes")
            }
        }
    }

    protected abstract fun acceptHeaders(): List<Header>

    protected abstract fun parseResult(result: Pair<ByteArray, ContentType>): T

    fun get(url: String, vararg with: NameValuePair): T {
        val req = build("GET", url, null, *with)
        if (logger.isTraceEnabled) traceRequest(req)
        val result = submit(req)
        return parseResult(result)
    }

    fun <B> post(url: String, body: B?, vararg with: NameValuePair): T {
        val req = build("POST", url, body, *with)
        if (logger.isTraceEnabled) traceRequest(req, body?.toString())
        val result = submit(req)
        return parseResult(result)
    }

    fun <B> patch(url: String, body: B?, vararg with: NameValuePair): T {
        val req = build("PATCH", url, body, *with)
        if (logger.isTraceEnabled) traceRequest(req, body?.toString())
        val result = submit(req)
        return parseResult(result)
    }

    fun <B> delete(url: String, body: B?, vararg with: NameValuePair): T {
        val req = build("DELETE", url, body, *with)
        if (logger.isTraceEnabled) traceRequest(req, body?.toString())
        val result = submit(req)
        return parseResult(result)
    }
}

object AgnosticApiClient: BaseApiClient<Pair<ByteArray, ContentType>>() {
    override fun acceptHeaders() = listOf(BasicHeader(HttpHeaders.ACCEPT, "*/*"))
    override fun parseResult(result: Pair<ByteArray, ContentType>) = result
}

object JsonApiClient: BaseApiClient<Json>() {

    override fun acceptHeaders(): List<Header> {
        return listOf(BasicHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.mimeType))
    }

    override fun parseResult(result: Pair<ByteArray, ContentType>): Json {
        when (result.second.mimeType) {
            ContentType.APPLICATION_JSON.mimeType, "application/vnd.api+json" -> {
                return Json.parse(result.first.reader(result.second.charset ?: StandardCharsets.UTF_8))!!
            }
            ContentType.APPLICATION_FORM_URLENCODED.mimeType -> {
                val json = Json.MutableObject()
                val charset = result.second.charset ?: StandardCharsets.UTF_8
                val decoded = URLDecoder.decode(result.first.toString(charset), charset.name())
                decoded.split("&").forEach {
                    val keyValue = it.split(Regex("="), 2)
                    if (keyValue.size != 2) throw ProtocolException("expecting a key-value pair: $it")
                    val prev = json.put(keyValue[0], keyValue[1])
                    if (prev != null) throw ClientProtocolException("Unsupported redundant values in response for key: " + keyValue[0]);
                }
                return json
            }
            else -> throw ClientProtocolException("invalid content type: ${result.second.mimeType}")
        }
    }
}

//    CB TODO - needs to factorize XmlUtils with server => needs a common module
//
//object XmlApiClient: BaseApiClient<Element>() {
//
//    override fun acceptHeaders(): List<Header> {
//        return listOf(
//            BasicHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_XML.mimeType),
//            BasicHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_SOAP_XML.mimeType)
//        )
//    }
//
//    override fun parseResult(result: Pair<ByteArray, ContentType>): Element {
//        when (result.second.mimeType) {
//            ContentType.APPLICATION_XML.mimeType, ContentType.APPLICATION_SOAP_XML.mimeType -> {
//                return XmlUtils.parse(result.first.reader(result.second.charset ?: StandardCharsets.UTF_8)) ?: throw ClientProtocolException("empty XML body")
//            }
//            else -> throw ClientProtocolException("invalid content type: ${result.second.mimeType}")
//        }
//    }
//}
//
//object SoapTextApiClient: BaseApiClient<Element>() {
//
//    override fun acceptHeaders() = listOf(BasicHeader(HttpHeaders.ACCEPT, "*/*"))
//
//    override fun parseResult(result: Pair<ByteArray, ContentType>): Element {
//        when (result.second.mimeType) {
//            ContentType.TEXT_XML.mimeType -> {
//                return XmlUtils.parse(result.first.reader(result.second.charset ?: StandardCharsets.UTF_8)) ?: throw ClientProtocolException("empty XML body")
//            }
//            else -> throw ClientProtocolException("invalid content type: ${result.second.mimeType}")
//        }
//    }
//}
//
