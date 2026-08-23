package com.prof18.klead.internal.markdown

import com.fleeksoft.ksoup.nodes.Element

/**
 * Stateless helpers for normalizing footnote ids and cleaning up footnote definition markup.
 *
 * The renderer owns the collected footnote state; these pure helpers only inspect and rewrite
 * the markup/ids handed to them, so they live outside the renderer.
 */

internal fun String.stripTerminalInlineElementPeriod(): String {
    if (!endsWith(".")) return this
    val withoutPeriod = dropLast(1)
    return if (footnoteTerminalInlineElementPattern.containsMatchIn(withoutPeriod)) withoutPeriod else this
}

internal fun Element.cleanupFootnoteDefinition() {
    select(
        "a[href*=fnref], a[href*=ftnt_ref], a[href*=_ftnref], a[class*=backref], a[class*=to-top], " +
            "a[href*=FnAnchor], a[href*=-link], a[aria-label*=Back], a[aria-label*=back], " +
            "a[aria-label*=Jump], a[aria-label*=jump], " +
            ".footnote-backref, .data-footnote-backref, .easy-footnote-margin-adjust",
    ).remove()

    val first = children().firstOrNull()
    if (
        first != null &&
        first.normalName() in footnoteMarkerTags &&
        cleanFootnoteId(first.text()) == cleanFootnoteId(id())
    ) {
        first.remove()
    }
}

internal fun Element.isFootnoteReferenceLike(href: String): Boolean {
    val target = href.removePrefix("#")
    if (!footnoteIdHint.containsMatchIn(target)) return false
    val text = text().trim()
    return normalName() == "sup" ||
        parent()?.normalName() == "sup" ||
        className().contains("footnote", ignoreCase = true) ||
        text.matches(footnoteReferenceTextPattern)
}

internal fun cleanFootnoteId(raw: String): String {
    val value = raw.trim().trim('#', '-', ':', '_', '.')
    footnoteIdNumberPattern.find(value)
        ?.takeIf { match -> match.isStandaloneFootnoteNumberIn(value) }
        ?.let { return it.groupValues[1] }
    return value.removePrefix("fnref")
        .removePrefix("fn")
        .trim('-', ':', '_', '.')
        .ifBlank { raw }
}

private fun MatchResult.isStandaloneFootnoteNumberIn(value: String): Boolean {
    val nextIndex = range.last + 1
    return nextIndex >= value.length || !value[nextIndex].isLetterOrDigit()
}
