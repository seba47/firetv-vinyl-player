package app.vinilotv

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale

/**
 * Pruebas de humo contra YouTube real, corren en la Mac (JVM, sin Android). Sirven para saber
 * si NewPipeExtractor sigue andando antes de recompilar e instalar en la tele.
 *
 *   scripts/smoke-test.sh "artista tema"
 */
class ExtractionSmokeTest {
    companion object {
        @JvmStatic @BeforeClass fun setUp() = YouTube.init(Locale.forLanguageTag("es-AR"))
        private val query get() = System.getProperty("vinilo.query")?.takeIf { it.isNotBlank() } ?: "Soda Stereo"
    }

    @Test
    fun songSearchResolveAndFetchAudio() {
        val tracks = YouTube.searchSongs(query)
        println("Temas \"$query\": ${tracks.size}")
        tracks.take(3).forEach { println("  - ${it.artist} — ${it.title}  ${it.url}") }
        assertTrue("La búsqueda no devolvió temas", tracks.isNotEmpty())

        val resolved = YouTube.resolve(tracks.first())
        println("Resuelto: ${resolved.track.artist} — ${resolved.track.title} (${resolved.track.durationSec}s)")
        println("  ${resolved.format} ${resolved.bitrateKbps} kbps · tapa ${resolved.track.thumbnailUrl}")

        val request = Request.Builder()
            .url(resolved.audioUrl)
            .header("User-Agent", YouTube.USER_AGENT)
            .header("Range", "bytes=0-65535")
            .build()
        OkHttpClient().newCall(request).execute().use { response ->
            println("  descarga de audio: HTTP ${response.code}")
            assertEquals("El googlevideo no devolvió el audio", 206, response.code)
        }
    }

    @Test
    fun albumSearchAndTracks() {
        val albums = YouTube.search(query, SeedKind.ALBUM)
        println("Discos \"$query\": ${albums.size}")
        albums.take(3).forEach { println("  - ${it.title} · ${it.subtitle} (${it.trackCount}) ${it.url}") }
        assertTrue("La búsqueda no devolvió discos", albums.isNotEmpty())
        val tracks = YouTube.playlistTracks(albums.first().url)
        println("  temas del primero: ${tracks.size} → ${tracks.take(3).map { it.title }}")
        assertTrue(tracks.isNotEmpty())
    }

    @Test
    fun playlistSearch() {
        val lists = YouTube.search(query, SeedKind.PLAYLIST)
        println("Playlists \"$query\": ${lists.size}")
        lists.take(3).forEach { println("  - ${it.title} · ${it.subtitle} (${it.trackCount})") }
        assertTrue("La búsqueda no devolvió playlists", lists.isNotEmpty())
        val tracks = YouTube.playlistTracks(lists.first().url, max = 50)
        println("  temas de la primera: ${tracks.size}")
        assertTrue(tracks.isNotEmpty())
    }

    /** scripts/probar-disco.sh <url>: abre un disco/playlist y prueba resolver sus primeros temas. */
    @Test
    fun specificAlbum() {
        val url = System.getProperty("vinilo.url")?.takeIf { it.isNotBlank() } ?: return
        val tracks = YouTube.playlistTracks(url)
        println("Disco $url: ${tracks.size} temas")
        var ok = 0
        tracks.take(4).forEach { t ->
            val r = runCatching { YouTube.resolve(t) }
            r.onSuccess { ok++; println("  ✓ ${t.title} — ${it.format} ${it.bitrateKbps} kbps") }
            r.onFailure { println("  ✗ ${t.title} (${t.videoId}): ${it.javaClass.simpleName}: ${it.message}") }
        }
        assertTrue("No se pudo resolver ningún tema", ok > 0)
    }

    /** Links pegados desde el celular: disco, playlist de usuario y tema suelto. */
    @Test
    fun pastedLinks() {
        val list = YouTube.search(query, SeedKind.PLAYLIST).first().url
        val listId = Regex("list=([A-Za-z0-9_-]+)").find(list)!!.groupValues[1]
        val song = YouTube.searchSongs(query).first().videoId
        val links = listOf(
            "https://music.youtube.com/playlist?list=OLAK5uy_mEbM01YhGgpVCzga-KIXsHQ3ylB8iYPDE",   // disco (Recreo, Pim Pau)
            "https://www.youtube.com/playlist?list=$listId&si=abc",                                 // playlist de usuario
            "https://m.youtube.com/watch?v=$song&list=$listId",                                     // tema dentro de una playlist
            "https://youtu.be/$song?si=xyz",                                                         // tema suelto
            "https://music.youtube.com/watch?v=$song&list=RDAMVM$song",                              // mix → el tema
        )
        links.forEach { l ->
            val r = YouTube.fromUrl(l)
            println("  ${r.kind} · ${r.title} · ${r.subtitle} (${r.trackCount}) ← $l")
            assertTrue(r.title.isNotBlank())
        }
        assertEquals(SeedKind.ALBUM, YouTube.fromUrl(links[0]).kind)
        assertEquals(SeedKind.SONG, YouTube.fromUrl(links[3]).kind)
        assertTrue(YouTube.looksLikeUrl(" https://youtu.be/x ") && !YouTube.looksLikeUrl("soda stereo"))
    }

    /**
     * "Hechos para niños" (La vaca Lola): sin audio suelto en el cliente normal. Lo que se elija
     * tiene que poder leerse hasta el final; el audio de iOS da 403 pasado el primer pedazo.
     */
    @Test
    fun madeForKidsPlaysToTheEnd() {
        val r = YouTube.resolve(Track("uSAY4n84A9I", "", ""))
        val http = OkHttpClient()
        fun range(spec: String) = http.newCall(Request.Builder().url(r.audioUrl).header("User-Agent", YouTube.USER_AGENT)
            .header("Range", "bytes=$spec").build()).execute()
        val total = range("0-65535").use { assertEquals(206, it.code); ChunkedDataSource.totalFrom(it.headers.toMultimap()) }
        println("  ${r.track.title} · ${r.format} · $total bytes")
        range("${total - 65536}-${total - 1}").use { assertEquals("final del archivo", 206, it.code) }
    }

    @Test
    fun mixForEndlessRadio() {
        val seed = YouTube.searchSongs(query).first()
        val mix = YouTube.mix(seed.videoId)
        println("Mix de \"${seed.title}\": ${mix.size} temas")
        mix.take(5).forEach { println("  - ${it.artist} — ${it.title}") }
        assertTrue("El mix vino vacío", mix.size >= 5)
    }
}
