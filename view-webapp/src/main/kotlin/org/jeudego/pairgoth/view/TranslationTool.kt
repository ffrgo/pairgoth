package org.jeudego.pairgoth.view

import org.apache.velocity.tools.config.ValidScope
import org.jeudego.pairgoth.util.Translator

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

    companion object {
        val translator = ThreadLocal<Translator>()
    }
}