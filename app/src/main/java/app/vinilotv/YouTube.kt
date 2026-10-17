package app.vinilotv

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Un tema todavía sin URL de audio (esa vence en ~6 h: se resuelve justo antes de tocarlo). */
data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSec: Long = 0,
    val thumbnailUrl: String? = null,
) {
    val url get() = "https://www.youtube.com/watch?v=$videoId"
}

/** Tema listo para reproducir. [audioUrl] queda atada a la IP que la resolvió. */
data class ResolvedTrack(
    val track: Track,
    val audioUrl: String,
    val format: String,
    val bitrateKbps: Int,
)

enum class SeedKind(val label: String) { SONG("Tema"), ALBUM("Disco"), PLAYLIST("Playlist") }

/** Resultado de búsqueda: lo que se puede elegir como semilla de una estación. */
data class SearchResult(
    val kind: SeedKind,
    val title: String,
    val subtitle: String,
    val url: String,
    val thumbnailUrl: String?,
    val trackCount: Long = -1,
)

/** Capa fina sobre NewPipeExtractor. Llamar desde un hilo de fondo: todo es bloqueante. */
object YouTube {
    // Mismo UA de escritorio que usa NewPipe; también se usa para bajar el audio.
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"

    private val service = ServiceList.YouTube

    @Volatile private var initialized = false

    fun init(locale: Locale = Locale.getDefault()) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val country = locale.country.ifEmpty { "US" }
            NewPipe.init(OkHttpDownloader, Localization.fromLocale(locale), ContentCountry(country))
            initialized = true
        }
    }

    /** Busca en YouTube Music: temas, discos o playlists. */
    fun search(query: String, kind: SeedKind): List<SearchResult> {
        init()
        val filter = when (kind) {
            SeedKind.SONG -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
            SeedKind.ALBUM -> YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
            SeedKind.PLAYLIST -> YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
        }
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        return SearchInfo.getInfo(service, handler).relatedItems.mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> tidy(item.name, cleanArtist(item.uploaderName)).let { (title, artist) ->
                    SearchResult(SeedKind.SONG, title, artist, item.url, item.thumbnails.best(), 1)
                }
                is PlaylistInfoItem -> SearchResult(
                    kind, item.name, cleanArtist(item.uploaderName), item.url,
                    item.thumbnails.best(), item.streamCount,
                )
                else -> null
            }
        }
    }

    fun searchSongs(query: String): List<Track> =
        search(query, SeedKind.SONG).mapNotNull { r ->
            videoIdOf(r.url)?.let { Track(it, r.title, r.subtitle, 0, r.thumbnailUrl) }
        }

    /** ¿Parece un link de YouTube / YouTube Music? (para el buscador del celular) */
    fun looksLikeUrl(text: String): Boolean =
        Regex("(youtube\\.com|youtu\\.be)/", RegexOption.IGNORE_CASE).containsMatchIn(text.trim())

    /**
     * Arma una semilla a partir de un link pegado: playlist o disco (`list=`), o un tema
     * (`watch?v=`, `youtu.be/`, `/shorts/`). Los mixes automáticos (`list=RD…`) se toman como el tema.
     * Sirve para lo que la búsqueda no encuentra (p. ej. playlists de usuarios).
     */
    fun fromUrl(raw: String): SearchResult {
        init()
        val url = raw.trim()
        val list = Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)
        val video = videoIdOf(url)
            ?: Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
            ?: Regex("/shorts/([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)
        if (list != null && !list.startsWith("RD")) {
            val playlistUrl = "https://music.youtube.com/playlist?list=$list"
            val info = PlaylistInfo.getInfo(service, playlistUrl)
            val kind = if (list.startsWith("OLAK5uy_")) SeedKind.ALBUM else SeedKind.PLAYLIST
            val thumb = info.thumbnails.best() ?: info.relatedItems.firstOrNull()?.thumbnails?.best()
            // los discos vienen como "Album – Recreo" y sin autor: el artista sale del primer tema
            val title = info.name.replace(Regex("^Album\\s*[–-]\\s*"), "")
            val artist = cleanArtist(info.uploaderName).ifBlank { cleanArtist(info.relatedItems.firstOrNull()?.uploaderName) }
            return SearchResult(kind, title, artist, playlistUrl, thumb, info.streamCount)
        }
        if (video != null) {
            val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$video")
            val (title, artist) = tidy(info.name, cleanArtist(info.uploaderName))
            return SearchResult(SeedKind.SONG, title, artist, "https://www.youtube.com/watch?v=$video", info.thumbnails.best(), 1)
        }
        error("No reconozco ese link: pegá el de una playlist, un disco o un tema de YouTube")
    }

    /** Temas de un disco o playlist, en orden (hasta [max]). */
    fun playlistTracks(url: String, max: Int = 200): List<Track> {
        init()
        val info = PlaylistInfo.getInfo(service, url)
        val items = info.relatedItems.toMutableList()
        var page: Page? = info.nextPage
        while (page != null && items.size < max) {
            val more = PlaylistInfo.getMoreItems(service, url, page)
            items += more.items
            page = more.nextPage
        }
        val albumArt = info.thumbnails.best()
        return items.take(max).mapNotNull { it.toTrack(albumArt) }
    }

    /** "Radio" de YouTube Music a partir de un tema: temas parecidos, el primero suele ser el mismo. */
    fun mix(videoId: String): List<Track> {
        init()
        val info = PlaylistInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId&list=RDAMVM$videoId")
        return info.relatedItems.mapNotNull { it.toTrack(null) }.filter { it.videoId != videoId }
    }

    /**
     * Resuelve la mejor pista de audio directa (prefiere Opus, si no AAC).
     * Los videos "hechos para niños" (p. ej. Pim Pau) no traen pistas de audio con el cliente de
     * siempre: se vuelven a pedir como el cliente de iOS, y si tampoco, se usa el video combinado de
     * 360p (el reproductor ignora la imagen y solo suena el audio).
     */
    fun resolve(track: Track): ResolvedTrack {
        init()
        var info = StreamInfo.getInfo(service, track.url)
        var best = bestAudio(info)
        // "Hechos para niños": el cliente normal no da audio suelto, solo el video combinado de 360p
        // (AAC ~96 kbps), que se baja entero sin problema. El audio del cliente de iOS existe pero
        // sin "PO token" YouTube lo corta con 403 a mitad del archivo: queda como último recurso.
        var muxed = if (best == null) bestMuxed(info) else null
        if (best == null && muxed == null) {
            info = withIosClient { StreamInfo.getInfo(service, track.url) }
            best = bestAudio(info)
            muxed = bestMuxed(info)
        }
        val content: String
        val format: String
        val bitrate: Int
        if (best != null) {
            content = best.content; format = best.format?.getName() ?: "?"; bitrate = best.averageBitrate
        } else {
            muxed ?: error("Sin audio para ${track.url} (audio: ${info.audioStreams.size}, video: ${info.videoStreams.size})")
            content = muxed.content; format = "${muxed.format?.getName()} (video ${muxed.resolution}, solo audio)"; bitrate = 0
        }
        return ResolvedTrack(
            track = track.copy(
                title = tidy(info.name, cleanArtist(info.uploaderName)).first,
                artist = tidy(info.name, cleanArtist(info.uploaderName)).second,
                durationSec = info.duration,
                thumbnailUrl = track.thumbnailUrl ?: info.thumbnails.best(),
            ),
            audioUrl = content,
            format = format,
            bitrateKbps = bitrate,
        )
    }

    private fun bestAudio(info: StreamInfo): AudioStream? = info.audioStreams
        .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        .maxWithOrNull(compareBy<AudioStream>({ it.isOriginalTrack() }, { it.format == MediaFormat.WEBMA_OPUS }, { it.averageBitrate }))

    private fun bestMuxed(info: StreamInfo): VideoStream? = info.videoStreams
        .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        .minByOrNull { it.resolution.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }

    private val iosLock = Any()

    /** La opción del cliente de iOS es global en NewPipeExtractor: se prende solo para este pedido. */
    private fun <T> withIosClient(block: () -> T): T = synchronized(iosLock) {
        YoutubeStreamExtractor.setFetchIosClient(true)
        try { block() } finally { YoutubeStreamExtractor.setFetchIosClient(false) }
    }

    fun videoIdOf(url: String): String? = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)

    private fun StreamInfoItem.toTrack(fallbackThumb: String?): Track? {
        val id = videoIdOf(url) ?: return null
        val (title, artist) = tidy(name, cleanArtist(uploaderName))
        return Track(id, title, artist, duration.coerceAtLeast(0), fallbackThumb ?: thumbnails.best())
    }

    private fun List<Image>.best(): String? =
        (maxByOrNull { it.width * it.height }?.url ?: firstOrNull()?.url)?.let(::bigger)

    // Las tapas de YT Music vienen de googleusercontent con el tamaño en la URL (=w120-h120…):
    // se puede pedir más grande. Las miniaturas de i.ytimg.com quedan como están.
    fun bigger(url: String): String = url.replace(Regex("=w\\d+-h\\d+[^/]*$"), "=w720-h720-l90-rj")

    private val noise = Regex(
        "\\s*[(\\[][^)\\]]*(official|oficial|video|v[ií]deo|audio|lyric|letra|remaster|visualizer|\\bhd\\b|4k)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Título y artista prolijos para mostrar: saca "(Remasterizado 2007)", "(Official Video)", etc.
     * (deja "(En Vivo)" o "(MTV Unplugged)") y separa "Artista - Tema" de los videos de YouTube.
     */
    fun tidy(rawTitle: String, rawArtist: String): Pair<String, String> {
        var title = rawTitle.replace(noise, "").trim()
        var artist = rawArtist.removeSuffix("VEVO").trim()
        val dash = title.indexOf(" - ")
        if (dash > 0) {
            val left = title.substring(0, dash).trim()
            val right = title.substring(dash + 3).trim()
            val sameArtist = left.equals(artist, ignoreCase = true) ||
                left.replace(" ", "").equals(artist.replace(" ", ""), ignoreCase = true)
            if (sameArtist || rawArtist.endsWith("VEVO")) { artist = left; title = right }
        }
        return title.ifBlank { rawTitle } to artist.ifBlank { rawArtist }
    }

    // Los canales automáticos de YT Music se llaman "Artista - Topic".
    private fun cleanArtist(name: String?) = name.orEmpty().removeSuffix(" - Topic")

    // Temas con varias pistas de idioma (doblajes automáticos): quedarse con la original.
    private fun AudioStream.isOriginalTrack() =
        audioTrackType?.name.let { it == null || it == "ORIGINAL" }
}

/** Downloader de NewPipeExtractor sobre OkHttp (versión mínima del DownloaderImpl de NewPipe). */
private object OkHttpDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .url(request.url())
            .header("User-Agent", YouTube.USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("YouTube pidió captcha (HTTP 429)", request.url())
            }
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString(),
            )
        }
    }
}
