package com.prof18.klead.internal.removal

import org.jsoup.nodes.Element

internal fun Element.descendantsSnapshot(): List<Element> = getAllElements().drop(1)

internal fun Element.descendantsWithTagNamesSnapshot(tagNames: Set<String>): List<Element> =
    descendantsSnapshot().filter { it.normalName() in tagNames }
