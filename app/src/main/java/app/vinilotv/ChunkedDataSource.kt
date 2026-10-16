package app.vinilotv

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource

/**
 * Pide el audio de YouTube de a pedazos acotados (Range: bytes=a-b) en vez de "todo de una".
 *
 * Algunas URLs de googlevideo (las del cliente de iOS, que usamos para los videos "hechos para
 * niños") responden 403 a un pedido abierto (sin Range o "bytes=0-") y solo aceptan rangos
 * cerrados. ExoPlayer pide abierto, así que este DataSource encadena pedidos de [CHUNK] bytes
 * sin que el reproductor se entere. Para las URLs normales también anda (y YouTube corta menos).
 */
class ChunkedDataSource(private val upstream: HttpDataSource.Factory) : BaseDataSource(true) {

    class Factory(private val upstream: HttpDataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream)
    }

    private var spec: DataSpec? = null
    private var inner: HttpDataSource? = null
    private var position = 0L
    private var end = -1L          // fin pedido por el reproductor (exclusivo), -1 = hasta el final
    private var total = -1L        // tamaño total del archivo (de Content-Range), -1 = no se sabe
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        spec = dataSpec
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length else -1L
        openChunk()
        opened = true
        transferStarted(dataSpec)
        val limit = limit()
        return if (limit >= 0) limit - dataSpec.position else C.LENGTH_UNSET.toLong()
    }

    private fun limit(): Long = when {
        end >= 0 && total >= 0 -> minOf(end, total)
        end >= 0 -> end
        else -> total
    }

    private fun openChunk() {
        inner?.close()
        val limit = limit()
        val stop = if (limit >= 0) minOf(position + CHUNK, limit) else position + CHUNK
        val ds = upstream.createDataSource()
        ds.open(spec!!.buildUpon().setPosition(position).setLength(stop - position).build())
        if (total < 0) total = totalFrom(ds.responseHeaders)
        inner = ds
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val limit = limit()
        if (limit in 0..position) return C.RESULT_END_OF_INPUT
        var n = inner!!.read(buffer, offset, length)
        if (n == C.RESULT_END_OF_INPUT) {
            // terminó el pedazo: si queda archivo, se pide el siguiente
            if (limit < 0 || position >= limit) return C.RESULT_END_OF_INPUT
            openChunk()
            n = inner!!.read(buffer, offset, length)
            if (n == C.RESULT_END_OF_INPUT) return n
        }
        position += n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = inner?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            inner?.close()
        } finally {
            inner = null
            if (opened) { opened = false; transferEnded() }
        }
    }

    companion object {
        const val CHUNK = 1L shl 20       // 1 MB ≈ 50 s de Opus a 160 kbps

        /** "Content-Range: bytes 0-1048575/3456789" → 3456789. */
        fun totalFrom(headers: Map<String, List<String>>): Long {
            val value = headers.entries.firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }?.value?.firstOrNull()
                ?: return -1L
            return value.substringAfterLast('/', "").trim().toLongOrNull() ?: -1L
        }
    }
}
