package com.prof18.klead.internal.removal

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.dom.descendants

internal fun Element.descendantsSnapshot(): List<Element> = descendants()

internal fun Element.descendantsWithTagNamesSnapshot(tagNames: Set<String>): List<Element> =
    descendantsSnapshot().filter { it.normalName() in tagNames }
