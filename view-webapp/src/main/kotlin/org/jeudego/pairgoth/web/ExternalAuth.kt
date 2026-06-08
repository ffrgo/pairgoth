package org.jeudego.pairgoth.web

import com.republicate.kson.Json
import org.jeudego.pairgoth.web.AuthFilter.Companion.SESSION_KEY_USER
import org.jeudego.pairgoth.web.AuthFilter.Companion.getBearer
import org.jeudego.pairgoth.web.AuthFilter.Companion.handleSuccessfulLogin
import org.jeudego.pairgoth.web.AuthFilter.Companion.whitelisted
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.servlet.FilterChain
import javax.servlet.RequestDispatcher
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * `auth = external`: operators authenticate on the fronting site (EGC), which hands them over with a
 * short-lived one-time ticket on `/sso`; sessionless requests bounce to its login page carrying the
 * original target as `goto`. `/sso` is only honoured in this mode (the filter routes here, not the
 * static whitelist).
 */
object ExternalAuth {

    fun handle(req: ServletRequest, resp: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val response = resp as HttpServletResponse
        val uri = request.requestURI
        val loginUrl = WebappManager.getMandatoryProperty("auth.external.login_url")
        when {
            uri.replace(Regex("^/../"), "/") == "/sso" -> handover(request, response, loginUrl)
            whitelisted(uri)
                || request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) != null
                || request.getSession(false)?.getAttribute(SESSION_KEY_USER) != null ->
                chain.doFilter(req, resp)
            else -> {
                val target = uri + (request.queryString?.let { "?$it" } ?: "")
                response.sendRedirect("$loginUrl?goto=${URLEncoder.encode(target, StandardCharsets.UTF_8)}")
            }
        }
    }

    /** Consume an SSO ticket: JSON callers (server-to-server) get an opaque api bearer; browsers a redirect. */
    private fun handover(request: HttpServletRequest, response: HttpServletResponse, loginUrl: String) {
        val json = request.getHeader("Accept")?.contains("application/json") == true
        val email = request.getParameter("ticket")?.let { SsoTicket.validate(it) }
        if (email == null) {
            if (json) {
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.contentType = "application/json; charset=UTF-8"
                response.writer.println(Json.Object("error" to "invalid ticket"))
            } else response.sendRedirect(loginUrl)
            return
        }
        handleSuccessfulLogin(request, Json.Object("email" to email))
        if (json) {
            // the session's opaque api bearer, so the internal auth.shared_secret never leaves pairgoth
            response.contentType = "application/json; charset=UTF-8"
            response.writer.println(Json.Object("bearer" to getBearer(request)))
        } else response.sendRedirect(SsoTicket.safeGoto(request.getParameter("goto")))
    }
}
