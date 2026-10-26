package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/*
 * La lista negra de juegos (p. ej. Wallpaper Engine, que se queda abierto en segundo plano): esos juegos no salen en
 * ningún sitio. El PC ni los rastrea; aquí no salen en directo, en la biblioteca del PC, en capturas, en horas ni en el
 * diario, y su ficha se borra. Viaja con la cuenta (lista «negra»: { id, n, updated }) y vale para todos tus dispositivos.
 */
data class Vetado(val id: Long, val nombre: String, val updated: Long = 0L)

fun vetadoToJson(v: Vetado): JSONObject = JSONObject().put("id", v.id).put("n", v.nombre).put("updated", v.updated)
fun vetadosFromJson(a: JSONArray?): List<Vetado> = (0 until (a?.length() ?: 0)).mapNotNull { i ->
    a!!.optJSONObject(i)?.takeIf { it.optLong("id") != 0L && it.optString("n").isNotBlank() }?.let { Vetado(it.optLong("id"), it.optString("n"), it.optLong("updated", 0L)) }
}

object Negra {
    /** Los nombres vetados, ya simplificados (normName). */
    var nombres by mutableStateOf<Set<String>>(emptySet())
        private set
    var lista by mutableStateOf<List<Vetado>>(emptyList())
        private set

    fun poner(l: List<Vetado>) { lista = l.sortedBy { normName(it.nombre) }; nombres = l.map { normName(it.nombre) }.toSet() }
    fun vetado(nombre: String): Boolean = nombres.isNotEmpty() && normName(nombre) in nombres
    fun idDe(nombre: String): Long = customItemId(nombre)
    /** Añadir un juego desde su ficha (lo pone la app: lo guarda y se sale de la ficha). */
    var alAnadir: ((String) -> Unit)? = null
}

/** Gestionar la lista negra: lo que hay, quitar y añadir por nombre. */
@Composable
fun NegraDialogo(onAnadir: (String) -> Unit, onQuitar: (Vetado) -> Unit, onCerrar: () -> Unit) {
    var nuevo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Lista negra", fontFamily = Display) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Los juegos de aquí no salen en ningún sitio (ni en la portada, el diario o el calendario) y el PC deja de seguirlos. Útil para cosas como Wallpaper Engine.",
                    color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                Negra.lista.forEach { v ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(v.nombre, Modifier.weight(1f), fontFamily = Display, fontSize = 17.sp)
                        TextButton({ onQuitar(v) }) { Text("Quitar", color = ColGeneral) }
                    }
                }
                if (Negra.lista.isEmpty()) Text("Aún no hay ninguno.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(nuevo, { nuevo = it }, label = { Text("Añadir un juego (su nombre)") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
                TextButton({ if (nuevo.isNotBlank()) { onAnadir(nuevo.trim()); nuevo = "" } }, enabled = nuevo.isNotBlank()) { Text("Añadir y ocultar") }
            }
        },
        confirmButton = { TextButton(onCerrar) { Text("Cerrar") } },
        containerColor = Paper, shape = shp(if (Skin.revista) 4.dp else 24.dp)
    )
}

/** En «Editar datos» de la ficha de un juego: ponerlo en la lista negra (con confirmación: su ficha se borra). */
@Composable
fun BotonListaNegra(nombre: String) {
    var pregunta by remember { mutableStateOf(false) }
    TextButton({ pregunta = true }) { Text("Nunca mostrar este juego (lista negra)", color = Muted, fontSize = 13.sp) }
    if (pregunta) AlertDialog(
        onDismissRequest = { pregunta = false },
        title = { Text("¿A la lista negra?", fontFamily = Display) },
        text = { Text("«$nombre» dejará de salir en ningún sitio, en todos tus dispositivos, y su ficha (notas, apuntes) se borrará. Puedes quitarlo después en Perfil → Lista negra, pero la ficha no se recupera.") },
        confirmButton = { TextButton({ pregunta = false; Negra.alAnadir?.invoke(nombre) }) { Text("Ocultar y borrar", color = ColGeneral) } },
        dismissButton = { TextButton({ pregunta = false }) { Text("Cancelar") } },
        containerColor = Paper, shape = shp(if (Skin.revista) 4.dp else 24.dp)
    )
}
