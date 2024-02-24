package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

class TokenHandler: ApiHandler {
    companion object {
        const val AUTH_KEY = "pairgoth-auth"
        const val CHALLENGE_KEY = "pairgoth-challenge"
    }
    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val auth = request.session.getAttribute(AUTH_KEY) as String?
        if (auth == null) {
            failed(request.session, response)
            return null
        } else {
            return Json.Object(
                "success" to true,
                "auth" to auth
            )
        }
    }
    override fun post(request: HttpServletRequest, response: HttpServletResponse): Json {
        val auth = getObjectPayload(request)
        val answer = auth.getString("answer")
        if (answer == null) {
            failed(request.session, response)
        } else {


        }
    }

    private fun failed(session: HttpSession, response: HttpServletResponse) {
        val challenge = AuthChallenge()
        session.setAttribute(CHALLENGE_KEY, challenge)
        response.addHeader("WWW-Authenticate", challenge.value)
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.writer.println(Json.Object("status" to "failed"))
    }
}
