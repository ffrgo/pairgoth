package org.jeudego.pairgoth.web

import org.slf4j.LoggerFactory
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.RequestDispatcher
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class DispatchingFilter : Filter {

    protected val defaultRequestDispatcher: RequestDispatcher by lazy {
        filterConfig.servletContext.getNamedDispatcher("default")
    }

    private lateinit var filterConfig: FilterConfig

    override fun init(filterConfig: FilterConfig) {
        this.filterConfig = filterConfig
    }

    override fun destroy() {}

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest
        val resp = response as HttpServletResponse
        val uri = req.requestURI
        when {
            uri.endsWith('/') -> response.sendRedirect("${uri}index")
            uri.contains('.') ->
                if (uri.endsWith(".html") || uri.contains(".inc.")) resp.sendError(404)
                else defaultRequestDispatcher.forward(request, response)
            else -> chain.doFilter(request, response)
        }
    }
}