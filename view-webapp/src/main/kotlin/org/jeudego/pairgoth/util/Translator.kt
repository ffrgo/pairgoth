package org.jeudego.pairgoth.util

import org.apache.commons.lang3.StringEscapeUtils
import org.apache.commons.lang3.StringUtils
import org.apache.velocity.Template
import org.apache.velocity.runtime.parser.node.ASTText
import org.apache.velocity.runtime.parser.node.SimpleNode
import org.jeudego.pairgoth.web.WebappManager
import org.slf4j.LoggerFactory
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.regex.Pattern
import kotlin.io.path.readLines
import kotlin.io.path.useDirectoryEntries

class Translator private constructor(private val iso: String) {

    fun translate(enText: String) = translations[iso]?.get(enText) ?: enText.also {
        reportMissingTranslation(enText)
    }

    fun translate(uri: String, template: Template): Template? {
        if (iso == "en") return template
        val key = Pair(uri, iso)
        var translated = translatedTemplates[key]
        if (translated != null && translated.lastModified < template.lastModified) {
            translatedTemplates.remove(key)
            translated = null
        }
        if (translated == null) {
            synchronized(translatedTemplates) {
                translated = translatedTemplates[key]
                if (translated == null) {
                    translated = template.clone() as Template
                    val data: SimpleNode = translated!!.data as SimpleNode
                    translateNode(data)
                    translatedTemplates[key] = translated!!
                }
            }
        }
        return translated
    }

    private fun translateNode(node: SimpleNode, ignoringInput: String? = null): String? {
        var ignoring = ignoringInput
        if (node is ASTText) translateFragments(node.text, ignoring).let {
            node.text = it.first
            ignoring = it.second
        }
        else for (i in 0 until node.jjtGetNumChildren()) {
            ignoring = translateNode(node.jjtGetChild(i) as SimpleNode, ignoring)
        }
        return ignoring
    }

    private fun translateFragments(text: String, ignoringInput: String?): Pair<String, String?> {
        var ignoring = ignoringInput
        val ignoreMap = buildIgnoreMap(text, ignoring).also {
            ignoring = it.second
        }.first
        val sw = StringWriter()
        val output = PrintWriter(sw)
        val matcher = textExtractor.matcher(text)
        var pos = 0
        while (matcher.find(pos)) {
            val start = matcher.start()
            val end = matcher.end()
            if (start > pos) output.print(text.substring(pos, start))
            val ignore: Boolean = ignoreMap.floorEntry(start).value
            if (ignore) output.print(text.substring(start, end)) else {
                var group = 1
                var groupStart = matcher.start(group)
                while (groupStart == -1 && group < matcher.groupCount()) groupStart = matcher.start(++group)
                if (groupStart == -1) throw RuntimeException("unexpected case")
                if (groupStart > start) output.print(text.substring(start, groupStart))
                val capture = matcher.group(group)

                // CB TODO - unescape and escape steps removed, because it breaks text blocks containing unescaped quotes.
                // See how it impacts the remaining.

                var token: String = capture // StringEscapeUtils.unescapeHtml4(capture)
                if (StringUtils.containsOnly(token, "\r\n\t -;:.'\"/<>\u00A00123456789€[]!")) output.print(capture) else {
                    token = normalize(token)
                    token = translate(token)
                    output.print(token) // (StringEscapeUtils.escapeHtml4(token))
                }
                val groupEnd = matcher.end(group)
                if (groupEnd < end) output.print(text.substring(groupEnd, end))
            }
            pos = end
        }
        if (pos < text.length) output.print(text.substring(pos))
        return Pair(sw.toString(), ignoring)
    }

    private fun normalize(str: String): String {
        return str.replace(Regex("\\s+"), " ")
    }

    private fun buildIgnoreMap(text: String, ignoringInput: String?): Pair<NavigableMap<Int, Boolean>, String?> {
        val map: NavigableMap<Int, Boolean> = TreeMap()
        var ignoring = ignoringInput
        var pos = 0
        map[0] = (ignoring != null)
        while (pos < text.length) {
            if (ignoring == null) {
                val nextIgnore = ignoredTags.mapNotNull { tag ->
                    Regex("<($tag)(?:>|\\s)").find(text)
                }.minByOrNull {
                    it.range.first
                }
                if (nextIgnore == null) pos = text.length
                else {
                    ignoring = nextIgnore.groupValues[1]
                    pos += ignoring.length + 2
                }
            } else {
                val closingTag = text.indexOf("</$ignoring>")
                if (closingTag == -1) pos = text.length
                else {
                    pos += ignoring.length + 3
                    ignoring = null
                }
            }
        }
        return Pair(map, ignoring)
    }

    private var ASTText.text: String
        get() = textAccessor[this] as String
        set(value: String) { textAccessor[this] = value }

    private val saveMissingTranslations = System.getProperty("pairgoth.webapp.env") == "dev"
    private val missingTranslations: MutableSet<String> = ConcurrentSkipListSet()

    private fun reportMissingTranslation(enText: String) {
        logger.debug("missing translation towards {}: {}", iso, enText)
        if (saveMissingTranslations) missingTranslations.add(enText)
    }

    companion object {
        private val textAccessor = ASTText::class.java.getDeclaredField("ctext").apply { isAccessible = true }
        private val logger = LoggerFactory.getLogger("translation")
        private val translatedTemplates: MutableMap<Pair<String, String>, Template> = ConcurrentHashMap<Pair<String, String>, Template>()
        private val textExtractor = Pattern.compile(
            "<[^>]+\\s(?:placeholder|title)=\"(?<placeholder>[^\"]*)\"[^>]*>|(?<=>)(?:[ \\r\\n\\t\u00A0/–-]|&nbsp;|&dash;)*(?<text>[^<>]+?)(?:[ \\r\\n\\t\u00A0/–-]|&nbsp;|&dash;)*(?=<|$)|(?<=>|^)(?:[ \\r\\n\\t\u00A0/–-]|&nbsp;|&dash;)*(?<text2>[^<>]+?)(?:[ \\r\\n\\t\u00A0/–-]|&nbsp;|&dash;)*(?=<)|^(?:[ \\r\\n\\t /–-]|&nbsp;|&dash;)*(?<text3>[^<>]+?)(?:[ \\r\\n\\t /–-]|&nbsp;|&dash;)*(?=$)",
            Pattern.DOTALL
        )
        private val ignoredTags = setOf("head", "script", "style")

        private val translations = Path.of(WebappManager.context.getRealPath("WEB-INF/translations")).useDirectoryEntries("??") { entries ->
            entries.map { file ->
                Pair(
                    file.fileName.toString(),
                    file.readLines(StandardCharsets.UTF_8).filter {
                        it.isNotEmpty() && it.contains('\t') && !it.startsWith('#')
                    }.map {
                        Pair(it.substringBefore('\t'), it.substringAfter('\t'))
                    }.toMap()
                )
            }.toMap()
        }

        private val translators = ConcurrentHashMap<String, Translator>()
        fun getTranslator(iso: String) = translators.getOrPut(iso) { Translator(iso) }

        val providedLanguages = setOf("en", "fr", "kr")
        const val defaultLanguage = "en"
        const val defaultLocale = "en"

        internal fun notifyExiting() {
            translators.values.filter {
                it.saveMissingTranslations && it.missingTranslations.isNotEmpty()
            }.forEach {
                val missing = File("${it.iso}.missing")
                logger.info("Saving missing translations for ${it.iso} to ${missing.canonicalPath}")
                missing.printWriter().use { out ->

                    out.println(it.missingTranslations.map { "${it}\t" }.joinToString("\n"))
                }
            }
        }
    }
}
