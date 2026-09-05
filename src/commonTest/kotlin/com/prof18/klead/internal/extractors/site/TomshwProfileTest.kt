package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TomshwProfileTest {
    @Test
    fun `Tomshw footer widgets are removed only on Tomshw`() {
        val article = """
            <article>
              <h1>Wi-Fi 7, cosa cambia davvero per la rete di casa</h1>
              <p>Il nuovo router migliora la stabilità della connessione grazie alla gestione simultanea di più bande. Nel test abbiamo misurato la velocità in stanze diverse, verificando anche il comportamento quando molti dispositivi trasferiscono dati nello stesso momento.</p>
              <p>Il prodotto include una porta Ethernet da 2,5 Gbps e una procedura di configurazione guidata. I risultati dipendono dalla distanza dal punto di accesso, dalle interferenze e dai client compatibili, quindi le prestazioni indicate dal produttore non sono garantite in ogni abitazione.</p>
              <section class="faq">
                <h2>Domande frequenti</h2>
                <p>Serve un dispositivo compatibile per sfruttare tutte le funzioni di Wi-Fi 7. Con i dispositivi precedenti il router resta utilizzabile, ma la velocità massima sarà quella dello standard supportato dal client.</p>
              </section>
              <div class="copy-row">
                <a href="https://google.com/preferences/source?q=https://www.tomshw.it">Aggiungi Tom's Hardware alle fonti preferite</a>
              </div>
              <div x-data="miniArticlePaginate()">Scopri tutti gli articoli e le offerte del momento</div>
            </article>
        """.trimIndent()

        for (host in listOf("tomshw.it", "www.tomshw.it", "example.com")) {
            val result = parseHtmlForTest(article, "https://$host/news/wifi-7", testOptions())
            for (output in listOf(result.content.requireHtml(), result.content.requireMarkdown())) {
                assertTrue(output.contains("Il nuovo router migliora la stabilità"), host)
                assertTrue(output.contains("Domande frequenti"), host)
                assertTrue(output.contains("Serve un dispositivo compatibile"), host)
                assertTrue(output.contains("Il prodotto include una porta Ethernet"), host)
                assertEquals(host == "example.com", output.contains("Aggiungi Tom's Hardware"), host)
                assertEquals(host == "example.com", output.contains("Scopri tutti gli articoli"), host)
            }
        }
    }
}
