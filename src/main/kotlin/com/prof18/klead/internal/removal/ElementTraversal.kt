package com.prof18.klead.internal.removal

import com.prof18.klead.internal.dom.descendants
import org.jsoup.nodes.Element

internal fun Element.descendantsSnapshot(): List<Element> = descendants()

internal fun Element.descendantsWithTagNamesSnapshot(tagNames: Set<String>): List<Element> =
    descendantsSnapshot().filter { it.normalName() in tagNames }
