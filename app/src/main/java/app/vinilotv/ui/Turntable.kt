package app.vinilotv.ui

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.RoundRect
import kotlin.random.Random

// Dibujo del tocadiscos en coordenadas del gabinete (1000 x 860 "px" de la tele a 1080p).
// Las funciones reciben un DrawScope ya escalado a esas unidades.

const val DECK_W = 1000f
const val DECK_H = 860f
val PlatterCenter = Offset(420f, 430f)
const val PLATTER_R = 360f
const val RECORD_R = 346f
const val LABEL_R = 114f
val ArmPivot = Offset(870f, 120f)

/** Ángulos del brazo: en el apoyabrazo, en el borde del disco y al final de los surcos. */
const val ARM_REST = 0f
const val ARM_START = 8f
const val ARM_END = 29f

/** Gabinete de nogal con veta, canto frontal y sombra. Estático: se dibuja una sola vez. */
fun DrawScope.drawPlinth() {
    val body = RoundRect(0f, 0f, DECK_W, DECK_H, CornerRadius(20f))
    val shape = Path().apply { addRoundRect(body) }
    clipPath(shape) {
        drawRect(Brush.linearGradient(
            0f to Palette.Walnut, .55f to Palette.WalnutMid, 1f to Palette.WalnutDeep,
            start = Offset(0f, 0f), end = Offset(DECK_W * .7f, DECK_H * 1.3f),
        ))
        // veta: franjas casi verticales de ancho y tono al azar (semilla fija = siempre igual)
        val rnd = Random(47)
        var x = 0f
        while (x < DECK_W) {
            val w = 1.5f + rnd.nextFloat() * 6f
            val c = if (rnd.nextBoolean()) Color.Black.copy(alpha = rnd.nextFloat() * .09f)
            else Color(0xFFFFDCB4).copy(alpha = rnd.nextFloat() * .045f)
            val tilt = rnd.nextFloat() * 6f - 3f
            drawLine(c, Offset(x, 0f), Offset(x + tilt, DECK_H), strokeWidth = w)
            x += w + rnd.nextFloat() * 5f
        }
        drawRect(Color(0xFFFFDCB4).copy(alpha = .12f), Offset.Zero, Size(DECK_W, 2f))   // filo de luz
        drawRect(Color(0xFF22150C), Offset(0f, DECK_H - 14f), Size(DECK_W, 14f))       // canto frontal
    }
    // plato: base oscura con borde metálico
    drawCircle(Color.Black.copy(alpha = .45f), PLATTER_R + 6f, PlatterCenter + Offset(0f, 16f))
    drawCircle(Color(0xFF3A3633), PLATTER_R, PlatterCenter)
    drawCircle(Color(0xFF8C867E), PLATTER_R - 4f, PlatterCenter, style = Stroke(2.5f))
    drawCircle(Color(0xFF2A2725), PLATTER_R - 8f, PlatterCenter, style = Stroke(3f))
    // perilla de velocidad
    val knob = Offset(102f, 778f)
    drawCircle(Color.Black.copy(alpha = .5f), 34f, knob + Offset(0f, 6f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF4A433D), Color(0xFF1F1B18)), knob - Offset(8f, 10f), 40f), 32f, knob)
    drawRoundRect(Palette.Brass, knob + Offset(-2f, -24f), Size(4f, 16f), CornerRadius(2f))
    // apoyabrazo
    drawRoundRect(Color.Black.copy(alpha = .45f), Offset(726f, 590f), Size(46f, 62f), CornerRadius(10f))
    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF2A2522), Color(0xFF4A433D), Color(0xFF2A2522)), 722f, 768f),
        Offset(722f, 580f), Size(46f, 62f), CornerRadius(10f))
    drawRoundRect(Color(0xFF15120F), Offset(737f, 586f), Size(16f, 10f), CornerRadius(4f))
}

/** Vinilo negro mate con surcos (sin la etiqueta, que gira aparte). Estático. */
fun DrawScope.drawRecord() {
    val c = PlatterCenter
    drawCircle(Palette.Vinyl, RECORD_R, c)
    var r = 172f
    while (r < RECORD_R - 3f) {                               // surcos
        drawCircle(Color.White.copy(alpha = .028f), r, c, style = Stroke(1f))
        r += 3f
    }
    drawCircle(Color(0xFF1A1717), 170f, c)                   // zona de salida
    drawCircle(Color(0xFF0B0A0A), 305f, c, style = Stroke(3f)) // separación entre temas
    drawCircle(Color(0xFF1B1818), RECORD_R - 1f, c, style = Stroke(2f))
    drawCircle(Color.Black.copy(alpha = .6f), LABEL_R + 2f, c, style = Stroke(5f))
}

/** Brillo del vinilo: queda quieto mientras el disco gira, como la luz real. */
fun DrawScope.drawSheen() {
    val brush = Brush.sweepGradient(
        0f to Color.Transparent, .083f to Color.Transparent, .153f to Color(0x0FFFF5E6), .236f to Color.Transparent,
        .583f to Color.Transparent, .653f to Color(0x1AFFF5E6), .736f to Color.Transparent, 1f to Color.Transparent,
        center = PlatterCenter,
    )
    drawCircle(brush, (120f + RECORD_R) / 2f, PlatterCenter, style = Stroke(RECORD_R - 120f))
}

/** Eje central. */
fun DrawScope.drawSpindle() {
    drawCircle(Color.Black.copy(alpha = .7f), 9f, PlatterCenter + Offset(0f, 1.5f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF2E6CF), Color(0xFF9A8F80), Color(0xFF4D463E)),
        PlatterCenter - Offset(3f, 3f), 10f), 8f, PlatterCenter)
}

/** Luz piloto: prendida mientras suena. */
fun DrawScope.drawPilot(on: Float) {
    val p = Offset(159f, 801f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE8A54C).copy(alpha = .55f * on), Color.Transparent), p, 18f), 18f, p)
    drawCircle(Color(0xFFE8A54C).copy(alpha = .2f + .8f * on), 5f, p)
}

/**
 * Brazo, palanca de elevación y contrapeso.
 * @param angle giro en grados (0 = apoyabrazo). @param lift 0 = púa apoyada, 1 = levantado.
 * @param bob vaivén vertical de la cápsula (copia el alabeo del disco).
 */
fun DrawScope.drawArm(angle: Float, lift: Float, bob: Float) {
    val h = lift.coerceIn(-.05f, 1.05f)
    val p = ArmPivot
    // base del brazo
    drawCircle(Color.Black.copy(alpha = .5f), 56f, p + Offset(0f, 10f))
    drawCircle(Brush.radialGradient(0f to Color(0xFF1C1815), .42f to Color(0xFF1C1815), .45f to Color(0xFF3F3833),
        .58f to Color(0xFF1D1916), 1f to Color(0xFF302A25), center = p, radius = 54f), 54f, p)
    // palanca de elevación: sube con el brazo
    rotate(-42f * h.coerceIn(0f, 1f), Offset(942f, 240f)) {
        drawRoundRect(Color.Black.copy(alpha = .4f), Offset(940f, 202f), Size(12f, 50f), CornerRadius(6f))
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF2A2522), Color(0xFF5F574F), Color(0xFF2A2522)), 936f, 948f),
            Offset(936f, 196f), Size(12f, 50f), CornerRadius(6f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFF0D29F), Palette.Brass, Color(0xFF6B4C25)), Offset(940f, 190f), 14f), 10f, Offset(942f, 192f))
    }

    val liftScale = 1f + .028f * h
    // sombra: se aleja y se desenfoca cuando el brazo sube
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(((.55f - .2f * h) * 255).toInt().coerceIn(0, 255), 0, 0, 0)
            maskFilter = BlurMaskFilter(7f + 14f * h, BlurMaskFilter.Blur.NORMAL)
        }
        nc.save()
        nc.translate(9f + 16f * h, 13f + 30f * h)
        nc.rotate(angle + 14f, p.x, p.y)
        nc.scale(liftScale, liftScale, p.x, p.y)
        nc.translate(p.x, p.y)
        nc.drawPath(armSilhouette(bob).asAndroidPath(), paint)
        nc.restore()
    }

    // contrapeso (gira con el brazo pero no con el ángulo del caño)
    withTransform({ rotate(angle, p); scale(liftScale, liftScale, p) }) {
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF6F6A64), Color(0xFFD6D0C7), Color(0xFF6F6A64)), p.x - 16f, p.x + 16f),
            p + Offset(-16f, -56f), Size(32f, 18f))
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF25201D), Color(0xFF5D554E), Color(0xFF342E2A), Color(0xFF1C1815)), p.x - 22f, p.x + 22f),
            p + Offset(-22f, -120f), Size(44f, 66f), CornerRadius(8f))
        var y = -116f
        while (y < -58f) { drawRect(Color.Black.copy(alpha = .2f), p + Offset(-22f, y), Size(44f, 2f)); y += 6f }
    }

    // caño, portacápsula, cápsula, púa y levantador
    withTransform({ rotate(angle + 14f, p); scale(liftScale, liftScale, p); translate(p.x, p.y) }) {
        drawRoundRect(
            Brush.horizontalGradient(0f to Color(0xFF77726B), .34f to Color(0xFFF6F2EB), .52f to Color(0xFFC3BDB4),
                .78f to Color(0xFF8D8881), 1f to Color(0xFF5C5752), startX = -6f, endX = 6f),
            Offset(-6f, 0f), Size(12f, 566f), CornerRadius(6f),
        )
        translate(0f, 552f + bob) {
            rotate(-22f, Offset.Zero) {
                drawPath(shellPath(), Brush.horizontalGradient(0f to Color(0xFF8C867F), .44f to Color(0xFFECE7DF),
                    .7f to Color(0xFFA69F97), 1f to Color(0xFF6A655F), startX = -27f, endX = 27f))
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF342E2A), Color(0xFF121010)), 50f, 86f),
                    Offset(-19f, 50f), Size(38f, 36f), CornerRadius(4f))
                drawRoundRect(Palette.Brass.copy(alpha = .7f), Offset(-12f, 59f), Size(24f, 4f), CornerRadius(2f))
                drawRoundRect(Color(0xFFE2DBCF), Offset(-1f, 86f), Size(2f, 8f), CornerRadius(1f))   // púa
                rotate(-14f, Offset(24f, 36f)) {
                    drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF8C6A37), Color(0xFFE8C48D)), 21f, 51f),
                        Offset(21f, 34f), Size(30f, 5f), CornerRadius(3f))
                }
            }
        }
    }

    // tapa del pivote (no se escala: es el eje)
    drawCircle(Color.Black.copy(alpha = .4f), 36f, p + Offset(0f, 4f))
    drawCircle(Brush.radialGradient(0f to Color(0xFFF0D29F), .42f to Palette.Brass, 1f to Color(0xFF6B4C25),
        center = p - Offset(8f, 11f), radius = 46f), 34f, p)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF48403A), Color(0xFF191512)), p - Offset(3f, 4f), 16f), 14f, p)
}

private fun shellPath() = Path().apply {
    moveTo(-9f, 0f); lineTo(9f, 0f); lineTo(27f, 74f); lineTo(-27f, 74f); close()
}

/** Silueta para la sombra, en coordenadas del caño (origen en el pivote, y a lo largo del caño). */
private fun armSilhouette(bob: Float) = Path().apply {
    addRoundRect(RoundRect(-6f, 0f, 6f, 566f, CornerRadius(6f)))
    val head = Path().apply {
        moveTo(-9f, 0f); lineTo(9f, 0f); lineTo(27f, 74f); lineTo(19f, 86f); lineTo(-19f, 86f); lineTo(-27f, 74f); close()
    }
    val m = android.graphics.Matrix().apply { setRotate(-22f); postTranslate(0f, 552f + bob) }
    val ap = head.asAndroidPath().apply { transform(m) }
    asAndroidPath().addPath(ap)
}

/** Canvas en unidades del gabinete: escala [k] = píxeles reales por unidad. */
@androidx.compose.runtime.Composable
fun DeckCanvas(modifier: androidx.compose.ui.Modifier, k: Float, draw: DrawScope.() -> Unit) {
    Canvas(modifier) { scale(k, k, Offset.Zero) { draw() } }
}
