package dev.defuddle.dom

import org.jsoup.nodes.Element

fun Element.tagLower(): String = normalName().lowercase()

fun Element.classNameSafe(): String = className().trim()

fun Element.idSafe(): String = id().trim()

fun Element.textContentLike(): String = text().normalizeSpace()

fun Element.ownTextContentLike(): String = ownText().normalizeSpace()

fun Element.outerHtmlStable(): String = outerHtml().trim()

fun Element.innerHtmlStable(): String = html().trim()

fun Element.childrenElements(): List<Element> = children().toList()

fun Element.descendants(): List<Element> =
    select("*").filterNot { it === this }

internal fun String.normalizeSpace(): String =
    replace(Regex("""\s+"""), " ").trim()
