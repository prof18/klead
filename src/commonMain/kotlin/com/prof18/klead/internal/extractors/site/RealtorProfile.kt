package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.dom.ParsedSelectors
import com.prof18.klead.internal.dom.parseKleadUri
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object RealtorProfile : DomExtractor {
    override val id: String = "realtor"
    override val domains: Set<String> = setOf("realtor.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[data-testid="text-widget"]""",
    )

    override fun preProcess(content: Element, context: DomExtractorContext) {
        val post = context.document.nextDataJsonObject()?.objectAt("props", "pageProps", "post") ?: return
        fillLazyGalleryImages(content, post)
        removeAuthorBio(content, post)
    }

    // Gallery figures ship an empty spinner <picture>; the image only exists in the post's CoreImage blocks.
    private fun fillLazyGalleryImages(content: Element, post: JsonObject) {
        val imagesByCaption = (post["editorBlocks"] as? JsonArray).orEmpty()
            .mapNotNull { block -> (block as? JsonObject)?.takeIf { it.string("__typename") == "CoreImage" } }
            .mapNotNull { block ->
                val attributes = block["attributes"] as? JsonObject ?: return@mapNotNull null
                val caption = attributes.string("caption")?.plainText()?.ifBlank { null } ?: return@mapNotNull null
                val src = attributes.string("src")?.takeIf { it.isHttpUrl() } ?: return@mapNotNull null
                caption to RealtorImage(src = src, alt = attributes.string("alt").orEmpty())
            }
            .toMap()
        if (imagesByCaption.isEmpty()) return

        content.select("figure").forEach { figure ->
            figure.selectFirst(ParsedSelectors[".image-caption + .image-credit"])?.prependText(" ")
            val picture = figure.selectFirst("""picture[data-lazy]""") ?: return@forEach
            if (picture.selectFirst("img[src], source[srcset]") != null) return@forEach
            val caption = figure.selectFirst(".image-caption")?.text()?.normalizedSpaces() ?: return@forEach
            val image = imagesByCaption[caption] ?: return@forEach
            picture.replaceWith(
                Element("img")
                    .attr("src", image.src)
                    .attr("alt", image.alt),
            )
        }
    }

    private fun removeAuthorBio(content: Element, post: JsonObject) {
        val bio = post.stringAt("author", "node", "description")?.plainText()?.ifBlank { null } ?: return
        content.select("p")
            .filter { paragraph -> paragraph.text().normalizedSpaces() == bio }
            .forEach(Element::remove)
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun String.plainText(): String = Ksoup.parseBodyFragment(this).text().normalizedSpaces()

    private fun String.normalizedSpaces(): String = trim().replace(WHITESPACE, " ")

    private fun String.isHttpUrl(): Boolean = parseKleadUri(this)?.scheme?.lowercase() in setOf("http", "https")

    private val WHITESPACE = Regex("""\s+""")
}

private data class RealtorImage(val src: String, val alt: String)
