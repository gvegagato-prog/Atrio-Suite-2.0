@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as EstiloFecha
import java.util.Locale

/*
 * La pantalla del Diario. Arriba, hoy: lo que escribes, lo que llevas hecho hoy y lo que tienes entre manos para
 * apuntar algo en un toque. Debajo, los días anteriores como páginas (del más nuevo al más viejo), agrupados por meses
 * con su resumen. Y el calendario, para ir a cualquier día de un vistazo.
 */

private val ES = Locale("es", "ES")
private val FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
private val FECHA_CON_ANO = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES)

/** «Lunes 26 de octubre» (y el año si no es este). */
fun fechaDiario(dia: Long, hoy: LocalDate = LocalDate.now()): String {
    val d = LocalDate.ofEpochDay(dia)
    return d.format(if (d.year == hoy.year) FECHA_LARGA else FECHA_CON_ANO).replaceFirstChar { it.uppercase() }
}

private fun diaSemana(dia: Long) = LocalDate.ofEpochDay(dia).dayOfWeek.getDisplayName(EstiloFecha.FULL, ES)

/** Lo que la pantalla puede pedir a la app. */
class DiarioAcciones(
    /** La ficha de lo que tocaste ese día. */
    val abrir: (Toque) -> Unit = {},
    /** El libro, justo en ese subrayado. */
    val cita: (Toque, BookNote) -> Unit = { _, _ -> },
    val captura: (Shot) -> Unit = {},
    /** Escribir ese día (o cambiar lo que escribiste). */
    val escribir: (Long, Nota?) -> Unit = { _, _ -> },
    /** Un apunte rápido de algo que llevas entre manos. */
    val apuntar: (Kind, Play) -> Unit = { _, _ -> },
    /** Una ficha de las que habla lo que escribiste ("categoría:id"). */
    val abrirRef: (String) -> Unit = {},
    /** El recordatorio para escribir (null: sin botón). */
    val aviso: (() -> Unit)? = null
)

@Composable
fun DiarioScreen(
    dias: List<Dia>?,
    enCurso: List<Pair<Kind, Play>>,
    kinds: List<Kind>, filtro: Int?, onFiltro: (Int?) -> Unit,
    pareja: String?, viendoPareja: Boolean, onPareja: (() -> Unit)?,
    /** El nombre de una ficha por su referencia ("categoría:id"), o null si ya no está. */
    nombreDe: (String) -> String?,
    acc: DiarioAcciones
) {
    var vista by rememberSaveable { mutableIntStateOf(0) }
    var abierto by remember { mutableStateOf<Long?>(null) }
    val hoy = today()
    val lista = dias.orEmpty()
    val porDia = remember(lista) { lista.associateBy { it.dia } }
    val racha = remember(lista, hoy) { rachaDiario(porDia.keys, hoy) }
    val soloLectura = viendoPareja
    // (en la tablet en horizontal, una columna cómoda de leer, centrada)
    Column(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 880.dp).statusBarsPadding().padding(horizontal = if (Skin.revista) 18.dp else 14.dp)) {
        BigHeader(if (viendoPareja && pareja != null) "Diario de $pareja" else "Diario", fechaDiario(hoy)) {
            if (acc.aviso != null && !soloLectura) IconPill(Icons.Rounded.NotificationsNone, "Recordarme escribir", onClick = acc.aviso)
            if (onPareja != null) PartnerToggle(viendoPareja, onPareja)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Pestanas(listOf("Días", "Calendario"), vista) { vista = it }
            Spacer(Modifier.weight(1f))
            FiltroDiario(kinds, filtro, onFiltro)
        }
        if (racha >= 2) Text("$racha días seguidos con algo en tu diario", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        if (dias == null) { Spacer(Modifier.weight(1f)); return@Column }
        when (vista) {
            0 -> DiarioDias(lista, porDia[hoy], enCurso, soloLectura, pareja, nombreDe, acc)
            else -> DiarioCalendario(lista, porDia) { d -> if (porDia[d] != null) abierto = d else if (!soloLectura && d <= hoy) acc.escribir(d, null) }
        }
    }
    abierto?.let { d ->
        porDia[d]?.let { dia ->
            DiaHoja(dia, soloLectura, pareja, nombreDe, acc) { abierto = null }
        } ?: run { abierto = null }
    }
}

/** Días · Calendario: versalitas, la elegida en el acento y subrayada (fuera de la revista, chips). */
@Composable
private fun Pestanas(opciones: List<String>, elegida: Int, onElegir: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (Skin.revista) 18.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        opciones.forEachIndexed { i, o ->
            val on = i == elegida
            if (Skin.revista) Text(
                o.uppercase(), color = if (on) ColGeneral else Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.press(haptic = false) { if (!on) onElegir(i) }.padding(vertical = 10.dp)
                    .drawBehind { if (on) drawRect(ColGeneral, topLeft = Offset(0f, size.height - 7.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())) }
            ) else SoftChip(o, on) { if (!on) onElegir(i) }
        }
    }
}

/** Qué se ve: todo o una categoría. */
@Composable
private fun FiltroDiario(kinds: List<Kind>, filtro: Int?, onFiltro: (Int?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val actual = kinds.find { it.id == filtro }?.title ?: "Todo"
    Box {
        Text(actual.uppercase() + " ▾", color = Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1,
            modifier = Modifier.press(haptic = false) { menu = true }.padding(vertical = 10.dp, horizontal = 4.dp))
        DropdownMenu(menu, { menu = false }, containerColor = Paper) {
            DropdownMenuItem({ Text("Todo", fontWeight = if (filtro == null) FontWeight.Bold else FontWeight.Normal) }, { menu = false; onFiltro(null) })
            kinds.forEach { k ->
                DropdownMenuItem({ Text(k.title, fontWeight = if (filtro == k.id) FontWeight.Bold else FontWeight.Normal) }, { menu = false; onFiltro(k.id) })
            }
        }
    }
}

// ---------------------------------------------------------------- Días

@Composable
private fun DiarioDias(
    dias: List<Dia>, deHoy: Dia?, enCurso: List<Pair<Kind, Play>>, soloLectura: Boolean, pareja: String?,
    nombreDe: (String) -> String?, acc: DiarioAcciones
) {
    val hoy = today()
    val pasados = remember(dias, hoy) { dias.filter { it.dia < hoy } }
    val talDia = remember(dias, hoy) { talDiaComoHoy(dias, LocalDate.ofEpochDay(hoy)) }
    val meses = remember(pasados) { pasados.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.dia)) } }
    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = PaddingValues(bottom = dockSpace(24.dp))) {
        item(key = "hoy") { HoyBloque(deHoy, enCurso, soloLectura, pareja, nombreDe, acc) }
        if (talDia.isNotEmpty()) item(key = "taldia") { TalDiaBloque(talDia, acc) }
        if (dias.isEmpty()) item(key = "vacio") { DiarioVacio(soloLectura, pareja) }
        meses.forEach { (ym, ds) ->
            item(key = "m$ym") {
                // (el resumen del mes cuenta también lo de hoy, que va arriba)
                val r = resumenDiario(dias, ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay())
                MesCabecera(ym, r)
            }
            ds.forEach { d -> item(key = "d${d.dia}") { DiaPagina(d, soloLectura, pareja, nombreDe, acc) } }
        }
    }
}

/** Hoy: lo que escribes, lo que llevas y lo que tienes entre manos. */
@Composable
private fun HoyBloque(d: Dia?, enCurso: List<Pair<Kind, Play>>, soloLectura: Boolean, pareja: String?, nombreDe: (String) -> String?, acc: DiarioAcciones) {
    val hoy = today()
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Antetitulo("Hoy", Modifier.padding(bottom = 10.dp))
        d?.notas?.forEach { n -> NotaBloque(n, soloLectura, pareja, nombreDe, acc) }
        if (!soloLectura) Escribir(if (d?.notas.isNullOrEmpty()) "¿Qué tal hoy? Lo que has jugado, leído o visto, lo que te ha gustado…" else "Añadir algo más") { acc.escribir(hoy, null) }
        d?.toques?.forEach { t -> ToqueFila(t, acc) }
        if (d == null || d.toques.isEmpty()) Text(
            if (soloLectura) "Hoy aún no hay nada." else "Lo que termines, empieces, leas o juegues hoy aparecerá aquí solo.",
            color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 8.dp)
        )
        if (!soloLectura && enCurso.isNotEmpty()) {
            Text("APUNTAR SOBRE LO QUE LLEVAS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(enCurso.size) { i ->
                    val (k, p) = enCurso[i]
                    Row(
                        Modifier.press { acc.apuntar(k, p) }.then(if (Skin.revista) Modifier.border(1.dp, Charcoal.copy(alpha = 0.35f)) else Modifier.background(Crudo, shp(12.dp)))
                            .padding(end = 10.dp).widthIn(max = 220.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ListCover(p.cover, p.name, 30.dp, 44.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(p.name, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("+ Apunte", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

/** El hueco para escribir: una línea en cursiva que abre el editor. */
@Composable
private fun Escribir(texto: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().press(haptic = false, onClick = onClick).padding(vertical = 8.dp)
            .drawBehind { drawLine(Charcoal.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(texto, Modifier.weight(1f), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp)
        Icon(Icons.Rounded.EditNote, "Escribir", tint = ColGeneral, modifier = Modifier.padding(start = 8.dp).size(26.dp))
    }
}

/** Sin nada aún: qué es el diario y cómo se llena. */
@Composable
private fun DiarioVacio(soloLectura: Boolean, pareja: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
        Filete(1.dp, Charcoal.copy(alpha = 0.35f))
        Spacer(Modifier.height(16.dp))
        Text(
            if (soloLectura) "${pareja ?: "Tu pareja"} aún no tiene nada en su diario."
            else "Tu diario se escribe casi solo: cada día aparece lo que terminas, empiezas, lees o juegas (también en el PC), tus apuntes, subrayados y capturas. Y lo que tú quieras contar de ese día.",
            fontFamily = Display, fontSize = 18.sp, lineHeight = 26.sp, color = Charcoal
        )
    }
}

/** El mes: su nombre en grande y lo que hubo. */
@Composable
private fun MesCabecera(ym: YearMonth, r: ResumenDiario) {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(ym.month.getDisplayName(EstiloFecha.FULL, ES).replaceFirstChar { it.uppercase() }, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 32.sp, lineHeight = 36.sp, color = Charcoal)
            Text("${ym.year}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        }
        val t = r.texto()
        if (t.isNotBlank()) Text(t, color = Muted, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(8.dp))
        Filete(2.dp)
    }
}

/** Un día anterior: su fecha, lo que escribiste y lo que hiciste. */
@Composable
private fun DiaPagina(d: Dia, soloLectura: Boolean, pareja: String?, nombreDe: (String) -> String?, acc: DiarioAcciones, conFilete: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        if (conFilete) Filete(1.dp, Charcoal.copy(alpha = 0.25f))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text("${LocalDate.ofEpochDay(d.dia).dayOfMonth}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp, color = Charcoal)
                Text(diaSemana(d.dia), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, color = Muted, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
            }
            if (!soloLectura) Box(Modifier.size(40.dp).press { acc.escribir(d.dia, null) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.EditNote, "Escribir este día", tint = Muted, modifier = Modifier.size(22.dp))
            }
        }
        d.notas.forEach { n -> NotaBloque(n, soloLectura, pareja, nombreDe, acc) }
        d.toques.forEach { t -> ToqueFila(t, acc) }
        Spacer(Modifier.height(10.dp))
    }
}

/** Lo que escribiste: en la letra de la revista; tocarlo lo abre para cambiarlo. */
@Composable
private fun NotaBloque(n: Nota, soloLectura: Boolean, pareja: String?, nombreDe: (String) -> String?, acc: DiarioAcciones) {
    Column(Modifier.fillMaxWidth().then(if (soloLectura) Modifier else Modifier.press(haptic = false) { acc.escribir(n.dia, n) }).padding(vertical = 8.dp)) {
        if (n.texto.isNotBlank()) Text(n.texto, fontFamily = Display, fontSize = 18.sp, lineHeight = 26.sp, color = Charcoal)
        if (n.foto.isNotBlank()) {
            val v = Photos.version
            key(v) {
                AsyncImage(n.foto, null, Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = 280.dp).clip(shp(4.dp)), contentScale = ContentScale.FillWidth)
            }
        }
        val refs = n.sobre.mapNotNull { r -> nombreDe(r)?.let { r to it } }
        if (refs.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            refs.forEach { (r, nm) ->
                Text("$nm →", color = ColGeneral, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { acc.abrirRef(r) }.padding(vertical = 4.dp))
            }
        }
        if (n.comparte && pareja != null && !soloLectura) Text("Lo ve $pareja", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Lo que hiciste con una ficha ese día: su portada, qué pasó y lo que apuntaste, subrayaste o capturaste. */
@Composable
private fun ToqueFila(t: Toque, acc: DiarioAcciones) {
    Row(Modifier.fillMaxWidth().then(if (t.id != 0L) Modifier.press(haptic = false) { acc.abrir(t) } else Modifier).padding(vertical = 8.dp)) {
        ListCover(t.portada, t.nombre, 40.dp, 58.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.nombre, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 21.sp, color = Charcoal, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val r = t.resumen()
            if (r.isNotBlank()) Text(r, color = if (t.terminado) ColGeneral else Muted, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = if (t.terminado) FontWeight.Bold else FontWeight.Normal)
            if (t.terminado && t.opinion.isNotBlank()) Text(extracto(t.opinion, 150), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, color = Charcoal.copy(alpha = 0.85f), modifier = Modifier.padding(top = 3.dp))
            t.valoraciones.sortedBy { it.time }.forEach { e ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                    Text("${e.general}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Charcoal, modifier = Modifier.padding(end = 8.dp))
                    if (e.note.isNotBlank()) Text(e.note, fontFamily = Display, fontSize = 15.sp, lineHeight = 20.sp, color = Charcoal, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            t.apuntes.sortedBy { it.t }.forEach { m ->
                Column(Modifier.padding(top = 4.dp)) {
                    if (m.x.isNotBlank()) Text(m.x, fontFamily = Display, fontSize = 15.sp, lineHeight = 20.sp, color = Charcoal, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Suite.shotById(m.sh)?.takeIf { it.local }?.let { s ->
                        AsyncImage(s.uri, null, Modifier.padding(top = 4.dp).size(160.dp, 90.dp).clip(shp(3.dp)).clickable { acc.captura(s) }, contentScale = ContentScale.Crop)
                    }
                }
            }
            t.citas.sortedBy { it.created }.take(3).forEach { n ->
                Text("«${extracto(n.text, 180)}»", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp, color = Charcoal,
                    modifier = Modifier.padding(top = 4.dp).clickable { acc.cita(t, n) })
                if (n.note.isNotBlank()) Text(n.note, color = ColGeneral, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (t.citas.size > 3) Text("y ${t.citas.size - 3} más", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            val shots = t.capturas.filter { it.local }.sortedBy { it.time }
            if (shots.isNotEmpty()) LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shots.size) { i ->
                    AsyncImage(shots[i].uri, null, Modifier.size(128.dp, 72.dp).clip(shp(3.dp)).clickable { acc.captura(shots[i]) }, contentScale = ContentScale.Crop)
                }
            }
        }
        if (t.terminado && t.nota != null) Text("${t.nota}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = ColorCifras, modifier = Modifier.padding(start = 10.dp))
    }
}

/** El mismo día de otros años. */
@Composable
private fun TalDiaBloque(l: List<Pair<Int, Dia>>, acc: DiarioAcciones) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Filete(1.dp, Charcoal.copy(alpha = 0.35f))
        Antetitulo("Tal día como hoy", Modifier.padding(top = 14.dp, bottom = 6.dp))
        l.take(3).forEach { (anos, d) ->
            Text(if (anos == 1) "HACE UN AÑO" else "HACE $anos AÑOS", color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, modifier = Modifier.padding(top = 8.dp))
            d.notas.firstOrNull()?.takeIf { it.texto.isNotBlank() }?.let { n ->
                Text(extracto(n.texto, 200), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, color = Charcoal, modifier = Modifier.padding(top = 2.dp))
            }
            d.toques.take(3).forEach { t ->
                Row(Modifier.fillMaxWidth().then(if (t.id != 0L) Modifier.clickable { acc.abrir(t) } else Modifier).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.nombre, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Charcoal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    val r = t.resumen()
                    if (r.isNotBlank()) Text(" · " + r.replaceFirstChar { it.lowercase() }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** Un día abierto desde el calendario. */
@Composable
private fun DiaHoja(d: Dia, soloLectura: Boolean, pareja: String?, nombreDe: (String) -> String?, acc: DiarioAcciones, onCerrar: () -> Unit) {
    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).widthIn(max = 640.dp).heightIn(max = 640.dp).clip(shp(if (Skin.revista) 2.dp else 24.dp)).background(Paper)
                .then(if (Skin.revista) Modifier.border(1.dp, Charcoal) else Modifier)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fechaDiario(d.dia), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, color = Charcoal, modifier = Modifier.weight(1f))
                Text("CERRAR", color = Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press { onCerrar() }.padding(8.dp))
            }
            Filete(2.dp)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                val ac = DiarioAcciones(
                    abrir = { onCerrar(); acc.abrir(it) }, cita = { t, n -> onCerrar(); acc.cita(t, n) }, captura = { onCerrar(); acc.captura(it) },
                    escribir = { dia, n -> onCerrar(); acc.escribir(dia, n) }, apuntar = acc.apuntar, abrirRef = { onCerrar(); acc.abrirRef(it) }
                )
                d.notas.forEach { n -> NotaBloque(n, soloLectura, pareja, nombreDe, ac) }
                d.toques.forEach { t -> ToqueFila(t, ac) }
                if (!soloLectura) Escribir(if (d.notas.isEmpty()) "Escribir sobre este día" else "Añadir algo más") { onCerrar(); acc.escribir(d.dia, null) }
            }
        }
    }
}

// ---------------------------------------------------------------- Calendario

@Composable
private fun DiarioCalendario(dias: List<Dia>, porDia: Map<Long, Dia>, onDia: (Long) -> Unit) {
    var ym by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<YearMonth, String>({ it.toString() }, { YearMonth.parse(it) })) { mutableStateOf(YearMonth.now()) }
    val hoy = today()
    val nombre = ym.month.getDisplayName(EstiloFecha.FULL, ES).replaceFirstChar { it.uppercase() }
    val largo = ym.lengthOfMonth()
    val antes = ym.atDay(1).dayOfWeek.value - 1
    val semanas = (antes + largo + 6) / 7
    val d0 = ym.atDay(1).toEpochDay(); val d1 = ym.atEndOfMonth().toEpochDay()
    val resumen = remember(dias, ym) { resumenDiario(dias, d0, d1) }
    // Lo más que se hizo un día del mes, para el tono de cada día
    val tope = remember(dias, ym) { (d0..d1).maxOfOrNull { d -> porDia[d]?.let { it.leidoMs + it.jugadoMs } ?: 0L }?.coerceAtLeast(30 * 60_000L) ?: 1L }
    var arrastre by remember { mutableFloatStateOf(0f) }
    val umbral = with(LocalDensity.current) { 70.dp.toPx() }
    Column(Modifier.fillMaxSize().padding(bottom = dockSpace(8.dp)).pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { arrastre = 0f },
            onDragEnd = { if (arrastre < -umbral) ym = ym.plusMonths(1) else if (arrastre > umbral) ym = ym.minusMonths(1); arrastre = 0f },
            onDragCancel = { arrastre = 0f }
        ) { _, dx -> arrastre += dx }
    }) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { ym = YearMonth.now() }, verticalAlignment = Alignment.Bottom) {
                Text(nombre, fontSize = 30.sp, fontFamily = Display, fontStyle = FontStyle.Italic, color = Charcoal)
                Text("${ym.year}", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
            }
            Flecha(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mes anterior") { ym = ym.minusMonths(1) }
            Spacer(Modifier.width(6.dp))
            Flecha(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Mes siguiente") { ym = ym.plusMonths(1) }
        }
        Text(resumen.texto().ifBlank { "Nada este mes" } + if (resumen.dias > 0) " · ${resumen.dias} día${if (resumen.dias == 1) "" else "s"} con algo" else "",
            color = Muted, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, letterSpacing = 1.2.sp, color = Muted, fontWeight = FontWeight.Medium) }
        }
        Column(Modifier.fillMaxWidth().weight(1f)) {
            for (w in 0 until semanas) Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until 7) {
                    val n = w * 7 + c - antes + 1
                    val mod = Modifier.weight(1f).fillMaxHeight()
                    if (n < 1 || n > largo) Box(mod) else {
                        val ep = ym.atDay(n).toEpochDay()
                        CeldaDia(n, porDia[ep], ep == hoy, ep > hoy, tope, mod.clickable(enabled = ep <= hoy || porDia[ep] != null) { onDia(ep) })
                    }
                }
            }
        }
        Text("Toca un día para verlo o escribir en él · el tono, lo que leíste y jugaste", color = Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Flecha(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).press(onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, desc, tint = Charcoal, modifier = Modifier.size(22.dp)) }
}

/** Un día del calendario: el número; la portada de lo que terminaste (o el principio de lo que escribiste); el tono, lo que leíste y jugaste. */
@Composable
private fun CeldaDia(n: Int, d: Dia?, esHoy: Boolean, futuro: Boolean, tope: Long, modifier: Modifier) {
    val min = d?.let { it.leidoMs + it.jugadoMs } ?: 0L
    val tono = if (min >= 60_000L) (0.06f + 0.26f * (min.toFloat() / tope).coerceIn(0f, 1f)) else 0f
    Column(
        modifier.padding(horizontal = 1.5.dp, vertical = 1.5.dp)
            .background(if (tono > 0f) ColGeneral.copy(alpha = tono) else Color.Transparent)
            .drawBehind { drawLine(if (esHoy) ColGeneral else Charcoal.copy(alpha = 0.3f), Offset(0f, 0f), Offset(size.width, 0f), if (esHoy) 3.dp.toPx() else 1.dp.toPx()) }
            .padding(start = 3.dp, end = 3.dp, top = 4.dp, bottom = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$n", color = when { esHoy -> ColGeneral; futuro || d == null -> Muted; else -> Charcoal }, fontFamily = Display,
                fontWeight = if (esHoy || d != null) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
            if (d?.notas?.isNotEmpty() == true) Text(" ✎", color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        if (d != null) {
            val fin = d.terminados.firstOrNull { it.portada.isNotBlank() }
            when {
                fin != null -> Box(Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp).clip(shp(3.dp))) {
                    AsyncImage(fin.portada, fin.nombre, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = Ediciones.filtro)
                    if (d.terminados.size > 1) Text("+${d.terminados.size - 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomEnd).background(ColGeneral).padding(horizontal = 3.dp))
                }
                // (sin partir palabras: si una no cabe, la letra baja un poco)
                d.terminados.isNotEmpty() -> FitTitle(d.terminados[0].nombre, 9.sp, 10.sp, color = Charcoal, maxLines = 3, overflow = TextOverflow.Ellipsis, minSize = 6f, conEdicion = false)
                d.notas.isNotEmpty() && d.notas[0].texto.isNotBlank() -> Text(d.notas[0].texto, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 9.sp, lineHeight = 10.5.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, color = Charcoal)
                else -> d.toques.firstOrNull()?.let { FitTitle(it.nombre, 8.5.sp, 10.sp, color = Charcoal.copy(alpha = 0.8f), fontWeight = FontWeight.Normal, maxLines = 3, overflow = TextOverflow.Ellipsis, minSize = 6f, family = androidx.compose.ui.text.font.FontFamily.Default, conEdicion = false) }
            }
        }
    }
}

// ---------------------------------------------------------------- Escribir

/** Una ficha que se puede mencionar en lo que escribes. */
class RefFicha(val ref: String, val kind: Kind, val nombre: String, val portada: String)

/**
 * Escribir en el diario: a pantalla completa, con la fecha (se cambia tocándola), de qué fichas hablas, una foto y,
 * si tienes pareja, si quieres que lo vea. Al salir se guarda (sin texto ni foto, no se guarda nada o se borra).
 */
@Composable
fun EscribirDiario(
    dia0: Long, nota: Nota?, sobre0: List<String>,
    /** Las fichas que se proponen primero (lo de ese día y lo que llevas entre manos). */
    propuestas: List<RefFicha>,
    /** Todas, para buscar otra. */
    todas: List<RefFicha>,
    pareja: String?,
    elegirFoto: ((String) -> Unit) -> Unit,
    onGuardar: (Nota) -> Unit, onBorrar: (Nota) -> Unit, onCerrar: () -> Unit
) {
    var texto by remember { mutableStateOf(nota?.texto ?: "") }
    var dia by remember { mutableLongStateOf(nota?.dia ?: dia0) }
    var sobre by remember { mutableStateOf(nota?.sobre ?: sobre0) }
    var foto by remember { mutableStateOf(nota?.foto ?: "") }
    var comparte by remember { mutableStateOf(nota?.comparte ?: false) }
    var fecha by remember { mutableStateOf(false) }
    var buscando by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    fun cerrar() {
        val hay = texto.isNotBlank() || foto.isNotBlank()
        val ahora = System.currentTimeMillis()
        when {
            hay -> {
                val n = (nota ?: Nota(ahora, dia, "")).copy(dia = dia, texto = texto.trim(), sobre = sobre, foto = foto, comparte = comparte && pareja != null, updated = ahora)
                if (n != nota?.copy(updated = ahora)) onGuardar(n)
            }
            nota != null -> onBorrar(nota)
        }
        onCerrar()
    }
    Dialog(::cerrar, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val foco = remember { FocusRequester() }
        Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { fecha = true }) {
                    Text(if (dia == today()) "HOY" else "OTRO DÍA", color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text(fechaDiario(dia) + " ▾", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, color = Charcoal)
                }
                Text("LISTO", color = ColGeneral, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press { cerrar() }.padding(10.dp))
            }
            Filete(3.dp)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                BasicTextField(
                    texto, { texto = it },
                    textStyle = TextStyle(color = Charcoal, fontSize = 19.sp, lineHeight = 28.sp, fontFamily = Display),
                    cursorBrush = SolidColor(ColGeneral),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(vertical = 14.dp).focusRequester(foco),
                    decorationBox = { inner ->
                        Box {
                            if (texto.isEmpty()) Text("¿Qué tal el día? Lo que has jugado, leído o visto, lo que te ha gustado, lo que no…", color = Muted, fontSize = 19.sp, lineHeight = 28.sp, fontFamily = Display, fontStyle = FontStyle.Italic)
                            inner()
                        }
                    }
                )
                LaunchedEffect(Unit) { if (nota == null) { kotlinx.coroutines.delay(200); runCatching { foco.requestFocus() } } }
                Filete(1.dp, Line)
                // De qué hablas
                Text("SOBRE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                val elegidas = sobre.mapNotNull { r -> (propuestas + todas).find { it.ref == r } }
                val opciones = (elegidas + propuestas.filter { p -> p.ref !in sobre }).distinctBy { it.ref }.take(14)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    opciones.forEach { o ->
                        val on = o.ref in sobre
                        Text(o.nombre, color = if (on) Paper else Charcoal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp).press(haptic = false) { sobre = if (on) sobre - o.ref else sobre + o.ref }
                                .background(if (on) Charcoal else Color.Transparent, shp(if (Skin.revista) 0.dp else 14.dp))
                                .border(1.dp, if (on) Charcoal else Charcoal.copy(alpha = 0.35f), shp(if (Skin.revista) 0.dp else 14.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Text(if (buscando) "Cerrar" else "Otra…", color = ColGeneral, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.press(haptic = false) { buscando = !buscando; q = "" }.padding(horizontal = 6.dp, vertical = 6.dp))
                }
                if (buscando) {
                    BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Charcoal, fontSize = 17.sp, fontFamily = Display), cursorBrush = SolidColor(ColGeneral),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).drawBehind { drawLine(Charcoal, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx()) }.padding(vertical = 8.dp),
                        decorationBox = { inner -> Box { if (q.isEmpty()) Text("Busca un juego, libro, peli…", color = Muted, fontSize = 17.sp, fontFamily = Display, fontStyle = FontStyle.Italic); inner() } })
                    val res = remember(q, todas) { if (q.isBlank()) emptyList() else todas.filter { libMatches(q, it.nombre, "") && it.ref !in sobre }.take(6) }
                    res.forEach { r ->
                        Row(Modifier.fillMaxWidth().clickable { sobre = sobre + r.ref; buscando = false; q = "" }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ListCover(r.portada, r.nombre, 26.dp, 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(r.nombre, fontFamily = Display, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(r.kind.title, color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                // Una foto
                Text("FOTO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                if (foto.isNotBlank()) {
                    AsyncImage(foto, null, Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(shp(4.dp)), contentScale = ContentScale.FillWidth)
                    Text("QUITAR LA FOTO", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.clickable { foto = "" }.padding(vertical = 10.dp))
                } else Text("Añadir una foto de la galería →", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Charcoal,
                    modifier = Modifier.clickable { elegirFoto { foto = it } }.padding(vertical = 4.dp))
                if (pareja != null) {
                    Text("QUIÉN LO VE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 18.dp))
                    MagChoice(listOf("Solo yo", "También $pareja"), if (comparte) 1 else 0) { comparte = it == 1 }
                }
                if (nota != null) Text("BORRAR ESTA ENTRADA", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 22.dp).clickable { onBorrar(nota); onCerrar() }.padding(vertical = 8.dp))
                Spacer(Modifier.height(30.dp))
            }
        }
        if (fecha) {
            val st = rememberDatePickerState(initialSelectedDateMillis = dia * 86_400_000L)
            DatePickerDialog(
                onDismissRequest = { fecha = false },
                confirmButton = { TextButton({ st.selectedDateMillis?.let { dia = minOf(it / 86_400_000L, today()) }; fecha = false }) { Text("Aceptar") } },
                dismissButton = { TextButton({ fecha = false }) { Text("Cancelar") } }
            ) { DatePicker(state = st) }
        }
    }
}

/** El recordatorio para escribir: a qué hora (si ese día aún no has escrito nada). */
@Composable
fun AvisoDiarioDialogo(hora: Int, onElegir: (Int) -> Unit, onCerrar: () -> Unit) {
    val horas = listOf(-1, 20, 21, 22, 23)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Recordarme escribir", fontFamily = Display) },
        text = {
            Column {
                Text("Si un día aún no has escrito nada, un aviso a esa hora con lo que has hecho.", color = Muted, fontSize = 14.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(8.dp))
                MagChoice(horas.map { if (it < 0) "Nunca" else "$it:00" }, horas.indexOf(hora).coerceAtLeast(0)) { onElegir(horas[it]) }
            }
        },
        confirmButton = { TextButton(onCerrar) { Text("Cerrar") } },
        containerColor = Paper, shape = shp(if (Skin.revista) 4.dp else 24.dp)
    )
}

/** La casilla «En tu diario» de una ficha: lo que has escrito de ella (lo más nuevo primero); tocarlo lo abre. */
@Composable
fun DiarioDeFicha(kind: Kind, id: Long) {
    val ns = DiarioHub.de(kind.id, id)
    ns.take(4).forEach { n ->
        Column(Modifier.fillMaxWidth().clickable { DiarioHub.editar?.invoke(n) }.padding(vertical = 6.dp)) {
            Text(fechaDiario(n.dia).uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            if (n.texto.isNotBlank()) Text(extracto(n.texto, 320), fontFamily = Display, fontSize = 17.sp, lineHeight = 25.sp, color = Charcoal)
        }
    }
    if (ns.size > 4) Text("y ${ns.size - 4} más en tu diario", color = Muted, fontSize = 12.sp)
}
