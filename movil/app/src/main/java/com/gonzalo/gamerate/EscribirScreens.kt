@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gonzalo.gamerate

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private val ES = Locale("es", "ES")
fun cifraE(n: Int): String = NumberFormat.getIntegerInstance(ES).format(n)

/** «hace 2 h», «ayer», «el 3 de mayo»… */
fun haceE(t: Long): String {
    val d = System.currentTimeMillis() - t
    return when {
        d < 90_000 -> "ahora mismo"
        d < 3_600_000 -> "hace ${d / 60_000} min"
        d < 86_400_000 -> "hace ${d / 3_600_000} h"
        d < 2 * 86_400_000 -> "ayer"
        d < 30L * 86_400_000 -> "hace ${d / 86_400_000} días"
        else -> java.text.SimpleDateFormat("d 'de' MMM", ES).format(java.util.Date(t))
    }
}

/**
 * Escribir: tus obras, y dentro de cada una el manuscrito (capítulos y escenas), las fichas, las notas, la línea
 * temporal y las cifras. Sin conexión se escribe igual y se sube solo (ver [Escritos]).
 */
@Composable
fun EscribirScreen(onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    var obraId by rememberSaveable { mutableStateOf<String?>(null) }
    var parteId by rememberSaveable { mutableStateOf<String?>(null) }
    var capId by rememberSaveable { mutableStateOf<String?>(null) }
    var escenaId by rememberSaveable { mutableStateOf<String?>(null) }
    val ver = e.version
    fun cierraObra() { obraId = null; parteId = null; capId = null; escenaId = null }
    // Una obra borrada desde dentro: se vuelve a la biblioteca
    LaunchedEffect(EscribirHub.obraBorrada) { if (EscribirHub.obraBorrada.isNotEmpty() && EscribirHub.obraBorrada == obraId) cierraObra() }
    if (obraId != null && e.get(obraId!!) == null) cierraObra()
    /** Abrir una escena: al volver del editor se va a la página de su capítulo (como en el PC). */
    fun abreEscena(id: String) {
        val s = e.get(id) ?: return
        val c = e.get(s.s("cid"))
        obraId = s.pid; capId = c?.id; parteId = c?.s("aid")?.takeIf { e.get(it) != null }; escenaId = id
    }
    // Desde la portada: «Seguir con esta escena»
    // (la pantalla puede componerse dos veces durante la transición: cada una la aplica; caduca a los pocos segundos)
    val pide = EscribirHub.abrir
    LaunchedEffect(pide) { if (pide != null && System.currentTimeMillis() - pide.second < 4000 && e.get(pide.first) != null) abreEscena(pide.first) }
    // «Ver en el árbol» desde una parte o un capítulo: a la obra, pestaña Árbol
    val verArbol: (String) -> Unit = { id -> parteId = null; capId = null; EscribirHub.irArbol = id }
    BackHandler(enabled = escenaId == null && (obraId != null)) {
        when {
            capId != null -> capId = null
            parteId != null -> parteId = null
            else -> obraId = null
        }
    }
    VigilaSprint(e, ctx)
    when {
        escenaId != null -> EscribirEditor(escenaId!!, onBack = { escenaId = null }, abrirEscena = { abreEscena(it) })
        capId != null -> CapituloScreen(e, capId!!, onBack = { capId = null }, abrir = { escenaId = it }, verArbol = verArbol)
        parteId != null -> ParteScreen(e, parteId!!, onBack = { parteId = null }, abrirCapitulo = { capId = it }, abrir = { abreEscena(it) }, verArbol = verArbol)
        obraId != null -> ObraScreen(e, obraId!!, onBack = { obraId = null }, abrir = { abreEscena(it) },
            abrirParte = { parteId = it }, abrirCapitulo = { id -> capId = id; parteId = e.get(id)?.s("aid")?.takeIf { e.get(it) != null } })
        else -> BibliotecaEscribir(e, ver, onBack, abrirObra = { obraId = it }, abrirEscena = { abreEscena(it) })
    }
}

@Composable
private fun BibliotecaEscribir(e: Escritos, ver: Int, onBack: () -> Unit, abrirObra: (String) -> Unit, abrirEscena: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var nueva by remember { mutableStateOf(false) }
    var archivo by remember { mutableStateOf(false) }
    var papelera by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf("") }
    val obras = remember(ver) { e.obras() }
    val enPapelera = remember(ver) { e.papelera().size }
    val activas = obras.filter { !it.b("arch") }.sortedByDescending { o -> e.escenasDe(o.id).maxOfOrNull { it.u } ?: o.u }
    val archivadas = obras.filter { it.b("arch") }
    val hoy = Escritos.hoy()
    val palabrasHoy = remember(ver) { e.escritas(hoy) }
    val racha = remember(ver) { e.racha() }
    val diaria = activas.sumOf { it.i("diaria") }
    val total = remember(ver) { activas.sumOf { e.palabrasObra(it.id) } }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    val nombre = (ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Importada")
                    val txt = if (nombre.endsWith(".docx", true)) EscribirExport.textoDeDocx(bytes) else bytes.toString(Charsets.UTF_8)
                    EscribirExport.importaTexto(e, nombre.substringBeforeLast('.'), txt)
                }
            }
            aviso = r.fold({ "Importada. Ya la tienes en tus obras." }, { "No he podido importarla (${it.message ?: "archivo no válido"})." })
        }
    }
    Column(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 880.dp).statusBarsPadding().padding(horizontal = if (Skin.revista) 18.dp else 14.dp)) {
        // Volver a Inicio
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            VolverRevista(onBack, "Atrio")
            Spacer(Modifier.weight(1f))
            EnlaceRevista("Importar", Modifier.semantics { contentDescription = "Importar un texto (Word, Markdown o texto)" }) { importar.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream")) }
            EnlaceRevista("+ Nueva obra", Modifier.semantics { contentDescription = "Nueva obra" }, acento = true) { nueva = true }
        }
        BigHeader("Escribir", if (palabrasHoy > 0) "Hoy · ${cifraE(palabrasHoy)} palabras" else "Tus obras")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(24.dp))) {
            item("hoy") { ResumenHoy(e, palabrasHoy, diaria, racha, total, ver) }
            val ultima = e.ultimaEscena()
            if (ultima != null) item("seguir") { SigueEscribiendo(e, ultima) { abrirEscena(ultima.id) } }
            if (aviso.isNotEmpty()) item("aviso") { Text(aviso, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            item("sync") { EstadoNube(e, ver) }
            if (activas.isEmpty()) item("vacio") {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Aquí empieza tu novela.", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Charcoal)
                    Text("Crea una obra y escribe por capítulos y escenas, con fichas de personajes y lugares, notas, borradores e historial. Se guarda sola, funciona sin conexión y aparece en todos tus dispositivos con tu cuenta.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                    BotonEscribir("Nueva obra") { nueva = true }
                    BotonEscribir("Importar un texto", suave = true) { importar.launch(arrayOf("text/*", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream")) }
                }
            }
            items(activas, key = { it.id }) { o -> TarjetaObra(e, o, ver) { abrirObra(o.id) } }
            if (archivadas.isNotEmpty()) {
                item("archivo") {
                    Text((if (archivo) "ARCHIVADAS ▴ " else "ARCHIVADAS ▾ ") + archivadas.size, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).press(haptic = false) { archivo = !archivo }.padding(vertical = 6.dp))
                }
                if (archivo) items(archivadas, key = { "a" + it.id }) { o -> TarjetaObra(e, o, ver) { abrirObra(o.id) } }
            }
            item("papelera") {
                Text("PAPELERA" + if (enPapelera > 0) " · $enPapelera" else "", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp).press(haptic = false) { papelera = true }.padding(vertical = 6.dp))
            }
        }
    }
    if (nueva) ObraDialogo(e, null, onCerrar = { nueva = false }, onCreada = { abrirObra(it.id) })
    if (papelera) PapeleraEscribir(e, onCerrar = { papelera = false })
}

/** La última escena que tocaste, para seguir donde lo dejaste. */
@Composable
fun SigueEscribiendo(e: Escritos, s: Doc, abrir: () -> Unit) {
    val obra = e.get(s.pid)
    val linea = s.s("texto").lineSequence().map { it.trim() }.lastOrNull { it.isNotBlank() && !it.startsWith("#") && it != "***" }.orEmpty()
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).press(haptic = false, onClick = abrir).clip(shp(16.dp)).background(Crudo).border(1.dp, Line, shp(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("SIGUE ESCRIBIENDO", color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text(s.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 25.sp, color = Charcoal, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (linea.isNotEmpty()) Text("…" + linea.takeLast(140), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(obra?.s("titulo")?.ifBlank { null }, "${cifraE(s.i("palabras"))} palabras", "editada ${haceE(s.u)}").joinToString(" · ") + "  ·  Abrir →", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun EstadoNube(e: Escritos, ver: Int) {
    val ctx = LocalContext.current
    val st = remember { Store(ctx) }
    val pend = remember(ver) { e.pendientes().size }
    val txt = when {
        !st.paired -> "Se guarda en este dispositivo. Entra en tu cuenta (Ajustes) para tenerlo en todos."
        e.error.isNotEmpty() && pend > 0 -> "Sin conexión: $pend ${if (pend == 1) "cambio" else "cambios"} por subir. Se subirán solos cuando haya red."
        e.error.isNotEmpty() -> "Sin conexión con tu cuenta (se reintenta solo)."
        pend > 0 -> "Subiendo $pend ${if (pend == 1) "cambio" else "cambios"}…"
        e.ultima > 0 -> "Todo sincronizado"
        else -> ""
    }
    if (txt.isNotEmpty()) Text(txt, color = if (e.error.isNotEmpty() && pend > 0) ColGeneral else Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun ResumenHoy(e: Escritos, hoy: Int, diaria: Int, racha: Int, total: Int, ver: Int) {
    val dias = remember(ver) { e.ultimosDias(14) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
            Column {
                Text(cifraE(hoy), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, color = if (diaria > 0 && hoy >= diaria) Color(0xFF5B8C5A) else Charcoal)
                Text(if (diaria > 0) "HOY · DE ${cifraE(diaria)}" else "PALABRAS HOY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            }
            Column { Text("$racha", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Charcoal); Text(if (racha == 1) "DÍA SEGUIDO" else "DÍAS SEGUIDOS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp) }
            Column { Text(cifraE(total), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Charcoal); Text("PALABRAS EN TOTAL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp) }
        }
        if (diaria > 0) Bar((hoy.toFloat() / diaria).coerceIn(0f, 1f), if (hoy >= diaria) Color(0xFF5B8C5A) else ColGeneral, height = 4.dp)
        Barras(dias.map { it.second }, Modifier.fillMaxWidth().height(44.dp))
    }
}

/** Barras sencillas (una por día); la última (hoy) en el acento. */
@Composable
fun Barras(valores: List<Int>, modifier: Modifier = Modifier, meta: Int = 0) {
    val acento = ColGeneral; val tenue = Line
    Box(modifier.drawBehind {
        val max = maxOf(1, valores.maxOrNull() ?: 1, meta)
        val n = valores.size.coerceAtLeast(1)
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        valores.forEachIndexed { i, v ->
            val h = if (v <= 0) 2.dp.toPx() else (size.height * v / max).coerceAtLeast(3.dp.toPx())
            drawRect(if (i == valores.lastIndex) acento else if (v > 0) Charcoal.copy(alpha = 0.55f) else tenue, Offset(i * (w + gap), size.height - h), Size(w, h))
        }
        if (meta > 0) drawRect(acento.copy(alpha = 0.55f), Offset(0f, size.height - size.height * meta / max), Size(size.width, 1.dp.toPx()))
    })
}

@Composable
private fun TarjetaObra(e: Escritos, o: Doc, ver: Int, onClick: () -> Unit) {
    val palabras = remember(ver, o.id) { e.palabrasObra(o.id) }
    val escenas = remember(ver, o.id) { e.escenasDe(o.id) }
    val meta = o.i("meta")
    val color = colorEscrito(o.s("color"))
    val ultima = escenas.maxOfOrNull { it.u } ?: o.u
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).press(haptic = false, onClick = onClick).clip(shp(16.dp)).background(Crudo).border(1.dp, Line, shp(16.dp)).height(IntrinsicSize.Min)) {
        Box(Modifier.width(8.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(o.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 29.sp, color = Charcoal, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(o.s("autor"), o.s("genero")).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 13.sp, maxLines = 1)
            if (o.s("sinopsis").isNotBlank()) Text(o.s("sinopsis"), color = Charcoal, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontStyle = FontStyle.Italic, fontFamily = Display)
            if (meta > 0) Bar((palabras.toFloat() / meta).coerceIn(0f, 1f), color, height = 4.dp)
            Text(cifraE(palabras) + (if (meta > 0) " de ${cifraE(meta)} palabras · ${(palabras * 100L / meta).coerceAtMost(999)} %" else " palabras") + " · ${escenas.size} ${if (escenas.size == 1) "escena" else "escenas"} · editada ${haceE(ultima)}",
                color = Muted, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- Una obra

@Composable
private fun ObraScreen(e: Escritos, id: String, onBack: () -> Unit, abrir: (String) -> Unit, abrirParte: (String) -> Unit, abrirCapitulo: (String) -> Unit) {
    val ver = e.version
    val obra = e.get(id) ?: return
    val nueva = EscribirHub.guiaAlEntrar
    var pestana by rememberSaveable(id) { mutableIntStateOf(if (nueva != null && nueva.first == id && System.currentTimeMillis() - nueva.second < 4000) TAB_GUIA else 0) }
    var focoArbol by remember { mutableStateOf<String?>(null) }
    val irArbol = EscribirHub.irArbol
    LaunchedEffect(irArbol) { if (irArbol != null) { focoArbol = irArbol; pestana = TAB_ARBOL; EscribirHub.irArbol = null } }
    val a = remember { AbreEscribir() }
    var accionGuia by remember { mutableStateOf<Int?>(null) }
    var ajustes by remember { mutableStateOf(false) }
    var exportar by remember { mutableStateOf(false) }
    var buscar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 880.dp).statusBarsPadding().padding(horizontal = if (Skin.revista) 18.dp else 14.dp)) {
        TopBar("Escribir", onBack, obra.s("titulo").ifBlank { "Sin título" }) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                EnlaceRevista("Buscar", Modifier.semantics { contentDescription = "Buscar en la obra" }) { buscar = true }
                EnlaceRevista("Exportar") { exportar = true }
                EnlaceRevista("Ajustes", Modifier.semantics { contentDescription = "Ajustes de la obra" }) { ajustes = true }
            }
        }
        PestanasRevista(PESTANAS_OBRA, pestana, Modifier.padding(bottom = 10.dp)) { pestana = it }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (pestana) {
                0 -> ManuscritoObra(e, obra, ver, abrirParte, abrirCapitulo, abrir, hacerGuia = { accionGuia = it }, verGuia = { pestana = TAB_GUIA })
                1 -> FichasObra(e, obra, ver, a)
                TAB_ARBOL -> ArbolObra(e, obra, ver, a, focoArbol)
                3 -> NotasObra(e, obra, ver, a)
                4 -> LineaObra(e, obra, ver, a, abrir)
                TAB_GUIA -> GuiaObra(e, obra, ver, hacer = { accionGuia = it })
                else -> CifrasObra(e, obra, ver)
            }
        }
    }
    accionGuia?.let { a -> AccionGuia(a, e, obra, abrir = abrir, irManuscrito = { pestana = 0 }, exportar = { exportar = true }, onCerrar = { accionGuia = null }) }
    if (ajustes) ObraDialogo(e, obra, onCerrar = { ajustes = false })
    if (exportar) ExportarDialogo(e, obra, onCerrar = { exportar = false })
    if (buscar) BuscarObraDialogo(e, obra, onCerrar = { buscar = false }, abrir = { buscar = false; abrir(it) })
    VentanasEscribir(e, obra.id, a, abrir, verArbol = { focoArbol = it; pestana = TAB_ARBOL })
}

/** Las pestañas de una obra (las mismas que en el PC). */
val PESTANAS_OBRA = listOf("Manuscrito", "Fichas", "Árbol", "Notas", "Línea", "Cifras", "Guía")
const val TAB_ARBOL = 2
const val TAB_GUIA = 6

/** El manuscrito en lista (capítulos y escenas, como antes de los cuadrados). */
@Composable
fun ManuscritoLista(e: Escritos, obra: Doc, ver: Int, abrir: (String) -> Unit, abrirCapitulo: (String) -> Unit, hacerGuia: (Int) -> Unit = {}, verGuia: () -> Unit = {}, cabecera: @Composable () -> Unit = {}) {
    val pid = obra.id
    var esquema by rememberSaveable { mutableStateOf(false) }
    var etiqueta by rememberSaveable(pid) { mutableStateOf("") }
    var capDlg by remember { mutableStateOf<Doc?>(null) }
    var capNuevo by remember { mutableStateOf(false) }
    val caps = remember(ver, pid) { e.capitulos(pid) }
    val palabras = remember(ver, pid) { e.palabrasObra(pid) }
    val meta = obra.i("meta")
    val estados = remember(ver, pid) { e.escenasDe(pid).groupingBy { it.i("estado") }.eachCount() }
    val etiquetas = remember(ver, pid) { e.escenasDe(pid).flatMap { it.lista("etq") }.map { it.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    if (etiqueta.isNotEmpty() && etiqueta !in etiquetas) etiqueta = ""
    fun pasa(s: Doc) = etiqueta.isEmpty() || s.lista("etq").any { it.trim() == etiqueta }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(40.dp))) {
        item("guia") { TarjetaGuia(e, obra, ver, hacerGuia, verGuia) }
        item("cab") { cabecera() }
        item("resumen") {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SoftChip(if (esquema) "Ver el texto" else "Ver resúmenes", esquema) { esquema = !esquema }
                }
                // Filtrar por etiqueta (#flashback, #Aldara…): solo las escenas que la llevan
                if (etiquetas.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftChip("Todas", etiqueta.isEmpty()) { etiqueta = "" }
                    etiquetas.forEach { t -> SoftChip("#$t", etiqueta == t) { etiqueta = if (etiqueta == t) "" else t } }
                }
            }
        }
        var parte = ""
        caps.forEachIndexed { ci, c ->
            val esc = e.escenas(c.id).filter { pasa(it) }
            if (etiqueta.isNotEmpty() && esc.isEmpty()) return@forEachIndexed
            val p = c.s("aid").let { e.get(it)?.s("titulo")?.ifBlank { "Sin título" } } ?: c.s("parte")
            if (p.isNotBlank() && p != parte) { parte = p; item("p$ci${c.id}") { Text(p.uppercase(), color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(top = 22.dp, bottom = 2.dp)) } }
            item("c${c.id}") { CabeceraCapitulo(e, c, ci, caps, onAbrir = { abrirCapitulo(c.id) }, onEditar = { capDlg = c }) }
            items(esc, key = { it.id }) { s -> FilaEscena(e, s, caps, esquema, onAbrir = { abrir(s.id) }) }
            if (etiqueta.isEmpty()) item("n${c.id}") { Text("+ ESCENA", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp).press(haptic = false) { val n = e.nuevo(Escritos.T_ESCENA, pid) { put("cid", c.id); put("titulo", "Escena ${esc.size + 1}") }; abrir(n.id) }.padding(8.dp)) }
        }
        item("nuevoCap") { BotonEscribir("Nuevo capítulo", suave = true, modifier = Modifier.padding(top = 18.dp)) { capNuevo = true } }
    }
    if (capNuevo) CapituloDialogo(e, pid, null, onCerrar = { capNuevo = false })
    capDlg?.let { c -> CapituloDialogo(e, pid, e.get(c.id), onCerrar = { capDlg = null }) }
}

@Composable
private fun CabeceraCapitulo(e: Escritos, c: Doc, i: Int, caps: List<Doc>, onAbrir: () -> Unit, onEditar: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val pal = e.palabrasCapitulo(c.id)
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).press(haptic = false, onClick = onAbrir)) {
            Text("CAPÍTULO ${i + 1}", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
            Text(c.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 26.sp, color = Charcoal)
            if (c.s("resumen").isNotBlank()) Text(c.s("resumen"), color = Muted, fontSize = 13.sp, fontStyle = FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(cifraE(pal), color = Muted, fontSize = 12.sp)
        Box {
            IconPill(Icons.Rounded.MoreVert, "Más") { menu = true }
            DropdownMenu(menu, { menu = false }, containerColor = Paper) {
                DropdownMenuItem({ Text("Editar") }, { menu = false; onEditar() })
                DropdownMenuItem({ Text("Abrir su página") }, { menu = false; onAbrir() })
                // (sube y baja entre los de su misma parte)
                val hermanos = caps.filter { it.s("aid") == c.s("aid") }
                val j = hermanos.indexOfFirst { it.id == c.id }
                if (j > 0) DropdownMenuItem({ Text("Subir") }, { menu = false; e.mueveCapitulo(c.id, c.s("aid"), hermanos.getOrNull(j - 2)?.id, hermanos[j - 1].id) })
                if (j in 0 until hermanos.size - 1) DropdownMenuItem({ Text("Bajar") }, { menu = false; e.mueveCapitulo(c.id, c.s("aid"), hermanos[j + 1].id, hermanos.getOrNull(j + 2)?.id) })
            }
        }
    }
}

@Composable
private fun FilaEscena(e: Escritos, s: Doc, caps: List<Doc>, esquema: Boolean, onAbrir: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var mover by remember { mutableStateOf(false) }
    val estado = s.i("estado")
    Row(Modifier.fillMaxWidth().animateContentSize().press(haptic = false, onClick = onAbrir).padding(start = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp, end = 12.dp).size(12.dp).clip(CircleShape).background(colorEstado(estado)).press(haptic = false) { e.edita(s.id) { put("estado", (estado + 1) % 3) } })
        Column(Modifier.weight(1f)) {
            Text(s.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontSize = 18.sp, lineHeight = 22.sp, color = Charcoal, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val texto = if (esquema) s.s("resumen").ifBlank { s.s("texto").lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() } else s.s("texto").lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("#") }.orEmpty()
            if (texto.isNotBlank()) Text(texto, color = Muted, fontSize = 13.sp, maxLines = if (esquema) 4 else 1, overflow = TextOverflow.Ellipsis)
            if (s.s("cuando").isNotBlank() || s.lista("etq").isNotEmpty()) Text((listOf(s.s("cuando")) + s.lista("etq").map { "#$it" }).filter { it.isNotBlank() }.joinToString(" · "), color = ColGeneral, fontSize = 11.sp, maxLines = 1)
        }
        Text(cifraE(s.i("palabras")), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, end = 2.dp))
        Box {
            Icon(Icons.Rounded.MoreVert, "Más", tint = Muted, modifier = Modifier.press(haptic = false) { menu = true }.padding(8.dp).size(20.dp))
            DropdownMenu(menu, { menu = false }, containerColor = Paper) {
                DropdownMenuItem({ Text("Abrir") }, { menu = false; onAbrir() })
                DropdownMenuItem({ Text("Subir") }, { menu = false; sube(e, s, caps) })
                DropdownMenuItem({ Text("Bajar") }, { menu = false; baja(e, s, caps) })
                DropdownMenuItem({ Text("Mover a otro capítulo…") }, { menu = false; mover = true })
                DropdownMenuItem({ Text("Duplicar") }, { menu = false; e.duplicaEscena(s.id) })
                DropdownMenuItem({ Text("Borrar", color = ColGeneral) }, { menu = false; aLaPapelera(e, s) })
            }
        }
    }
    if (mover) HojaEscribir("Mover «${s.s("titulo").ifBlank { "escena" }}»", { mover = false }) {
        caps.forEach { c -> Text(c.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontSize = 20.sp, color = if (c.id == s.s("cid")) Muted else Charcoal,
            modifier = Modifier.fillMaxWidth().press(haptic = false) { e.mueveEscena(s.id, c.id, e.escenas(c.id).lastOrNull { it.id != s.id }?.id, null); mover = false }.padding(vertical = 10.dp)) }
    }
}

/** Una escena un puesto arriba (la primera de un capítulo pasa al final del anterior). */
private fun sube(e: Escritos, s: Doc, caps: List<Doc>) {
    val cid = s.s("cid")
    val hermanas = e.escenas(cid)
    val i = hermanas.indexOfFirst { it.id == s.id }
    if (i > 0) e.mueveEscena(s.id, cid, hermanas.getOrNull(i - 2)?.id, hermanas[i - 1].id)
    else caps.indexOfFirst { it.id == cid }.takeIf { it > 0 }?.let { ci -> val ant = caps[ci - 1].id; e.mueveEscena(s.id, ant, e.escenas(ant).lastOrNull()?.id, null) }
}

private fun baja(e: Escritos, s: Doc, caps: List<Doc>) {
    val cid = s.s("cid")
    val hermanas = e.escenas(cid)
    val i = hermanas.indexOfFirst { it.id == s.id }
    if (i < hermanas.size - 1) e.mueveEscena(s.id, cid, hermanas[i + 1].id, hermanas.getOrNull(i + 2)?.id)
    else caps.indexOfFirst { it.id == cid }.takeIf { it in 0 until caps.size - 1 }?.let { ci -> val sig = caps[ci + 1].id; e.mueveEscena(s.id, sig, null, e.escenas(sig).firstOrNull()?.id) }
}

@Composable
private fun BuscarObraDialogo(e: Escritos, obra: Doc, onCerrar: () -> Unit, abrir: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val res = remember(q, e.version) { if (q.trim().length < 2) emptyList() else e.busca(obra.id, q) }
    HojaEscribir("Buscar en «${obra.s("titulo")}»", onCerrar) {
        CampoEscribir(q, { q = it }, "Una palabra o una frase", mayus = false)
        if (q.trim().length >= 2 && res.isEmpty()) Text("Nada con «$q».", color = Muted, fontSize = 14.sp)
        if (res.isNotEmpty()) Text("${res.size} ${if (res.size == 1) "resultado" else "resultados"}", color = Muted, fontSize = 12.sp)
        res.forEach { h ->
            Column(Modifier.fillMaxWidth().press(haptic = false) {
                when (h.doc.t) { Escritos.T_ESCENA -> abrir(h.doc.id); else -> onCerrar() }
            }.padding(vertical = 6.dp)) {
                Text(h.donde.uppercase(), color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1)
                Text(h.extracto, fontFamily = Display, fontSize = 15.sp, color = Charcoal, maxLines = 3)
            }
        }
    }
}
