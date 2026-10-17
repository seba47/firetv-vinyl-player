package app.vinilotv

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ViniloTV"

/** Lo que se eligió para armar una estación: un tema, un disco o una playlist. */
data class Seed(
    val kind: SeedKind,
    val title: String,
    val subtitle: String,
    val url: String,
    val thumbnailUrl: String?,
) {
    fun toJson() = JSONObject().put("kind", kind.name).put("title", title).put("subtitle", subtitle)
        .put("url", url).put("thumb", thumbnailUrl ?: "")

    companion object {
        fun fromJson(o: JSONObject) = Seed(
            SeedKind.valueOf(o.getString("kind")), o.getString("title"), o.optString("subtitle"),
            o.getString("url"), o.optString("thumb").ifEmpty { null },
        )
    }
}

/** Un tema en la cola. [auto] = lo sumó el enganche (parecidos), no la semilla. */
data class QueueItem(
    val track: Track,
    val auto: Boolean,
    val albumIndex: Int = -1,
    val albumSize: Int = 0,
)

data class RadioState(
    val ready: Boolean = false,
    val seed: Seed? = null,
    val current: QueueItem? = null,
    val upNext: List<QueueItem> = emptyList(),
    val playing: Boolean = false,
    val loading: Boolean = false,
    val recent: List<Seed> = emptyList(),
    val toast: Pair<Long, String>? = null,
    val webUrl: String? = null,
)

/**
 * La radio: arma la cola a partir de una semilla y, cuando se acaba, sigue enganchando temas
 * parecidos (mix de YouTube Music) sin repetir. Cada tema se resuelve justo antes de sonar
 * (las URLs de audio vencen) y el siguiente se precarga para que no haya silencio entre temas.
 *
 * Vive en el proceso de la app; la crea y le da el reproductor [PlaybackService]. Todo lo que
 * toca el reproductor corre en el hilo principal.
 */
object Radio {
    private val _state = MutableStateFlow(RadioState())
    val state: StateFlow<RadioState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var store: StationStore? = null

    private val queue = mutableListOf<QueueItem>()
    private var index = 0
    private val playedIds = HashSet<String>()
    private val playedTitles = HashSet<String>()
    private var generation = 0          // cambia con cada estación: invalida trabajos viejos
    private var startJob: Job? = null
    private var aheadJob: Job? = null
    private var retriedCurrent = false
    private var seedReadAt = 0L        // última vez que se leyó la playlist de la semilla

    fun attach(context: Context, exo: ExoPlayer) {
        player = exo
        store = StationStore(context)
        exo.addListener(listener)
        _state.update { it.copy(ready = true, recent = store!!.load()) }
    }

    fun detach() {
        player?.removeListener(listener)
        player = null
        scope.coroutineContext.cancelChildren()
        _state.update { RadioState(recent = it.recent) }
    }

    fun setWebUrl(url: String?) = _state.update { it.copy(webUrl = url) }

    fun positionMs(): Long = player?.currentPosition ?: 0
    fun durationMs(): Long = player?.duration?.takeIf { it > 0 } ?: ((_state.value.current?.track?.durationSec ?: 0) * 1000)

    // ---------------------------------------------------------------- comandos

    /** Arranca una estación nueva. [fromPhone] muestra un aviso en la tele. */
    fun play(seed: Seed, fromPhone: Boolean = false, remember: Boolean = true) {
        val gen = ++generation
        startJob?.cancel(); aheadJob?.cancel()
        if (remember) store?.let { s -> _state.update { it.copy(recent = s.remember(seed)) } }
        _state.update {
            it.copy(seed = seed, loading = true,
                toast = if (fromPhone) System.currentTimeMillis() to "Desde el celular · ${seed.kind.label}: ${seed.title}" else it.toast)
        }
        startJob = scope.launch {
            val items = withContext(Dispatchers.IO) { runCatching { loadSeed(seed) } }
            if (gen != generation) return@launch
            items.onFailure { e ->
                Log.e(TAG, "No pude abrir ${seed.url}", e)
                toast("No pude abrir ${seed.title}")
                _state.update { it.copy(loading = false) }
                return@launch
            }
            queue.clear(); index = 0; playedIds.clear(); playedTitles.clear()
            seedReadAt = System.currentTimeMillis()
            queue += items.getOrThrow()
            // stop() antes de vaciar: si no, el reproductor avisa "terminó" y la radio avanzaría sola.
            player?.run { stop(); clearMediaItems() }
            startCurrent(gen)
        }
    }

    /** Busca un tema y lo usa de semilla (lo usa scripts/play.sh). */
    fun playQuery(query: String) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { YouTube.search(query, SeedKind.SONG).firstOrNull() }.getOrNull() }
            if (r == null) toast("Sin resultados para \"$query\"")
            else play(Seed(SeedKind.SONG, r.title, r.subtitle, r.url, r.thumbnailUrl))
        }
    }

    fun next() {
        val p = player ?: return
        if (_state.value.current == null) return
        if (p.mediaItemCount > p.currentMediaItemIndex + 1) {
            p.seekToNextMediaItem()           // ya estaba precargado: la transición avanza la cola
            p.play()
        } else {
            val gen = generation
            aheadJob?.cancel()
            index++
            startJob = scope.launch {
                if (index >= queue.size) topUp(gen)
                startCurrent(gen)
            }
        }
    }

    fun togglePause() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_IDLE && _state.value.current != null) {
            startJob = scope.launch { startCurrent(generation) }
            return
        }
        p.playWhenReady = !p.playWhenReady
    }

    /** ↑/↓ del control: recorre las estaciones recientes sin reordenarlas. */
    fun cycleStation(delta: Int) {
        val recent = _state.value.recent
        if (recent.isEmpty()) return
        val i = recent.indexOfFirst { it.url == _state.value.seed?.url }
        val target = recent[((if (i < 0) 0 else i + delta) % recent.size + recent.size) % recent.size]
        play(target, remember = false)
    }

    fun toast(text: String) = _state.update { it.copy(toast = System.currentTimeMillis() to text) }

    // ---------------------------------------------------------------- cola

    private fun loadSeed(seed: Seed): List<QueueItem> = when (seed.kind) {
        SeedKind.SONG -> {
            val id = YouTube.videoIdOf(seed.url) ?: error("URL de tema inválida: ${seed.url}")
            listOf(QueueItem(Track(id, seed.title, seed.subtitle, 0, seed.thumbnailUrl), auto = false))
        }
        SeedKind.ALBUM -> YouTube.playlistTracks(seed.url).let { tracks ->
            tracks.mapIndexed { i, t -> QueueItem(t.copy(thumbnailUrl = seed.thumbnailUrl ?: t.thumbnailUrl), false, i, tracks.size) }
        }
        SeedKind.PLAYLIST -> YouTube.playlistTracks(seed.url).map { QueueItem(it, auto = false) }
    }.also { require(it.isNotEmpty()) { "La semilla no tiene temas" } }

    private suspend fun startCurrent(gen: Int, attempts: Int = 0) {
        val p = player ?: return
        if (index >= queue.size) topUp(gen)
        val item = queue.getOrNull(index) ?: run {
            toast("No encontré más temas para enganchar")
            _state.update { it.copy(loading = false) }
            return
        }
        _state.update { it.copy(loading = true) }
        publish()
        val resolved = withContext(Dispatchers.IO) { runCatching { YouTube.resolve(item.track) } }
        if (gen != generation) return
        resolved.onFailure { e ->
            Log.w(TAG, "Salteo ${item.track.title}: ${e.message}")
            queue.removeAt(index)                    // los temas que fallan se saltean
            if (attempts < 6) startCurrent(gen, attempts + 1)
            else { toast("YouTube no está respondiendo"); _state.update { it.copy(loading = false) } }
            return
        }
        val r = resolved.getOrThrow()
        val updated = item.copy(track = mergeTrack(item.track, r.track))
        queue[index] = updated
        Log.i(TAG, "Suena ${r.track.artist} — ${r.track.title} (${r.format} ${r.bitrateKbps} kbps)")
        retriedCurrent = false
        p.setMediaItem(mediaItem(updated, r))
        p.prepare()
        p.play()
        markPlayed(updated)
        _state.update { it.copy(loading = false) }
        publish()
        ensureAhead(gen)
    }

    /** Cuando quedan pocos temas, suma parecidos (mix de un tema reciente) que no hayan sonado. */
    private suspend fun topUp(gen: Int) {
        if (queue.size - index > 3) return
        if (refreshPlaylist(gen)) return
        val bases = queue.subList((index - 2).coerceAtLeast(0), queue.size).map { it.track }.shuffled()
        for (base in bases.take(3)) {
            val mix = withContext(Dispatchers.IO) {
                runCatching { YouTube.mix(base.videoId) }.onFailure { Log.w(TAG, "Mix falló: ${it.message}") }.getOrDefault(emptyList())
            }
            if (gen != generation) return
            val known = playedIds + queue.map { it.track.videoId }
            val knownTitles = playedTitles + queue.map { titleKey(it.track) }
            val fresh = mix.filter { it.videoId !in known && titleKey(it) !in knownTitles }
                .distinctBy(::titleKey).shuffled().take(6)
            if (fresh.isNotEmpty()) {
                queue += fresh.map { QueueItem(it, auto = true) }
                Log.i(TAG, "Enganche: +${fresh.size} parecidos a ${base.title}")
                publish()
                return
            }
        }
    }

    /**
     * Una playlist puede ir creciendo (el usuario le agrega temas): cuando se están por terminar sus
     * temas, si pasaron 10 min desde la última lectura, se vuelve a leer y se suman los nuevos antes
     * de pasar a los parecidos. (Al ponerla de nuevo siempre se lee entera.)
     */
    private suspend fun refreshPlaylist(gen: Int): Boolean {
        val seed = _state.value.seed ?: return false
        if (seed.kind != SeedKind.PLAYLIST) return false
        if (System.currentTimeMillis() - seedReadAt < 10 * 60_000L) return false
        seedReadAt = System.currentTimeMillis()
        val fresh = withContext(Dispatchers.IO) { runCatching { YouTube.playlistTracks(seed.url) }.getOrDefault(emptyList()) }
        if (gen != generation) return false
        val known = playedIds + queue.map { it.track.videoId }
        val added = fresh.filter { it.videoId !in known }
        if (added.isEmpty()) return false
        queue += added.map { QueueItem(it, auto = false) }
        Log.i(TAG, "Playlist releída: +${added.size} temas nuevos")
        toast("${added.size} ${if (added.size == 1) "tema nuevo" else "temas nuevos"} en ${seed.title}")
        publish()
        return true
    }

    private fun ensureAhead(gen: Int) {
        aheadJob?.cancel()
        aheadJob = scope.launch {
            topUp(gen)
            prefetchNext(gen)
        }
    }

    /** Resuelve el próximo tema y lo agrega al reproductor para que entre sin silencio. */
    private suspend fun prefetchNext(gen: Int) {
        val p = player ?: return
        repeat(3) {
            if (p.mediaItemCount > p.currentMediaItemIndex + 1) return
            val next = queue.getOrNull(index + 1) ?: return
            val r = withContext(Dispatchers.IO) { runCatching { YouTube.resolve(next.track) }.getOrNull() }
            if (gen != generation) return
            if (r == null) { queue.remove(next); publish(); return@repeat }
            if (queue.getOrNull(index + 1) != next) return
            val updated = next.copy(track = mergeTrack(next.track, r.track))
            queue[index + 1] = updated
            p.addMediaItem(mediaItem(updated, r))
            publish()
            return
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = player ?: return
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) return
            val nextItem = queue.getOrNull(index + 1) ?: return
            if (mediaItem?.mediaId != nextItem.track.videoId) return
            index++
            markPlayed(nextItem)
            while (p.currentMediaItemIndex > 0) p.removeMediaItem(0)
            retriedCurrent = false
            publish()
            ensureAhead(generation)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) next()
            publish()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Error de reproducción (${error.errorCodeName})", error)
            val gen = generation
            player?.run { stop(); clearMediaItems() }
            aheadJob?.cancel()
            startJob = scope.launch {
                if (!retriedCurrent) { retriedCurrent = true; startCurrent(gen) }   // URL vencida: se vuelve a resolver
                else { index++; startCurrent(gen) }                                   // si sigue fallando, se saltea
            }
        }
    }

    private fun publish() {
        val p = player
        _state.update {
            it.copy(
                current = queue.getOrNull(index),
                upNext = queue.drop(index + 1).take(3),
                playing = p?.isPlaying == true || (p?.playWhenReady == true && p.playbackState == Player.STATE_BUFFERING),
            )
        }
    }

    private fun markPlayed(item: QueueItem) {
        playedIds += item.track.videoId
        playedTitles += titleKey(item.track)
    }

    // "De Música Ligera (Remasterizado 2007)" y "De Música Ligera (En Vivo)" cuentan como el mismo.
    private fun titleKey(t: Track) = t.title.lowercase().replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim()

    private fun mergeTrack(old: Track, fresh: Track) = old.copy(
        title = old.title.ifBlank { fresh.title },
        artist = old.artist.ifBlank { fresh.artist },
        durationSec = fresh.durationSec.takeIf { it > 0 } ?: old.durationSec,
        thumbnailUrl = old.thumbnailUrl ?: fresh.thumbnailUrl,
    )

    private fun mediaItem(item: QueueItem, r: ResolvedTrack) = MediaItem.Builder()
        .setMediaId(item.track.videoId)
        .setUri(r.audioUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(item.track.title)
                .setArtist(item.track.artist)
                .setAlbumTitle(_state.value.seed?.takeIf { it.kind == SeedKind.ALBUM }?.title)
                .setArtworkUri(item.track.thumbnailUrl?.let(Uri::parse))
                .build(),
        )
        .build()
}

/** Estaciones recientes, guardadas en el dispositivo. */
class StationStore(context: Context) {
    private val prefs = context.getSharedPreferences("estaciones", Context.MODE_PRIVATE)

    fun load(): List<Seed> = runCatching {
        val arr = JSONArray(prefs.getString("recientes", "[]"))
        (0 until arr.length()).map { Seed.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun remember(seed: Seed): List<Seed> {
        val list = (listOf(seed) + load().filter { it.url != seed.url }).take(8)
        prefs.edit().putString("recientes", JSONArray(list.map { it.toJson() }).toString()).apply()
        return list
    }
}
