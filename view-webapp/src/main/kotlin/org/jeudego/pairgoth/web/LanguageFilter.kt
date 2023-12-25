package org.jeudego.pairgoth.web

import org.jeudego.pairgoth.util.Translator
import org.jeudego.pairgoth.util.Translator.Companion.defaultLanguage
import org.jeudego.pairgoth.util.Translator.Companion.defaultLocale
import org.jeudego.pairgoth.util.Translator.Companion.providedLanguages
import org.jeudego.pairgoth.view.TranslationTool
import java.util.*
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

        if (uri.startsWith("/api/")) {
            chain.doFilter(request, response)
            return
        }

        val match = langPattern.matchEntire(uri)
        val askedLanguage = match?.groupValues?.get(1)
        val target = match?.groupValues?.get(2) ?: uri

        val reqLang = request.getAttribute("lang") as String?
        if (reqLang != null) {
            // this is a forwarded request, language and locale should already have been set
            TranslationTool.translator.set(Translator.getTranslator(reqLang))
            chain.doFilter(request, response)
        } else {
            val (preferredLanguage, preferredLocale) = parseLanguageHeader(request)
            if (askedLanguage != null && providedLanguages.contains(askedLanguage)) {
                // the target URI contains a language we provide
                request.setAttribute("lang", askedLanguage)
                request.setAttribute("loc",
                    if (askedLanguage == preferredLanguage) preferredLocale
                    else askedLanguage
                )
                request.setAttribute("target", target)
                filterConfig!!.servletContext.getRequestDispatcher(target).forward(request, response)
            } else {
                // the request must be redirected
                val destination = if (askedLanguage != null) target else uri
                val query = request.queryString ?: ""
                response.sendRedirect("/${preferredLanguage}${destination}${if (query.isEmpty()) "" else "?$query"}")
            }
        }
    }

    /**
     * Returns Pair(language, locale)
     */
    private fun parseLanguageHeader(request: HttpServletRequest): Pair<String, String> {
        langHeaderParser.findAll(request.getHeader("Accept-Language") ?: "").filter {
            providedLanguages.contains(it.groupValues[1])
        }.sortedByDescending {
            it.groupValues[3].toDoubleOrNull() ?: 1.0
        }.firstOrNull()?.let { match ->
            val lang = match.groupValues[1].let { if (it == "*") defaultLanguage else it }
            val variant = match.groupValues.getOrNull(2)?.lowercase(Locale.ROOT)
            // by convention, the variant is only kept if different from the language (fr-FR => fr)
            val locale = variant?.let { if (lang == variant) lang else "$lang-${variant.uppercase(Locale.ROOT)}" } ?: lang
            return Pair(lang, locale)
        }
        return Pair(defaultLanguage, defaultLanguage)
    }

    override fun destroy() {}

    companion object {
        private val langPattern = Regex("/([a-z]{2})(/.+)")
        private val langHeaderParser = Regex("(?:\\b(\\*|[a-z]{2})(?:(?:_|-)(\\w+))?)(?:;q=([0-9.]+))?")
    }
}
