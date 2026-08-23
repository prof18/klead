package com.prof18.klead

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertFalse

class ExtractorPublicApiTest {
    @Test
    fun `public extractor signatures do not expose ksoup`() {
        val publicTypes = listOf(
            Extractor::class.java,
            ExtractorContext::class.java,
            ExtractorResult::class.java,
            ExtractorMetadata::class.java,
        )
        val signatures = publicTypes.flatMap { type ->
            buildList {
                add(type.toGenericString())
                type.declaredConstructors
                    .filter { Modifier.isPublic(it.modifiers) }
                    .mapTo(this) { it.toGenericString() }
                type.declaredMethods
                    .filter { Modifier.isPublic(it.modifiers) }
                    .mapTo(this) { it.toGenericString() }
                type.declaredFields
                    .filter { Modifier.isPublic(it.modifiers) }
                    .mapTo(this) { it.toGenericString() }
            }
        }

        assertFalse(signatures.any { "com.fleeksoft.ksoup" in it }, signatures.joinToString("\n"))
    }
}
