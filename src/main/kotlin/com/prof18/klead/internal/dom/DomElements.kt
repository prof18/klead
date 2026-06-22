package com.prof18.klead.internal.dom

import org.jsoup.nodes.Element

internal fun Element.tagLower(): String = normalName().lowercase()

internal fun Element.classNameSafe(): String = className().trim()

internal fun Element.idSafe(): String = id().trim()

internal fun Element.textContentLike(): String = text().normalizeSpace()

internal fun Element.ownTextContentLike(): String = ownText().normalizeSpace()

internal fun Element.outerHtmlStable(): String = outerHtml().trim()

internal fun Element.innerHtmlStable(): String = html().trim()

internal fun Element.childrenElements(): List<Element> = children().toList()

internal fun Element.descendants(): List<Element> = select("*").filterNot { it === this }

internal fun Element.isAttachedTo(root: Element): Boolean = this === root || parents().any { it === root }

internal fun String.normalizeSpace(): String = replace(WHITESPACE_REGEX, " ").trim()

internal fun String.isoDatePart(): String = substringBefore("T")

internal fun Element.textTrimmedOrNull(): String? = text().trim().ifBlank { null }

internal fun Element.attrTrimmedOrNull(name: String): String? = attr(name).trim().ifBlank { null }

private val WHITESPACE_REGEX = Regex("""\s+""")
