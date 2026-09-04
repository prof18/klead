package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.media.TrustedEmbeds

internal object ChipProfile : DomExtractor {
    override val id: String = "chip"
    override val domains: Set<String> = setOf("chip.de")
    override val contentSelectors: List<String> = listOf("article.Article-Container")
    override val preContentRemoveSelectors: List<String> = listOf(
        """[data-island="AdSlot"]""",
        "[data-qa-ad-slot]",
        ".Ad-Carousel",
        ".Affiliate-Note",
        """[data-island="GooglePreferredSourcesButton"]""",
        "[data-qa-top-list-auto-table]",
    )

    override fun preProcess(content: Element, context: DomExtractorContext) {
        normalizeDownloadWidgets(content)

        val instagramUrls = content.select("a[href]")
            .mapNotNull { link ->
                TrustedEmbeds.markdownMediaFromUrl(link.attr("href"))
                    ?.takeIf { media -> media.markdownLinkLabel == "Instagram post" }
                    ?.watchUrl
            }
            .distinct()
        if (instagramUrls.isEmpty()) return

        val instagramPlaceholders = content.select("""div[data-island="SocialWidgetBase"]""")
            .filter { placeholder -> placeholder.selectFirst(".Social-Widget__Instagram") != null }

        instagramPlaceholders.zip(instagramUrls).forEach { (placeholder, instagramUrl) ->
            val embed = Element("blockquote")
                .addClass("instagram-media")
                .attr("data-instgrm-permalink", instagramUrl)
            placeholder.replaceWith(embed)
        }
    }

    private fun normalizeDownloadWidgets(content: Element) {
        content.select("[data-qa-download-page-widget]").toList().forEach { widget ->
            val rows = mutableListOf<Pair<String, String>>()
            fun addRow(label: String, value: String?) {
                val normalizedValue = value?.trim().orEmpty()
                if (normalizedValue.isNotBlank()) rows += label to normalizedValue
            }

            addRow("Anbieter", widget.selectFirst("[data-qa-download-page-vendor-link]")?.text())
            addRow(
                "Version",
                widget.selectFirst("[data-qa-download-page-version-line]")
                    ?.text()
                    ?.removePrefix("Version")
                    ?.trim(),
            )
            addRow("Bewertung", widget.selectFirst("[data-qa-download-page-chip-rating]")?.text())
            addRow("Rang", widget.selectFirst("[data-qa-download-page-rank]")?.text())

            widget.select(".DownloadPageWidget-MetaField").forEach { field ->
                val label = field.selectFirst("dt")?.text()?.trim()?.removeSuffix(":") ?: return@forEach
                addRow(label, field.selectFirst("dd")?.text())
            }

            addRow(
                "Vorteile",
                widget.select("[data-qa-pro-con-advantages] dd")
                    .map { it.text().trim() }
                    .filter(String::isNotBlank)
                    .joinToString("; "),
            )
            addRow(
                "Nachteile",
                widget.select("[data-qa-pro-con-disadvantages] dd")
                    .map { it.text().trim() }
                    .filter(String::isNotBlank)
                    .joinToString("; "),
            )

            if (rows.isEmpty()) {
                widget.remove()
                return@forEach
            }

            val table = Element("table")
            table.appendElement("thead").appendElement("tr").also { header ->
                header.appendElement("th").text("Information")
                header.appendElement("th").text("Wert")
            }
            val body = table.appendElement("tbody")
            rows.forEach { (label, value) ->
                body.appendElement("tr").also { row ->
                    row.appendElement("th").text(label)
                    row.appendElement("td").text(value)
                }
            }
            widget.replaceWith(table)
        }
    }
}
