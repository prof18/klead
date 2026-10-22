package com.prof18.klead.internal

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.dom.isDangerousUrl
import com.prof18.klead.internal.media.TrustedEmbeds

// Strips active and dangerous markup from the detected content before any other processing:
// scripts (except math sources), style/embed/frame/link elements, http-equiv meta tags, untrusted
// iframes, event-handler attributes, srcdoc, and dangerous URLs in link-bearing attributes.
internal object ContentSanitizer {
    fun stripUnsafe(content: Element) {
        content.select("script").filterNot { script ->
            script.attr("type").contains("math/tex", ignoreCase = true)
        }.forEach { it.remove() }
        // <link> would load third-party stylesheets or imports into the reader, and <meta http-equiv>
        // (refresh) would navigate it away; microdata <meta itemprop> stays for metadata extraction.
        content.select("style, noscript, frame, frameset, object, embed, applet, base, link, meta[http-equiv]").remove()
        content.select("iframe").filterNot(::isTrustedVideoIframe).forEach { it.remove() }

        for (element in content.select("*")) {
            if (element.attributesSize() == 0) continue
            for (attribute in element.attributes().asList()) {
                val key = attribute.key.lowercase()
                val value = attribute.value
                val shouldRemove = key.startsWith("on") ||
                    key == "srcdoc" ||
                    (key in DANGEROUS_URL_ATTRIBUTES && isDangerousUrl(value, element.loadsImageFrom(key)))
                if (shouldRemove) {
                    element.removeAttr(attribute.key)
                }
            }
        }
    }

    private fun isTrustedVideoIframe(element: Element): Boolean {
        val src = element.absUrl("src").ifBlank { element.attr("src").trim() }
        if (src.isBlank() || isDangerousUrl(src)) return false
        return TrustedEmbeds.isTrustedIframeSrc(src, element.baseUri())
    }

    private fun Element.loadsImageFrom(attribute: String): Boolean =
        attribute == "src" || (normalName() == "image" && (attribute == "href" || attribute == "xlink:href"))

    private val DANGEROUS_URL_ATTRIBUTES = setOf("href", "src", "action", "formaction", "xlink:href")
}
