@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Escribir: el manuscrito en cuadrados (partes y capítulos sueltos), la página de cada parte (sus capítulos y lo vinculado:
 * notas, resúmenes, esquemas, fichas) y la de cada capítulo (sus escenas, leerlo entero y sus notas). Igual que el PC
 * (escribir-partes.js). Para ordenar: mantener pulsado un cuadrado (subir, bajar, meter en una parte…).
 */

private val NUMS = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
private fun primeraLinea(t: String) = t.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") && it != "***" }.orEmpty()

/** El número de un capítulo en el libro (1, 2, 3… seguidos aunque estén en partes). */
fun numeroCapitulo(e: Escritos, c: Doc): Int = e.capitulos(c.pid).indexOfFirst { it.id == c.id } + 1

/** Un cuadrado: franja de color arriba, lo pequeño encima, el título, un texto y el pie. */
@Composable
fun CuadroEscribir(color: Color, ante: String, titulo: String, texto: String, pie: String, onClick: () -> Unit, onLong: (() -> Unit)? = null, anteColor: Color = Muted, extra: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().heightIn(min = 168.dp).clip(shp(14.dp)).background(color.copy(alpha = 0.09f)).border(1.dp, color.copy(alpha = 0.45f), shp(14.dp)).press(haptic = false, onLong = onLong, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(color))
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(ante.uppercase(), color = anteColor, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(titulo, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 22.sp, color = Charcoal, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (texto.isNotBlank()) Text(texto, color = Muted, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, fontStyle = FontStyle.Italic)
            extra()
            if (pie.isNotBlank()) Text(pie, color = Muted, fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
private fun CuadroNuevo(texto: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(shp(14.dp)).border(1.dp, Line, shp(14.dp)).press(haptic = false, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("+", fontFamily = Display, fontSize = 34.sp, color = Muted)
        Text(texto.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
    }
}

/** Mini barra con cuántas escenas hay en borrador, revisión y final. */
@Composable
private fun MiniEstados(escenas: List<Doc>) {
    val n = IntArray(3); escenas.forEach { n[it.i("estado").coerceIn(0, 2)]++ }
    if (escenas.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(4.dp).clip(shp(2.dp))) { (0..2).forEach { i -> if (n[i] > 0) Box(Modifier.weight(n[i].toFloat()).fillMaxHeight().background(colorEstado(i))) } }
}

@Composable
fun CuadroCapitulo(e: Escritos, c: Doc, onClick: () -> Unit, onLong: (() -> Unit)? = null) {
    val esc = e.escenas(c.id)
    CuadroEscribir(colorEscrito(c.s("color"), Charcoal), "Capítulo ${numeroCapitulo(e, c)}", c.s("titulo").ifBlank { "Sin título" },
        c.s("resumen").ifBlank { primeraLinea(esc.firstOrNull()?.s("texto").orEmpty()) }.ifBlank { "Sin escribir todavía." },
        "${esc.size} ${if (esc.size == 1) "escena" else "escenas"} · ${cifraE(e.palabrasCapitulo(c.id))} palabras", onClick, onLong) { MiniEstados(esc) }
}

@Composable
private fun CuadroParte(e: Escritos, a: Doc, caps: List<Doc>, onClick: () -> Unit, onLong: () -> Unit) {
    val pal = caps.sumOf { e.palabrasCapitulo(it.id) }
    CuadroEscribir(colorEscrito(a.s("color")), "Parte", a.s("titulo").ifBlank { "Sin título" }, a.s("resumen"),
        "${caps.size} ${if (caps.size == 1) "capítulo" else "capítulos"} · ${cifraE(pal)} palabras", onClick, onLong, anteColor = colorEscrito(a.s("color"))) {
        if (caps.isEmpty()) Text("Sin capítulos todavía.", color = Muted, fontSize = 12.sp)
        caps.take(6).forEach { c -> Text("${numeroCapitulo(e, c)}. ${c.s("titulo").ifBlank { "Sin título" }}", color = Charcoal, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (caps.size > 6) Text("… y ${caps.size - 6} más", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun CuadroVinculado(d: Doc, onClick: () -> Unit) {
    val ficha = d.t == Escritos.T_FICHA
    CuadroEscribir(colorEscrito(d.s("color")), if (ficha) singularFicha(d.s("tipo")) else singularNota(d.s("tipo")),
        if (ficha) d.s("nombre").ifBlank { "Sin nombre" } else d.s("titulo").ifBlank { singularNota(d.s("tipo")) },
        if (ficha) d.s("resumen").ifBlank { primeraLinea(d.s("texto")) } else d.s("texto"), "", onClick)
}

private fun LazyGridScope.cabecera(key: String, contenido: @Composable () -> Unit) = item(key, span = { GridItemSpan(maxLineSpan) }) { contenido() }

@Composable
private fun Seccion(t: String, accion: String? = null, onAccion: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t.uppercase(), color = Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp, modifier = Modifier.weight(1f))
        if (accion != null) Text(accion.uppercase(), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, modifier = Modifier.press(haptic = false, onClick = onAccion).padding(8.dp))
    }
}

// ---------------------------------------------------------------- El manuscrito

@Composable
fun ManuscritoObra(e: Escritos, obra: Doc, ver: Int, abrirParte: (String) -> Unit, abrirCapitulo: (String) -> Unit, abrir: (String) -> Unit, hacerGuia: (Int) -> Unit, verGuia: () -> Unit) {
    val pid = obra.id
    // (como en el PC: la vista elegida se mantiene al cambiar de pestaña o de obra)
    var lista by EscribirHub::listaManuscrito
    var parteNueva by remember { mutableStateOf(false) }
    var capNuevo by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    var meter by remember { mutableStateOf<Doc?>(null) }
    val a = remember { AbreEscribir() }
    // Las partes de antes (un texto en cada capítulo) pasan a ser partes de verdad
    LaunchedEffect(pid) { e.migraPartes(pid) }
    val est = remember(ver, pid) { EscribirEstructura.estructura(e.deTipo(Escritos.T_CAP, pid), e.partes(pid)) }
    val palabras = remember(ver, pid) { e.palabrasObra(pid) }
    val meta = obra.i("meta")
    val estados = remember(ver, pid) { e.escenasDe(pid).groupingBy { it.i("estado") }.eachCount() }
    val conmuta: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cifraE(palabras) + (if (meta > 0) " de ${cifraE(meta)} palabras" else " palabras") + " · ${e.partes(pid).size} ${if (e.partes(pid).size == 1) "parte" else "partes"} · ${est.libro.size} ${if (est.libro.size == 1) "capítulo" else "capítulos"}", color = Muted, fontSize = 13.sp)
            if (meta > 0) Bar((palabras.toFloat() / meta).coerceIn(0f, 1f), colorEscrito(obra.s("color")), height = 4.dp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ESTADOS_ESCENA.forEachIndexed { i, n -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(colorEstado(i))); Spacer(Modifier.width(5.dp)); Text("${estados[i] ?: 0} ${n.lowercase()}", color = Muted, fontSize = 12.sp) } }
                FiltroRevista("Cuadrados", !lista) { lista = false }
                FiltroRevista("Lista", lista) { lista = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonEscribir("Nueva parte") { parteNueva = true }
                BotonEscribir("Nuevo capítulo", suave = true) { capNuevo = true }
            }
        }
    }
    if (lista) ManuscritoLista(e, obra, ver, abrir, abrirCapitulo, hacerGuia, verGuia, cabecera = conmuta)
    else LazyVerticalGrid(GridCells.Adaptive(158.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(40.dp)), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cabecera("guia") { TarjetaGuia(e, obra, ver, hacerGuia, verGuia) }
        cabecera("resumen") { conmuta() }
        if (est.items.isEmpty()) cabecera("vacio") {
            Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("El manuscrito", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Charcoal)
                Text("Crea partes (actos, libros…) y capítulos dentro, o capítulos sueltos si no quieres partes. Cada capítulo es un cuadrado: ábrelo para escribir sus escenas, leerlo entero o apuntar notas.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        val arriba = est.items.map { if (it is EscribirEstructura.Item.Parte) it.parte else (it as EscribirEstructura.Item.Cap).cap }
        // Subir o bajar algo de arriba (partes y capítulos sueltos comparten el orden)
        fun mover(x: Doc, d: Int) {
            val i = arriba.indexOfFirst { it.id == x.id }; val j = i + d
            if (j !in arriba.indices) return
            val (antes, despues) = if (d < 0) arriba.getOrNull(j - 1)?.id to arriba[j].id else arriba[j].id to arriba.getOrNull(j + 1)?.id
            if (x.t == Escritos.T_CAP) e.mueveCapitulo(x.id, "", antes, despues) else e.mueve(x.id, antes, despues)
        }
        est.items.forEach { it ->
            val d = if (it is EscribirEstructura.Item.Parte) it.parte else (it as EscribirEstructura.Item.Cap).cap
            item(d.id) {
                Box {
                    if (it is EscribirEstructura.Item.Parte) CuadroParte(e, it.parte, it.caps, onClick = { abrirParte(d.id) }, onLong = { menu = d.id })
                    else CuadroCapitulo(e, d, onClick = { abrirCapitulo(d.id) }, onLong = { menu = d.id })
                    DropdownMenu(menu == d.id, { menu = null }, containerColor = Paper) {
                        DropdownMenuItem({ Text("Abrir") }, { menu = null; if (d.t == Escritos.T_PARTE) abrirParte(d.id) else abrirCapitulo(d.id) })
                        DropdownMenuItem({ Text("Editar") }, { menu = null; a.editar = d.id })
                        if (arriba.firstOrNull()?.id != d.id) DropdownMenuItem({ Text("Mover antes") }, { menu = null; mover(d, -1) })
                        if (arriba.lastOrNull()?.id != d.id) DropdownMenuItem({ Text("Mover después") }, { menu = null; mover(d, 1) })
                        if (d.t == Escritos.T_CAP && e.partes(pid).isNotEmpty()) DropdownMenuItem({ Text("Meter en una parte…") }, { menu = null; meter = d })
                    }
                }
            }
        }
        if (est.items.isNotEmpty()) cabecera("ayuda") { Text("Mantén pulsado un cuadrado para ordenarlo o meter un capítulo en una parte.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
    if (parteNueva) ParteDialogo(e, pid, null, onCerrar = { parteNueva = false })
    if (capNuevo) CapituloDialogo(e, pid, null, onCerrar = { capNuevo = false })
    meter?.let { c -> MeterEnParte(e, c) { meter = null } }
    VentanasEscribir(e, pid, a, abrir)
}

/** Elegir a qué parte va un capítulo (o sacarlo). */
@Composable
fun MeterEnParte(e: Escritos, c: Doc, onCerrar: () -> Unit) {
    HojaEscribir("¿A qué parte va «${c.s("titulo").ifBlank { "el capítulo" }}»?", onCerrar) {
        e.partes(c.pid).forEach { p ->
            Row(Modifier.fillMaxWidth().press(haptic = false) { e.mueveCapitulo(c.id, p.id, null, null); onCerrar() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(colorEscrito(p.s("color"))))
                Spacer(Modifier.width(12.dp))
                Text(p.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontSize = 20.sp, color = if (p.id == c.s("aid")) Muted else Charcoal)
            }
        }
        if (c.s("aid").isNotEmpty()) BotonEscribir("Sacarlo de la parte (suelto)", suave = true) { e.mueveCapitulo(c.id, "", null, null); onCerrar() }
    }
}

/** Vincular notas y fichas que ya existen a una parte o un capítulo (o quitarlas). */
@Composable
fun VincularDialogo(e: Escritos, pid: String, destino: Doc, onCerrar: () -> Unit) {
    val todas = remember { e.notas(pid) + e.fichas(pid) }
    val sel = remember { mutableStateListOf<String>().also { l -> l.addAll(todas.filter { destino.id in it.vin() }.map { it.id }) } }
    HojaEscribir("Vincular a «${destino.s("titulo").ifBlank { "Sin título" }}»", onCerrar) {
        if (todas.isEmpty()) Text("Aún no hay notas ni fichas en esta obra.", color = Muted, fontSize = 14.sp)
        else Text("Lo que marques sale también aquí. Las notas siguen en Notas y las fichas en Fichas.", color = Muted, fontSize = 13.sp)
        listOf("Notas" to e.notas(pid), "Fichas" to e.fichas(pid)).forEach { (t, l) ->
            if (l.isEmpty()) return@forEach
            EtiquetaEscribir(t)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                l.forEach { d ->
                    val on = d.id in sel
                    SoftChip((if (on) "✓ " else "") + (if (d.t == Escritos.T_FICHA) d.s("nombre") else d.s("titulo").ifBlank { singularNota(d.s("tipo")) }).ifBlank { "Sin título" }, on) { if (on) sel.remove(d.id) else sel.add(d.id) }
                }
            }
        }
        BotonEscribir("Guardar") {
            todas.forEach { x ->
                val tiene = destino.id in x.vin()
                if (tiene != (x.id in sel)) e.edita(x.id) { ponVin(if (x.id in sel) x.vin() + destino.id else x.vin() - destino.id) }
            }
            onCerrar()
        }
    }
}

// ---------------------------------------------------------------- La página de una parte

@Composable
fun ParteScreen(e: Escritos, aid: String, onBack: () -> Unit, abrirCapitulo: (String) -> Unit, abrir: (String) -> Unit, verArbol: (String) -> Unit) {
    val ver = e.version
    val p = e.get(aid)
    if (p == null || p.t != Escritos.T_PARTE) { LaunchedEffect(aid) { onBack() }; return }
    val pid = p.pid
    val obra = e.get(pid)
    val caps = remember(ver, aid) { EscribirEstructura.estructura(e.deTipo(Escritos.T_CAP, pid), e.partes(pid)).items.filterIsInstance<EscribirEstructura.Item.Parte>().find { it.parte.id == aid }?.caps.orEmpty() }
    val vin = remember(ver, aid) { EscribirEstructura.vinculados(aid, e.notas(pid) + e.fichas(pid)) }
    val numero = e.partes(pid).indexOfFirst { it.id == aid }
    val a = remember { AbreEscribir() }
    var capNuevo by remember { mutableStateOf(false) }
    var notaNueva by remember { mutableStateOf<String?>(null) }
    var vincular by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 880.dp).statusBarsPadding().padding(horizontal = if (Skin.revista) 18.dp else 14.dp)) {
        TopBar(obra?.s("titulo").orEmpty().ifBlank { "La obra" }, onBack, p.s("titulo").ifBlank { "Sin título" }) {
            EnlaceRevista("Editar", Modifier.semantics { contentDescription = "Editar la parte" }) { a.editar = aid }
        }
        LazyVerticalGrid(GridCells.Adaptive(158.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(40.dp)), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cabecera("info") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Parte ${NUMS.getOrNull(numero) ?: "${numero + 1}"} · ${caps.size} ${if (caps.size == 1) "capítulo" else "capítulos"} · ${cifraE(caps.sumOf { e.palabrasCapitulo(it.id) })} palabras", color = Muted, fontSize = 13.sp)
                    Box(Modifier.width(56.dp).height(5.dp).background(colorEscrito(p.s("color"))))
                    if (p.s("resumen").isNotBlank()) Text(p.s("resumen"), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 23.sp, color = Charcoal)
                }
            }
            cabecera("capsT") { Seccion("Capítulos") }
            caps.forEachIndexed { i, c ->
                item(c.id) {
                    Box {
                        CuadroCapitulo(e, c, onClick = { abrirCapitulo(c.id) }, onLong = { menu = c.id })
                        DropdownMenu(menu == c.id, { menu = null }, containerColor = Paper) {
                            DropdownMenuItem({ Text("Editar") }, { menu = null; a.editar = c.id })
                            if (i > 0) DropdownMenuItem({ Text("Mover antes") }, { menu = null; e.mueveCapitulo(c.id, aid, caps.getOrNull(i - 2)?.id, caps[i - 1].id) })
                            if (i < caps.lastIndex) DropdownMenuItem({ Text("Mover después") }, { menu = null; e.mueveCapitulo(c.id, aid, caps[i + 1].id, caps.getOrNull(i + 2)?.id) })
                            DropdownMenuItem({ Text("Sacarlo de la parte") }, { menu = null; e.mueveCapitulo(c.id, "", null, null) })
                        }
                    }
                }
            }
            item("capN") { CuadroNuevo("Nuevo capítulo") { capNuevo = true } }
            cabecera("vinT") { Seccion("Notas y fichas", "Vincular →") { vincular = true } }
            vin.forEach { d -> item("v" + d.id) { CuadroVinculado(d) { a.ver = d.id } } }
            listOf("nota" to "Nota", "resumen" to "Resumen", "esquema" to "Esquema").forEach { (k, n) -> item("n$k") { CuadroNuevo(n) { notaNueva = k } } }
        }
    }
    if (capNuevo) CapituloDialogo(e, pid, null, onCerrar = { capNuevo = false }, aidNuevo = aid)
    notaNueva?.let { t -> NotaDialogo(e, pid, null, tipoNuevo = t, onCerrar = { notaNueva = null }, vinNuevo = listOf(aid)) }
    if (vincular) VincularDialogo(e, pid, p) { vincular = false }
    VentanasEscribir(e, pid, a, abrir, verArbol)
}

// ---------------------------------------------------------------- La página de un capítulo

@Composable
fun CapituloScreen(e: Escritos, cid: String, onBack: () -> Unit, abrir: (String) -> Unit, verArbol: (String) -> Unit) {
    val ver = e.version
    val c = e.get(cid)
    if (c == null || c.t != Escritos.T_CAP) { LaunchedEffect(cid) { onBack() }; return }
    val pid = c.pid
    val obra = e.get(pid)
    val parte = e.get(c.s("aid"))
    val esc = remember(ver, cid) { e.escenas(cid) }
    val caps = remember(ver, pid) { e.capitulos(pid) }
    val vin = remember(ver, cid) { EscribirEstructura.vinculados(cid, e.notas(pid) + e.fichas(pid)) }
    val a = remember { AbreEscribir() }
    var leer by remember { mutableStateOf(false) }
    var notaNueva by remember { mutableStateOf<String?>(null) }
    var vincular by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    var mover by remember { mutableStateOf<Doc?>(null) }
    Column(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 880.dp).statusBarsPadding().padding(horizontal = if (Skin.revista) 18.dp else 14.dp)) {
        TopBar(parte?.s("titulo")?.ifBlank { "La parte" } ?: obra?.s("titulo").orEmpty().ifBlank { "La obra" }, onBack, c.s("titulo").ifBlank { "Sin título" }) {
            EnlaceRevista("Editar", Modifier.semantics { contentDescription = "Editar el capítulo" }) { a.editar = cid }
        }
        LazyVerticalGrid(GridCells.Adaptive(158.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(40.dp)), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cabecera("info") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Capítulo ${numeroCapitulo(e, c)} · ${esc.size} ${if (esc.size == 1) "escena" else "escenas"} · ${cifraE(e.palabrasCapitulo(cid))} palabras", color = Muted, fontSize = 13.sp)
                    Box(Modifier.width(56.dp).height(5.dp).background(colorEscrito(c.s("color"), Charcoal)))
                    if (c.s("resumen").isNotBlank()) Text(c.s("resumen"), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, lineHeight = 23.sp, color = Charcoal)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BotonEscribir("Escribir") {
                            val s = esc.maxByOrNull { it.u }
                            if (s != null) abrir(s.id) else abrir(e.nuevo(Escritos.T_ESCENA, pid) { put("cid", cid); put("titulo", "Escena 1") }.id)
                        }
                        BotonEscribir("Leerlo entero", suave = true) { leer = true }
                    }
                }
            }
            cabecera("escT") { Seccion("Escenas") }
            esc.forEachIndexed { i, s ->
                item(s.id) {
                    Box {
                        val estado = s.i("estado").coerceIn(0, 2)
                        CuadroEscribir(colorEscrito(s.s("color"), colorEstado(estado)), ESTADOS_ESCENA[estado] + if (s.s("cuando").isNotBlank()) " · ${s.s("cuando")}" else "", s.s("titulo").ifBlank { "Sin título" },
                            s.s("resumen").ifBlank { primeraLinea(s.s("texto")) }.ifBlank { "Sin escribir todavía." },
                            "${cifraE(s.i("palabras"))} palabras" + if (s.lista("etq").isNotEmpty()) " · " + s.lista("etq").joinToString(" ") { "#$it" } else "",
                            onClick = { abrir(s.id) }, onLong = { menu = s.id })
                        DropdownMenu(menu == s.id, { menu = null }, containerColor = Paper) {
                            DropdownMenuItem({ Text("Abrir") }, { menu = null; abrir(s.id) })
                            if (i > 0) DropdownMenuItem({ Text("Mover antes") }, { menu = null; e.mueveEscena(s.id, cid, esc.getOrNull(i - 2)?.id, esc[i - 1].id) })
                            if (i < esc.lastIndex) DropdownMenuItem({ Text("Mover después") }, { menu = null; e.mueveEscena(s.id, cid, esc[i + 1].id, esc.getOrNull(i + 2)?.id) })
                            DropdownMenuItem({ Text("Mover a otro capítulo…") }, { menu = null; mover = s })
                            DropdownMenuItem({ Text("Duplicar") }, { menu = null; e.duplicaEscena(s.id) })
                            DropdownMenuItem({ Text("Borrar", color = ColGeneral) }, { menu = null; aLaPapelera(e, s) })
                        }
                    }
                }
            }
            item("escN") { CuadroNuevo("Nueva escena") { abrir(e.nuevo(Escritos.T_ESCENA, pid) { put("cid", cid); put("titulo", "Escena ${esc.size + 1}") }.id) } }
            cabecera("vinT") { Seccion("Notas y fichas", "Vincular →") { vincular = true } }
            vin.forEach { d -> item("v" + d.id) { CuadroVinculado(d) { a.ver = d.id } } }
            listOf("nota" to "Nota", "resumen" to "Resumen").forEach { (k, n) -> item("n$k") { CuadroNuevo(n) { notaNueva = k } } }
        }
    }
    if (leer) LeerCapitulo(e, c) { leer = false }
    notaNueva?.let { t -> NotaDialogo(e, pid, null, tipoNuevo = t, onCerrar = { notaNueva = null }, vinNuevo = listOf(cid)) }
    if (vincular) VincularDialogo(e, pid, c) { vincular = false }
    mover?.let { s ->
        HojaEscribir("Mover «${s.s("titulo").ifBlank { "escena" }}»", { mover = null }) {
            caps.forEach { x -> Text("${numeroCapitulo(e, x)}. " + x.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontSize = 20.sp, color = if (x.id == cid) Muted else Charcoal,
                modifier = Modifier.fillMaxWidth().press(haptic = false) { e.mueveEscena(s.id, x.id, e.escenas(x.id).lastOrNull { it.id != s.id }?.id, null); mover = null }.padding(vertical = 10.dp)) }
        }
    }
    VentanasEscribir(e, pid, a, abrir, verArbol)
}

/** Leer el capítulo entero, como un libro (sin poder escribir), con sus escenas una tras otra. */
@Composable
fun LeerCapitulo(e: Escritos, c: Doc, onCerrar: () -> Unit) {
    val esc = e.escenas(c.id)
    HojaEscribir(c.s("titulo").ifBlank { "Sin título" }, onCerrar) {
        Text("CAPÍTULO ${numeroCapitulo(e, c)}", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        if (esc.all { it.s("texto").isBlank() }) Text("Todavía no hay nada escrito.", color = Muted, fontSize = 14.sp)
        esc.forEachIndexed { i, s ->
            if (i > 0) Text("* * *", color = Muted, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().wrapContentWidth().padding(vertical = 8.dp))
            if (s.s("titulo").isNotBlank()) Text(s.s("titulo").uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
            ParrafosEscribir(s.s("texto"), tam = 18)
        }
    }
}
