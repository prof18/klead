package com.prof18.klead.internal.content

// Counts words as letter/number runs joined by single apostrophes or hyphens ("don't", "state-of-the-art").
// This replaces the regex [\p{L}\p{N}]+(?:['-][\p{L}\p{N}]+)*: Kotlin/Native's regex engine recurses once per
// repeated group, so a long hyphenated run in page text overflowed the stack and killed the host app.
// Chinese and Japanese are written without spaces, so their runs are estimated at two characters per word.
internal fun String.joinedWordCount(): Int {
    var count = 0
    var index = 0
    while (index < length) {
        if (this[index].isCjkCharacter()) {
            val runStart = index
            while (index < length && this[index].isCjkCharacter()) index++
            count += (index - runStart).cjkWords()
            continue
        }
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

private fun Char.isWordCharacter(): Boolean = (isLetter() || category in NUMBER_CATEGORIES) && !isCjkCharacter()

/** Words in a whitespace-separated token, estimating unspaced Chinese and Japanese text. */
internal fun String.tokenWordCount(): Int {
    if (isBlank()) return 0
    val cjkCharacters = count { it.isCjkCharacter() }
    if (cjkCharacters == 0) return 1
    val hasOtherWord = any { (it.isLetter() || it.isDigit()) && !it.isCjkCharacter() }
    return cjkCharacters.cjkWords() + if (hasOtherWord) 1 else 0
}

private fun Int.cjkWords(): Int = (this + CJK_CHARACTERS_PER_WORD - 1) / CJK_CHARACTERS_PER_WORD

// Han ideographs and kana. Korean Hangul is written with spaces between words, so it counts normally.
private fun Char.isCjkCharacter(): Boolean = this in '\u4E00'..'\u9FFF' ||
    this in '\u3400'..'\u4DBF' ||
    this in '\uF900'..'\uFAFF' ||
    this in '\u3040'..'\u30FF' ||
    this in '\uFF66'..'\uFF9F'

private const val CJK_CHARACTERS_PER_WORD = 2

private val NUMBER_CATEGORIES = setOf(
    CharCategory.DECIMAL_DIGIT_NUMBER,
    CharCategory.LETTER_NUMBER,
    CharCategory.OTHER_NUMBER,
)
