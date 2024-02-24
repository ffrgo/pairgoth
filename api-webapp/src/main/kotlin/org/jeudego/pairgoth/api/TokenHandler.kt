package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.util.AESCryptograph
import org.jeudego.pairgoth.util.Cryptograph
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

class TokenHandler: ApiHandler {
    companion object {
        const val AUTH_KEY = "pairgoth-auth"
        const val CHALLENGE_KEY = "pairgoth-challenge"
        private val cryptograph: Cryptograph = AESCryptograph().apply {
            init("78659783ed8ccc0e")
        }
    }
    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val auth = request.session.getAttribute(AUTH_KEY) as String?
        if (auth == null) {
            failed(request, response)
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
        val challenge = request.session.getAttribute(CHALLENGE_KEY) as AuthChallenge?
        if (answer == null || challenge == null) {
            failed(request, response)
        } else {
            val parts = cryptograph.webDecrypt(answer).split(":")
            if (parts.size != 2)
        }
    }

    override fun delete(request: HttpServletRequest, response: HttpServletResponse): Json {
        request.session.removeAttribute(AUTH_KEY)
        return Json.Object("success" to true)
    }

    private fun failed(request: HttpServletRequest, response: HttpServletResponse) {
        val session = request.session
        val challenge = AuthChallenge()
        session.setAttribute(CHALLENGE_KEY, challenge)
        response.addHeader("WWW-Authenticate", challenge.value)
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.writer.println(Json.Object("status" to "failed", "message" to "unauthorized"))
    }
}
