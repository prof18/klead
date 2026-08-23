package com.prof18.klead.internal.dom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrlResolutionCharacterizationTest {
    private val base = "https://example.com/a/b?old=1"

    @Test
    fun `resolves absolute network and relative references like java URI`() {
        val cases = mapOf(
            "https://other.test/x" to "https://other.test/x",
            "//cdn.test/p" to "https://cdn.test/p",
            "/root" to "https://example.com/root",
            "child" to "https://example.com/a/child",
            "../up" to "https://example.com/up",
            "./same" to "https://example.com/a/same",
            "?q=1" to "https://example.com/a/?q=1",
            "#frag" to "https://example.com/a/b?old=1#frag",
        )

        cases.forEach { (value, expected) ->
            assertEquals(expected, resolveUrl(base, value), value)
        }
    }

    @Test
    fun `keeps java URI base path and trailing slash behavior`() {
        assertEquals("https://example.com/child", resolveUrl("https://example.com/a", "child"))
        assertEquals("https://example.com/a/child", resolveUrl("https://example.com/a/", "child"))
        assertEquals("https://example.com/a/child", resolveUrl("https://example.com/a/b?old=1", "child"))
    }

    @Test
    fun `rejects blank spaces and malformed references like current resolver`() {
        assertEquals("", resolveUrl(base, ""))
        assertEquals("", resolveUrl(base, "   "))
        assertEquals("", resolveUrl(base, "has space"))
        assertEquals("", resolveUrl(base, "ht!tp://"))
    }

    @Test
    fun `preserves unicode references during resolution`() {
        assertEquals(
            "https://münich.example/über",
            resolveUrl(base, "https://münich.example/über"),
        )
    }

    @Test
    fun `parses host decoded path raw components and ascii form like java URI`() {
        val parsed = parseKleadUri("https://www.Example.COM/a%20b?q=x%20y")

        assertEquals("https", parsed?.scheme)
        assertEquals("www.Example.COM", parsed?.host)
        assertEquals("/a b", parsed?.path)
        assertEquals("/a%20b", parsed?.rawPath)
        assertEquals("q=x%20y", parsed?.rawQuery)
        assertEquals("https://www.Example.COM/a%20b?q=x%20y", parsed?.asciiString)
        assertEquals("www.example.com", parsed?.host?.lowercase())
    }

    @Test
    fun `keeps java URI international host and ascii encoding quirks`() {
        val parsed = parseKleadUri("https://münich.example/über")

        assertNull(parsed?.host)
        assertEquals("/über", parsed?.path)
        assertEquals("/über", parsed?.rawPath)
        assertEquals("https://m%C3%BCnich.example/%C3%BCber", parsed?.asciiString)
    }

    @Test
    fun `returns null for malformed URI and nullable hierarchical parts for opaque URI`() {
        assertNull(parseKleadUri("not a url"))

        val opaque = parseKleadUri("mailto:test@example.com")
        assertEquals("mailto", opaque?.scheme)
        assertNull(opaque?.host)
        assertNull(opaque?.path)
        assertNull(opaque?.rawPath)
        assertNull(opaque?.rawQuery)
        assertEquals("mailto:test@example.com", opaque?.asciiString)
    }
}
