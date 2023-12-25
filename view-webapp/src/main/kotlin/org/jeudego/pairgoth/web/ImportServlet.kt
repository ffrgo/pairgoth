package org.jeudego.pairgoth.web

import org.jeudego.pairgoth.util.Upload
import org.jeudego.pairgoth.view.ApiTool
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class ImportServlet: HttpServlet() {

    private val api by lazy { ApiTool() }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val uploads = Upload.handleFileUpload(req)
        if (uploads.size != 1) resp.sendError(HttpServletResponse.SC_BAD_REQUEST)
        else {
            val xml = uploads.first().second.toString(StandardCharsets.UTF_8)
            val apiResp = api.post("tour", xml)
            if (apiResp.isObject && apiResp.asObject().getBoolean("success") == true) {
                resp.contentType = "application/json; charset=UTF-8"
                resp.writer.println(apiResp.toString())
            } else {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            }
        }
    }
}
