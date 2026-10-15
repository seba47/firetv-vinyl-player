package app.vinilotv

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.vinilotv.ui.CrateUi
import app.vinilotv.ui.VinylScreen
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay

/** Sin tocar el control este rato, queda solo el tocadiscos (cuida la pantalla). */
private const val IDLE_MS = 3 * 60_000L

/**
 * Pantalla del tocadiscos. Control remoto: derecha = siguiente, centro = pausa,
 * abajo = la caja de discos (← → revolver, OK poner, Atrás cerrar), arriba = estación anterior,
 * ≡ = elegir música desde el celular (QR).
 * Desde la Mac: scripts/play.sh "artista tema".
 */
class MainActivity : ComponentActivity() {
    private var controller: ListenableFuture<MediaController>? = null
    private var pickerOpen by mutableStateOf(false)
    private var lastInput by mutableLongStateOf(SystemClock.uptimeMillis())
    private var idle by mutableStateOf(false)
    private var pendingQuery: String? = null
    // caja de discos
    private var crateOpen by mutableStateOf(false)
    private var crateIndex by mutableStateOf(0)
    private var cratePicking by mutableStateOf(false)
    private var crateItems: List<CrateItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingQuery = intent.getStringExtra("q")
        Settings.init(this)
        Crate.init(this)

        setContent {
            val state by Radio.state.collectAsStateWithLifecycle()
            val look by Settings.state.collectAsStateWithLifecycle()
            val crateList by Crate.items.collectAsStateWithLifecycle()
            val items = remember(state.recent, crateList, state.seed?.url) { crateItems(state.recent, crateList, state.seed?.url) }
            crateItems = items
            LaunchedEffect(items.size) { if (crateIndex > items.lastIndex) crateIndex = items.lastIndex.coerceAtLeast(0) }
            LaunchedEffect(idle) { if (idle) crateOpen = false }

            // Al abrir: si viene una búsqueda desde la Mac la toca; si no, retoma la última estación.
            LaunchedEffect(state.ready) {
                if (!state.ready) return@LaunchedEffect
                val q = pendingQuery
                pendingQuery = null
                when {
                    q != null -> Radio.playQuery(q)
                    state.seed != null -> Unit
                    state.recent.isNotEmpty() -> Radio.play(state.recent.first(), remember = false)
                    else -> pickerOpen = true
                }
            }
            // Pantalla prendida mientras suena.
            LaunchedEffect(state.playing) {
                if (state.playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // Salvapantallas propio.
            LaunchedEffect(lastInput, pickerOpen) {
                idle = false
                if (pickerOpen) return@LaunchedEffect
                delay(IDLE_MS)
                idle = true
            }
            // Cuando llega algo desde el celular, se cierra el panel del QR.
            LaunchedEffect(state.toast?.first) { if (state.toast != null) pickerOpen = false }

            VinylScreen(state, pickerOpen, idle, look, CrateUi(crateOpen, items, crateIndex, cratePicking))
        }
    }

    override fun onStart() {
        super.onStart()
        // Conectarse a la sesión crea el servicio de reproducción (y con él la radio).
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    }

    override fun onStop() {
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("q")?.let { q ->
            if (Radio.state.value.ready) Radio.playQuery(q) else pendingQuery = q
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val wasIdle = idle
        lastInput = SystemClock.uptimeMillis()
        val code = event.keyCode
        if (wasIdle && code != KeyEvent.KEYCODE_BACK && code !in mediaKeys) return true   // la primera tecla solo despierta
        if (crateOpen && code !in mediaKeys) return crateKey(code, event)
        when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> Radio.next()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> Radio.togglePause()
            KeyEvent.KEYCODE_DPAD_UP -> Radio.cycleStation(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> openCrate()
            KeyEvent.KEYCODE_MENU -> pickerOpen = !pickerOpen
            KeyEvent.KEYCODE_BACK -> if (pickerOpen && Radio.state.value.current != null) pickerOpen = false else return super.dispatchKeyEvent(event)
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    private fun openCrate() {
        if (crateItems.isEmpty()) return
        pickerOpen = false
        crateIndex = crateStartIndex(crateItems)
        crateOpen = true
    }

    /** Con la caja abierta: ← → revolver, OK poner, ↑/Atrás cerrar, ≡ ir al QR. */
    private fun crateKey(code: Int, event: KeyEvent): Boolean {
        if (cratePicking) return true
        when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> crateIndex = (crateIndex + 1).coerceAtMost(crateItems.lastIndex)
            KeyEvent.KEYCODE_DPAD_LEFT -> crateIndex = (crateIndex - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BACK -> crateOpen = false
            KeyEvent.KEYCODE_MENU -> { crateOpen = false; pickerOpen = true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> pickFromCrate()
            KeyEvent.KEYCODE_DPAD_DOWN -> Unit
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    /** El disco elegido sale volando al plato y arranca esa estación. */
    private fun pickFromCrate() {
        val seed = when (val item = crateItems.getOrNull(crateIndex)) {
            is CrateItem.Record -> item.seed
            CrateItem.Surprise -> Crate.items.value.randomOrNull() ?: return
            else -> return
        }
        cratePicking = true
        lifecycleScope.launch {
            delay(450)
            crateOpen = false
            cratePicking = false
            Radio.play(seed)
        }
    }

    private val mediaKeys = setOf(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    )
}
