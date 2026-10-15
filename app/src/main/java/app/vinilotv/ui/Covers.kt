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
suspend fun bakeLabel(context: Context, url: String?, px: Int): ImageBitmap? {
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

private fun withAlpha(argb: Int, a: Float) = (argb and 0x00FFFFFF) or ((255 * a).toInt() shl 24)
