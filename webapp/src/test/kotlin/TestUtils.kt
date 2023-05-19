package org.jeudego.pairgoth.test

import com.republicate.kson.Json
import org.jeudego.pairgoth.api.ApiHandler
import org.jeudego.pairgoth.web.ApiServlet
import org.jeudego.pairgoth.web.WebappManager
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


// J2EE server basic mocking

object TestAPI {

    fun Any?.toUnit() = Unit

    private val apiServlet = ApiServlet()

    private fun <T> testRequest(reqMethod: String, uri: String, payload: T? = null): Json {

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
            on { getHeader(eq("Accept")) } doReturn "application/json"
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

        return Json.parse(buffer.toString()) ?: throw Error("no response payload")
    }

    fun get(uri: String) = testRequest<Void>("GET", uri)
    fun <T> post(uri: String, payload: T) = testRequest("POST", uri, payload)
    fun <T> put(uri: String, payload: T) = testRequest("PUT", uri, payload)
    fun <T> delete(uri: String, payload: T) = testRequest("DELETE", uri, payload)
}

// Get a list of resources

fun getTestResources(path: String) = File("${System.getProperty("user.dir")}/src/test/resources/$path").listFiles()