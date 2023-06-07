package org.jeudego.pairgoth.web

import org.jeudego.pairgoth.util.Translator
import org.jeudego.pairgoth.util.Translator.Companion.defaultLanguage
import org.jeudego.pairgoth.util.Translator.Companion.getTranslator
import org.jeudego.pairgoth.util.Translator.Companion.providedLanguages
import org.jeudego.pairgoth.view.TranslationTool
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class LanguageFilter : Filter {
    private var filterConfig: FilterConfig? = null

    override fun init(filterConfig: FilterConfig) {
        this.filterConfig = filterConfig
    }

    override fun doFilter(req: ServletRequest, resp: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val response = resp as HttpServletResponse

        val uri = request.requestURI
        val match = langPattern.matchEntire(uri)
        val lang = match?.groupValues?.get(1)
        val target = match?.groupValues?.get(2) ?: uri

        if (lang != null && providedLanguages.contains(lang)) {
            // the target URI contains a language we provide
            request.setAttribute("lang", lang)
            request.setAttribute("target", target)
            TranslationTool.translator.set(Translator.getTranslator(lang))
            chain.doFilter(request, response)
        } else {
            // the request must be redirected
            val preferredLanguage = getPreferredLanguage(request)
            val destination = if (lang != null) target else uri
            response.sendRedirect("${preferredLanguage}${destination}")
        }
    }

    private fun getPreferredLanguage(request: HttpServletRequest): String {
        return (request.session.getAttribute("lang") as String?) ?:
        ( langHeaderParser.findAll(request.getHeader("Accept-Language") ?: "").filter {
            providedLanguages.contains(it.groupValues[1])
        }.sortedByDescending {
            it.groupValues[2].toDoubleOrNull() ?: 1.0
        }.firstOrNull()?.let {
            it.groupValues[1]
        } ?: defaultLanguage ).also {
            request.session.setAttribute("lang", it)
        }
    }

    override fun destroy() {}

    companion object {
        private val langPattern = Regex("/([a-z]{2})(/.+)")
        private val langHeaderParser = Regex("(?:\\b(\\*|[a-z]{2})(?:_\\w+)?)(?:;q=([0-9.]+))?")
    }
}
