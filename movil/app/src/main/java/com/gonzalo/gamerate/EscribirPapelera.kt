package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun tipoEscrito(t: String): String = when (t) {
    Escritos.T_OBRA -> "Obra"; Escritos.T_CAP -> "Capítulo"; Escritos.T_ESCENA -> "Escena"; Escritos.T_FICHA -> "Ficha"
    Escritos.T_NOTA -> "Nota"; Escritos.T_HITO -> "Hito"; Escritos.T_BORRADOR -> "Borrador"; else -> "Escrito"
}

fun tituloEscrito(d: Doc): String = (if (d.t == Escritos.T_FICHA) d.s("nombre") else d.s("titulo").ifBlank { d.s("nombre") }).ifBlank { "Sin título" }

/** Manda algo a la papelera y avisa con «Deshacer». */
fun aLaPapelera(e: Escritos, d: Doc) {
    e.borra(d.id)
    EscribirHub.deshacer?.invoke("${tipoEscrito(d.t)} «${tituloEscrito(d)}» en la papelera") { e.restaura(d.id) }
}

private fun cuantos(l: List<Doc>): String {
    val n = l.groupingBy { it.t }.eachCount()
    return listOf(Escritos.T_CAP to ("capítulo" to "capítulos"), Escritos.T_ESCENA to ("escena" to "escenas"), Escritos.T_FICHA to ("ficha" to "fichas"),
        Escritos.T_NOTA to ("nota" to "notas"), Escritos.T_HITO to ("hito" to "hitos"), Escritos.T_BORRADOR to ("borrador" to "borradores"))
        .mapNotNull { (t, p) -> n[t]?.let { c -> "$c ${if (c == 1) p.first else p.second}" } }
        .let { if (it.size > 1) it.dropLast(1).joinToString(", ") + " y " + it.last() else it.firstOrNull().orEmpty() }
}

/** La papelera: lo borrado de los últimos 30 días, para recuperarlo (vuelve a su sitio) o borrarlo para siempre. */
@Composable
fun PapeleraEscribir(e: Escritos, onCerrar: () -> Unit) {
    val ver = e.version
    val lista = remember(ver) { e.papelera() }
    var vaciar by remember { mutableStateOf(false) }
    HojaEscribir("Papelera", onCerrar) {
        Text("Lo que borras se queda aquí 30 días, también en tus otros dispositivos. Al recuperarlo vuelve a su sitio, con lo que se fue con ello.",
            color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        if (lista.isEmpty()) Text("La papelera está vacía.", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = Muted, modifier = Modifier.padding(vertical = 24.dp))
        lista.forEach { d -> key(d.id) { FilaPapelera(e, d, ver) } }
        if (lista.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            if (!vaciar) BotonEscribir("Vaciar la papelera", peligro = true, suave = true) { vaciar = true }
            else {
                Text("Se borrará para siempre todo lo que hay aquí, en todos tus dispositivos.", color = ColGeneral, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonEscribir("Sí, vaciarla", peligro = true) { e.vaciaPapelera(); vaciar = false }
                    BotonEscribir("No", suave = true) { vaciar = false }
                }
            }
        }
    }
}

@Composable
private fun FilaPapelera(e: Escritos, d: Doc, ver: Int) {
    var seguro by remember { mutableStateOf(false) }
    val con = remember(ver, d.id) { e.conElBorrado(d.id) }
    val obra = if (d.t == Escritos.T_OBRA) null else e.cualquiera(d.pid)
    val cuando = d.n("dt", d.u)
    val quedan = ((cuando + Escritos.PAPELERA - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
    val extracto = when (d.t) {
        Escritos.T_ESCENA, Escritos.T_NOTA, Escritos.T_BORRADOR -> d.s("texto").lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("#") }.orEmpty()
        Escritos.T_FICHA -> d.s("resumen")
        Escritos.T_OBRA -> d.s("sinopsis")
        else -> d.s("resumen")
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Text((tipoEscrito(d.t) + (obra?.let { " · " + tituloEscrito(it) } ?: "")).uppercase(), color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(tituloEscrito(d), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.sp, color = Charcoal, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (extracto.isNotBlank()) Text(extracto, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOf(cuantos(con).let { if (it.isEmpty()) "" else "Con $it" }, (if (d.t == Escritos.T_ESCENA || d.t == Escritos.T_BORRADOR) cifraE(Escritos.palabras(d.s("texto"))) + " palabras" else ""),
            "borrado " + haceE(cuando), if (quedan <= 1) "se borra del todo mañana" else "quedan $quedan días").filter { it.isNotBlank() }.joinToString(" · "),
            color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            BotonEscribir("Recuperar") { e.restaura(d.id) }
            if (!seguro) BotonEscribir("Borrar para siempre", suave = true) { seguro = true }
            else BotonEscribir("Sí, para siempre", peligro = true) { e.purga(d.id) }
        }
    }
}
