package org.jeudego.pairgoth.web

import com.republicate.kson.Json
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class LogoutServlet: HttpServlet() {

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        req.session.removeAttribute("logged")
        val ret = Json.Object("status" to "ok")
        resp.contentType = "application/json"
        resp.writer.println(ret.toString())
    }
}
