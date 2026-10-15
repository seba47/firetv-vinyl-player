package app.vinilotv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrateItemsTest {
    private fun seed(n: Int) = Seed(SeedKind.ALBUM, "Disco $n", "Banda", "https://music.youtube.com/playlist?list=L$n", null)

    @Test fun cajaConDiscosYRecientes() {
        val items = crateItems(recent = listOf(seed(1), seed(2), seed(3)), crate = listOf(seed(10), seed(11)), playingUrl = seed(1).url)
        // lo último (sin lo que está sonando), la caja, sus discos y la sorpresa
        assertEquals(CrateItem.Divider("Lo último", "Lo último que sonó"), items[0])
        assertEquals(listOf("Disco 2", "Disco 3"), items.subList(1, 3).map { (it as CrateItem.Record).seed.title })
        assertEquals("2 discos", (items[3] as CrateItem.Divider).note)
        assertEquals(CrateItem.Surprise, items.last())
        // al abrir arranca en el primer disco cargado, no en lo último
        assertEquals("Disco 10", (items[crateStartIndex(items)] as CrateItem.Record).seed.title)
    }

    @Test fun cajaVaciaExplicaComoCargarla() {
        val items = crateItems(recent = listOf(seed(1)), crate = emptyList(), playingUrl = null)
        val empty = items.last() as CrateItem.Divider
        assertEquals("La caja está vacía", empty.text)
        assertTrue(empty.note!!.contains("celular"))
        assertTrue(items.none { it == CrateItem.Surprise })                 // sin discos no hay sorpresa
        assertEquals("Disco 1", (items[crateStartIndex(items)] as CrateItem.Record).seed.title)
    }

    @Test fun sinNadaQuedaSoloElSeparador() {
        val items = crateItems(emptyList(), emptyList(), null)
        assertEquals(1, items.size)
        assertEquals(0, crateStartIndex(items))
    }
}
