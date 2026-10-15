package app.vinilotv

import org.junit.Assert.assertEquals
import org.junit.Test

class TidyTest {
    private fun t(title: String, artist: String) = YouTube.tidy(title, artist)

    @Test fun sacaRemasterYVideoOficial() {
        assertEquals("De Música Ligera" to "Soda Stereo", t("De Música Ligera (Remasterizado 2007)", "Soda Stereo"))
        assertEquals("Thunderstruck" to "AC/DC", t("AC/DC - Thunderstruck (Official Video)", "AC/DC"))
    }

    @Test fun dejaVersionesEnVivo() {
        assertEquals("Entre Caníbales (MTV Unplugged)" to "Soda Stereo", t("Entre Caníbales (MTV Unplugged)", "Soda Stereo"))
    }

    @Test fun separaArtistaDeCanalesVevo() {
        assertEquals("Crimen" to "Gustavo Cerati", t("Gustavo Cerati - Crimen", "GustavoCeratiVEVO"))
    }

    @Test fun noTocaGuionesQueSonDelTitulo() {
        assertEquals("Tema - Parte 2" to "Banda", t("Tema - Parte 2", "Banda"))
    }
}
