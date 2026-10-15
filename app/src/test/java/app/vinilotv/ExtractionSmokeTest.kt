package app.vinilotv

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Prueba de humo contra YouTube real, corre en la Mac (JVM, sin Android):
 * buscar → resolver audio → bajar el primer bloque. Sirve para saber si
 * NewPipeExtractor sigue andando antes de recompilar e instalar en la tele.
 *
 *   scripts/smoke-test.sh "artista tema"
 */
class ExtractionSmokeTest {
    @Test
    fun searchResolveAndFetchAudio() {
        YouTube.init(Locale.forLanguageTag("es-AR"))
        val query = System.getProperty("vinilo.query")?.takeIf { it.isNotBlank() }
            ?: "Soda Stereo De música ligera"

        val tracks = YouTube.searchSongs(query)
        println("Búsqueda \"$query\": ${tracks.size} resultados")
        tracks.take(5).forEach { println("  - ${it.artist} — ${it.title}  ${it.url}") }
        assertTrue("La búsqueda no devolvió temas", tracks.isNotEmpty())

        val resolved = YouTube.resolve(tracks.first())
        println("Resuelto: ${resolved.track.artist} — ${resolved.track.title}")
        println("  formato=${resolved.format} codec=${resolved.codec} ${resolved.bitrateKbps} kbps")

        val request = Request.Builder()
            .url(resolved.audioUrl)
            .header("User-Agent", YouTube.USER_AGENT)
            .header("Range", "bytes=0-65535")
            .build()
        OkHttpClient().newCall(request).execute().use { response ->
            val bytes = response.body.bytes().size
            println("  descarga de audio: HTTP ${response.code}, $bytes bytes")
            assertEquals("El googlevideo no devolvió el audio", 206, response.code)
            assertTrue(bytes > 0)
        }
    }
}
