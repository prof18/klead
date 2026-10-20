package com.prof18.klead.internal.content

// Counts words as letter/number runs joined by single apostrophes or hyphens ("don't", "state-of-the-art").
// This replaces the regex [\p{L}\p{N}]+(?:['-][\p{L}\p{N}]+)*: Kotlin/Native's regex engine recurses once per
// repeated group, so a long hyphenated run in page text overflowed the stack and killed the host app.
internal fun String.joinedWordCount(): Int {
    var count = 0
    var index = 0
    while (index < length) {
        if (!this[index].isWordCharacter()) {
            index++
            continue
        }
        count++
        index++
        while (index < length) {
            val char = this[index]
            index += when {
                char.isWordCharacter() -> 1
                char.isWordJoiner() && getOrNull(index + 1)?.isWordCharacter() == true -> 2
                else -> break
            }
        }
    }
    return count
}

private fun Char.isWordJoiner(): Boolean = this == '\'' || this == '-'

private fun Char.isWordCharacter(): Boolean = isLetter() || category in NUMBER_CATEGORIES

private val NUMBER_CATEGORIES = setOf(
    CharCategory.DECIMAL_DIGIT_NUMBER,
    CharCategory.LETTER_NUMBER,
    CharCategory.OTHER_NUMBER,
)
