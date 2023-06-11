package org.jeudego.pairgoth.view

import org.apache.velocity.tools.config.ValidScope
import org.jeudego.pairgoth.util.Translator

@ValidScope("request")
class TranslationTool {

    fun translate(enText: String): String {
        return translator.get().translate(enText)
    }

    companion object {
        val translator = ThreadLocal<Translator>()
    }
}