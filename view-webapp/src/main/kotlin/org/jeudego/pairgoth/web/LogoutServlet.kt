package org.jeudego.pairgoth.web

import com.republicate.kson.Json
import okhttp3.Request
import org.jeudego.pairgoth.view.ApiTool
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class LogoutServlet: HttpServlet() {

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        AuthFilter.clearApiToken(req)
        req.session.removeAttribute(AuthFilter.SESSION_KEY_USER)
        req.session.removeAttribute(AuthFilter.SESSION_KEY_API_TOKEN)
        val ret = Json.Object("status" to "ok")
        resp.contentType = "application/json"
        resp.writer.println(ret.toString())
    }
}
