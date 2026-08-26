package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.absUrlOrEmpty
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object StatistaProfile : DomExtractor {
    override val id: String = "statista"
    override val domains: Set<String> = setOf("statista.com")

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val preview = context.document.selectFirst("[data-chart-preview][data-src]")
        val renderedImage = context.document.selectFirst("[data-statistic-chart] img[src]")
        val imageSource = preview?.absUrlOrEmpty("data-src")
            ?.ifBlank { null }
            ?: renderedImage?.absUrlOrEmpty("src")?.ifBlank { null }
            ?: return null

        val article = Element("article")
        article.appendElement("img").also { image ->
            image.attr("src", imageSource)
            image.attr("alt", preview?.attr("data-alt").orEmpty().ifBlank { renderedImage?.attr("alt").orEmpty() })
            (preview?.numericAttribute("data-width") ?: renderedImage?.numericAttribute("width"))
                ?.let { image.attr("width", it) }
            (preview?.numericAttribute("data-height") ?: renderedImage?.numericAttribute("height"))
                ?.let { image.attr("height", it) }
        }
        context.document.selectFirst("#readingAidText > p")?.let { description ->
            article.appendChild(description.clone())
        }

        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = context.document.selectFirst("#statisticTitle")?.text()?.trim()?.ifBlank { null }
                    ?: context.document.selectFirst("#statisticSectionTitle")?.text()?.trim()?.ifBlank { null },
                author = context.document.selectFirst(".content__author--name a")?.text()?.trim()?.ifBlank { null },
                site = context.document.metaContent("og:site_name") ?: "Statista",
                description = context.document.metaContent("og:description"),
            ),
        )
    }

    private fun Element.numericAttribute(name: String): String? = attr(name)
        .trim()
        .takeIf { value -> value.isNotBlank() && value.all(Char::isDigit) }

    private fun Document.metaContent(name: String): String? =
        selectFirst("""meta[property="$name"], meta[name="$name"]""")
            ?.attr("content")
            ?.trim()
            ?.ifBlank { null }
}
