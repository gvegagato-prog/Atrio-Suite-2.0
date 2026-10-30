@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lo que se abre al pulsar una ficha, una nota, un suceso de la línea o una unión del árbol: en modo lectura (con «Editar»
 * arriba). Solo al crearlas se abre el formulario. Cada pantalla tiene su [AbreEscribir] y pinta [VentanasEscribir].
 */
class AbreEscribir {
    /** Lo que se está leyendo (su id). */
    var ver by mutableStateOf<String?>(null)
    /** Lo que se está editando (su id). */
    var editar by mutableStateOf<String?>(null)
}

@Composable
fun VentanasEscribir(e: Escritos, pid: String, a: AbreEscribir, abrirEscena: (String) -> Unit, verArbol: ((String) -> Unit)? = null) {
    a.ver?.let { id ->
        val d = e.get(id)
        val cerrar = { a.ver = null }
        val editar = { a.ver = null; a.editar = id }
        when (d?.t) {
            Escritos.T_FICHA -> VerFicha(e, pid, d, a, cerrar, editar, abrirEscena, verArbol)
            Escritos.T_NOTA -> VerNota(e, d, cerrar, editar)
            Escritos.T_HITO -> VerHito(e, d, cerrar, editar, abrirEscena)
            Escritos.T_REL -> VerRelacion(e, d, a, cerrar, editar)
            else -> LaunchedEffect(id) { a.ver = null }
        }
    }
    a.editar?.let { id ->
        val d = e.get(id)
        val cerrar = { a.editar = null }
        when (d?.t) {
            Escritos.T_FICHA -> FichaDialogo(e, pid, d, onCerrar = cerrar, abrirEscena = { abrirEscena(it.id) })
            Escritos.T_NOTA -> NotaDialogo(e, pid, d, onCerrar = cerrar)
            Escritos.T_HITO -> HitoDialogo(e, pid, d, onCerrar = cerrar)
            Escritos.T_REL -> RelacionDialogo(e, pid, d, onCerrar = cerrar)
            Escritos.T_RAMA -> RamaDialogo(e, pid, d, onCerrar = cerrar)
            Escritos.T_PARTE -> ParteDialogo(e, pid, d, onCerrar = cerrar)
            Escritos.T_CAP -> CapituloDialogo(e, pid, d, onCerrar = cerrar)
            else -> LaunchedEffect(id) { a.editar = null }
        }
    }
}

/** Un texto largo en párrafos, con **negrita** y *cursiva* como en el editor. */
@Composable
fun ParrafosEscribir(texto: String, tam: Int = 16) {
    texto.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { l ->
        when {
            l == "***" -> Text("* * *", color = Muted, fontSize = tam.sp, modifier = Modifier.fillMaxWidth().wrapContentWidth())
            Regex("^#{1,3} ").containsMatchIn(l) -> Text(l.replace(Regex("^#+ "), ""), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (tam + 4).sp, color = Charcoal)
            l.startsWith("> ") -> Text(conFormato(l.removePrefix("> ")), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = tam.sp, lineHeight = (tam * 1.5).sp, color = Muted, modifier = Modifier.padding(start = 14.dp))
            else -> Text(conFormato(l), fontFamily = Display, fontSize = tam.sp, lineHeight = (tam * 1.5).sp, color = Charcoal)
        }
    }
}

fun conFormato(t: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    for (m in Regex("\\*\\*[^*]+\\*\\*|\\*[^*]+\\*").findAll(t)) {
        append(t.substring(i, m.range.first))
        val v = m.value
        if (v.startsWith("**")) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(v.substring(2, v.length - 2)) }
        else withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(v.substring(1, v.length - 1)) }
        i = m.range.last + 1
    }
    append(t.substring(i))
}

@Composable
private fun AnteLectura(t: String) = Text(t.uppercase(), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)

@Composable
private fun FranjaLectura(c: androidx.compose.ui.graphics.Color) = Box(Modifier.width(56.dp).height(5.dp).background(c))

/** Los enlaces a las partes y capítulos a los que está vinculada. */
@Composable
private fun VinculadaA(e: Escritos, d: Doc) {
    val destinos = d.vin().mapNotNull { e.get(it) }
    if (destinos.isEmpty()) return
    EtiquetaEscribir("Vinculada a")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        destinos.forEach { x -> SoftChip((if (x.t == Escritos.T_PARTE) "Parte · " else "Capítulo · ") + x.s("titulo").ifBlank { "Sin título" }, false) {} }
    }
}

@Composable
private fun RowScope.Editar(onEditar: () -> Unit) = BotonEscribir("Editar", suave = true, onClick = onEditar)

@Composable
fun VerFicha(e: Escritos, pid: String, f: Doc, a: AbreEscribir, onCerrar: () -> Unit, onEditar: () -> Unit, abrirEscena: (String) -> Unit, verArbol: ((String) -> Unit)?) {
    val ver = e.version
    val sale = remember(ver, f.id) { e.escenasDe(pid).filter { s -> e.fichasEn(pid, s.s("texto")).any { it.id == f.id } } }
    val rels = remember(ver, f.id) { e.relaciones(pid).filter { it.s("a") == f.id || it.s("b") == f.id }.mapNotNull { r -> e.get(if (r.s("a") == f.id) r.s("b") else r.s("a"))?.let { r to it } } }
    val datos = remember(ver, f.id) { f.o.optJSONArray("campos")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { it.optString("k") to it.optString("v") }.filter { it.first.isNotBlank() || it.second.isNotBlank() } }.orEmpty() }
    HojaEscribir(f.s("nombre").ifBlank { "Sin nombre" }, onCerrar, acciones = { Editar(onEditar) }) {
        AnteLectura(singularFicha(f.s("tipo")) + if (f.s("alias").isNotBlank()) " · también «${f.s("alias")}»" else "")
        FranjaLectura(colorEscrito(f.s("color")))
        if (f.s("resumen").isNotBlank()) Text(f.s("resumen"), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 26.sp, color = Charcoal)
        if (datos.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            datos.forEach { (k, v) ->
                Row { Text(k.ifBlank { "—" }.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.width(110.dp).padding(top = 3.dp)); Text(v.ifBlank { "—" }, color = Charcoal, fontSize = 15.sp, modifier = Modifier.weight(1f)) }
            }
        }
        if (f.s("texto").isNotBlank()) ParrafosEscribir(f.s("texto"))
        VinculadaA(e, f)
        EtiquetaEscribir(if (rels.isEmpty()) "Uniones" else "Uniones · ${rels.size}")
        if (rels.isEmpty()) Text("Todavía no está unida a nada. Únela en la pestaña Árbol.", color = Muted, fontSize = 13.sp)
        rels.forEach { (r, otra) ->
            val flecha = r.i("flecha") == 1
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text((if (flecha && r.s("b") == f.id) "← " else "") + r.s("titulo").ifBlank { "Unión" }.uppercase() + (if (flecha && r.s("a") == f.id) " →" else ""),
                        color = colorEscrito(r.s("color"), Charcoal), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.press(haptic = false) { a.ver = r.id }.padding(vertical = 6.dp))
                    Text(otra.s("nombre").ifBlank { "Sin nombre" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Charcoal, modifier = Modifier.press(haptic = false) { a.ver = otra.id }.padding(vertical = 6.dp))
                }
                if (r.s("texto").isNotBlank()) Text(r.s("texto").lineSequence().first().take(140), color = Muted, fontStyle = FontStyle.Italic, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (sale.isNotEmpty()) {
            EtiquetaEscribir("Sale en ${sale.size} ${if (sale.size == 1) "escena" else "escenas"}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sale.take(40).forEach { s -> SoftChip(s.s("titulo").ifBlank { "Sin título" }, false) { onCerrar(); abrirEscena(s.id) } }
            }
        }
        if (verArbol != null) BotonEscribir("Ver en el árbol", suave = true, modifier = Modifier.padding(top = 8.dp)) { onCerrar(); verArbol(f.id) }
    }
}

@Composable
fun VerNota(e: Escritos, n: Doc, onCerrar: () -> Unit, onEditar: () -> Unit) {
    val tipo = singularNota(n.s("tipo"))
    HojaEscribir(n.s("titulo").ifBlank { tipo }, onCerrar, acciones = { Editar(onEditar) }) {
        AnteLectura(tipo + if (n.b("fija")) " · fijada" else "")
        FranjaLectura(colorEscrito(n.s("color")))
        if (n.s("texto").isNotBlank()) ParrafosEscribir(n.s("texto")) else Text("Sin texto.", color = Muted, fontSize = 14.sp)
        VinculadaA(e, n)
    }
}

@Composable
fun VerHito(e: Escritos, x: Doc, onCerrar: () -> Unit, onEditar: () -> Unit, abrirEscena: (String) -> Unit) {
    val r = e.get(x.s("rama"))
    val s = e.get(x.s("eid"))
    HojaEscribir(x.s("titulo").ifBlank { "Suceso" }, onCerrar, acciones = { Editar(onEditar) }) {
        AnteLectura(listOfNotNull(if (r != null) "Rama · ${r.s("titulo")}" else "Línea principal", x.s("cuando").ifBlank { null }).joinToString(" · "))
        FranjaLectura(colorEscrito(x.s("color").ifBlank { r?.s("color").orEmpty() }, Charcoal))
        if (x.s("nota").isNotBlank()) ParrafosEscribir(x.s("nota")) else Text("Sin más detalles.", color = Muted, fontSize = 14.sp)
        if (s != null) {
            EtiquetaEscribir("Se cuenta en")
            SoftChip(s.s("titulo").ifBlank { "la escena" }, false) { onCerrar(); abrirEscena(s.id) }
        }
    }
}

@Composable
fun VerRelacion(e: Escritos, r: Doc, a: AbreEscribir, onCerrar: () -> Unit, onEditar: () -> Unit) {
    val fa = e.get(r.s("a")); val fb = e.get(r.s("b"))
    if (fa == null || fb == null) { LaunchedEffect(r.id) { onCerrar() }; return }
    HojaEscribir(r.s("titulo").ifBlank { "Unión" }.uppercase(), onCerrar, acciones = { Editar(onEditar) }) {
        AnteLectura("Unión")
        FranjaLectura(colorEscrito(r.s("color"), Charcoal))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(fa.s("nombre").ifBlank { "Sin nombre" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Charcoal, modifier = Modifier.press(haptic = false) { a.ver = fa.id })
            Text(if (r.i("flecha") == 1) "→" else "—", color = Muted, fontSize = 18.sp)
            Text(fb.s("nombre").ifBlank { "Sin nombre" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Charcoal, modifier = Modifier.press(haptic = false) { a.ver = fb.id })
        }
        if (r.s("texto").isNotBlank()) ParrafosEscribir(r.s("texto")) else Text("Solo la palabra; no has escrito nada más de esta unión.", color = Muted, fontSize = 14.sp)
    }
}
