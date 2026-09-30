package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextFlightDataTest {
    @Test
    fun `text frames use UTF8 lengths and can cross scripts without confusing embedded newlines`() {
        val body = "<p>Café 🍎</p>\n9:{\"notARecord\":true}"
        val stream = "a:T${body.encodeToByteArray().size.toString(16)},${body}b:{\"post\":true}\n"
        val document = Ksoup.parse(flightScript(stream.take(12)) + flightScript(stream.drop(12)))
        val data = document.nextFlightData()

        assertEquals(body, data.resolveText("\$a"))
        assertEquals(1, data.models.size)
        assertNull(data.resolveText("\$missing"))
        assertEquals("literal HTML", data.resolveText("literal HTML"))
    }

    @Test
    fun `malformed and unrelated javascript is ignored without execution`() {
        val document = Ksoup.parse(
            "<script>other.push([1,\"a:T4,text\"])</script>" +
                "<script>self.__next_f.push([invalid]);</script>" +
                flightScript("a:Tffff,truncated"),
        )
        val data = document.nextFlightData()

        assertTrue(data.models.isEmpty())
        assertNull(data.resolveText("\$a"))
    }

    @Test
    fun `large escaped payload is read without recursive regular expressions`() {
        val body = "<p>Article text with brackets [ ] and a quoted \\\"word\\\".</p>".repeat(8000)
        val data = Ksoup.parse(flightScript("f:T${body.encodeToByteArray().size.toString(16)},$body"))
            .nextFlightData()

        assertEquals(body, data.resolveText("\$f"))
    }
}

internal fun flightScript(chunk: String): String {
    val encoded = JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(chunk))).toString().replace("<", "\\u003c")
    return "<script>self.__next_f.push($encoded)</script>"
}
