package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler
import org.jeudego.pairgoth.server.ApiServlet
import org.jeudego.pairgoth.server.SSEServlet
import org.jeudego.pairgoth.server.WebappManager
import org.mockito.kotlin.*
import java.io.*
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


// J2EE server basic mocking

object TestAPI {

    fun Any?.toUnit() = Unit

    private val apiServlet = ApiServlet()
    private val sseServlet = SSEServlet()

    private fun <T> testRequest(reqMethod: String, uri: String, accept: String = "application/json", payload: T? = null): String {

        WebappManager.properties["webapp.env"] = "test"

        // mock request
        val myHeaderNames = if (reqMethod == "GET") emptyList() else listOf("Content-Type")
        val selector = argumentCaptor<String>()
        val subSelector = argumentCaptor<String>()
        val reqPayload = argumentCaptor<String>()
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

        return buffer.toString() ?: throw Error("no response payload")
    }

    fun get(uri: String): Json = Json.parse(testRequest<Void>("GET", uri)) ?: throw Error("no payload")
    fun getXml(uri: String): String = testRequest<Void>("GET", uri, "application/xml")
    fun <T> post(uri: String, payload: T) = Json.parse(testRequest("POST", uri, payload = payload)) ?: throw Error("no payload")
    fun <T> put(uri: String, payload: T) = Json.parse(testRequest("PUT", uri, payload = payload)) ?: throw Error("no payload")
    fun <T> delete(uri: String, payload: T) = Json.parse(testRequest("DELETE", uri, payload = payload)) ?: throw Error("no payload")
}

// Get a list of resources

fun getTestResources(path: String) = File("${System.getProperty("user.dir")}/src/test/resources/$path").listFiles()

fun getTestFile(path: String) = File("${System.getProperty("user.dir")}/src/test/resources/$path")