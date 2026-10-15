package app.vinilotv

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Tema encontrado en una búsqueda: todavía sin URL de audio (esa vence, se resuelve al tocarlo). */
data class Track(val url: String, val title: String, val artist: String)

/** Tema listo para reproducir. [audioUrl] vence en ~6 h y queda atada a la IP que la resolvió. */
data class ResolvedTrack(
    val track: Track,
    val audioUrl: String,
    val format: String,
    val codec: String?,
    val bitrateKbps: Int,
    val thumbnailUrl: String?,
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

    /** Busca en YouTube Music (solo canciones): da audios oficiales, sin videoclips. */
    fun searchSongs(query: String): List<Track> {
        init()
        val handler = service.searchQHFactory
            .fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), "")
        return SearchInfo.getInfo(service, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { Track(it.url, it.name, it.uploaderName.orEmpty().removeSuffix(" - Topic")) }
    }

    /** Resuelve la mejor pista de audio directa (prefiere Opus, si no AAC). */
    fun resolve(track: Track): ResolvedTrack {
        init()
        val info = StreamInfo.getInfo(service, track.url)
        val candidates = info.audioStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        val best = candidates.maxWithOrNull(
            compareBy<AudioStream>({ it.isOriginalTrack() }, { it.format == MediaFormat.WEBMA_OPUS }, { it.averageBitrate })
        ) ?: error("Sin audio directo para ${track.url} (streams: ${info.audioStreams.size})")
        return ResolvedTrack(
            track = track.copy(title = info.name, artist = info.uploaderName.orEmpty().removeSuffix(" - Topic")),
            audioUrl = best.content,
            format = best.format?.getName() ?: "?",
            codec = best.codec,
            bitrateKbps = best.averageBitrate,
            thumbnailUrl = info.thumbnails.maxByOrNull { it.width }?.url,
        )
    }

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
