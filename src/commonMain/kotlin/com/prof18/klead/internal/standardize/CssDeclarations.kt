package com.prof18.klead.internal.standardize

/** Minimal CSS declaration helpers that honor strings, comments, and function arguments. */
internal object CssDeclarations {
    /** Split declarations while honoring strings, comments, and function arguments. */
    fun split(style: String): List<String> {
        val declarations = mutableListOf<String>()
        var start = 0
        var parentheses = 0
        var quote: Char? = null
        var escaped = false
        var inComment = false
        var index = 0
        while (index < style.length) {
            val character = style[index]
            if (escaped) {
                escaped = false
                index++
                continue
            }
            if (quote != null) {
                if (character == quote) quote = null
                if (character == '\\') escaped = true
                index++
                continue
            }
            if (inComment) {
                if (character == '*' && style.getOrNull(index + 1) == '/') {
                    inComment = false
                    index += 2
                } else {
                    index++
                }
                continue
            }
            if (character == '/' && style.getOrNull(index + 1) == '*') {
                inComment = true
                index += 2
                continue
            }
            when (character) {
                '\'', '"' -> quote = character

                '(' -> parentheses++

                ')' -> if (parentheses > 0) parentheses--

                ';' -> if (parentheses == 0) {
                    style.substring(start, index).trim().takeIf(String::isNotEmpty)?.let(declarations::add)
                    start = index + 1
                }
            }
            if (character == '\\') escaped = true
            index++
        }

        style.substring(start).trim().takeIf(String::isNotEmpty)?.let(declarations::add)
        return declarations
    }

    fun withoutComments(value: String): String {
        val result = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (escaped) {
                result.append(character)
                escaped = false
            } else if (quote != null) {
                result.append(character)
                if (character == quote) quote = null
                if (character == '\\') escaped = true
            } else if (character == '\\') {
                result.append(character)
                escaped = true
            } else if (character == '\'' || character == '"') {
                result.append(character)
                quote = character
            } else if (character == '/' && value.getOrNull(index + 1) == '*') {
                val end = value.indexOf("*/", startIndex = index + 2)
                if (end < 0) break
                index = end + 2
                result.append(' ')
                continue
            } else {
                result.append(character)
            }
            index++
        }
        return result.toString()
    }
}
