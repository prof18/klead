package com.prof18.klead.internal.extractors.site

import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import org.jsoup.nodes.Element

internal object StripeDocsProfile : Extractor {
    override val id: String = "stripe-docs"
    override val contentSelectors: List<String> = listOf("article#content")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[role="toolbar"]""",
        """[role="listbox"]""",
        ".CodeBlock-header",
    )

    override fun matches(context: ExtractorContext): Boolean = context.hostMatches(setOf("stripe.com")) ||
        context.document.selectFirst("""meta[property=og:site_name][content="Stripe Documentation"]""") != null ||
        context.document.title().contains("Stripe Documentation", ignoreCase = true)

    override fun postProcess(content: Element, context: ExtractorContext, debug: MutableList<RemovalRecord>) {
        content.selectFirst("h1")
            ?.takeIf { heading ->
                heading.parent() === content || heading.parents().any { it === content }
            }
            ?.remove()
        content.select(".CodeBlock code[class], .CodeBlock pre[class]").forEach { code ->
            code.removeAttr("class")
        }
        content.select("pre").forEach { pre ->
            generateSequence(pre as Element?) { it.parent() }
                .takeWhile { it.normalName() != "body" }
                .forEach { element ->
                    element.classNames()
                        .filter { it.lowercase() in STRIPE_CODE_WRAPPER_CLASSES }
                        .forEach { className -> element.removeClass(className) }
                }
        }
    }

    private val STRIPE_CODE_WRAPPER_CLASSES = setOf(
        "box-root",
        "codeblock",
        "codeblock-code",
        "codeblock-content",
        "codeblock-numbered",
        "codetabgroup",
        "codetabgroup-content-dropdown-select",
        "content",
        "content-article",
        "content-container",
        "controlledcontentgroup-content",
        "document",
        "markdoccontentwrapper",
        "section",
        "section--numbered",
        "section-content",
    )
}
