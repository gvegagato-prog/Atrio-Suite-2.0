@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale

// ---------------------------------------------------------------- Fichas

@Composable
fun FichasObra(e: Escritos, obra: Doc, ver: Int, abrir: (String) -> Unit) {
    val pid = obra.id
    var tipo by remember { mutableStateOf("") }
    var editar by remember { mutableStateOf<Doc?>(null) }
    var nueva by remember { mutableStateOf(false) }
    val fichas = remember(ver, pid) { e.fichas(pid) }
    val escenas = remember(ver, pid) { e.escenasDe(pid) }
    // En cuántas escenas sale cada ficha
    val sale = remember(ver, pid) { val m = HashMap<String, Int>(); escenas.forEach { s -> e.fichasEn(pid, s.s("texto")).forEach { m[it.id] = (m[it.id] ?: 0) + 1 } }; m }
    val lista = fichas.filter { tipo.isBlank() || it.s("tipo") == tipo }.sortedBy { it.s("nombre").lowercase() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(40.dp))) {
        item("tipos") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftChip("Todas ${fichas.size}", tipo.isBlank()) { tipo = "" }
                TIPOS_FICHA.forEach { (k, n) -> val c = fichas.count { it.s("tipo") == k }; if (c > 0 || tipo == k) SoftChip("$n $c", tipo == k) { tipo = k } }
            }
        }
        if (fichas.isEmpty()) item("vacio") {
            Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("El mundo de tu historia", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Charcoal)
                Text("Personajes, lugares, objetos y reglas del mundo en fichas. Cuando escribas su nombre (o un alias) en una escena, quedará subrayado y lo tendrás en el panel, con sus datos, sin salir del texto.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        items(lista.size) { i ->
            val f = lista[i]
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(shp(14.dp)).background(Crudo).border(1.dp, Line, shp(14.dp)).press(haptic = false) { editar = f }.padding(14.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 5.dp, end = 12.dp).size(14.dp).clip(CircleShape).background(colorEscrito(f.s("color"), ColGeneral)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(f.s("nombre"), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Charcoal)
                    Text((singularFicha(f.s("tipo"))) + (sale[f.id]?.let { " · sale en $it ${if (it == 1) "escena" else "escenas"}" } ?: " · aún no sale"), color = Muted, fontSize = 12.sp)
                    if (f.s("resumen").isNotBlank()) Text(f.s("resumen"), color = Charcoal, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item("nueva") { BotonEscribir("Nueva ficha", suave = true, modifier = Modifier.padding(top = 14.dp)) { nueva = true } }
    }
    if (nueva) FichaDialogo(e, pid, null, tipoNuevo = tipo.ifBlank { "personaje" }, onCerrar = { nueva = false })
    editar?.let { f -> FichaDialogo(e, pid, e.get(f.id), onCerrar = { editar = null }, abrirEscena = { abrir(it.id) }) }
}

// ---------------------------------------------------------------- Notas (el tablón)

@Composable
fun NotasObra(e: Escritos, obra: Doc, ver: Int) {
    val pid = obra.id
    var tipo by remember { mutableStateOf("") }
    var editar by remember { mutableStateOf<Doc?>(null) }
    var nueva by remember { mutableStateOf(false) }
    val notas = remember(ver, pid) { e.notas(pid) }
    val lista = notas.filter { tipo.isBlank() || it.s("tipo") == tipo }.sortedWith(compareByDescending<Doc> { it.b("fija") }.thenByDescending { it.u })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(40.dp))) {
        item("tipos") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftChip("Todo ${notas.size}", tipo.isBlank()) { tipo = "" }
                TIPOS_NOTA.forEach { (k, n) -> val c = notas.count { it.s("tipo") == k }; SoftChip("$n $c", tipo == k) { tipo = k } }
            }
        }
        if (notas.isEmpty()) item("vacio") {
            Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("El tablón", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Charcoal)
                Text("Ideas sueltas, investigación, cosas por arreglar y plantillas (como una ficha de escena) que puedes tener junto al texto mientras escribes.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
        }
        // Dos columnas de tarjetas, como un tablón
        item("tablon") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                listOf(0, 1).forEach { col ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        lista.filterIndexed { i, _ -> i % 2 == col }.forEach { n ->
                            val c = colorEscrito(n.s("color"), ColGeneral)
                            Column(Modifier.fillMaxWidth().clip(shp(14.dp)).background(c.copy(alpha = 0.16f)).border(1.dp, c.copy(alpha = 0.5f), shp(14.dp)).press(haptic = false) { editar = n }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text((TIPOS_NOTA.find { it.first == n.s("tipo") }?.second?.removeSuffix("s")?.uppercase() ?: "NOTA") + if (n.b("fija")) " · FIJADA" else "", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                                if (n.s("titulo").isNotBlank()) Text(n.s("titulo"), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.sp, color = Charcoal)
                                if (n.s("texto").isNotBlank()) Text(n.s("texto"), color = Charcoal, fontSize = 13.sp, maxLines = 9, overflow = TextOverflow.Ellipsis, fontStyle = if (n.s("tipo") == "idea") FontStyle.Italic else FontStyle.Normal)
                            }
                        }
                    }
                }
            }
        }
        item("nueva") { BotonEscribir("Nueva nota", suave = true, modifier = Modifier.padding(top = 14.dp)) { nueva = true } }
    }
    if (nueva) NotaDialogo(e, pid, null, tipoNuevo = tipo.ifBlank { "nota" }, onCerrar = { nueva = false })
    editar?.let { n -> NotaDialogo(e, pid, e.get(n.id), onCerrar = { editar = null }) }
}

// ---------------------------------------------------------------- Línea temporal

@Composable
fun LineaObra(e: Escritos, obra: Doc, ver: Int, abrir: (String) -> Unit) {
    val pid = obra.id
    var editar by remember { mutableStateOf<Doc?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    val hitos = remember(ver, pid) { e.hitos(pid) }
    val escenas = remember(ver, pid) { e.escenasDe(pid) }
    val conFecha = escenas.filter { it.s("cuando").isNotBlank() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(40.dp))) {
        item("intro") { Text("Los hitos de tu historia en orden (arrástralos con ▲▼) y, debajo, las escenas a las que has puesto «cuándo pasa» en su panel.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp)) }
        items(hitos.size) { i ->
            val h = hitos[i]
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(2.dp).height(14.dp).background(if (i == 0) Color.Transparent else Charcoal))
                    Box(Modifier.size(12.dp).clip(CircleShape).background(ColGeneral))
                    Box(Modifier.width(2.dp).weight(1f).background(if (i == hitos.lastIndex) Color.Transparent else Charcoal))
                }
                Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 14.dp).press(haptic = false) { editar = h }) {
                    if (h.s("cuando").isNotBlank()) Text(h.s("cuando").uppercase(), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text(h.s("titulo"), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 23.sp, color = Charcoal)
                    if (h.s("nota").isNotBlank()) Text(h.s("nota"), color = Muted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    e.get(h.s("eid"))?.let { s -> Text("→ " + s.s("titulo").ifBlank { "escena" }, color = ColGeneral, fontSize = 13.sp, modifier = Modifier.press(haptic = false) { abrir(s.id) }.padding(vertical = 4.dp)) }
                }
                Column {
                    Text("▲", color = if (i > 0) Charcoal else Line, fontSize = 15.sp, modifier = Modifier.press(haptic = false) { if (i > 0) e.mueve(h.id, hitos.getOrNull(i - 2)?.id, hitos[i - 1].id) }.padding(8.dp))
                    Text("▼", color = if (i < hitos.lastIndex) Charcoal else Line, fontSize = 15.sp, modifier = Modifier.press(haptic = false) { if (i < hitos.lastIndex) e.mueve(h.id, hitos[i + 1].id, hitos.getOrNull(i + 2)?.id) }.padding(8.dp))
                }
            }
        }
        item("nuevo") { BotonEscribir("Nuevo hito", suave = true, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)) { nuevo = true } }
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
    if (nuevo) HitoDialogo(e, pid, null, onCerrar = { nuevo = false })
    editar?.let { h -> HitoDialogo(e, pid, e.get(h.id), onCerrar = { editar = null }) }
}

// ---------------------------------------------------------------- Cifras

@Composable
fun CifrasObra(e: Escritos, obra: Doc, ver: Int) {
    val pid = obra.id
    val caps = remember(ver, pid) { e.capitulos(pid) }
    val escenas = remember(ver, pid) { e.escenasDe(pid) }
    val total = escenas.sumOf { it.i("palabras") }
    val meta = obra.i("meta")
    val diaria = obra.i("diaria")
    val dias = remember(ver, pid) { e.ultimosDias(30, pid) }
    val ultimos14 = dias.takeLast(14).map { it.second }
    val ritmo = if (ultimos14.any { it > 0 }) ultimos14.sum() / 14.0 else 0.0
    val hoy = e.escritas(Escritos.hoy(), pid)
    val mejor = dias.maxByOrNull { it.second }
    val estados = escenas.groupingBy { it.i("estado") }.eachCount()
    val maxCap = (caps.maxOfOrNull { e.palabrasCapitulo(it.id) } ?: 1).coerceAtLeast(1)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(40.dp)), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("grandes") {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Grande(cifraE(total), "palabras")
                Grande("${escenas.size}", if (escenas.size == 1) "escena" else "escenas")
                Grande("${caps.size}", if (caps.size == 1) "capítulo" else "capítulos")
                Grande(cifraE(if (escenas.isEmpty()) 0 else total / escenas.size), "por escena")
                Grande("${maxOf(1, (total + 219) / 220)}", "min de lectura")
            }
        }
        if (meta > 0) item("meta") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Etiq("Meta de la obra")
                Bar((total.toFloat() / meta).coerceIn(0f, 1f), colorEscrito(obra.s("color")), height = 6.dp)
                val pct = (total * 100L / meta)
                val falta = (meta - total).coerceAtLeast(0)
                Text("$pct % · " + if (falta == 0) "meta alcanzada" else "faltan ${cifraE(falta)} palabras" + if (ritmo >= 1) {
                    val diasQ = Math.ceil(falta / ritmo).toLong()
                    " · a tu ritmo de los últimos 14 días (${cifraE(ritmo.toInt())} al día), el " + java.text.SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es")).format(java.util.Date(System.currentTimeMillis() + diasQ * 86_400_000L))
                } else "", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        item("dias") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Etiq("Últimos 30 días")
                Barras(dias.map { it.second }, Modifier.fillMaxWidth().height(90.dp), diaria)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Hoy ${cifraE(hoy)}" + if (diaria > 0) " de ${cifraE(diaria)}" else "", color = Muted, fontSize = 12.sp)
                    Text("Racha ${e.racha(pid)} ${if (e.racha(pid) == 1) "día" else "días"}", color = Muted, fontSize = 12.sp)
                    if (mejor != null && mejor.second > 0) Text("Tu mejor día: ${cifraE(mejor.second)} (${LocalDate.ofEpochDay(mejor.first).dayOfMonth}/${LocalDate.ofEpochDay(mejor.first).monthValue})", color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (escenas.isNotEmpty()) item("estados") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Etiq("Cómo va el manuscrito")
                Row(Modifier.fillMaxWidth().height(10.dp).clip(shp(5.dp))) {
                    ESTADOS_ESCENA.indices.forEach { i -> val n = estados[i] ?: 0; if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(colorEstado(i))) }
                }
                Text(ESTADOS_ESCENA.mapIndexed { i, n -> "${estados[i] ?: 0} ${n.lowercase()}" }.joinToString(" · "), color = Muted, fontSize = 12.sp)
            }
        }
        item("sprints") { SprintsObra(pid) }
        if (caps.isNotEmpty()) item("caps") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Etiq("Palabras por capítulo")
                caps.forEachIndexed { i, c ->
                    val p = e.palabrasCapitulo(c.id)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${i + 1}", color = Muted, fontSize = 12.sp, modifier = Modifier.width(22.dp))
                        Box(Modifier.weight(1f).height(10.dp).clip(shp(5.dp)).background(Line.copy(alpha = 0.5f))) { Box(Modifier.fillMaxHeight().fillMaxWidth(p.toFloat() / maxCap).background(Charcoal.copy(alpha = 0.75f))) }
                        Text(cifraE(p), color = Muted, fontSize = 12.sp, modifier = Modifier.width(56.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun Grande(n: String, t: String) = Column { Text(n, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp, color = Charcoal); Text(t.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp) }
@Composable private fun Etiq(t: String) = Text(t.uppercase(), color = Charcoal, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)

// ---------------------------------------------------------------- Exportar

@Composable
fun ExportarDialogo(e: Escritos, obra: Doc, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var formato by remember { mutableStateOf("docx") }
    var capitulos by remember { mutableStateOf(true) }
    var escenasT by remember { mutableStateOf(false) }
    var minimo by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }
    val formatos = listOf("docx" to "Word (.docx)", "epub" to "Libro (.epub)", "md" to "Markdown (.md)", "txt" to "Texto (.txt)")
    HojaEscribir("Exportar «${obra.s("titulo")}»", onCerrar) {
        Text("Se hace en tu móvil: el texto no se envía a ningún sitio. Después eliges con qué app guardarlo o enviarlo.", color = Muted, fontSize = 13.sp)
        EtiquetaEscribir("Formato")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { formatos.forEach { (k, n) -> SoftChip(n, formato == k) { formato = k } } }
        EtiquetaEscribir("Qué incluir")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftChip("Títulos de capítulo", capitulos) { capitulos = !capitulos }
            SoftChip("Títulos de escena", escenasT) { escenasT = !escenasT }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftChip("Todas las escenas", minimo == 0) { minimo = 0 }
            SoftChip("Revisión y finales", minimo == 1) { minimo = 1 }
            SoftChip("Solo finales", minimo == 2) { minimo = 2 }
        }
        BotonEscribir("Exportar") {
            val op = OpcionesExport(titulosDeCapitulo = capitulos, titulosDeEscena = escenasT, soloEstadoMinimo = minimo)
            val r = runCatching {
                val bytes = when (formato) { "docx" -> EscribirExport.docx(e, obra.id, op); "epub" -> EscribirExport.epub(e, obra.id, op); "md" -> EscribirExport.markdown(e, obra.id, op).toByteArray(); else -> EscribirExport.texto(e, obra.id, op).toByteArray() }
                val dir = java.io.File(ctx.cacheDir, "compartir").apply { mkdirs() }
                val nombre = obra.s("titulo").ifBlank { "obra" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifBlank { "obra" }
                val f = java.io.File(dir, "$nombre.$formato").apply { writeBytes(bytes) }
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".archivos", f)
                val mime = when (formato) { "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; "epub" -> "application/epub+zip"; "md" -> "text/markdown"; else -> "text/plain" }
                ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType(mime).putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), "Guardar o enviar").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            msg = r.fold({ "" }, { "No se ha podido exportar (${it.message ?: "error"})." })
        }
        if (msg.isNotEmpty()) Text(msg, color = ColGeneral, fontSize = 13.sp)
    }
}
