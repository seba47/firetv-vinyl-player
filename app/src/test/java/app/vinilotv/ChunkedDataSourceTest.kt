package app.vinilotv

import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkedDataSourceTest {
    @Test fun leeElTamañoTotalDeContentRange() {
        assertEquals(3456789L, ChunkedDataSource.totalFrom(mapOf("Content-Range" to listOf("bytes 0-1048575/3456789"))))
        assertEquals(42L, ChunkedDataSource.totalFrom(mapOf("content-range" to listOf("bytes 0-9/42"))))
        assertEquals(-1L, ChunkedDataSource.totalFrom(mapOf("Content-Range" to listOf("bytes 0-9/*"))))
        assertEquals(-1L, ChunkedDataSource.totalFrom(emptyMap()))
    }
}
