package org.jeudego.pairgoth.view

import org.apache.velocity.Template
import org.apache.velocity.runtime.directive.Parse

class TranslateDirective : Parse() {
    override fun getName(): String {
        return "translate"
    }

    override fun getTemplate(path: String, encoding: String): Template? {
        val template = super.getTemplate(path, encoding)
        val translator = TranslationTool.translator.get()
            ?: throw RuntimeException("no current active translator")
        return translator.translate(path, template)
    }
}