package com.prof18.klead.internal.markdown

internal val srcsetDelimiter = Regex(""",\s+""")
internal val bareOriginUrl = Regex("""https?://[^/?#]+""")
internal val placeholderDotsPattern = Regex("""(^|[\s"'“‘(\[])\. \.(?=$|[\s"'”’)\],.;:!?])""")
internal val spacedEllipsisPattern = Regex("""(?<=\S)\s+\.{3}""")
internal const val PERSIAN_COMMA = "،"
internal val codeLanguageClass = Regex("""[A-Za-z][A-Za-z0-9_+-]*""")
internal val backtickRunPattern = Regex("`+")
internal val highlightCodeClassNoise = setOf(
    "block",
    "code",
    "hl",
    "highlight",
    "source",
)
internal val rawIframeAttributes = listOf("width", "height", "frameborder", "allow", "allowfullscreen")
internal val footnoteIdHint = Regex(
    """(?i)(?:fn|ftn|ftnt|footnote|easy-footnote|_ftn|fna|cite_note|^r\d+$|^ref\d+$|reference)""",
)
internal val footnoteIdNumberPattern = Regex(
    """(?i)(?:fnref|fn|ftnref|ftn|ftnt_ref|ftnt|footnote|easy-footnote-bottom|easy-footnote|_ftnref|_ftn|fna|cite_note(?:-[A-Za-z0-9_]+)?|ref|reference|^r)[-_:.\s]*(\d+)""",
)
internal val numericSubscriptPattern = Regex("""\d{1,4}""")
internal val nextNumericSubscriptCharPattern = Regex("""[),;:.\]}]""")
internal val footnoteNumberPattern = Regex("""\d{1,4}""")
internal val footnoteReferenceTextPattern = Regex("""\[?\d{1,4}]?""")
internal val footnoteTerminalInlineElementPattern = Regex("""(?:\]\([^)]+\)|`+)$""")
internal val emptyLinkPattern = Regex("""\n*(?<!!)\[]\([^)]+\)\n*""")
internal val inlineWhitespacePattern = Regex("""[ \t]+(?!\n)""")
internal val inlineIndentedNewlinePattern = Regex("""\n[ \t]+(?=\S)""")
internal val linkTitleWhitespacePattern = Regex("""\s+""")
internal val horizontalWhitespacePattern = Regex("""[ \t]+""")
internal val tagGapWhitespacePattern = Regex(""">\s+<""")
internal val srcsetWhitespacePattern = Regex("""\s+""")
internal val imageDimensionSuffixPattern =
    Regex("""(?:-\d+x\d+|-\d+w|-(?:small|medium|large|thumb|thumbnail))(?=\.[A-Za-z0-9]+$)""")
internal val imageFileExtensionPattern = Regex("""\.[A-Za-z0-9]+$""")
internal val leadingHardBreakRun = Regex("""^(?:  \n)+""")
internal val headingTagPattern = Regex("""h[1-6]""")
internal val blockLikeSpanHint = Regex("""(?:^|[\s_-])(?:caption|credit|credits)(?:$|[\s_-])""")
internal val svgSelfClosingTagPattern = Regex("""<([A-Za-z][A-Za-z0-9:_-]*)([^>]*)\s/>""")
internal val svgTextLabelGroupSpacingPattern = Regex("""</text>(</g>(?:</g>)?<g)""")
internal val svgTextLabelPathSpacingPattern = Regex("""</text>(<path\b)""")
internal val svgNumberDelimiter = Regex("""[\s,]+""")
internal val svgRenderableElementSelector = listOf(
    "circle",
    "ellipse",
    "image",
    "line",
    "path",
    "polygon",
    "polyline",
    "rect",
    "text",
).joinToString(",")
internal val svgColorAttributes = setOf("fill", "stroke")
internal val svgAttributeValueFallbacks = mapOf(
    "var(--background-color-card)" to "Canvas",
    "var(--text-color-body)" to "currentColor",
    "var(--color-amber-600)" to "#d97706",
    "var(--color-green-600)" to "#16a34a",
    "light-dark(var(--color-slate-400), var(--color-slate-300))" to "#94a3b8",
)
internal val svgClassAttributeFallbacks = mapOf(
    "fill-amber-500" to ("fill" to "#f59e0b"),
    "fill-orange-500" to ("fill" to "#f97316"),
    "stroke-zinc-400" to ("stroke" to "#a1a1aa"),
)
internal val footnoteBlockTags = setOf("p", "ul", "ol", "blockquote", "pre", "table", "figure")
internal val footnoteMarkerTags = setOf("sup", "strong", "b")
internal val footnoteSeparatingPunctuation = setOf('.', ',', ';', ':')
internal val footnoteSeparatingSentencePunctuation = setOf('.', '!', '?')
internal val footnoteSeparatingClosingQuotes = setOf('"', '\'', '”', '’')
internal val footnoteSeparatingInlineSuffixes = setOf('*', '~', '`')
internal val footnoteAttachingPunctuation = setOf('.', ',', ';', ':', '!', '?')
internal val tightPunctuation = setOf('.', ',', ';', ':', '!', '?')
internal val delimitedInlineTags = setOf("strong", "b", "em", "i", "del", "s")
internal val delimitedInlineLeadingSpacingChars = setOf('*', '~', '"', '“', '‘')
internal val delimitedInlineTrailingSpacingChars = setOf('"', '”', '’')
internal val linkOpeningQuoteSpacingChars = setOf('"', '\'', '“', '‘')
internal val linkClosingQuoteSpacingChars = setOf('"', '\'', '”', '’')
internal val linkedImageSpacingLeadingChars = setOf('"', '\'', '“', '‘', '(')
internal val inlineFlowTags = setOf(
    "a",
    "abbr",
    "b",
    "br",
    "cite",
    "code",
    "del",
    "em",
    "font",
    "i",
    "ins",
    "mark",
    "math",
    "small",
    "span",
    "strong",
    "sub",
    "sup",
    "s",
)
internal val blockFlowTags = setOf(
    "article",
    "aside",
    "blockquote",
    "div",
    "figure",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "hr",
    "main",
    "ol",
    "p",
    "pre",
    "section",
    "table",
    "ul",
)
