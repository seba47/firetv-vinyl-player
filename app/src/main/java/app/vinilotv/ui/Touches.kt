package app.vinilotv.ui

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import app.vinilotv.QueueItem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Toques visuales. Cada uno se apaga poniendo su llave en false y reinstalando
 * (scripts/install.sh). Todos son baratos para la GPU del Fire TV (ver docs/rendimiento.md).
 */
object Touches {
    /** Motas de polvo flotando sobre el tocadiscos. */
    const val DUST = true

    /** Haz de luz cálida que barre la pantalla cada tanto (apagado: molestaba el reflejo). */
    const val LIGHT_BEAM = false

    /** El vinilo se ondula apenas al girar (alabeo) y la púa lo acompaña. */
    const val WARP = true

    /** En un disco: al terminar el lado A el disco se da vuelta; la etiqueta dice A o B. */
    const val SIDE_FLIP = true
}

/** Lado de un tema dentro de un disco: letra, posición dentro del lado y temas del lado. */
data class Side(val letter: Char, val index: Int, val size: Int)

fun sideOf(item: QueueItem?): Side? {
    if (item == null || item.albumIndex < 0 || item.albumSize < 2) return null
    val half = ceil(item.albumSize / 2f).toInt()
    return if (item.albumIndex < half) Side('A', item.albumIndex, half)
    else Side('B', item.albumIndex - half, item.albumSize - half)
}

// ------------------------------------------------------------------ polvo y luz

private const val BEAM_ANGLE = 18f            // grados respecto de la vertical
private const val BEAM_HALF = 150f            // medio ancho del haz
private const val BEAM_PERIOD = 40f           // pasa cada 40 s…
private const val BEAM_CROSS = 14f            // …y tarda 14 s en cruzar

private class Mote(rnd: Random) {
    val x = 120f + rnd.nextFloat() * 980f
    val y = 140f + rnd.nextFloat() * 860f
    val vx = rnd.nextFloat() * 8f - 4f
    val vy = -2f - rnd.nextFloat() * 6f       // suben despacio
    val r = 1.1f + rnd.nextFloat() * 2.1f
    val phase = rnd.nextFloat() * 6.28f
    val twinkle = .4f + rnd.nextFloat() * 1.2f
}

private fun wrap(v: Float, lo: Float, hi: Float): Float {
    val range = hi - lo
    return lo + (((v - lo) % range) + range) % range
}

/** El haz: una franja cálida con bordes y puntas difusas, pintada una sola vez. */
private fun bakeBeam(): ImageBitmap {
    val w = 300; val h = 1700
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val warm = 0xFFF6E2BD.toInt()
    fun a(alpha: Float) = (warm and 0x00FFFFFF) or ((255 * alpha).toInt() shl 24)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
        shader = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(a(0f), a(.09f), a(.12f), a(.09f), a(0f)),
            floatArrayOf(0f, .3f, .5f, .7f, 1f), Shader.TileMode.CLAMP)
    })
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0x00000000, -0x1000000, -0x1000000, 0x00000000),
            floatArrayOf(0f, .3f, .7f, 1f), Shader.TileMode.CLAMP)
    })
    return bmp.asImageBitmap()
}

/** Polvo en el aire y, cada tanto, un haz de luz que cruza el tocadiscos. [time] en segundos. */
@Composable
fun DustAndLight(time: () -> Float, k: Float, modifier: Modifier = Modifier) {
    if (!Touches.DUST && !Touches.LIGHT_BEAM) return
    val beam = remember { bakeBeam() }
    val motes = remember { Random(11).let { rnd -> List(26) { Mote(rnd) } } }
    val cosA = cos(BEAM_ANGLE * PI.toFloat() / 180f)
    val sinA = sin(BEAM_ANGLE * PI.toFloat() / 180f)
    Canvas(modifier.fillMaxSize()) {
        val t = time()
        val cycle = t % BEAM_PERIOD
        val pass = cycle / BEAM_CROSS
        val beamOn = Touches.LIGHT_BEAM && pass < 1f
        val beamX = -300f + 1700f * pass.coerceAtMost(1f)
        val beamA = if (beamOn) sin(PI.toFloat() * pass) else 0f
        scale(k, k, Offset.Zero) {
            if (beamA > .01f) {
                rotate(BEAM_ANGLE, Offset(beamX, 540f)) {
                    drawImage(beam, Offset(beamX - 150f, 540f - 850f), alpha = beamA)
                }
            }
            if (Touches.DUST) for (m in motes) {
                val x = wrap(m.x + m.vx * t + 6f * sin(t * .3f + m.phase), 100f, 1120f)
                val y = wrap(m.y + m.vy * t, 120f, 1010f)
                val d = abs((x - beamX) * cosA + (y - 540f) * sinA)
                val inBeam = (1f - d / BEAM_HALF).coerceIn(0f, 1f) * beamA
                val glow = (.05f + .6f * inBeam) * (.7f + .3f * sin(t * m.twinkle + m.phase))
                drawCircle(Color(0xFFF6E2BD), m.r * (1f + .4f * inBeam), Offset(x, y), alpha = glow)
            }
        }
    }
}
