package com.prof18.klead.internal.standardize

import kotlin.test.Test
import kotlin.test.assertEquals

class CssDeclarationsTest {
    @Test
    fun `splits on top level semicolons only`() {
        val style = """content: "a;b"; background: url(data:image/png;base64,AAA); /* x; y */ color: red !important; margin: 0"""

        assertEquals(
            listOf(
                """content: "a;b"""",
                "background: url(data:image/png;base64,AAA)",
                "/* x; y */ color: red !important",
                "margin: 0",
            ),
            CssDeclarations.split(style),
        )
    }

    @Test
    fun `honors escaped quotes and trailing semicolons`() {
        assertEquals(
            listOf("""content: "a\";b"""", "color: red"),
            CssDeclarations.split("""content: "a\";b"; color: red;;"""),
        )
    }

    @Test
    fun `removes comments outside strings`() {
        assertEquals("mar gin: 0", CssDeclarations.withoutComments("mar/**/gin: 0"))
        assertEquals("""content: "/* keep */"""", CssDeclarations.withoutComments("""content: "/* keep */""""))
        assertEquals("margin  : 0", CssDeclarations.withoutComments("margin /* c */: 0"))
    }
}
