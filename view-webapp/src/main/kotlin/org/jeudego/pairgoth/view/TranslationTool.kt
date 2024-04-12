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

    val defaultCountry = Translator.providedLanguages.associate { iso ->
        when (iso) {
            "en" -> "gb"
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

    fun datepickerLocale(language: String, locale: String) =
        if (datepickerLocales.contains(locale)) locale
        else if (locale == "en") "en-GB"
        else language

    companion object {
        val datepickerLocales = setOf("ar-DZ", "ar", "ar-TN", "az", "bg", "bm", "bn", "br", "bs", "ca", "cs", "cy", "da", "de", "el", "en-AU", "en-CA", "en-GB", "en-IE", "en-NZ", "en-ZA", "eo", "es", "et", "eu", "fa", "fi", "fo", "fr-CH", "fr", "gl", "he", "hi", "hr", "hu", "hy", "id", "is", "it-CH", "it", "ja", "ka", "kk", "km", "ko", "lt", "lv", "me", "mk", "mn", "mr", "ms", "nl-BE", "nl", "no", "oc", "pl", "pt-BR", "pt", "ro", "ru", "si", "sk", "sl", "sq", "sr", "sr-latn", "sv", "sw", "ta", "tg", "th", "tk", "tr", "uk", "uz-cyrl", "uz-latn", "vi", "zh-CN", "zh-TW")
        val translator = ThreadLocal<Translator>()
    }
}