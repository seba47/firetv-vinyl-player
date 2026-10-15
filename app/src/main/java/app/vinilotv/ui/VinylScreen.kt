package app.vinilotv.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import app.vinilotv.QueueItem
import app.vinilotv.Radio
import app.vinilotv.RadioState
import app.vinilotv.Seed
import app.vinilotv.SeedKind
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

/** Unidades de diseño: píxeles de una tele a 1080p, escalados a la pantalla real. */
private class Stage(val k: Float, private val density: Density) {
    fun dp(px: Float): Dp = with(density) { (px * k).toDp() }
    fun sp(px: Float): TextUnit = with(density) { (px * k).toSp() }
}

private fun Modifier.place(s: Stage, x: Float, y: Float, w: Float, h: Float) =
    offset(s.dp(x), s.dp(y)).size(s.dp(w), s.dp(h))

/** Estado de la animación del tocadiscos (cambia en cada cuadro). */
private class DeckMotion {
    var recordAngle by mutableFloatStateOf(0f)
    var speed = 0f
    var armAngle by mutableFloatStateOf(ARM_REST)
    var armVel = 0f
    var armTarget = ARM_REST
    var lift by mutableFloatStateOf(1f)
    var liftVel = 0f
    var liftTarget = 1f
    var sequence = false              // un cambio de tema está moviendo el brazo "a mano"
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)

    fun step(dt: Float, spinning: Boolean) {
        val target = if (spinning) 200f else 0f                              // 33⅓ rpm = 200°/s
        speed += (target - speed) * (dt * if (spinning) 1.6f else 1.1f).coerceAtMost(1f)
        recordAngle = (recordAngle + speed * dt) % 360f
        // giro del brazo: resorte casi amortiguado (inercia y se acomoda)
        val k = 16f; val c = 2f * sqrt(k) * .78f
        armVel += ((armTarget - armAngle) * k - armVel * c) * dt
        armAngle += armVel * dt
        // altura: sube rápido; baja despacio como la palanca hidráulica, con un rebotito
        val down = liftTarget < lift
        val kh = if (down) 7f else 45f; val ch = 2f * sqrt(kh) * (if (down) .62f else 1f)
        liftVel += ((liftTarget - lift) * kh - liftVel * ch) * dt
        lift += liftVel * dt
    }
}

/** Ángulo del brazo según el avance: dentro de un disco avanza tema a tema hacia el centro. */
private fun progressAngle(item: QueueItem?, positionMs: Long, durationMs: Long): Float {
    val p = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val frac = if (item != null && item.albumIndex >= 0 && item.albumSize > 0) (item.albumIndex + p) / item.albumSize else p
    return ARM_START + (ARM_END - ARM_START) * frac
}

private fun continuesAlbum(prev: QueueItem?, next: QueueItem?) =
    prev != null && next != null && prev.albumIndex >= 0 && next.albumIndex == prev.albumIndex + 1

@Composable
fun VinylScreen(state: RadioState, pickerOpen: Boolean, idle: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Room)) {
        val k = constraints.maxWidth / 1920f
        val s = Stage(k, LocalDensity.current)
        val motion = remember { DeckMotion() }
        val current by rememberUpdatedState(state.current)
        val playing by rememberUpdatedState(state.playing && !state.loading)
        var labelUrl by remember { mutableStateOf<String?>(null) }
        val discAway = remember { Animatable(0f) }
        var lastItem by remember { mutableStateOf<QueueItem?>(null) }

        // Cuadro a cuadro: giro del disco, resortes del brazo y avance del tema.
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(.1f)
                last = now
                motion.positionMs = Radio.positionMs()
                motion.durationMs = Radio.durationMs()
                if (!motion.sequence) {
                    motion.armTarget = if (current == null) ARM_REST else progressAngle(current, motion.positionMs, motion.durationMs)
                    motion.liftTarget = if (playing) 0f else 1f        // en pausa el brazo se levanta
                }
                motion.step(dt, playing && motion.lift < .5f)
            }
        }

        // Cambio de tema: levanta, (cambia el disco si es otro), viaja y apoya.
        LaunchedEffect(state.current?.track?.videoId) {
            val next = state.current ?: return@LaunchedEffect
            val prev = lastItem
            lastItem = next
            val newCover = next.track.thumbnailUrl
            if (prev == null) { labelUrl = newCover; return@LaunchedEffect }
            if (continuesAlbum(prev, next) && labelUrl == newCover) return@LaunchedEffect   // el surco sigue solo
            motion.sequence = true
            motion.liftTarget = 1f
            delay(380)
            if (newCover != labelUrl) {
                motion.armTarget = ARM_REST + 1.5f
                delay(700)
                discAway.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
                labelUrl = newCover
                discAway.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
            }
            motion.armTarget = progressAngle(next, 0, 1)
            delay(1100)
            motion.sequence = false                                 // al sonar, el bucle lo apoya
        }

        val idleT by animateFloatAsState(if (idle) 1f else 0f, tween(2600, easing = FastOutSlowInEasing), label = "idle")
        val pickerT by animateFloatAsState(if (pickerOpen) 1f else 0f, tween(600, easing = FastOutSlowInEasing), label = "picker")

        // Luz ambiente: la tapa en 12 px estirada a toda la pantalla (= desenfocada).
        AmbientLight(state.current?.track?.thumbnailUrl)

        // Tocadiscos
        Box(
            Modifier.place(s, 80f, 120f, DECK_W, DECK_H).graphicsLayer {
                translationX = 380f * k * idleT
                translationY = -40f * k * idleT
                scaleX = 1f - .06f * idleT; scaleY = scaleX
            },
        ) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    rotationX = 24f
                    transformOrigin = TransformOrigin(.5f, 1f)
                    cameraDistance = 18f * density
                },
            ) {
                Box(Modifier.fillMaxSize().drawWithCache { onDrawBehind { scale(k, k, Offset.Zero) { drawPlinth() } } })
                // disco + etiqueta (se van juntos al cambiar de disco)
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val a = discAway.value
                        translationX = -60f * k * a; translationY = -90f * k * a
                        scaleX = 1f + .06f * a; scaleY = scaleX; alpha = 1f - a
                    },
                ) {
                    Box(Modifier.fillMaxSize().drawWithCache { onDrawBehind { scale(k, k, Offset.Zero) { drawRecord() } } })
                    AsyncImage(
                        model = labelUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,                  // recorta la tapa cuadrada del 16:9
                        modifier = Modifier
                            .place(s, PlatterCenter.x - LABEL_R, PlatterCenter.y - LABEL_R, LABEL_R * 2, LABEL_R * 2)
                            .graphicsLayer { rotationZ = motion.recordAngle }
                            .clip(CircleShape)
                            .background(Color(0xFF3A2A1D)),
                    )
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = .8f + .2f * sin(motion.recordAngle * PI.toFloat() / 90f) }
                        .drawWithCache { onDrawBehind { scale(k, k, Offset.Zero) { drawSheen() } } })
                }
                DeckCanvas(Modifier.fillMaxSize(), k) {
                    drawSpindle()
                    drawPilot(if (playing) 1f else 0f)
                    val onRecord = motion.lift < .03f && playing
                    val a = motion.recordAngle * PI.toFloat() / 180f
                    drawArm(
                        angle = motion.armAngle + if (onRecord) sin(a) * .07f else 0f,
                        lift = motion.lift,
                        bob = if (onRecord) sin(a * 2f) * .6f else 0f,
                    )
                }
                Engrave("33⅓ rpm", s, 20f, Palette.Cream.copy(alpha = .5f), Modifier.offset(s.dp(150f), s.dp(764f)))
            }
        }

        // Columna de lectura
        Column(
            Modifier.place(s, 1160f, 168f, 680f, 840f).graphicsLayer { alpha = (1f - idleT) * (1f - pickerT) },
        ) {
            AnimatedContent(
                targetState = state.current,
                contentKey = { it?.track?.videoId },
                transitionSpec = {
                    (fadeIn(tween(500, 250)) + slideInVertically(tween(600, 250)) { it / 12 }) togetherWith fadeOut(tween(400))
                },
                label = "tema",
            ) { item -> NowPlaying(item, state.seed, s) }
            Spacer(Modifier.height(s.dp(30f)))
            ProgressRow(motion.positionMs, motion.durationMs, s)
            Spacer(Modifier.height(s.dp(16f)))
            val status = when {
                state.loading -> "Cargando…"
                state.current != null && !state.playing -> "En pausa"
                else -> ""
            }
            Engrave(status, s, 20f, Palette.BrassLight)
            Spacer(Modifier.height(s.dp(58f)))
            UpNext(state, s)
        }

        // Ayuda del control
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = s.dp(80f), bottom = s.dp(50f))
                .graphicsLayer { alpha = 1f - idleT },
            horizontalArrangement = Arrangement.spacedBy(s.dp(34f)),
        ) {
            listOf("→" to "siguiente", "OK" to "pausa", "↑↓" to "estación", "≡" to "elegir música").forEach { (key, what) ->
                Row {
                    BasicText(key, style = body(s, 18f, Palette.Cream.copy(alpha = .5f), FontWeight.Medium))
                    Spacer(Modifier.width(s.dp(8f)))
                    BasicText(what, style = body(s, 18f, Palette.Cream.copy(alpha = .3f)))
                }
            }
        }

        // Salvapantallas: solo un epígrafe abajo
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = s.dp(64f)).graphicsLayer { alpha = idleT },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(state.current?.track?.title.orEmpty(), style = TextStyle(fontFamily = Fonts.Display, fontSize = s.sp(34f), color = Palette.Cream))
            Spacer(Modifier.height(s.dp(6f)))
            BasicText(state.current?.track?.artist.orEmpty(), style = body(s, 18f, Palette.Cream3))
        }

        Picker(state, s, pickerT)
        Toast(state.toast, s)
    }
}

@Composable
private fun AmbientLight(url: String?) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).size(16).precision(Precision.EXACT).memoryCacheKey("ambient:$url").crossfade(1800).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.15f; scaleY = 1.15f; alpha = .42f },
        )
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val vignette = Brush.radialGradient(
                    0f to Palette.Room.copy(alpha = .15f), .8f to Palette.Room.copy(alpha = .78f), 1f to Palette.Room.copy(alpha = .9f),
                    center = Offset(size.width * .32f, size.height * .55f), radius = size.width * .75f,
                )
                onDrawBehind { drawRect(vignette) }
            },
        )
    }
}

@Composable
private fun NowPlaying(item: QueueItem?, seed: Seed?, s: Stage) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            val (kind, name) = when {
                item == null -> "Vinilo" to ""
                item.auto -> "Enganchando" to "parecidos a ${seed?.title.orEmpty()}"
                else -> (seed?.kind?.label ?: "") to listOfNotNull(seed?.title, seed?.subtitle?.takeIf { seed.kind == SeedKind.ALBUM }).joinToString(" · ")
            }
            Engrave(kind, s, 24f, Palette.Brass, bold = true)
            Spacer(Modifier.width(s.dp(16f)))
            Engrave(name, s, 24f, Palette.Brass.copy(alpha = .78f), spacing = .14f)
        }
        Spacer(Modifier.height(s.dp(30f)))
        BasicText(
            item?.track?.title ?: "Elegí qué suena",
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Fonts.Display, fontSize = s.sp(78f), lineHeight = s.sp(81f), color = Palette.Cream, letterSpacing = (-.01f).em),
        )
        Spacer(Modifier.height(s.dp(20f)))
        BasicText(
            item?.track?.artist ?: "Apretá ≡ en el control y escaneá el código con el celular.",
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Fonts.Display, fontSize = s.sp(if (item == null) 30f else 42f), color = Palette.Cream2),
        )
        if (item != null && item.albumIndex >= 0 && item.albumSize > 1) {
            val half = ceil(item.albumSize / 2f).toInt()
            val side = if (item.albumIndex < half) "A" else "B"
            val n = if (side == "A") item.albumIndex + 1 else item.albumIndex - half + 1
            Spacer(Modifier.height(s.dp(26f)))
            Engrave("Lado $side · tema $n", s, 20f, Palette.Cream3)
        }
    }
}

@Composable
private fun ProgressRow(positionMs: Long, durationMs: Long, s: Stage) {
    fun fmt(ms: Long) = (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(fmt(positionMs), style = body(s, 20f, Palette.Cream3))
        Spacer(Modifier.width(s.dp(20f)))
        Canvas(Modifier.weight(1f).height(s.dp(3f))) {
            drawRect(Palette.Cream4)
            drawRect(Palette.Brass, size = Size(size.width * frac, size.height))
        }
        Spacer(Modifier.width(s.dp(20f)))
        BasicText(fmt(durationMs), style = body(s, 20f, Palette.Cream3))
    }
}

@Composable
private fun UpNext(state: RadioState, s: Stage) {
    val cur = state.current ?: return
    Column {
        Engrave("Lo que viene", s, 18f, Palette.Cream3)
        Spacer(Modifier.height(s.dp(14f)))
        val firstAuto = if (cur.auto) -1 else state.upNext.indexOfFirst { it.auto }
        var rows = 0
        state.upNext.forEachIndexed { i, item ->
            if (rows >= 3) return@forEachIndexed
            if (i == firstAuto) {
                val what = when (state.seed?.kind) { SeedKind.ALBUM -> "el disco"; SeedKind.PLAYLIST -> "la playlist"; else -> "el tema" }
                QueueRow(null, "Termina $what: siguen parecidos al azar", null, s, Palette.Brass); rows++
            }
            if (rows < 3) { QueueRow(item.track.thumbnailUrl, item.track.title, item.track.artist, s, Palette.Cream2); rows++ }
        }
        if (state.upNext.isEmpty()) QueueRow(null, "Buscando parecidos…", null, s, Palette.Brass)
    }
}

@Composable
private fun QueueRow(thumb: String?, title: String, artist: String?, s: Stage, color: Color) {
    Row(Modifier.padding(vertical = s.dp(8f)), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(s.dp(52f)).clip(RoundedCornerShape(s.dp(3f))).background(Palette.Cream4),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) AsyncImage(thumb, null, Modifier.fillMaxSize().graphicsLayer { alpha = .85f }, contentScale = ContentScale.Crop)
            else BasicText("⤨", style = body(s, 26f, Palette.Brass))
        }
        Spacer(Modifier.width(s.dp(18f)))
        val text = buildString { append(title); if (artist != null) append("  ·  ").append(artist) }
        BasicText(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = body(s, 24f, color))
    }
}

@Composable
private fun Picker(state: RadioState, s: Stage, t: Float) {
    if (t <= 0f) return
    Box(
        Modifier.fillMaxSize().graphicsLayer { translationX = (1f - t) * 780f * s.k },
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            Modifier.width(s.dp(780f)).fillMaxHeightCompat()
                .background(Brush.horizontalGradient(0f to Color(0x00161210), (110f / 780f) to Color(0xF7161210), 1f to Color(0xF7161210)))
                .padding(start = s.dp(120f), end = s.dp(90f), top = s.dp(150f)),
        ) {
            BasicText("Elegí qué suena", style = TextStyle(fontFamily = Fonts.Display, fontSize = s.sp(60f), color = Palette.Cream))
            Spacer(Modifier.height(s.dp(18f)))
            BasicText("Escaneá con el celular y buscá un tema, un disco o una playlist.",
                style = body(s, 24f, Palette.Cream2).copy(lineHeight = s.sp(35f)))
            Spacer(Modifier.height(s.dp(44f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(s.dp(260f)).clip(RoundedCornerShape(s.dp(6f))).background(Palette.Cream).padding(s.dp(18f))) {
                    state.webUrl?.let { QrCode(it, Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.width(s.dp(36f)))
                Column {
                    BasicText("o entrá desde el navegador a", style = body(s, 22f, Palette.Cream3))
                    Spacer(Modifier.height(s.dp(6f)))
                    BasicText(state.webUrl?.removePrefix("http://") ?: "sin WiFi", style = body(s, 26f, Palette.Brass, FontWeight.Medium))
                }
            }
            if (state.recent.isNotEmpty()) {
                Spacer(Modifier.height(s.dp(64f)))
                Engrave("Estaciones recientes · ↑↓", s, 18f, Palette.Cream3)
                Spacer(Modifier.height(s.dp(14f)))
                state.recent.take(6).forEach { seed ->
                    val on = seed.url == state.seed?.url
                    Row(Modifier.padding(vertical = s.dp(9f)), verticalAlignment = Alignment.CenterVertically) {
                        Engrave(seed.kind.label, s, 17f, Palette.Brass, modifier = Modifier.width(s.dp(104f)))
                        BasicText(seed.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = body(s, 24f, if (on) Palette.Cream else Palette.Cream3))
                    }
                }
            }
        }
    }
}

private fun Modifier.fillMaxHeightCompat() = this.then(Modifier.fillMaxSize())

@Composable
private fun QrCode(text: String, modifier: Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    }
    Canvas(modifier) {
        val n = matrix.width
        val cell = size.minDimension / n
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix[x, y]) drawRect(Palette.WalnutDeep, Offset(x * cell, y * cell), Size(cell + .5f, cell + .5f))
        }
    }
}

@Composable
private fun Toast(toast: Pair<Long, String>?, s: Stage) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(toast?.first) {
        if (toast == null) return@LaunchedEffect
        visible = true; delay(2800); visible = false
    }
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(300), label = "toast")
    if (a <= 0f || toast == null) return
    Box(Modifier.fillMaxSize().padding(bottom = s.dp(120f)), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 20f * s.k }
                .clip(RoundedCornerShape(s.dp(40f))).background(Color(0xF22E221A))
                .padding(horizontal = s.dp(28f), vertical = s.dp(16f)),
        ) { BasicText(toast.second, style = body(s, 24f, Palette.Cream)) }
    }
}

@Composable
private fun Engrave(
    text: String, s: Stage, sizePx: Float, color: Color, modifier: Modifier = Modifier,
    bold: Boolean = false, spacing: Float = .24f,
) = BasicText(
    text.uppercase(), modifier, maxLines = 1, overflow = TextOverflow.Ellipsis,
    style = TextStyle(fontFamily = Fonts.Engrave, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        fontSize = s.sp(sizePx), letterSpacing = spacing.em, color = color),
)

private fun body(s: Stage, sizePx: Float, color: Color, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Fonts.Body, fontWeight = weight, fontSize = s.sp(sizePx), color = color)
