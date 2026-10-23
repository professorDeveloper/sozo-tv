package com.saikou.sozo_tv.engine.player

import org.w3c.dom.Element
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

/** Resolves each DASH hierarchy level before replacing URLs; template tokens stay visible. */
object DashManifestRewriter {
    fun rewrite(xml: String, manifestUrl: String, proxy: (String) -> String): String {
        require(!Regex("<!DOCTYPE|<!ENTITY", RegexOption.IGNORE_CASE).containsMatchIn(xml)) { "External XML declarations are not supported" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { isXIncludeAware = false }
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        fun resolve(base: String, path: String): String {
            // URI rejects the printf escape in $Number%05d$; keep tokens out while resolving.
            val tokens = mutableListOf<String>()
            val protected = Regex("\\$[^$]+\\$").replace(path) {
                tokens.add(it.value); "SOZODASHTOKEN${tokens.lastIndex}END"
            }
            var url = StreamRequestRules.resolve(base, protected)
            tokens.forEachIndexed { i, token -> url = url.replace("SOZODASHTOKEN${i}END", token) }
            return url
        }
        fun walk(node: Element, inherited: String, inheritedTemplate: Element? = null) {
            val ownTemplates = (0 until node.childNodes.length).mapNotNull { node.childNodes.item(it) as? Element }
                .filter { (it.localName ?: it.tagName) == "SegmentTemplate" }
            if ((node.localName ?: node.tagName) == "Representation" && ownTemplates.isEmpty() && inheritedTemplate != null)
                node.appendChild(inheritedTemplate.cloneNode(true))
            val template = ownTemplates.firstOrNull()?.cloneNode(true) as? Element ?: inheritedTemplate
            val children = (0 until node.childNodes.length).mapNotNull { node.childNodes.item(it) as? Element }
            val bases = children.filter { it.localName == "BaseURL" || it.tagName == "BaseURL" }
            val base = bases.firstOrNull()?.textContent?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { resolve(inherited, it) } ?: inherited
            val hasSegments = node.getElementsByTagNameNS("*", "SegmentTemplate").length > 0 ||
                node.getElementsByTagNameNS("*", "SegmentList").length > 0
            if (hasSegments) bases.forEach { node.removeChild(it) }
            else bases.forEach { it.textContent = proxy(resolve(inherited, it.textContent.trim())) }
            val attrs = when (node.localName ?: node.tagName) {
                "SegmentTemplate" -> listOf("media", "initialization", "index", "bitstreamSwitching")
                "SegmentURL" -> listOf("media", "index")
                "Initialization", "RepresentationIndex" -> listOf("sourceURL")
                else -> emptyList()
            }
            attrs.forEach { attr -> if (node.hasAttribute(attr)) node.setAttribute(attr, proxy(resolve(base, node.getAttribute(attr)))) }
            children.filterNot { it in bases }.forEach { walk(it, base, template) }
        }
        walk(doc.documentElement, manifestUrl)
        return StringWriter().also { TransformerFactory.newInstance().newTransformer()
            .transform(DOMSource(doc), StreamResult(it)) }.toString()
    }
}
