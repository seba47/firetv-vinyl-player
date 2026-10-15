package app.vinilotv

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address

/**
 * Página y API para elegir música desde el celular, en la red local (la tele muestra un QR).
 *   GET  /                  la página (assets/web/index.html)
 *   GET  /api/state         qué suena y estaciones recientes
 *   GET  /api/search?q=&kind=song|album|playlist
 *   POST /api/play          {kind,title,subtitle,url,thumb}: arranca esa estación
 *   POST /api/crate/add     {kind,title,subtitle,url,thumb}: lo guarda en la caja de discos
 *   POST /api/crate/remove  {url}      POST /api/crate/move {url, delta}
 *   POST /api/next, /api/toggle
 *   POST /api/settings      {wall: LIVING|ESTUFA, plaster: 40..95, edgeTop, edgeBottom}: fondo de la tele
 * Cualquiera en el mismo WiFi puede usarla (igual que el control remoto).
 */
class WebServer private constructor(private val context: Context, port: Int) : NanoHTTPD(port) {
    private val main = Handler(Looper.getMainLooper())

    fun publicUrl(): String? = localIpv4(context)?.let { "http://$it:$listeningPort" }

    override fun serve(session: IHTTPSession): Response = try {
        val path = session.uri.trimEnd('/').ifEmpty { "/" }
        when {
            path == "/" -> asset("web/index.html", "text/html; charset=utf-8")
            path == "/api/state" -> json(stateJson())
            path == "/api/search" -> {
                val q = session.parameters["q"]?.firstOrNull().orEmpty().trim()
                val kind = when (session.parameters["kind"]?.firstOrNull()) {
                    "album" -> SeedKind.ALBUM
                    "playlist" -> SeedKind.PLAYLIST
                    else -> SeedKind.SONG
                }
                if (q.isEmpty()) json(JSONArray()) else json(JSONArray(YouTube.search(q, kind).map { it.toJson() }))
            }
            path == "/api/play" && session.method == Method.POST -> {
                val seed = Seed.fromJson(JSONObject(body(session)))
                main.post { Radio.play(seed, fromPhone = true) }
                json(JSONObject().put("ok", true))
            }
            path == "/api/settings" && session.method == Method.POST -> {
                val o = JSONObject(body(session))
                val wall = o.optString("wall").takeIf { it.isNotEmpty() }?.let { runCatching { Wall.valueOf(it) }.getOrNull() }
                fun int(k: String) = if (o.has(k)) o.getInt(k) else null
                main.post { Settings.update(wall, int("plaster"), int("edgeTop"), int("edgeBottom")) }
                json(JSONObject().put("ok", true))
            }
            path == "/api/crate/add" && session.method == Method.POST -> {
                val seed = Seed.fromJson(JSONObject(body(session)))
                val ok = Crate.add(seed)
                if (ok) main.post { Radio.toast("Guardado en la caja: ${seed.title}") }
                json(JSONObject().put("ok", ok).put("full", !ok))
            }
            path == "/api/crate/remove" && session.method == Method.POST -> {
                Crate.remove(JSONObject(body(session)).getString("url")); json(JSONObject().put("ok", true))
            }
            path == "/api/crate/move" && session.method == Method.POST -> {
                val o = JSONObject(body(session)); Crate.move(o.getString("url"), o.getInt("delta")); json(JSONObject().put("ok", true))
            }
            path == "/api/next" && session.method == Method.POST -> { main.post { Radio.next() }; json(JSONObject().put("ok", true)) }
            path == "/api/toggle" && session.method == Method.POST -> { main.post { Radio.togglePause() }; json(JSONObject().put("ok", true)) }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No existe")
        }
    } catch (e: Exception) {
        Log.e("ViniloTV", "Web ${session.uri}", e)
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
            JSONObject().put("error", e.message ?: e.javaClass.simpleName).toString())
    }

    private fun stateJson(): JSONObject {
        val s = Radio.state.value
        return JSONObject()
            .put("seed", s.seed?.toJson())
            .put("current", s.current?.let {
                JSONObject().put("title", it.track.title).put("artist", it.track.artist)
                    .put("thumb", it.track.thumbnailUrl ?: "").put("auto", it.auto)
            })
            .put("playing", s.playing)
            .put("loading", s.loading)
            .put("recent", JSONArray(s.recent.map { it.toJson() }))
            .put("settings", Settings.state.value.toJson())
            .put("crate", JSONArray(Crate.items.value.map { it.toJson() }))
            .put("crateMax", Crate.MAX)
    }

    private fun SearchResult.toJson() = JSONObject().put("kind", kind.name).put("title", title).put("subtitle", subtitle)
        .put("url", url).put("thumb", thumbnailUrl ?: "").put("count", trackCount)

    // Se lee a mano como UTF-8: parseBody() de NanoHTTPD usa ISO-8859-1 y rompe acentos y eñes.
    private fun body(session: IHTTPSession): String {
        val len = session.headers["content-length"]?.toIntOrNull() ?: return ""
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = session.inputStream.read(buf, read, len - read)
            if (n < 0) break
            read += n
        }
        return String(buf, 0, read, Charsets.UTF_8)
    }

    private fun asset(name: String, mime: String): Response =
        newChunkedResponse(Response.Status.OK, mime, context.assets.open(name))

    private fun json(body: Any): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body.toString()).apply {
            addHeader("Cache-Control", "no-store")
        }

    companion object {
        /** Prueba 8080 y, si está ocupado, los siguientes. */
        fun start(context: Context): WebServer? {
            for (port in 8080..8089) {
                val server = WebServer(context.applicationContext, port)
                try {
                    server.start(SOCKET_READ_TIMEOUT, false)
                    return server
                } catch (e: Exception) {
                    Log.w("ViniloTV", "Puerto $port ocupado: ${e.message}")
                }
            }
            return null
        }

        fun localIpv4(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
            return props.linkAddresses.map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }
    }
}
