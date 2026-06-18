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

internal fun String.normalizeSpace(): String = replace(Regex("""\s+"""), " ").trim()
