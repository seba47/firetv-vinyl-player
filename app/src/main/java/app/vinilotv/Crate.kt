package app.vinilotv

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * La caja de discos: lo que el usuario carga desde el celular (cada tanto) para elegir después con
 * el control, sin celular. Guarda semillas (discos, playlists o temas), en el orden que se eligió.
 */
object Crate {
    const val MAX = 30

    private val _items = MutableStateFlow<List<Seed>>(emptyList())
    val items: StateFlow<List<Seed>> = _items
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("caja", Context.MODE_PRIVATE)
        prefs = p
        _items.value = runCatching {
            val arr = JSONArray(p.getString("discos", "[]"))
            (0 until arr.length()).map { Seed.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** Suma un disco adelante de todo (si ya estaba, lo trae adelante). Devuelve false si está llena. */
    fun add(seed: Seed): Boolean {
        val rest = _items.value.filter { it.url != seed.url }
        if (rest.size >= MAX) return false
        save(listOf(seed) + rest)
        return true
    }

    fun remove(url: String) = save(_items.value.filter { it.url != url })

    /** Mueve un disco [delta] lugares (negativo = hacia adelante de la caja). */
    fun move(url: String, delta: Int) {
        val list = _items.value.toMutableList()
        val i = list.indexOfFirst { it.url == url }
        if (i < 0) return
        val j = (i + delta).coerceIn(0, list.lastIndex)
        list.add(j, list.removeAt(i))
        save(list)
    }

    private fun save(list: List<Seed>) {
        _items.value = list
        prefs?.edit()?.putString("discos", JSONArray(list.map { it.toJson() }).toString())?.apply()
    }
}

/** Lo que hay en la caja al revolver, en orden. */
sealed interface CrateItem {
    /** Separador de cartón con pestaña, escrito a mano. */
    data class Divider(val tab: String, val text: String, val note: String? = null) : CrateItem
    data class Record(val seed: Seed, val recent: Boolean) : CrateItem
    /** Sobre de papel madera: un disco al azar de la caja. */
    data object Surprise : CrateItem
}

/** Arma la caja: lo último que sonó (sin la estación actual), los discos cargados y la sorpresa. */
fun crateItems(recent: List<Seed>, crate: List<Seed>, playingUrl: String?): List<CrateItem> {
    val last = recent.filter { it.url != playingUrl }.take(4)
    return buildList {
        if (last.isNotEmpty()) {
            add(CrateItem.Divider("Lo último", "Lo último que sonó"))
            last.forEach { add(CrateItem.Record(it, recent = true)) }
        }
        if (crate.isEmpty()) {
            add(CrateItem.Divider("La caja", "La caja está vacía", "Cargala desde el celular: apretá ≡ y escaneá el código"))
        } else {
            add(CrateItem.Divider("La caja", "La caja", "${crate.size} ${if (crate.size == 1) "disco" else "discos"}"))
            crate.forEach { add(CrateItem.Record(it, recent = false)) }
            add(CrateItem.Surprise)
        }
    }
}

/** Dónde arranca la caja al abrirla: el primer disco cargado (o el primero de lo último). */
fun crateStartIndex(items: List<CrateItem>): Int {
    val box = items.indexOfFirst { it is CrateItem.Divider && it.tab == "La caja" }
    val firstRecord = items.withIndex().firstOrNull { (i, it) -> it is CrateItem.Record && i > box }?.index
    return firstRecord ?: items.indexOfFirst { it is CrateItem.Record }.takeIf { it >= 0 } ?: 0
}
