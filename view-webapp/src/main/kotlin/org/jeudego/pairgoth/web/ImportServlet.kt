package org.jeudego.pairgoth.web

import com.republicate.kson.Json
import org.jeudego.pairgoth.util.Upload
import org.jeudego.pairgoth.view.ApiTool
import org.jeudego.pairgoth.view.PairgothTool.Companion.EXAMPLES_DIRECTORY
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ImportServlet: HttpServlet() {

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val api = ApiTool().apply { setRequest(req) }
        val example = req.getParameter("example") as String?
        if (example != null) uploadExample(req, resp)
        else {
            val uploads = Upload.handleFileUpload(req)
            if (uploads.size != 1) resp.sendError(HttpServletResponse.SC_BAD_REQUEST)
            else {
                val name = uploads.first().first
                val bytes = uploads.first().second
                var apiResp: Json? = null
                if (name.endsWith(".tour")) {
                    val json = Json.parse(bytes.toString(StandardCharsets.UTF_8))
                    if (json == null || !json.isObject) {
                        resp.sendError(HttpServletResponse.SC_BAD_REQUEST)
                    } else {
                        val filtered = Json.MutableObject(json.asObject())
                        filtered.remove("id")
                        apiResp = api.post("tour", filtered)
                    }
                }
                else { // xml ?
                    val xml = bytes.toString(StandardCharsets.UTF_8)
                    apiResp = api.post("tour", xml)
                }
                if (apiResp != null) {
                    if (apiResp.isObject && apiResp.asObject().getBoolean("success") == true) {
                        resp.contentType = "application/json; charset=UTF-8"
                        resp.writer.println(apiResp.toString())
                    } else {
                        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                    }
                }
            }
        }
    }

    private fun uploadExample(request: HttpServletRequest, response: HttpServletResponse) {
        val name = request.getParameter("example")
        val classLoader = ImportServlet::class.java.classLoader
        val example = classLoader.getResource("$EXAMPLES_DIRECTORY/$name.tour")?.readText()?.let {
            Json.parse(it)
        }
        if (example == null || !example.isObject) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST)
        } else {
            val filtered = Json.MutableObject(example.asObject())
            filtered.remove("id")
            val api = ApiTool().apply { setRequest(request) }
            val apiResp = api.post("tour", filtered)
            if (apiResp.isObject && apiResp.asObject().getBoolean("success") == true) {
                response.contentType = "application/json; charset=UTF-8"
                response.writer.println(apiResp.toString())
            } else {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            }
        }
    }
}
