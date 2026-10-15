package app.vinilotv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.vinilotv.R

// Paleta apagada y cálida (ver design/mockup/vistoso.html).
object Palette {
    val Room = Color(0xFF1B1613)        // grafito cálido del fondo
    val Walnut = Color(0xFF5B3B25)      // nogal del gabinete
    val WalnutMid = Color(0xFF452B1A)
    val WalnutDeep = Color(0xFF2E1C10)
    val Cream = Color(0xFFEEE3CF)       // texto
    val Cream2 = Cream.copy(alpha = .64f)
    val Cream3 = Cream.copy(alpha = .36f)
    val Cream4 = Cream.copy(alpha = .14f)
    val Brass = Color(0xFFC2924F)       // latón: estación, progreso, detalles
    val BrassLight = Color(0xFFE3BD85)
    val Vinyl = Color(0xFF111010)
    // tinta para escribir sobre la pared de yeso (fondo estufa)
    val Ink = Color(0xFF3B2F26)
    val Ink2 = Color(0xFF3B2F26).copy(alpha = .62f)
    val InkBrass = Color(0xFF8A5F2C)
}

// Tipografías empaquetadas (OFL, licencias en docs/licencias): en el Fire TV no hay fuentes
// descargables de Google Play Services.
object Fonts {
    /** Títulos: serif con aire de tapa de disco de los 70. */
    val Display = FontFamily(Font(R.font.young_serif))

    /** Texto general. */
    @OptIn(ExperimentalTextApi::class)
    val Body = FontFamily(
        Font(R.font.familjen_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.familjen_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    )

    /** Etiquetas en mayúsculas, como grabado de equipo hi-fi. */
    @OptIn(ExperimentalTextApi::class)
    val Engrave = FontFamily(
        Font(R.font.big_shoulders_text, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.big_shoulders_text, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
}
