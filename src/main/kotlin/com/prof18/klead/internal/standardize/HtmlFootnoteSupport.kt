package com.prof18.klead.internal.standardize

import org.jsoup.nodes.Element

// Plumbing shared by the footnote normalizers: the canonical target section that collected
// definitions are appended to, and the patterns that recognize footnote headings and numbers.

internal fun lazyFootnoteSection(content: Element): Element {
    content.selectFirst("section[data-footnotes]")?.let { return it }
    return Element("section")
        .attr("data-footnotes", "true")
        .addClass("footnotes")
        .also { content.appendChild(it) }
}

internal val FOOTNOTE_HEADING_PATTERN = Regex(
    """(?i)^(notes|footnotes|endnotes|sidenotes|references(?:\s+and\s+notes)?)$""",
)
internal val FOOTNOTE_NUMBER_PATTERN = Regex("""\d{1,4}""")
