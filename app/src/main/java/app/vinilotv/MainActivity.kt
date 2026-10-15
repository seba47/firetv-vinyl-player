package app.vinilotv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ViniloTV"
private const val DEFAULT_QUERY = "Soda Stereo De música ligera"

/**
 * Spike (fase 0): busca un tema con NewPipeExtractor y lo toca con ExoPlayer.
 * Control: derecha = siguiente resultado, centro = pausa.
 * Desde la Mac: scripts/play.sh "artista tema".
 */
class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var results: List<Track> = emptyList()
    private var index = 0

    private var nowPlaying by mutableStateOf("")
    private var detail by mutableStateOf("")
    private var status by mutableStateOf("Arrancando…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(YouTube.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> status = "Cargando audio…"
                    Player.STATE_READY -> status = if (player.playWhenReady) "Sonando" else "En pausa"
                    Player.STATE_ENDED -> playNext()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (player.playbackState == Player.STATE_READY) {
                    status = if (isPlaying) "Sonando" else "En pausa"
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Error de reproducción", error)
                status = "Error de reproducción: ${error.errorCodeName}"
            }
        })

        setContent { SpikeScreen(nowPlaying, detail, status) }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        search(intent.getStringExtra("q")?.takeIf { it.isNotBlank() } ?: DEFAULT_QUERY)
    }

    private fun search(query: String) {
        status = "Buscando \"$query\"…"
        lifecycleScope.launch {
            try {
                results = withContext(Dispatchers.IO) { YouTube.searchSongs(query) }
                Log.i(TAG, "Búsqueda \"$query\": ${results.size} resultados")
                if (results.isEmpty()) {
                    status = "Sin resultados para \"$query\""
                } else {
                    index = 0
                    playCurrent()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falló la búsqueda", e)
                status = "Falló la búsqueda: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    private fun playNext() {
        if (results.isEmpty()) return
        index = (index + 1) % results.size
        playCurrent()
    }

    private fun playCurrent() {
        val track = results[index]
        nowPlaying = "${track.title}\n${track.artist}"
        detail = ""
        status = "Resolviendo audio…"
        lifecycleScope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) { YouTube.resolve(track) }
                Log.i(TAG, "Resuelto ${resolved.track.title}: ${resolved.format} ${resolved.bitrateKbps} kbps")
                nowPlaying = "${resolved.track.title}\n${resolved.track.artist}"
                detail = "${resolved.format} · ${resolved.codec ?: "?"} · ${resolved.bitrateKbps} kbps" +
                    "   ·   ${index + 1}/${results.size}"
                player.setMediaItem(MediaItem.fromUri(resolved.audioUrl))
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo resolver ${track.url}", e)
                status = "No se pudo resolver: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> playNext()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> player.playWhenReady = !player.playWhenReady
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@androidx.compose.runtime.Composable
private fun SpikeScreen(nowPlaying: String, detail: String, status: String) {
    val cream = Color(0xFFEDE3D1)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2B211B))
            .padding(64.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(nowPlaying, style = TextStyle(color = cream, fontSize = 40.sp, lineHeight = 52.sp))
        Spacer(Modifier.height(24.dp))
        BasicText(detail, style = TextStyle(color = Color(0xFFB8894A), fontSize = 18.sp))
        Spacer(Modifier.height(12.dp))
        BasicText(status, style = TextStyle(color = cream.copy(alpha = 0.6f), fontSize = 18.sp))
    }
}
