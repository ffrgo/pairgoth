package org.jeudego.pairgoth.web

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.toc.TocExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import org.apache.velocity.Template
import org.apache.velocity.context.Context
import org.apache.velocity.tools.view.VelocityViewServlet
import org.slf4j.LoggerFactory
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class MarkdownServlet : VelocityViewServlet() {

    private val mdParser: Parser
    private val mdRenderer: HtmlRenderer

    init {
        val options = MutableDataSet()
        options.set(Parser.EXTENSIONS, listOf(
            TablesExtension.create(),
            AnchorLinkExtension.create(),
            TocExtension.create()
        ))
        options.set(AnchorLinkExtension.ANCHORLINKS_WRAP_TEXT, false)
        options.set(TocExtension.DIV_CLASS, "table-of-contents")
        options.set(TocExtension.LEVELS, 2 or 4) // h2 + h3
        mdParser = Parser.builder(options).build()
        mdRenderer = HtmlRenderer.builder(options).build()
    }

    override fun getTemplate(request: HttpServletRequest, response: HttpServletResponse?): Template {
        return getTemplate(DOC_LAYOUT)
    }

    override fun fillContext(context: Context, request: HttpServletRequest) {
        super.fillContext(context, request)
        val uri = request.requestURI
        val page = uri.removePrefix("/doc/").ifEmpty { null }

        if (page == null || page.contains("..") || page.contains("/")) {
            context.put("docContent", "")
            return
        }

        val lang = request.getAttribute("lang") as? String ?: "en"

        // try localized version first, then fallback to default
        val markdown = readResource("doc/$page.$lang.md") ?: readResource("doc/$page.md")

        if (markdown == null) {
            context.put("docContent", "<p>Page not found.</p>")
            return
        }

        val document = mdParser.parse(markdown)
        context.put("docContent", mdRenderer.render(document))
    }

    private fun readResource(path: String): String? {
        return javaClass.classLoader.getResourceAsStream(path)?.bufferedReader()?.readText()
    }

    companion object {
        private val logger = LoggerFactory.getLogger("doc")
        private const val DOC_LAYOUT = "/WEB-INF/layouts/doc.html"
    }
}
