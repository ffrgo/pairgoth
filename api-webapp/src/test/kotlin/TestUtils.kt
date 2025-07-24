package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler
import org.jeudego.pairgoth.server.ApiServlet
import org.jeudego.pairgoth.server.SSEServlet
import org.jeudego.pairgoth.server.WebappManager
import org.mockito.kotlin.*
import java.io.*
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.*
import javax.servlet.ReadListener
import javax.servlet.ServletInputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


// J2EE server basic mocking

object TestAPI {

    fun Any?.toUnit() = Unit

    fun parseURL(url: String): Pair<String, Map<String, String>> {
        val qm = url.indexOf('?')
        if (qm == -1) {
            return url to emptyMap()
        }
        val uri = url.substring(0, qm)
        val params = url.substring(qm + 1)
            .split('&')
            .map { it.split('=') }
            .mapNotNull {
                when (it.size) {
                    1 -> it[0].decodeUTF8() to ""
                    2 -> it[0].decodeUTF8() to it[1].decodeUTF8()
                    else -> null
                }
            }
            .toMap()
        return uri to params
    }

    private fun String.decodeUTF8() = URLDecoder.decode(this, "UTF-8") // decode page=%22ABC%22 to page="ABC"

    private val apiServlet = ApiServlet()
    private val sseServlet = SSEServlet()

    private fun <T> testRequest(reqMethod: String, url: String, accept: String = "application/json", payload: T? = null): String {

        WebappManager.properties["auth"] = "none"
        WebappManager.properties["store"] = "memory"
        WebappManager.properties["webapp.env"] = "test"

        val (uri, parameters) = parseURL(url)

        // mock request
        val myHeaderNames = if (reqMethod == "GET") emptyList() else listOf("Content-Type")
        val selector = argumentCaptor<String>()
        val subSelector = argumentCaptor<String>()
        val reqPayload = argumentCaptor<String>()
        val parameter = argumentCaptor<String>()
        val myInputStream = payload?.let { DelegatingServletInputStream(payload.toString().byteInputStream(StandardCharsets.UTF_8)) }
        val myReader = payload?.let { BufferedReader(StringReader(payload.toString())) }
        val req = mock<HttpServletRequest> {
            on { method } doReturn reqMethod
            on { requestURI } doReturn uri
            on { setAttribute(eq(ApiHandler.SELECTOR_KEY), selector.capture()) } doAnswer {}
            on { setAttribute(eq(ApiHandler.SUBSELECTOR_KEY), subSelector.capture()) } doAnswer {}
            on { setAttribute(eq(ApiHandler.PAYLOAD_KEY), reqPayload.capture()) } doAnswer {}
            on { getAttribute(ApiHandler.SELECTOR_KEY) } doAnswer { selector.allValues.lastOrNull() }
            on { getAttribute(ApiHandler.SUBSELECTOR_KEY) } doAnswer { subSelector.allValues.lastOrNull() }
            on { getAttribute(ApiHandler.PAYLOAD_KEY) } doAnswer { reqPayload.allValues.lastOrNull() }
            on { reader } doReturn myReader
            on { inputStream } doReturn myInputStream
            on { scheme } doReturn "http"
            on { localName } doReturn "pairgoth"
            on { localPort } doReturn 80
            on { contextPath } doReturn ""
            on { contentType } doReturn if (reqMethod == "GET") null else when (payload) {
                is Json -> "application/json; charset=UTF-8"
                is String -> "application/xml; charset=UTF-8"
                else -> throw Error("unhandled case")
            }
            on { headerNames } doReturn Collections.enumeration(myHeaderNames)
            on { getHeader(eq("Accept")) } doReturn accept
            on { getParameter(parameter.capture()) } doAnswer { parameters[parameter.lastValue] }
        }

        // mock response
        val buffer = StringWriter()
        val errCode = argumentCaptor<Int>()
        val errMessage = argumentCaptor<String>()
        val resp = mock<HttpServletResponse> {
            on { writer } doAnswer { PrintWriter(buffer) }
            on { sendError(errCode.capture(), errMessage.capture()) } doAnswer { throw Error("${errCode.lastValue} ${errMessage.lastValue}") }
        }

        when (reqMethod) {
            "GET" -> apiServlet.doGet(req, resp)
            "POST" -> apiServlet.doPost(req, resp)
            "PUT" -> apiServlet.doPut(req, resp)
            "DELETE" -> apiServlet.doDelete(req, resp)
        }

        return buffer.toString()
    }

    fun get(uri: String): Json = Json.parse(testRequest<Void>("GET", uri)) ?: throw Error("no payload")
    fun getXml(uri: String): String = testRequest<Void>("GET", uri, "application/xml")
    fun getJson(uri: String): String = testRequest<Void>("GET", uri, "application/pairgoth")
    fun getCSV(uri: String): String = testRequest<Void>("GET", uri, "text/csv")
    fun getFFG(uri: String): String = testRequest<Void>("GET", uri, "application/ffg")
    fun <T> post(uri: String, payload: T) = Json.parse(testRequest("POST", uri, payload = payload)) ?: throw Error("no payload")
    fun <T> put(uri: String, payload: T) = Json.parse(testRequest("PUT", uri, payload = payload)) ?: throw Error("no payload")
    fun <T> delete(uri: String, payload: T) = Json.parse(testRequest("DELETE", uri, payload = payload)) ?: throw Error("no payload")
}

// Get a list of resources

fun getTestResources(path: String) = getTestFile(path).listFiles()

fun getTestFile(path: String) = File("${System.getProperty("user.dir")}/src/test/resources/$path")

fun getOutputFile(path: String) = File("${System.getProperty("test.build.dir")}/$path")

class DelegatingServletInputStream(val sourceStream: InputStream) : ServletInputStream() {

    override fun read(): Int {
        return sourceStream.read()
    }

    override fun isFinished(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isReady(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setReadListener(readListener: ReadListener?) {
        TODO("Not yet implemented")
    }

    override fun close() {
        super.close()
        sourceStream.close()
    }
}
