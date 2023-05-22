package org.jeudego.pairgoth.util

import org.apache.commons.lang3.StringEscapeUtils
import org.slf4j.LoggerFactory
import org.w3c.dom.*
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.lang.ref.SoftReference
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory


/**
 *
 * Utility class for simplifying parsing of xml documents. Documents are not validated, and
 * loading of external files (xinclude, external entities, DTDs, etc.) are disabled.
 *
 * @author Claude Brisson
 */
object XmlUtils {
    /* several pieces of code were borrowed from the Apache Shindig XmlUtil class.*/
    private val LOGGER = LoggerFactory.getLogger(XmlUtils::class.java)

    /**
     * Handles xml errors so that they're not logged to stderr.
     */
    private val errorHandler: ErrorHandler = object : ErrorHandler {
        @Throws(SAXException::class)
        override fun error(exception: SAXParseException) {
            throw exception
        }

        @Throws(SAXException::class)
        override fun fatalError(exception: SAXParseException) {
            throw exception
        }

        override fun warning(exception: SAXParseException) {
            LOGGER.info("warning during parsing", exception)
        }
    }

    private var canReuseBuilders = false
    private val builderFactory = createDocumentBuilderFactory()
    private fun createDocumentBuilderFactory(): DocumentBuilderFactory {
        val builderFactory = DocumentBuilderFactory.newInstance()
        // Namespace support is required for <os:> elements
        builderFactory.isNamespaceAware = true

        // Disable various insecure and/or expensive options.
        builderFactory.isValidating = false

        // Can't disable doctypes entirely because they're usually harmless. External entity
        // resolution, however, is both expensive and insecure.
        try {
            builderFactory.setAttribute("http://xml.org/sax/features/external-general-entities", false)
        } catch (e: IllegalArgumentException) {
            // Not supported by some very old parsers.
            LOGGER.info("Error parsing external general entities: ", e)
        }
        try {
            builderFactory.setAttribute("http://xml.org/sax/features/external-parameter-entities", false)
        } catch (e: IllegalArgumentException) {
            // Not supported by some very old parsers.
            LOGGER.info("Error parsing external parameter entities: ", e)
        }
        try {
            builderFactory.setAttribute("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        } catch (e: IllegalArgumentException) {
            // Only supported by Apache's XML parsers.
            LOGGER.info("Error parsing external DTD: ", e)
        }
        try {
            builderFactory.setAttribute(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        } catch (e: IllegalArgumentException) {
            // Not supported by older parsers.
            LOGGER.info("Error parsing secure XML: ", e)
        }
        return builderFactory
    }

    private val reusableBuilder: ThreadLocal<DocumentBuilder> = object : ThreadLocal<DocumentBuilder>() {
        override fun initialValue(): DocumentBuilder {
            return try {
                LOGGER.trace("Created a new document builder")
                builderFactory.newDocumentBuilder()
            } catch (e: ParserConfigurationException) {
                throw Error(e)
            }
        }
    }

    init {
        try {
            val builder = builderFactory.newDocumentBuilder()
            builder.reset()
            canReuseBuilders = true
            LOGGER.trace("reusing document builders")
        } catch (e: UnsupportedOperationException) {
            // Only supported by newer parsers (xerces 2.8.x+ for instance).
            canReuseBuilders = false
            LOGGER.trace("not reusing document builders")
        } catch (e: ParserConfigurationException) {
            // Only supported by newer parsers (xerces 2.8.x+ for instance).
            canReuseBuilders = false
            LOGGER.trace("not reusing document builders")
        }
    }

    private val builderPool = LinkedBlockingDeque<SoftReference<DocumentBuilder?>>() // contains only idle builders
    private val maxBuildersCount = 100
    private var currentBuildersCount = 0

    /**
     * Get a document builder
     * @return document builder
     */
    @Synchronized
    private fun getDocumentBuilder(): DocumentBuilder {
        var builder: DocumentBuilder? = null
        if (canReuseBuilders && builderPool.size > 0) {
            builder = builderPool.pollFirst().get()
        }
        if (builder == null) {
            if (!canReuseBuilders || currentBuildersCount < maxBuildersCount) {
                try {
                    builder = builderFactory.newDocumentBuilder()
                    builder.setErrorHandler(errorHandler)
                    ++currentBuildersCount
                } catch (e: Exception) {
                    /* this is a fatal error */
                    throw Error("could not create a new XML DocumentBuilder instance", e)
                }
            } else {
                try {
                    LOGGER.warn(
                        "reached XML DocumentBuilder pool size limit, current thread needs to wait",
                    )
                    builder = builderPool.takeFirst().get()
                } catch (ie: InterruptedException) {
                    LOGGER.warn("caught an InterruptedException while waiting for a DocumentBuilder instance")
                }
            }
        }
        return builder ?: throw Error("could not create a new XML DocumentBuilder instance")
    }

    /**
     * Release the given document builder
     * @param builder document builder
     */
    @Synchronized
    private fun releaseBuilder(builder: DocumentBuilder?) {
        builder!!.reset()
        builderPool.addLast(SoftReference(builder))
    }

    /**
     * Creates an empty document
     */
    fun createDocument(): Document {
        val builder = getDocumentBuilder()
        val doc = builder.newDocument()
        releaseBuilder(builder)
        return doc
    }

    /**
     * Extracts an attribute from a node.
     *
     * @param node target node
     * @param attr attribute name
     * @param def default value
     * @return The value of the attribute, or def
     */
    fun getAttribute(node: Node, attr: String?, def: String?): String? {
        val attrs = node.attributes
        val `val` = attrs.getNamedItem(attr)
        return if (`val` != null) {
            `val`.nodeValue
        } else def
    }

    /**
     * @param node target node
     * @param attr attribute name
     * @return The value of the given attribute, or null if not present.
     */
    fun getAttribute(node: Node, attr: String?): String? {
        return getAttribute(node, attr, null)
    }

    /**
     * Retrieves an attribute as a boolean.
     *
     * @param node target node
     * @param attr attribute name
     * @param def default value
     * @return True if the attribute exists and is not equal to "false"
     * false if equal to "false", and def if not present.
     */
    fun getBoolAttribute(node: Node, attr: String?, def: Boolean): Boolean {
        val value = getAttribute(node, attr) ?: return def
        return java.lang.Boolean.parseBoolean(value)
    }

    /**
     * @param node target node
     * @param attr attribute name
     * @return True if the attribute exists and is not equal to "false"
     * false otherwise.
     */
    fun getBoolAttribute(node: Node, attr: String?): Boolean {
        return getBoolAttribute(node, attr, false)
    }

    /**
     * @param node target node
     * @param attr attribute name
     * @param def default value
     * @return An attribute coerced to an integer.
     */
    fun getIntAttribute(node: Node, attr: String?, def: Int): Int {
        val value = getAttribute(node, attr) ?: return def
        return try {
            value.toInt()
        } catch (e: NumberFormatException) {
            def
        }
    }

    /**
     * @param node target node
     * @param attr attribute name
     * @return An attribute coerced to an integer.
     */
    fun getIntAttribute(node: Node, attr: String?): Int {
        return getIntAttribute(node, attr, 0)
    }

    /**
     * Attempts to parse the input xml into a single element.
     * @param xml xml stream reader
     * @return The document object
     */
    fun parse(xml: Reader): Element {
        val builder = getDocumentBuilder()
        try {
            val doc = builder.parse(InputSource(xml))
            return doc.documentElement
        } finally {
            releaseBuilder(builder)
        }
    }

    /**
     * Attempts to parse the input xml into a single element.
     * @param xml xml string
     * @return The document object
     */
    fun parse(xml: String): Element = parse(StringReader(xml))

    /**
     * Search for nodes using an XPath expression
     * @param xpath XPath expression
     * @param context evaluation context
     * @return org.w3c.NodeList of found nodes
     * @throws XPathExpressionException
     */
    @Throws(XPathExpressionException::class)
    fun search(xpath: String?, context: Node?): NodeList {
        val xp = XPathFactory.newInstance().newXPath()
        val exp = xp.compile(xpath)
        return exp.evaluate(context, XPathConstants.NODESET) as NodeList
    }

    /**
     * Search for nodes using an XPath expression
     * @param xpath XPath expression
     * @param context evaluation context
     * @return List of found nodes
     * @throws XPathExpressionException
     */
    @Throws(XPathExpressionException::class)
    fun getNodes(xpath: String?, context: Node?): List<Node> {
        val ret: MutableList<Node> = ArrayList()
        val lst = search(xpath, context)
        for (i in 0 until lst.length) {
            ret.add(lst.item(i))
        }
        return ret
    }

    /**
     * Search for elements using an XPath expression
     * @param xpath XPath expression
     * @param context evaluation context
     * @return List of found elements
     * @throws XPathExpressionException
     */
    @Throws(XPathExpressionException::class)
    fun getElements(xpath: String?, context: Node?): List<Element> {
        val ret: MutableList<Element> = ArrayList()
        val lst = search(xpath, context)
        for (i in 0 until lst.length) {
            // will throw a ClassCastExpression if Node is not an Element,
            // that's what we want
            ret.add(lst.item(i) as Element)
        }
        return ret
    }

    /**
     *
     * Builds the xpath expression for a node (tries to use id/name nodes when possible to get a unique path)
     * @param n target node
     * @return node xpath
     */
    // (borrow from http://stackoverflow.com/questions/5046174/get-xpath-from-the-org-w3c-dom-node )
    fun nodePath(n: Node): String {

        // declarations
        var parent: Node?
        val hierarchy = Stack<Node>()
        val buffer = StringBuffer("/")

        // push element on stack
        hierarchy.push(n)
        parent = when (n.nodeType) {
            Node.ATTRIBUTE_NODE -> (n as Attr).ownerElement
            Node.COMMENT_NODE, Node.ELEMENT_NODE, Node.DOCUMENT_NODE -> n.parentNode
            else -> throw IllegalStateException("Unexpected Node type" + n.nodeType)
        }
        while (null != parent && parent.nodeType != Node.DOCUMENT_NODE) {
            // push on stack
            hierarchy.push(parent)

            // get parent of parent
            parent = parent.parentNode
        }

        // construct xpath
        var obj: Any? = null
        while (!hierarchy.isEmpty() && null != hierarchy.pop().also { obj = it }) {
            val node = obj as Node?
            var handled = false
            if (node!!.nodeType == Node.ELEMENT_NODE) {
                val e = node as Element?

                // is this the root element?
                if (buffer.length == 1) {
                    // root element - simply append element name
                    buffer.append(node.nodeName)
                } else {
                    // child element - append slash and element name
                    buffer.append("/")
                    buffer.append(node.nodeName)
                    if (node.hasAttributes()) {
                        // see if the element has a name or id attribute
                        if (e!!.hasAttribute("id")) {
                            // id attribute found - use that
                            buffer.append("[@id='" + e.getAttribute("id") + "']")
                            handled = true
                        } else if (e.hasAttribute("name")) {
                            // name attribute found - use that
                            buffer.append("[@name='" + e.getAttribute("name") + "']")
                            handled = true
                        }
                    }
                    if (!handled) {
                        // no known attribute we could use - get sibling index
                        var prev_siblings = 1
                        var prev_sibling = node.previousSibling
                        while (null != prev_sibling) {
                            if (prev_sibling.nodeType == node.nodeType) {
                                if (prev_sibling.nodeName.equals(
                                        node.nodeName, ignoreCase = true
                                    )
                                ) {
                                    prev_siblings++
                                }
                            }
                            prev_sibling = prev_sibling.previousSibling
                        }
                        buffer.append("[$prev_siblings]")
                    }
                }
            } else if (node.nodeType == Node.ATTRIBUTE_NODE) {
                buffer.append("/@")
                buffer.append(node.nodeName)
            }
        }
        // return buffer
        return buffer.toString()
    }

    /**
     * XML Node to string
     * @param node XML node
     * @return XML node string representation
     */
    fun nodeToString(node: Node?, encoding: Charset = Charsets.UTF_8): String {
        val sw = StringWriter()
        try {
            val t = TransformerFactory.newInstance().newTransformer()
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            t.setOutputProperty(OutputKeys.INDENT, "no")
            t.setOutputProperty(OutputKeys.ENCODING, encoding.name())
            t.transform(DOMSource(node), StreamResult(sw))
        } catch (te: TransformerException) {
            LOGGER.error("could not convert XML node to string", te)
        }
        return sw.toString()
    }

    /**
     * XML Node to string
     * @param node XML node
     * @return XML node string representation
     */
    fun nodeToPrettyString(node: Node, encoding: Charset = Charsets.UTF_8): String {
        val sw = StringWriter()
        try {
            val t = TransformerFactory.newInstance().newTransformer()
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            t.setOutputProperty(OutputKeys.INDENT, "yes")
            t.setOutputProperty(OutputKeys.ENCODING, encoding.name())
            t.transform(DOMSource(node), StreamResult(sw))
        } catch (te: TransformerException) {
            LOGGER.error("could not convert XML node to string", te)
        }
        return sw.toString()
    }

    /**
     * Checkes whether the given mime type is an XML format
     * @param mimeType mime type
     * @return `true` if this mime type is an XML format
     */
    fun isXmlMimeType(mimeType: String?): Boolean {
        return mimeType != null &&
                ("text/xml" == mimeType || "application/xml" == mimeType ||
                        mimeType.endsWith("+xml"))
    }
}

// utility extension functions

fun emptyDocument(root: String) = XmlUtils.createDocument().also { it.appendChild(it.createElement(root)) }

fun Node.element(): Element =
    when (this) {
        is Element -> this
        is Document -> documentElement
        else -> throw Error("invalid xml node")
    }

fun Element.children(): List<Element> {
    val ret = mutableListOf<Element>()
    for (i in 0..childNodes.length) {
        val child = childNodes[i]
        if (child is Element) ret.add(child)
    }
    return ret
}

fun Node.document(): Document = ownerDocument ?: this as Document

fun Element.childOrNull(key: String): Element? = children().firstOrNull { it.tagName == key }

fun Element.child(key: String): Element = childOrNull(key) ?: addChild(key)

fun Node.addChild(tag: String): Element = appendChild(document().createElement(tag)) as Element

fun Node.value(): String? = textContent.let { if (it.isEmpty()) null else it }

fun Node.attr(key: String, def: String? = null) = attributes.getNamedItem(key)?.nodeValue ?: def

fun Element.setAttr(key: String, value: Any) {
    setAttribute(key, value.toString())
}

fun Node.boolAttr(key: String, def: Boolean? = null) = attr(key)?.toBoolean() ?: def

fun Node.intAttr(key: String, def: Int? = null) = attr(key)?.toInt() ?: def

fun Node.longAttr(key: String, def: Long? = null) = attr(key)?.toLong() ?: def

fun Node.doubleAttr(key: String, def: Double? = null) = attr(key)?.toDouble() ?: def

fun Node.path() = XmlUtils.nodePath(this)

fun Node.find(xpath: String): NodeList {
    return XPathFactory.newInstance().newXPath().compile(xpath).evaluate(this, XPathConstants.NODESET) as NodeList
}

fun Node.print(encoding: Charset = Charsets.UTF_8) : String {
    trimTextNodes()
    return XmlUtils.nodeToString(this, encoding)
    /* previous implementation, without charset
    val domImplLS = document().implementation as DOMImplementationLS
    val serializer = domImplLS.createLSSerializer()
    return serializer.writeToString(this)
     */
}

fun Node.trimTextNodes() {
    val children: NodeList = getChildNodes()
    for (i in 0 until children.length) {
        val child = children.item(i)
        if (child.nodeType == Node.TEXT_NODE) {
            child.textContent = child.textContent.trim()
        }
        else child.trimTextNodes()
    }
}

fun Node.prettyPrint(encoding: Charset = Charsets.UTF_8): String {
    trimTextNodes()
    return XmlUtils.nodeToPrettyString(this, encoding)
}


// node list iteration and random access

class NodeListIterator(private val lst: NodeList): Iterator<Node> {
    private var nextPos = 0
    override fun hasNext() = nextPos < lst.length
    override fun next() = lst.item(nextPos++)
}

operator fun NodeList.iterator() = NodeListIterator(this)

operator fun NodeList.get(i: Int) = item(i)

// Encode XML entities in a string
fun String.encodeXmlEntities() = StringEscapeUtils.escapeXml(this)

