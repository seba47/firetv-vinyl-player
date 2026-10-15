package app.vinilotv

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/** Fondo de la pantalla: el living oscuro (por defecto) o la pared de yeso de la estufa. */
enum class Wall { LIVING, ESTUFA }

/** [plaster] = brillo del yeso en % (una pantalla brilla más que la pared real: de noche, menos). */
data class Appearance(val wall: Wall = Wall.LIVING, val plaster: Int = 78) {
    fun toJson(): JSONObject = JSONObject().put("wall", wall.name).put("plaster", plaster)
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
        )
    }

    fun update(wall: Wall? = null, plaster: Int? = null) {
        _state.update { it.copy(wall = wall ?: it.wall, plaster = (plaster ?: it.plaster).coerceIn(40, 95)) }
        prefs?.edit()?.putString("fondo", _state.value.wall.name)?.putInt("yeso", _state.value.plaster)?.apply()
    }
}
