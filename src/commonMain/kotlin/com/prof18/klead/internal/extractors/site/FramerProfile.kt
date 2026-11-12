package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.QueryParser
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.content.joinedWordCount
import com.prof18.klead.internal.dom.isAttachedTo
import com.prof18.klead.internal.dom.removeSafely
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.removal.recordAndRemove

/**
 * Framer sites have no semantic landmarks, so the whole body becomes the content. Their markup still
 * says which blocks only show at other screen widths and, on CMS pages, where the article body ends.
 */
internal object FramerProfile : DomExtractor {
    // Probed against the document's <meta> elements only (see DocumentSignals).
    private val FRAMER_GENERATOR = QueryParser.parse("meta[name=generator][content^=Framer]")

    override val id: String = "framer"

    override fun matches(context: DomExtractorContext): Boolean =
        context.signals.firstMeta(FRAMER_GENERATOR) != null

    override fun preProcess(content: Element, context: DomExtractorContext) {
        // Framer server-renders one copy of a block per breakpoint and hides the others with a
        // `hidden-<breakpoint>` class. Keep what the narrowest layout shows, as a reader would, before
        // the shared removals meet the duplicates.
        val narrowestBreakpoints = context.hydrationData()?.narrowestBreakpointHashes().orEmpty()
        if (narrowestBreakpoints.isEmpty()) return
        val hiddenClasses = narrowestBreakpoints.map { "hidden-$it" }
        content.select("[class*=hidden-]").toList()
            .filter { element -> hiddenClasses.any(element::hasClass) }
            .forEach { it.removeSafely() }
    }

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        // Only a CMS item page has one article whose end can be trusted; a landing page is all sections.
        if (context.hydrationData()?.contains(COLLECTION_ITEM_KEY) != true) return
        val body = content.select(RICH_TEXT_SELECTOR).lastOrNull { it.isArticleBody() } ?: return

        removeLabelBeforeTitle(content, body, debug)
        for (ancestor in generateSequence(body) { it.parent() }.takeWhile { it !== content }) {
            for (sibling in generateSequence(ancestor.nextElementSibling()) { it.nextElementSibling() }.toList()) {
                recordAndRemove(sibling, debug, STEP, null, "Framer block after the article body")
            }
        }
    }

    // A back link or category label above the headline ("Blog", "News") is navigation, not the article.
    private fun removeLabelBeforeTitle(content: Element, body: Element, debug: MutableList<RemovalRecord>) {
        val title = content.select("h1").singleOrNull() ?: return
        val leading = generateSequence(title) { it.parent() }
            .takeWhile { it !== content }
            .flatMap { ancestor ->
                generateSequence(ancestor.previousElementSibling()) { it.previousElementSibling() }
            }
            .toList()
        if (leading.any { it === body || it.selectFirst(MEDIA_SELECTOR) != null }) return
        if (leading.sumOf { it.text().joinedWordCount() } > TITLE_LABEL_MAX_WORDS) return
        leading.filter { it.isAttachedTo(content) && it.text().isNotBlank() }.forEach { label ->
            recordAndRemove(label, debug, STEP, null, "Framer label before the title")
        }
    }

    private fun Element.isArticleBody(): Boolean =
        children().count { it.normalName() in BODY_BLOCK_TAGS } >= ARTICLE_BODY_MIN_BLOCKS &&
            text().joinedWordCount() >= ARTICLE_BODY_MIN_WORDS

    private fun DomExtractorContext.hydrationData(): String? =
        document.selectFirst("[$HYDRATION_ATTRIBUTE]")?.attr(HYDRATION_ATTRIBUTE)

    private fun String.narrowestBreakpointHashes(): List<String> = BREAKPOINT_PATTERN.findAll(this)
        .filter { "min-width" !in it.groupValues[2] }
        .map { it.groupValues[1] }
        .toList()

    private const val STEP = "postProcess:framer"
    private const val HYDRATION_ATTRIBUTE = "data-framer-hydrate-v2"
    private const val COLLECTION_ITEM_KEY = "\"collectionItemId\""
    private const val RICH_TEXT_SELECTOR = "[data-framer-component-type=RichTextContainer]"
    private const val MEDIA_SELECTOR = "img, picture, video, audio, iframe, figure"
    private const val ARTICLE_BODY_MIN_BLOCKS = 3
    private const val ARTICLE_BODY_MIN_WORDS = 60
    private const val TITLE_LABEL_MAX_WORDS = 3

    private val BODY_BLOCK_TAGS = setOf("p", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "blockquote", "figure", "img")
    private val BREAKPOINT_PATTERN = Regex(""""hash":"([A-Za-z0-9]+)","mediaQuery":"([^"]*)"""")
}
