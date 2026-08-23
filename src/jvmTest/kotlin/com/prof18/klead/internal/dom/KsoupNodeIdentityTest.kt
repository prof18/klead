package com.prof18.klead.internal.dom

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KsoupNodeIdentityTest {
    @Test
    fun `standard collections key Ksoup nodes by identity`() {
        val siblings = Ksoup.parse("<div>same</div><div>same</div>").select("div")
        val first = siblings[0]
        val second = siblings[1]
        val scores = mutableMapOf(first to 1, second to 2)
        val seen = mutableSetOf(first, second)

        assertFalse(first == second)
        assertEquals(2, scores.size)
        assertEquals(1, scores[first])
        assertEquals(2, scores[second])
        assertEquals(2, seen.size)
    }
}
