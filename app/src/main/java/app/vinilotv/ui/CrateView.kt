package app.vinilotv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.core.content.res.ResourcesCompat
import app.vinilotv.CrateItem
import app.vinilotv.R
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

// La caja de discos: un cajón de fruta de madera abajo de la pantalla, con los discos parados.
// Se revuelve hacia adelante (← →): el de adelante se vuelca hacia uno y aparece el siguiente.
// Todo lo dibujado (madera, fundas, cartón) se pinta una vez; por cuadro solo se mueve.

// Medidas de diseño de la caja (los dibujos usan estas); en pantalla todo va escalado por SCALE.
private const val SCALE = 1.25f                         // discos +25 % (se ven mejor desde el sillón)
private const val CRATE_W = 760f
private const val CRATE_H = 420f
private const val CRATE_X = 960f - CRATE_W * SCALE / 2f        // en unidades de 1920x1080
private const val CRATE_Y = 1080f - CRATE_H * SCALE
private const val FRONT_H = 160f
private const val SLEEVE = 300f
private const val DIVIDER_H = 316f

/**
 * @param index disco en foco. @param picking el de adelante "sale volando" al tocadiscos.
 * @param estufaT 0 = fondo living (texto crema), 1 = fondo estufa (texto en tinta).
 */
@Composable
internal fun CrateView(open: Boolean, items: List<CrateItem>, index: Int, picking: Boolean, estufaT: Float, s: Stage) {
    val openT by animateFloatAsState(if (open) 1f else 0f, tween(650), label = "caja")
    if (openT <= 0.001f || items.isEmpty()) return
    val cursor by animateFloatAsState(index.toFloat(), spring(dampingRatio = .82f, stiffness = 260f), label = "revolver")
    val pickT by animateFloatAsState(if (picking) 1f else 0f, tween(450), label = "poner")
    val context = LocalContext.current
    val k = s.k
    val cache = remember { HashMap<String, ImageBitmap>() }

    Box(Modifier.fillMaxSize().graphicsLayer { translationY = (1f - openT) * 470f * k }) {
        // fondo del cajón (postes y el hueco oscuro), atrás de los discos
        Box(Modifier.place(s, CRATE_X, CRATE_Y, CRATE_W * SCALE, CRATE_H * SCALE).zIndex(0f).drawWithCache {
            val img = bakeCrateBack(k * SCALE)
            onDrawBehind { drawImage(img) }
        })
        // discos: solo los que se ven (dos volcados adelante y siete atrás)
        val lo = (index - 3).coerceAtLeast(0)
        val hi = (index + 8).coerceAtMost(items.lastIndex)
        for (i in lo..hi) key(i, sleeveKey(items[i])) {          // cada funda atada a su disco (si no, reusa la imagen de otra)
            val item = items[i]
            val h = if (item is CrateItem.Divider) DIVIDER_H else SLEEVE
            val key = sleeveKey(item)
            val img by produceState(cache[key], key) {
                value = cache[key] ?: bakeSleeveFor(context, item, (SLEEVE * SCALE * k).toInt(), (h * SCALE * k).toInt())?.also { cache[key] = it }
            }
            val d = i - cursor
            val tilt = (abs(key.hashCode()) % 25 - 12) / 10f                 // cada funda apenas torcida
            Box(
                Modifier.place(s, 960f - SLEEVE * SCALE / 2f, 1080f - (60f + h) * SCALE, SLEEVE * SCALE, h * SCALE)
                    .zIndex(100f - d)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(.5f, 1f)
                        cameraDistance = 14f * density
                        if (d >= 0f) {
                            translationY = -22f * SCALE * d * k
                            val sc = 1f - .03f * d
                            scaleX = sc; scaleY = sc
                            rotationX = -6f * (1f - min(d, 1f))
                            alpha = (6f - d).coerceIn(0f, 1f)                   // se ven hasta 5 atrás
                        } else {
                            val t = min(-d, 1f)
                            translationY = 160f * SCALE * t * k
                            rotationX = -6f - 72f * t
                            scaleX = 1f - .04f * t; scaleY = scaleX
                            alpha = .92f * (1f - (-d - 1f).coerceIn(0f, 1f))
                        }
                        rotationZ = tilt * (1f - abs(d).coerceAtMost(1f) * .3f)
                        if (picking && i == index) {                             // sale volando al plato
                            translationY -= 380f * pickT * k
                            scaleX *= 1f - .45f * pickT; scaleY = scaleX
                            alpha *= 1f - pickT
                        }
                    },
            ) {
                val dim = if (d > 0f) min(d, 6f) * .11f else min(-d, 1f) * .3f
                ComposeCanvas(Modifier.fillMaxSize()) {
                    val b = img
                    if (b == null) drawRect(Color(0xFF3A2A1D)) else drawImage(b, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
                    if (dim > 0f) drawRect(Color.Black, alpha = dim)
                }
            }
        }
        // frente del cajón, adelante de todo
        Box(Modifier.place(s, CRATE_X, 1080f - FRONT_H * SCALE, CRATE_W * SCALE, FRONT_H * SCALE).zIndex(300f).drawWithCache {
            val img = bakeCrateFront(context, k * SCALE)
            onDrawBehind { drawImage(img) }
        })
        // qué disco está en foco, a la derecha del cajón
        val cur = items.getOrNull(index)
        val ink = { a: Color, b: Color -> lerp(a, b, estufaT) }
        Column(Modifier.place(s, CRATE_X + CRATE_W * SCALE + 34f, 1080f - 370f, 420f, 320f).zIndex(310f).graphicsLayer { alpha = openT * (1f - pickT) }) {
            val (kind, title, sub) = when (cur) {
                is CrateItem.Record -> Triple(if (cur.recent) "Lo último que sonó · ${cur.seed.kind.label}" else cur.seed.kind.label, cur.seed.title, cur.seed.subtitle)
                is CrateItem.Divider -> Triple("Separador", cur.text, cur.note ?: "Seguí revolviendo")
                CrateItem.Surprise -> Triple("Sorpresa", "Que elija la caja", "Un disco al azar de la caja")
                null -> Triple("", "", "")
            }
            BasicText(kind.uppercase(), maxLines = 1, style = TextStyle(fontFamily = Fonts.Engrave, fontWeight = FontWeight.Medium,
                fontSize = s.sp(17f), letterSpacing = androidx.compose.ui.unit.TextUnit(.24f, androidx.compose.ui.unit.TextUnitType.Em), color = ink(Palette.Brass, Palette.InkBrass)))
            Spacer(Modifier.height(s.dp(8f)))
            BasicText(title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Fonts.Display, fontSize = s.sp(40f), lineHeight = s.sp(44f), color = ink(Palette.Cream, Palette.Ink)))
            Spacer(Modifier.height(s.dp(6f)))
            BasicText(sub, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Fonts.Body, fontSize = s.sp(20f), color = ink(Palette.Cream2, Palette.Ink2)))
            Spacer(Modifier.height(s.dp(12f)))
            val hint = if (cur is CrateItem.Divider) "← → revolver · Atrás cierra" else "OK lo pone · ← → revolver · Atrás cierra"
            BasicText(hint, style = TextStyle(fontFamily = Fonts.Body, fontSize = s.sp(17f), color = ink(Palette.Cream3, Palette.Ink2)))
        }
    }
}

private fun sleeveKey(item: CrateItem) = when (item) {
    is CrateItem.Record -> "r:" + (item.seed.thumbnailUrl ?: item.seed.url)
    is CrateItem.Divider -> "d:${item.tab}|${item.text}|${item.note}"
    CrateItem.Surprise -> "s"
}

private suspend fun bakeSleeveFor(context: Context, item: CrateItem, w: Int, h: Int): ImageBitmap? = when (item) {
    is CrateItem.Record -> bakeSleeve(context, item.seed.thumbnailUrl, w)
    is CrateItem.Divider -> withContext(Dispatchers.Default) { bakeDivider(context, w, h, item) }
    CrateItem.Surprise -> withContext(Dispatchers.Default) { bakeSurprise(context, w) }
}

// ------------------------------------------------------------------ dibujos (una vez)

private fun hand(context: Context, weight: Int): Typeface? =
    ResourcesCompat.getFont(context, R.font.caveat)

private fun Paint.wght(w: Int) = apply { fontVariationSettings = "'wght' $w" }

/** Funda de un disco con uso: tapa, círculo gastado por el disco, bordes rozados. */
private suspend fun bakeSleeve(context: Context, url: String?, px: Int): ImageBitmap? {
    if (url == null) return null
    val req = ImageRequest.Builder(context).data(url).size(px).allowHardware(false).build()
    val src = (SingletonImageLoader.get(context).execute(req) as? SuccessResult)?.image?.toBitmap() ?: return null
    return withContext(Dispatchers.Default) {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val side = min(src.width, src.height)
        val sx = (src.width - side) / 2; val sy = (src.height - side) / 2
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * .012f, px * .012f, Path.Direction.CW) }
        c.clipPath(clip)
        c.drawBitmap(src, Rect(sx, sy, sx + side, sy + side), Rect(0, 0, px, px), Paint(Paint.FILTER_BITMAP_FLAG))
        // círculo gastado: la marca que deja el disco de tanto entrar y salir
        c.drawCircle(px / 2f, px / 2f, px * .43f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = px * .05f; color = 0x14FFFFFF
            maskFilter = BlurMaskFilter(px * .02f, BlurMaskFilter.Blur.NORMAL)
        })
        // bordes rozados y un poco de sombra de papel
        c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), Paint().apply {
            shader = RadialGradient(px / 2f, px / 2f, px * .75f, intArrayOf(0x00000000, 0x00000000, 0x33000000), floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP)
        })
        val rnd = Random(url.hashCode())
        val wear = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF; strokeWidth = px * .006f }
        repeat(18) {
            val edge = rnd.nextInt(4); val t = rnd.nextFloat() * px; val len = px * (.02f + rnd.nextFloat() * .06f)
            when (edge) {
                0 -> c.drawLine(t, 1f, t + len, 1f, wear)
                1 -> c.drawLine(t, px - 1f, t + len, px - 1f, wear)
                2 -> c.drawLine(1f, t, 1f, t + len, wear)
                else -> c.drawLine(px - 1f, t, px - 1f, t + len, wear)
            }
        }
        out.asImageBitmap()
    }
}

private fun kraft(c: Canvas, w: Float, h: Float, top: Float, seed: Int) {
    c.drawRect(0f, top, w, h, Paint().apply {
        shader = LinearGradient(0f, top, w, h, intArrayOf(0xFFC9A679.toInt(), 0xFFB48E62.toInt(), 0xFFA27C52.toInt()), null, Shader.TileMode.CLAMP)
    })
    val rnd = Random(seed)
    val fiber = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
    repeat((w * h / 900).toInt()) {                              // fibras del cartón
        val x = rnd.nextFloat() * w; val y = top + rnd.nextFloat() * (h - top)
        fiber.color = if (rnd.nextBoolean()) 0x18000000 else 0x14FFFFFF
        c.drawLine(x, y, x + rnd.nextFloat() * 8f - 4f, y + rnd.nextFloat() * 3f, fiber)
    }
}

/** Separador de cartón con pestaña, escrito con fibrón. */
private fun bakeDivider(context: Context, w: Int, h: Int, d: CrateItem.Divider): ImageBitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val u = w / SLEEVE                                           // px por unidad
    val tab = 34f * u
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE02B1D14.toInt(); typeface = hand(context, 700); textAlign = Paint.Align.CENTER }.wght(700)
    // pestaña
    ink.textSize = 30f * u
    val tabW = ink.measureText(d.tab) + 40f * u
    val tabPath = Path().apply { addRoundRect(RectF(22f * u, 0f, 22f * u + tabW, tab + 8f * u), floatArrayOf(10f * u, 10f * u, 10f * u, 10f * u, 0f, 0f, 0f, 0f), Path.Direction.CW) }
    c.save(); c.clipPath(tabPath); kraft(c, w.toFloat(), tab + 8f * u, 0f, d.tab.hashCode()); c.restore()
    c.drawText(d.tab, 22f * u + tabW / 2f, tab - 6f * u, ink)
    // cuerpo
    kraft(c, w.toFloat(), h.toFloat(), tab, d.text.hashCode())
    c.drawRect(0f, tab, w.toFloat(), tab + 2f * u, Paint().apply { color = 0x22000000 })
    c.save(); c.rotate(-2.5f, w / 2f, h / 2f)
    ink.textSize = 54f * u
    val lines = wrap(d.text, ink, w - 50f * u)
    var y = h * .46f - (lines.size - 1) * 30f * u
    lines.forEach { c.drawText(it, w / 2f, y, ink); y += 60f * u }
    d.note?.let { note ->
        val small = Paint(ink).apply { textSize = 28f * u; color = 0xB02B1D14.toInt() }.wght(500)
        wrap(note, small, w - 60f * u).forEach { c.drawText(it, w / 2f, y + 6f * u, small); y += 32f * u }
    }
    c.restore()
    // subrayado de fibrón
    c.drawLine(w * .3f, h * .46f + 16f * u, w * .7f, h * .46f + 10f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x882B1D14.toInt(); strokeWidth = 3f * u; strokeCap = Paint.Cap.ROUND })
    return out.asImageBitmap()
}

/** Sobre de papel madera con un "?" a mano: la sorpresa. */
private fun bakeSurprise(context: Context, px: Int): ImageBitmap {
    val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val u = px / SLEEVE
    kraft(c, px.toFloat(), px.toFloat(), 0f, 77)
    // solapa del sobre
    val flap = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * u; color = 0x30000000 }
    c.drawLine(0f, 0f, px / 2f, px * .32f, flap); c.drawLine(px.toFloat(), 0f, px / 2f, px * .32f, flap)
    // sello de goma
    c.drawCircle(px * .76f, px * .78f, 40f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * u; color = 0x55A33A2A })
    val stamp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66A33A2A; textSize = 16f * u; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; letterSpacing = .1f }
    c.drawText("AL AZAR", px * .76f, px * .78f + 6f * u, stamp)
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD02B1D14.toInt(); typeface = hand(context, 700); textAlign = Paint.Align.CENTER; textSize = 190f * u }.wght(700)
    c.save(); c.rotate(-6f, px / 2f, px / 2f)
    c.drawText("?", px * .46f, px * .66f, ink)
    c.drawText("sorpresa", px * .40f, px * .86f, Paint(ink).apply { textSize = 40f * u }.wght(600))
    c.restore()
    return out.asImageBitmap()
}

private fun wrap(text: String, p: Paint, max: Float): List<String> {
    val words = text.split(" "); val lines = mutableListOf<String>(); var cur = ""
    for (w in words) { val t = if (cur.isEmpty()) w else "$cur $w"; if (p.measureText(t) <= max) cur = t else { if (cur.isNotEmpty()) lines += cur; cur = w } }
    if (cur.isNotEmpty()) lines += cur
    return lines
}

/** Madera de cajón: tabla con veta, nudos y luz de arriba. */
private fun plank(c: Canvas, r: RectF, base: Int, rnd: Random, vertical: Boolean = false) {
    c.save(); c.clipRect(r)
    c.drawRect(r, Paint().apply {
        shader = if (vertical) LinearGradient(r.left, 0f, r.right, 0f, intArrayOf(shade(base, -.25f), base, shade(base, -.35f)), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        else LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(shade(base, .12f), base, shade(base, -.28f)), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
    })
    val grain = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val n = ((if (vertical) r.width() else r.height()) / 3f).toInt()
    repeat(n) {
        grain.strokeWidth = .6f + rnd.nextFloat() * 1.4f
        grain.color = if (rnd.nextFloat() < .7f) ((rnd.nextInt(28) + 10) shl 24) else ((rnd.nextInt(12) + 4) shl 24) or 0xFFE0C0
        val p = Path()
        if (vertical) {
            val x = r.left + rnd.nextFloat() * r.width(); p.moveTo(x, r.top)
            var y = r.top; while (y < r.bottom) { y += 40f; p.quadTo(x + rnd.nextFloat() * 4f - 2f, y - 20f, x + rnd.nextFloat() * 3f - 1.5f, y) }
        } else {
            val y = r.top + rnd.nextFloat() * r.height(); p.moveTo(r.left, y)
            var x = r.left; while (x < r.right) { x += 60f; p.quadTo(x - 30f, y + rnd.nextFloat() * 5f - 2.5f, x, y + rnd.nextFloat() * 3f - 1.5f) }
        }
        c.drawPath(p, grain)
    }
    if (!vertical && rnd.nextFloat() < .8f) {                        // un nudo
        val kx = r.left + 60f + rnd.nextFloat() * (r.width() - 120f); val ky = r.centerY() + rnd.nextFloat() * 8f - 4f
        val kr = 6f + rnd.nextFloat() * 7f
        c.drawOval(RectF(kx - kr * 2.2f, ky - kr, kx + kr * 2.2f, ky + kr), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(kx, ky, kr * 2.2f, intArrayOf(0xAA2A170B.toInt(), 0x552A170B, 0x00000000), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawOval(RectF(kx - kr * 3.4f, ky - kr * 1.6f, kx + kr * 3.4f, ky + kr * 1.6f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.2f; color = 0x332A170B
        })
    }
    c.drawLine(r.left, r.top + .5f, r.right, r.top + .5f, Paint().apply { color = 0x30FFDCB4; strokeWidth = 1.5f })
    c.restore()
}

private fun shade(c: Int, f: Float): Int {
    fun ch(v: Int) = (if (f >= 0) v + (255 - v) * f else v * (1 + f)).toInt().coerceIn(0, 255)
    return android.graphics.Color.argb(255, ch((c shr 16) and 255), ch((c shr 8) and 255), ch(c and 255))
}

private fun nail(c: Canvas, x: Float, y: Float) {
    c.drawCircle(x, y + 1f, 4.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 })
    c.drawCircle(x, y, 3.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(x - 1.2f, y - 1.2f, 4.5f, intArrayOf(0xFF8C847A.toInt(), 0xFF3A3530.toInt()), null, Shader.TileMode.CLAMP)
    })
}

/** Fondo del cajón: los postes de las esquinas y el hueco oscuro detrás de los discos. */
private fun bakeCrateBack(k: Float): ImageBitmap {
    val out = Bitmap.createBitmap((CRATE_W * k).toInt().coerceAtLeast(1), (CRATE_H * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(out); c.scale(k, k)
    val rnd = Random(21)
    // tabla de atrás asomando entre los postes
    plank(c, RectF(30f, 170f, CRATE_W - 30f, 220f), 0xFF5A3A22.toInt(), rnd)
    c.drawRect(RectF(30f, 220f, CRATE_W - 30f, CRATE_H - FRONT_H + 10f), Paint().apply {
        shader = LinearGradient(0f, 220f, 0f, CRATE_H - FRONT_H, 0xFF1A110A.toInt(), 0xFF2A1A0F.toInt(), Shader.TileMode.CLAMP)
    })
    // postes de las esquinas, más altos que el frente
    for (x in listOf(0f, CRATE_W - 34f)) {
        plank(c, RectF(x, 150f, x + 34f, CRATE_H), 0xFF4E321D.toInt(), rnd, vertical = true)
        c.drawRect(RectF(x, 150f, x + 34f, 154f), Paint().apply { color = 0x40FFDCB4 })
    }
    return out.asImageBitmap()
}

/** Frente del cajón: tres tablas con luz entre ellas, clavos, manija calada y el estarcido gastado. */
private fun bakeCrateFront(context: Context, k: Float): ImageBitmap {
    val out = Bitmap.createBitmap((CRATE_W * k).toInt().coerceAtLeast(1), (FRONT_H * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(out); c.scale(k, k)
    val rnd = Random(7)
    c.drawRect(0f, 0f, CRATE_W, FRONT_H, Paint().apply { color = 0xFF140D08.toInt() })     // luz entre tablas
    val slats = listOf(0f to 48f, 54f to 102f, 108f to FRONT_H)
    val tones = listOf(0xFF6B4528.toInt(), 0xFF5E3D23.toInt(), 0xFF6F482A.toInt())
    slats.forEachIndexed { i, (t, b) -> plank(c, RectF(0f, t, CRATE_W, b), tones[i], rnd) }
    // manija calada en la tabla de arriba
    val handle = RectF(CRATE_W / 2f - 78f, 12f, CRATE_W / 2f + 78f, 36f)
    c.drawRoundRect(handle, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E0805.toInt() })
    c.drawRoundRect(RectF(handle.left, handle.top, handle.right, handle.top + 6f), 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 })
    // postes de las esquinas
    for (x in listOf(0f, CRATE_W - 34f)) plank(c, RectF(x, 0f, x + 34f, FRONT_H), 0xFF4E321D.toInt(), rnd, vertical = true)
    // clavos: dos por tabla en cada poste
    slats.forEach { (t, b) -> val y = (t + b) / 2f; nail(c, 17f, y); nail(c, CRATE_W - 17f, y) }
    // estarcido gastado en la tabla del medio
    val stencil = Bitmap.createBitmap(CRATE_W.toInt(), 48, Bitmap.Config.ARGB_8888)
    val sc = Canvas(stencil)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF24160C.toInt(); textAlign = Paint.Align.CENTER; textSize = 30f; letterSpacing = .32f
        typeface = ResourcesCompat.getFont(context, R.font.big_shoulders_text)
    }.wght(800)
    sc.drawText("VINILO · DISCOS", CRATE_W / 2f, 35f, text)
    val wear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    val wr = Random(3)
    repeat(260) {
        wear.alpha = 60 + wr.nextInt(195)
        sc.drawCircle(CRATE_W / 2f - 160f + wr.nextFloat() * 320f, 6f + wr.nextFloat() * 36f, .6f + wr.nextFloat() * 2.2f, wear)
    }
    c.drawBitmap(stencil, 0f, 54f, Paint().apply { alpha = 150 })
    // sellito chico abajo a la derecha
    c.drawText("33⅓", CRATE_W - 90f, 142f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x6624160C; textSize = 20f; typeface = ResourcesCompat.getFont(context, R.font.big_shoulders_text); letterSpacing = .2f
    }.wght(700))
    // sombra que el cajón tira hacia arriba sobre los discos
    c.drawRect(0f, 0f, CRATE_W, 3f, Paint().apply { color = 0x50FFDCB4 })
    return out.asImageBitmap()
}
