package app.vinilotv

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/** Fondo de la pantalla: el living oscuro (por defecto) o la pared de yeso de la estufa. */
enum class Wall { LIVING, ESTUFA }

/**
 * [plaster] = brillo del yeso en % (una pantalla brilla más que la pared real: de noche, menos).
 * [edgeTop]/[edgeBottom] = medio ancho de la cara de la estufa arriba y abajo de la pantalla, en
 * milésimos del ancho (se ajustan mirando la tele para que las aristas sigan a la estufa real).
 * [animated] = false: modo quieto, el disco no gira y no hay polvo ni bamboleo (la música sigue
 * igual); para tenerlo de fondo sin que llame la atención, p. ej. con chicos.
 */
data class Appearance(
    val wall: Wall = Wall.LIVING, val plaster: Int = 78, val edgeTop: Int = 233, val edgeBottom: Int = 420,
    val animated: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().put("wall", wall.name).put("plaster", plaster)
        .put("edgeTop", edgeTop).put("edgeBottom", edgeBottom).put("animated", animated)
}

/** Ajustes de apariencia, guardados en el dispositivo. Se cambian desde la página del celular. */
object Settings {
    private val _state = MutableStateFlow(Appearance())
    val state: StateFlow<Appearance> = _state
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
        prefs = p
        _state.value = Appearance(
            wall = runCatching { Wall.valueOf(p.getString("fondo", Wall.LIVING.name)!!) }.getOrDefault(Wall.LIVING),
            plaster = p.getInt("yeso", 78),
            edgeTop = p.getInt("aristaArriba", 233),
            edgeBottom = p.getInt("aristaAbajo", 420),
            animated = p.getBoolean("animado", true),
        )
    }

    fun update(wall: Wall? = null, plaster: Int? = null, edgeTop: Int? = null, edgeBottom: Int? = null, animated: Boolean? = null) {
        _state.update {
            it.copy(
                wall = wall ?: it.wall,
                plaster = (plaster ?: it.plaster).coerceIn(40, 95),
                edgeTop = (edgeTop ?: it.edgeTop).coerceIn(100, 500),
                edgeBottom = (edgeBottom ?: it.edgeBottom).coerceIn(150, 800),
                animated = animated ?: it.animated,
            )
        }
        val v = _state.value
        prefs?.edit()?.putString("fondo", v.wall.name)?.putInt("yeso", v.plaster)
            ?.putInt("aristaArriba", v.edgeTop)?.putInt("aristaAbajo", v.edgeBottom)
            ?.putBoolean("animado", v.animated)?.apply()
    }
}
