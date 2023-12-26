package org.jeudego.pairgoth.web

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
        val auth = WebappManager.getProperty("auth") ?: throw Error("authentication not configured")

        if (auth == "none" || whitelist.contains(uri) || uri.contains(Regex("\\.(?!html)")) || session?.getAttribute("logged") != null) {
            chain.doFilter(req, resp)
        } else {
            // TODO - configure if unauth requests are redirected and/or forwarded
            // TODO - protection against brute force attacks
            if (uri == "/index") {
                request.getRequestDispatcher("/index-ffg").forward(req, resp)
            } else {
                response.sendRedirect("/login")
            }
        }
    }
    companion object {
        private val whitelist = setOf(
            "/index-ffg",
            "/login"
        )
    }
}