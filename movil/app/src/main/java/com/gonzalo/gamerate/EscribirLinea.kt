package com.gonzalo.gamerate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Escribir: la línea temporal. Una línea vertical con lo que pasa en la historia, en el orden en que pasa (no el del libro).
 * De cualquier suceso sale una rama a un lado: una trama entera, con sus sucesos, que baja en paralelo y puede volver a la
 * principal. Dónde va cada cosa lo calcula [EscribirEstructura.linea] (igual que el PC).
 */

private val COL = 26.dp
private val PUNTO_Y = 22.dp

@Composable
fun LineaObra(e: Escritos, obra: Doc, ver: Int, a: AbreEscribir, abrir: (String) -> Unit) {
    val pid = obra.id
    val hs = remember(ver, pid) { e.hitos(pid) }
    val rs = remember(ver, pid) { e.ramas(pid) }
    val L = remember(ver, pid) { EscribirEstructura.linea(hs, rs) }
    val porId = remember(ver, pid) { (hs + rs).associateBy { it.id } }
    val escenas = remember(ver, pid) { e.escenasDe(pid) }
    val conFecha = escenas.filter { it.s("cuando").isNotBlank() }
    var hitoNuevo by remember { mutableStateOf<Pair<String, Double>?>(null) }
    var ramaNueva by remember { mutableStateOf<Pair<Double?, Double?>?>(null) }
    var menu by remember { mutableStateOf(-1) }
    // Cada fila con su documento y su orden (el de un suceso, o donde nace / vuelve una rama)
    class F(val f: EscribirEstructura.Fila, val d: Doc, val o: Double)
    val orden = remember(L) {
        L.filas.mapNotNull { f ->
            val d = porId[f.id] ?: return@mapNotNull null
            val t = L.tramos[f.id]
            F(f, d, when (f.tipo) { "hito" -> d.orden; "inicio" -> t!!.ini; else -> t!!.fin })
        }
    }
    fun ordenTras(i: Int) = orden.getOrNull(i + 1)?.let { (orden[i].o + it.o) / 2 } ?: (orden[i].o + 1000)
    // El color de una columna en un punto: el de la rama que la ocupa ahí (dos ramas que no se pisan comparten columna)
    fun colorEn(c: Int, o: Double): Color {
        if (c == 0) return Charcoal
        val r = rs.find { x -> L.tramos[x.id]?.let { it.col == c && o >= it.ini && o <= it.fin } == true }
        return colorEscrito(r?.s("color").orEmpty(), Charcoal)
    }
    // Dónde empieza y acaba cada línea (para cortarla en el punto, no en el borde de la fila): 1 desde el punto, 2 hasta el punto
    fun extremo(c: Int, i: Int, f: EscribirEstructura.Fila): Int = when {
        f.tipo == "inicio" && c == f.col -> 1
        f.tipo == "fin" && c == f.col -> 2
        c == 0 && i == 0 -> 1
        c == 0 && i == orden.lastIndex -> 2
        // una rama que no vuelve acaba en su último suceso
        c > 0 && f.tipo == "hito" && f.col == c && orden.drop(i + 1).none { x -> c in x.f.activas && (x.f.tipo != "inicio" || x.f.col != c) } -> 2
        else -> 0
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(40.dp))) {
        item("intro") {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Lo que pasa en tu historia, en el orden en que pasa. De cualquier suceso puede salir una rama: una trama que baja en paralelo y, si quieres, vuelve a la principal.", color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonEscribir("Nuevo suceso") { hitoNuevo = "" to ((hs.maxOfOrNull { it.orden } ?: 0.0) + 1000) }
                    BotonEscribir("Nueva rama", suave = true) { ramaNueva = (hs.lastOrNull()?.orden ?: 0.0) to null }
                }
            }
        }
        if (orden.isEmpty()) item("vacio") {
            Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("La línea de tu historia", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Charcoal)
                Text("Apunta lo que pasa (aunque no salga en el libro, o salga en otro orden) y saca ramas para las tramas que van a la vez.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        items(orden.size, key = { orden[it].f.tipo + orden[it].f.id }) { i ->
            val (f, d, o) = Triple(orden[i].f, orden[i].d, orden[i].o)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // Las líneas que cruzan esta fila, la curva de una rama que nace o vuelve y el punto
                val colPunto = if (f.tipo == "hito") colorEscrito(d.s("color").ifBlank { porId[d.s("rama")]?.s("color").orEmpty() }, colorEn(f.col, o)) else colorEn(f.col, o)
                Canvas(Modifier.width(COL * L.columnas).fillMaxHeight()) {
                    val col = COL.toPx(); val py = PUNTO_Y.toPx(); val w = 2.dp.toPx()
                    f.activas.forEach { c ->
                        val x = c * col + col / 2
                        val (y0, y1) = when (extremo(c, i, f)) { 1 -> py to size.height; 2 -> 0f to py; else -> 0f to size.height }
                        drawLine(colorEn(c, o), Offset(x, y0), Offset(x, y1), w)
                    }
                    if (f.tipo != "hito") drawLine(colorEn(f.col, o), Offset(col / 2, py), Offset(f.col * col + col / 2, py), w)
                    val r = if (f.tipo == "hito") 6.dp.toPx() else 4.5.dp.toPx()
                    drawCircle(colPunto, r, Offset(f.col * col + col / 2, py))
                }
                if (f.tipo == "hito") {
                    val esc = e.get(d.s("eid"))
                    Row(Modifier.weight(1f).padding(start = 6.dp, bottom = 12.dp)) {
                        Column(Modifier.weight(1f).press(haptic = false) { a.ver = d.id }.padding(top = 10.dp, end = 4.dp)) {
                            if (d.s("cuando").isNotBlank()) Text(d.s("cuando").uppercase(), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                            Text(d.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 22.sp, color = Charcoal)
                            if (d.s("nota").isNotBlank()) Text(d.s("nota").lineSequence().first().take(180), color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (esc != null) Text("→ " + esc.s("titulo").ifBlank { "escena" }, color = ColGeneral, fontSize = 13.sp)
                        }
                        Box {
                            Icon(Icons.Rounded.MoreVert, "Más", tint = Muted, modifier = Modifier.press(haptic = false) { menu = i }.padding(top = 10.dp, start = 6.dp, end = 4.dp, bottom = 6.dp).size(20.dp))
                            DropdownMenu(menu == i, { menu = -1 }, containerColor = Paper) {
                                DropdownMenuItem({ Text("Un suceso justo después") }, { menu = -1; hitoNuevo = d.s("rama") to ordenTras(i) })
                                DropdownMenuItem({ Text("Una rama desde aquí") }, { menu = -1; ramaNueva = d.orden to orden.getOrNull(i + 1)?.o })
                                if (i > 0) DropdownMenuItem({ Text("Subir") }, { menu = -1; e.edita(d.id) { put("orden", EscribirEstructura.entre(orden.getOrNull(i - 2)?.o, orden[i - 1].o)) } })
                                if (i < orden.lastIndex) DropdownMenuItem({ Text("Bajar") }, { menu = -1; e.edita(d.id) { put("orden", EscribirEstructura.entre(orden[i + 1].o, orden.getOrNull(i + 2)?.o)) } })
                                DropdownMenuItem({ Text("Editar") }, { menu = -1; a.editar = d.id })
                            }
                        }
                    }
                } else {
                    val c = colorEn(f.col, o)
                    Column(Modifier.weight(1f).padding(start = 6.dp, top = 4.dp, bottom = 12.dp).clip(shp(12.dp)).background(c.copy(alpha = 0.08f)).border(1.dp, c.copy(alpha = 0.5f), shp(12.dp))
                        .press(haptic = false) { a.editar = d.id }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(if (f.tipo == "inicio") "NACE LA RAMA" else "VUELVE A LA PRINCIPAL", color = c, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                        Text(d.s("titulo").ifBlank { "Rama" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Charcoal)
                        if (f.tipo == "inicio" && d.s("texto").isNotBlank()) Text(d.s("texto").lineSequence().first().take(160), color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (conFecha.isNotEmpty()) {
            item("escenasT") { Text("ESCENAS CON FECHA", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) }
            items(conFecha.size) { i ->
                val s = conFecha[i]
                Column(Modifier.fillMaxWidth().press(haptic = false) { abrir(s.id) }.padding(vertical = 7.dp)) {
                    Text(s.s("cuando").uppercase(), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text(s.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontSize = 18.sp, color = Charcoal)
                }
            }
        }
    }
    hitoNuevo?.let { (rama, o) -> HitoDialogo(e, pid, null, onCerrar = { hitoNuevo = null }, ramaNueva = rama, ordenNuevo = o) }
    ramaNueva?.let { (desde, siguiente) ->
        RamaDialogo(e, pid, null, onCerrar = { ramaNueva = null }, desdeNuevo = desde, onCreada = { r ->
            // Su primer suceso, justo después de donde nace
            val d = r.d("desde")
            hitoNuevo = r.id to (if (siguiente != null) (d + siguiente) / 2 else d + 500)
        })
    }
}
