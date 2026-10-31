package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Tú, al escribir

/** «Terminado» y «Compartir con …» de un capítulo o una parte (ver EscribirPareja.kt). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarcasCompartir(e: Escritos, d: Doc) {
    val ctx = LocalContext.current
    val hecho = d.b("hecho")
    val comp = d.b("comp")
    val parte = if (d.t == Escritos.T_CAP) e.get(d.s("aid")) else null
    val nombre = remember { Pareja.nombre(ctx) }
    fun cambia(k: String, on: Boolean) { e.edita(d.id) { if (on) put(k, 1) else remove(k) } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftChip(if (hecho) "✓ Terminado" else "Marcar como terminado", hecho, Modifier.semantics { contentDescription = if (hecho) "Terminado (tocar para quitarlo)" else "Marcar como terminado" }) { cambia("hecho", !hecho) }
            if (Pareja.hay(ctx)) SoftChip(if (comp) "Compartido con $nombre" else "Compartir con $nombre", comp) { cambia("comp", !comp) }
        }
        when {
            parte?.b("comp") == true && !comp -> Text("Compartido con $nombre porque lo está la parte.", color = Muted, fontSize = 12.sp)
            parte?.b("hecho") == true && !hecho -> Text("Terminado porque lo está la parte.", color = Muted, fontSize = 12.sp)
            d.t == Escritos.T_PARTE && comp -> Text("$nombre puede leer todos los capítulos de esta parte (también los que añadas) y anotarlos.", color = Muted, fontSize = 12.sp)
            comp -> Text("$nombre puede leerlo en su Atrio y anotarlo.", color = Muted, fontSize = 12.sp)
            !Pareja.hay(ctx) -> Text("Para compartirlo, enlaza tu cuenta con la de tu pareja (Ajustes → Cuenta y pareja).", color = Muted, fontSize = 12.sp)
        }
    }
}

/** Lo que te ha anotado tu pareja en este capítulo (o en todos, con cid vacío). */
@Composable
fun NotasDeTuPareja(e: Escritos, cid: String, abrirEscena: (String) -> Unit) {
    val ctx = LocalContext.current
    var notas by remember { mutableStateOf<List<NotaLectura>>(emptyList()) }
    LaunchedEffect(cid, e.version) { notas = Pareja.notasDeMiPareja(ctx).filter { cid.isEmpty() || it.cid == cid } }
    if (notas.isEmpty()) return
    val nombre = remember { Pareja.nombre(ctx) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("LO QUE TE ANOTA ${nombre.uppercase()} · ${notas.size}", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        notas.forEach { n ->
            Column(Modifier.fillMaxWidth().border(1.dp, Line).press(haptic = false) { if (e.get(n.eid) != null) abrirEscena(n.eid) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (n.cita.isNotBlank()) Text("«${n.cita}»", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(n.texto, fontSize = 15.sp, lineHeight = 21.sp, color = Charcoal)
                Text(e.get(n.eid)?.s("titulo")?.ifBlank { null }?.let { "$it · ${haceE(n.u)}" } ?: haceE(n.u), color = Muted, fontSize = 11.sp)
            }
        }
    }
}

/** En tus obras: lo que te comparte tu pareja (si te comparte algo). */
@Composable
fun TeComparte(onAbrir: () -> Unit) {
    val ctx = LocalContext.current
    if (!Pareja.hay(ctx)) return
    val pe = remember { Pareja.escritos(ctx) }
    LaunchedEffect(Unit) { Pareja.bajaLoCompartido(ctx) }
    val caps = remember(pe.version) { pe.deTipo(Escritos.T_CAP) }
    if (caps.isEmpty()) return
    val nombre = remember { Pareja.nombre(ctx) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).border(1.5.dp, Charcoal).press { onAbrir() }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("TE COMPARTE ${nombre.uppercase()}", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        FitTitle(pe.obras().joinToString(" · ") { it.s("titulo").ifBlank { "Sin título" } }, 24.sp, 28.sp, color = Charcoal)
        Text("${caps.size} ${if (caps.size == 1) "capítulo" else "capítulos"} para leer y anotar →", color = Muted, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- Tu pareja, al leer

/** Lo que te comparte tu pareja: sus obras y capítulos; al abrir uno, se lee y se anota. */
@Composable
fun LecturaPareja(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pe = remember { Pareja.escritos(ctx) }
    val nombre = remember { Pareja.nombre(ctx) }
    var notas by remember { mutableStateOf<List<NotaLectura>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var abierto by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { Pareja.bajaLoCompartido(ctx); notas = Pareja.misNotas(ctx); cargando = false }
    androidx.activity.compose.BackHandler { if (abierto != null) abierto = null else onBack() }
    val v = pe.version
    abierto?.let { cid ->
        pe.get(cid)?.let { c -> LeerYAnotar(pe, c, notas, onNotas = { notas = it }, onCerrar = { abierto = null }); return }
    }
    Column(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 880.dp).statusBarsPadding().padding(horizontal = if (Skin.revista) 18.dp else 14.dp)) {
        TopBar("Escribir", onBack, "De $nombre") {
            EnlaceRevista("Actualizar") { scope.launch { cargando = true; Pareja.bajaLoCompartido(ctx); notas = Pareja.misNotas(ctx); cargando = false } }
        }
        val obras = remember(v) { pe.obras() }
        if (obras.isEmpty()) Text(if (cargando) "Cargando…" else "$nombre aún no te ha compartido nada.", color = Muted, fontSize = 15.sp, modifier = Modifier.padding(vertical = 20.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(24.dp)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            obras.forEach { o ->
                item("o" + o.id) {
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FitTitle(o.s("titulo").ifBlank { "Sin título" }, 30.sp, 33.sp, color = Charcoal)
                        if (o.s("sinopsis").isNotBlank()) Text(o.s("sinopsis"), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, color = Muted)
                    }
                }
                val caps = pe.capitulos(o.id)
                items(caps, key = { it.id }) { c ->
                    val mias = notas.count { it.cid == c.id }
                    val parte = pe.get(c.s("aid"))
                    CuadroEscribir(colorEscrito(c.s("color"), Charcoal),
                        listOfNotNull(parte?.s("titulo")?.ifBlank { null }, "Capítulo ${pe.capitulos(o.id).indexOf(c) + 1}", if (Compartir.hecho(pe, c)) "Terminado" else "En marcha").joinToString(" · "),
                        c.s("titulo").ifBlank { "Sin título" }, c.s("resumen").ifBlank { pe.escenas(c.id).firstOrNull()?.s("texto")?.let { Compartir.parrafos(it).firstOrNull() }.orEmpty() },
                        "${cifraE(pe.escenas(c.id).sumOf { Escritos.palabras(it.s("texto")) })} palabras" + if (mias > 0) " · $mias ${if (mias == 1) "nota tuya" else "notas tuyas"}" else "",
                        onClick = { abierto = c.id })
                }
            }
        }
    }
}

/** Leer un capítulo que te comparten y anotar sus párrafos (tocar un párrafo para anotarlo). */
@Composable
private fun LeerYAnotar(pe: Escritos, c: Doc, notas: List<NotaLectura>, onNotas: (List<NotaLectura>) -> Unit, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editando by remember { mutableStateOf<Triple<Doc, Int, NotaLectura?>?>(null) }
    var error by remember { mutableStateOf("") }
    val esc = pe.escenas(c.id)
    HojaEscribir(c.s("titulo").ifBlank { "Sin título" }, onCerrar) {
        Text("Toca un párrafo para anotarlo. Tus notas las ve ${Pareja.nombre(ctx)} en su Atrio.", color = Muted, fontSize = 13.sp)
        if (error.isNotEmpty()) Text(error, color = ColGeneral, fontSize = 13.sp)
        if (esc.all { it.s("texto").isBlank() }) Text("Todavía no hay nada escrito.", color = Muted, fontSize = 14.sp)
        esc.forEachIndexed { i, s ->
            if (i > 0) Text("* * *", color = Muted, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().wrapContentWidth().padding(vertical = 8.dp))
            if (s.s("titulo").isNotBlank()) Text(s.s("titulo").uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
            Compartir.parrafos(s.s("texto")).forEachIndexed { p, l ->
                val aqui = notas.filter { it.eid == s.id && (it.p == p || (it.p < 0 && Compartir.cita(l).startsWith(it.cita.take(40)))) }
                Column(Modifier.fillMaxWidth().press(haptic = false) { editando = Triple(s, p, null) }) {
                    ParrafosEscribir(l, tam = 18)
                }
                aqui.forEach { n ->
                    Column(Modifier.fillMaxWidth().padding(start = 12.dp).background(ColGeneral.copy(alpha = 0.08f))
                        .press(haptic = false) { editando = Triple(s, p, n) }.padding(10.dp)) {
                        Text("TU NOTA", color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                        Text(n.texto, fontSize = 15.sp, lineHeight = 21.sp, color = Charcoal)
                    }
                }
            }
        }
    }
    editando?.let { (s, p, n) ->
        val parrafo = Compartir.parrafos(s.s("texto")).getOrNull(p).orEmpty()
        var texto by remember(n?.id, s.id, p) { mutableStateOf(n?.texto.orEmpty()) }
        HojaEscribir(if (n == null) "Anotar" else "Tu nota", { editando = null }) {
            Text("«${Compartir.cita(parrafo)}»", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, color = Muted)
            CampoEscribir(texto, { texto = it }, "Tu nota", lineas = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                BotonEscribir("Guardar") {
                    if (texto.isBlank()) return@BotonEscribir
                    val nueva = NotaLectura(n?.id ?: Pareja.nuevoId(), c.id, s.id, p, Compartir.cita(parrafo), texto.trim(), System.currentTimeMillis())
                    scope.launch {
                        if (Pareja.anota(ctx, nueva)) { onNotas(notas.filter { it.id != nueva.id } + nueva); editando = null; error = "" }
                        else { error = "No he podido guardar la nota (¿sin conexión?). Vuelve a intentarlo."; editando = null }
                    }
                }
                if (n != null) BotonEscribir("Borrar", peligro = true, suave = true) {
                    scope.launch { if (Pareja.borraNota(ctx, n.id)) { onNotas(notas.filter { it.id != n.id }); editando = null } else { error = "No he podido borrar la nota."; editando = null } }
                }
            }
        }
    }
}
