package org.jeudego.pairgoth.web

import org.jeudego.pairgoth.oauth.OauthHelperFactory
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.RequestDispatcher
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

class AuthFilter: Filter {

    private lateinit var filterConfig: FilterConfig

    protected val defaultRequestDispatcher: RequestDispatcher by lazy {
        filterConfig.servletContext.getNamedDispatcher("default")
    }

    override fun init(filterConfig: FilterConfig) {
        this.filterConfig = filterConfig
    }

    override fun doFilter(req: ServletRequest, resp: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val response = resp as HttpServletResponse
        val uri = request.requestURI
        val session: HttpSession? = request.getSession(false)
        val auth = WebappManager.properties.getProperty("auth") ?: throw Error("authentication not configured")
        val forwarded = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI) != null

        if (auth == "oauth" && uri.startsWith("/oauth/")) {
            val provider = uri.substring("/oauth/".length)
            val helper = OauthHelperFactory.getHelper(provider)
            val accessToken = helper.getAccessToken(request.session.id, request.getParameter("code") ?: "")
            val user = helper.getUserInfos(accessToken)
            request.session.setAttribute("logged", user)
            response.sendRedirect("/index")
            return
        }

        if (auth == "none" || whitelisted(uri) || forwarded || session?.getAttribute("logged") != null) {
            chain.doFilter(req, resp)
        } else {
            // TODO - protection against brute force attacks
            if (uri.endsWith("/index")) {
                response.sendRedirect("/index-ffg")
            } else {
                response.sendRedirect("/login")
            }
        }
    }

    companion object {
        private val whitelist = setOf(
            "/login",
            "/index-ffg",
            "/api/login",
            "api/logout"
        )

        fun whitelisted(uri: String): Boolean {
            if (uri.contains(Regex("\\.(?!html)"))) return true
            val nolangUri = uri.replace(Regex("^/../"), "/")
            return whitelist.contains(nolangUri)
        }
    }
}
