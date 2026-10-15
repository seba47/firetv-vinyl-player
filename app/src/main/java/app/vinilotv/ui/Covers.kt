package app.vinilotv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

// Imágenes que se preparan una vez por tapa (en un hilo de fondo) para que la GPU del Fire TV
// solo tenga que copiar texturas: recortar en círculo o pintar gradientes de pantalla completa
// en cada cuadro la traba.

private suspend fun load(context: Context, url: String?, size: Int): Bitmap? {
    if (url == null) return null
    val request = ImageRequest.Builder(context).data(url).size(size).allowHardware(false).build()
    return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
}

/** La tapa recortada en círculo (centro cuadrado del 16:9), del tamaño real de la etiqueta. */
suspend fun bakeLabel(context: Context, url: String?, px: Int, sideLetter: Char? = null): ImageBitmap? {
    val src = load(context, url, 720) ?: return null
    return withContext(Dispatchers.Default) {
        val side = min(src.width, src.height)
        val sx = (src.width - side) / 2
        val sy = (src.height - side) / 2
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawCircle(px / 2f, px / 2f, px / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, Rect(sx, sy, sx + side, sy + side), Rect(0, 0, px, px), paint)
        if (sideLetter != null) drawSideMark(canvas, px, sideLetter)
        out.asImageBitmap()
    }
}

/**
 * Fondo del living en una sola imagen chica (se estira a pantalla completa): grafito cálido, la
 * tapa muy desenfocada como luz ambiente y una viñeta.
 */
suspend fun bakeBackdrop(context: Context, url: String?): ImageBitmap {
    val cover = load(context, url, 16)
    return withContext(Dispatchers.Default) {
        val w = 480; val h = 270
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val room = Palette.Room.toArgb()
        canvas.drawColor(room)
        if (cover != null) {
            // tapa de 16 px estirada (= desenfocada), un poco más grande que la pantalla
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (255 * .42f).toInt() }
            val side = min(cover.width, cover.height)
            val src = Rect((cover.width - side) / 2, (cover.height - side) / 2, (cover.width + side) / 2, (cover.height + side) / 2)
            val dw = w * 1.15f
            canvas.drawBitmap(cover, src, RectF((w - dw) / 2, (h - dw) / 2, (w + dw) / 2, (h + dw) / 2), paint)
        }
        val vignette = Paint().apply {
            shader = RadialGradient(
                w * .32f, h * .55f, w * .75f,
                intArrayOf(withAlpha(room, .15f), withAlpha(room, .78f), withAlpha(room, .9f)),
                floatArrayOf(0f, .8f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignette)
        out.asImageBitmap()
    }
}

/** Marca de lado en la etiqueta: el lado B apenas más oscuro y una plaquita de latón con la letra. */
private fun drawSideMark(canvas: Canvas, px: Int, side: Char) {
    if (side == 'B') canvas.drawColor(0x38000000, PorterDuff.Mode.SRC_ATOP)
    val cx = px / 2f; val cy = px * .80f; val r = px * .085f
    canvas.drawCircle(cx, cy + r * .12f, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 })
    canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.Brass.toArgb() })
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.WalnutDeep.toArgb()
        textSize = r * 1.25f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
    }
    canvas.drawText(side.toString(), cx, cy - (text.ascent() + text.descent()) / 2f, text)
}

// Centro de la columna de la estufa en la pantalla (fracción del ancho), medido en una foto de frente.
// El ancho de la cara arriba y abajo se ajusta desde el celular (Settings.edgeTop/edgeBottom).
const val ESTUFA_CENTER = .4985f

/**
 * Pared de yeso de la estufa, pintada una vez a media resolución (se estira a la pantalla):
 * revoque con manchas y grano, luz de arriba, las caras de la estufa en relieve (siguiendo la
 * columna: ~28 %–70 % arriba, abriéndose hacia abajo) y la sombra del tocadiscos sobre la pared.
 * [level] = brillo del yeso en %.
 */
suspend fun bakePlaster(level: Int, edgeTop: Int, edgeBottom: Int): ImageBitmap = withContext(Dispatchers.Default) {
    val w = 960; val h = 540
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(40f, .14f, level / 100f)))
    val rnd = java.util.Random(4)
    // manchas del revoque: ruido de baja frecuencia estirado (dos escalas)
    for ((gw, gh, a) in listOf(Triple(16, 9, .26f), Triple(44, 25, .14f))) {
        val noise = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        val px = IntArray(gw * gh) { val v = 128 + (rnd.nextGaussian() * 38).toInt().coerceIn(-90, 90); android.graphics.Color.rgb(v, v, v) }
        noise.setPixels(px, 0, gw, 0, 0, gw, gh)
        c.drawBitmap(noise, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (255 * a).toInt(); xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        })
    }
    // grano fino
    val grain = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    grain.setPixels(IntArray(w * h) { val v = 128 + (rnd.nextGaussian() * 30).toInt().coerceIn(-70, 70); android.graphics.Color.rgb(v, v, v) }, 0, w, 0, 0, w, h)
    c.drawBitmap(grain, 0f, 0f, Paint().apply { alpha = 26; xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY) })
    // luz: más clara arriba a la izquierda, un poco más oscura abajo
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), 0x10FFF8EC, 0x1F000000, Shader.TileMode.CLAMP)
    })
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
        shader = RadialGradient(w * .3f, h * .2f, w * .5f, 0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    })
    // caras de la estufa: la derecha en sombra, la izquierda apenas iluminada, aristas suaves
    // Vértices medidos sobre una foto de frente de la tele en la estufa (fracción del ancho de pantalla):
    // arriba coinciden con las aristas de la columna; abajo siguen la pendiente del cono (fuera de cuadro).
    val topL = w * (ESTUFA_CENTER - edgeTop / 1000f); val topR = w * (ESTUFA_CENTER + edgeTop / 1000f)
    val botL = w * (ESTUFA_CENTER - edgeBottom / 1000f); val botR = w * (ESTUFA_CENTER + edgeBottom / 1000f)
    val right = android.graphics.Path().apply { moveTo(topR, 0f); lineTo(w.toFloat(), 0f); lineTo(w.toFloat(), h.toFloat()); lineTo(botR, h.toFloat()); close() }
    val left = android.graphics.Path().apply { moveTo(0f, 0f); lineTo(topL, 0f); lineTo(botL, h.toFloat()); lineTo(0f, h.toFloat()); close() }
    c.drawPath(right, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(topR, 0f, w.toFloat(), 0f, 0x26000000, 0x0F000000, Shader.TileMode.CLAMP)
    })
    c.drawPath(left, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(0f, 0f, topL, 0f, 0x08000000, 0x12FFFFFF, Shader.TileMode.CLAMP)
    })
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; color = 0x22000000
        maskFilter = android.graphics.BlurMaskFilter(4f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    c.drawLine(topR, 0f, botR, h.toFloat(), edge)
    c.drawLine(topL, 0f, botL, h.toFloat(), edge.apply { color = 0x14000000 })
    // sombra del tocadiscos sobre la pared (composición centrada: gabinete en ~505..1415 x 235..865 de 1920x1080)
    c.drawRoundRect(RectF(260f, 140f, 706f, 452f), 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x5A2A1C12
        maskFilter = android.graphics.BlurMaskFilter(26f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    })
    out.asImageBitmap()
}

private fun withAlpha(argb: Int, a: Float) = (argb and 0x00FFFFFF) or ((255 * a).toInt() shl 24)
