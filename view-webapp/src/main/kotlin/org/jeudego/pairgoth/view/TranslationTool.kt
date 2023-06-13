package org.jeudego.pairgoth.view

import org.apache.velocity.tools.config.ValidScope
import org.jeudego.pairgoth.util.Translator
import javax.servlet.http.HttpServletRequest

@ValidScope("application")
class TranslationTool {

    fun translate(enText: String): String {
        return translator.get().translate(enText)
    }

    val languages = Translator.providedLanguages

    val flags = Translator.providedLanguages.associate { iso ->
        when (iso) {
            "en" -> "gb uk"
            "zh" -> "cn"
            else -> iso
        }.let { Pair(iso, it) }
    }

    fun url(request: HttpServletRequest, lang: String): String {
        val out = StringBuilder()
        out.append(request.requestURL.replaceFirst(Regex("://"), "://$lang/"))
        val qs: String? = request.queryString
        if (!qs.isNullOrEmpty()) out.append('?').append(qs)
        return out.toString()
    }

    companion object {
        val translator = ThreadLocal<Translator>()
    }
}