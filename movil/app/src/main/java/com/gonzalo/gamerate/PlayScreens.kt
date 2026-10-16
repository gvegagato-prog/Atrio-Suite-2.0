@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun Stepper(value: Int, suffix: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SoftChip("−", false, onPanel = true, onClick = onMinus)
        Text("$value$suffix", fontFamily = Display, fontSize = 20.sp)
        SoftChip("+", false, onPanel = true, onClick = onPlus)
    }
}

@Composable
fun PlayDetailScreen(
    kind: Kind, partner: PartnerSide?, p0: Play, startEditing: Boolean = false, sessId: Long, sessHours: Int, sessStart: Long,
    myReact: String?, onReact: (String?) -> Unit,
    onChange: (Play) -> Unit, onNote: (Play, Entry?) -> Unit,
    onStart: (Play, Int) -> Unit, onStop: () -> Unit,
    onArt: (Play, Boolean) -> Unit, onFinish: (Play) -> Unit,
    onRecommend: ((Play, String) -> Unit)?,
    onDelete: (Play) -> Unit,
    findTtb: (suspend (String) -> TtbResult)? = null,
    /** Series: episodios emitidos (id y nombre de la ficha). */
    findEpisodes: (suspend (Long, String) -> List<String>)? = null,
    // La suite: partes jugadas, apuntes rápidos y capturas
    onRateSeg: (Play, Seg) -> Unit = { _, _ -> },
    onAddSeg: (Play) -> Unit = {},
    onMemo: (Play) -> Unit = {},
    onDeleteMemo: (Play, Memo) -> Unit = { _, _ -> },
    onOpenShot: (Shot) -> Unit = {},
    onPartDone: (Play, String) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    var p by remember { mutableStateOf(p0) }
    // Lo que llega de otro dispositivo (Atrio añade partes y apuntes) se recoge sin perder lo que estás tocando
    LaunchedEffect(p0.segs, p0.memos, p0.entries) { p = p.copy(segs = p0.segs, memos = p0.memos, entries = p0.entries) }
    // Si la sesión termina, los minutos los añade la app: hay que recoger el valor nuevo.
    LaunchedEffect(p0.minutes) { if (p0.minutes != p.minutes) p = p.copy(minutes = p0.minutes) }
    // El director o los creadores llegan de internet poco después de añadirla: se recogen aquí.
    LaunchedEffect(p0.creator) { if (p.creator.isBlank() && p0.creator.isNotBlank()) p = p.copy(creator = p0.creator) }
    fun upd(n: Play) { p = n; onChange(n) }
    val b = p.baremo()
    val due = kind.isBook && p.status == 1 && p.pending() >= p.every
    var viewP by remember { mutableStateOf(false) }
    val partnerName = partner?.name ?: ""
    var recommend by remember { mutableStateOf(false) }
    // Se abre en modo lectura (como los valorados): los datos se cambian con «Editar»
    var editing by remember(p0.id, startEditing) { mutableStateOf(startEditing) }
    // Revista: el diseño de la página (qué va en cada casilla)
    var designing by remember(p0.id) { mutableStateOf(false) }
    LaunchedEffect(viewP) { Mode.pageP = viewP }
    DisposableEffect(Unit) { onDispose { Mode.pageP = false } }
    Crossfade(viewP && partner != null, animationSpec = tween(450), label = "pareja") { pv ->
    val ps = if (pv) partner else null
    if (ps != null) {
        PartnerPage(ps, p.id, kind, p.name, p.cover, p.hero, { withReveal { viewP = false } }, { withReveal { viewP = false } }, myReact, onReact)
    } else {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        if (Skin.revista && !editing) {
            // ---------------- Revista: la página en casillas, sin imágenes ----------------
            val ctx = androidx.compose.ui.platform.LocalContext.current
            RevistaTopBar(onBack) {
                if (partner != null) Text("PAREJA", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.clickable { withReveal { viewP = true } }.padding(8.dp))
                Text(if (designing) "LISTO" else "EDITAR", color = if (designing) ColGeneral else Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                    modifier = Modifier.clickable { designing = !designing }.padding(8.dp))
            }
            Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(3.dp).background(Charcoal))
            Spacer(Modifier.height(14.dp))
            RevistaHeadline(p.name, listOfNotNull(kind.statuses.getOrNull(p.status), kind.title, p.creator.ifBlank { null }).joinToString(" · "))
            val deck = listOfNotNull(
                p.platform.ifBlank { null },
                if (p.start > 0L) "desde el ${fmtDay(p.start)}" else null,
                if (!kind.isBook && p.minutes > 0) hoursText(p.minutes) else null
            ).joinToString(" · ")
            if (deck.isNotBlank()) Text(deck, color = Muted, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            partner?.reactsToMe?.get(p.id)?.let { Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) { TheirReaction(partnerName, it) } }
            Spacer(Modifier.height(14.dp))
            if (designing) DesignHint(onData = { designing = false; editing = true }, name = p.name, onDelete = { onDelete(p) })

            val defs = linkedMapOf<String, MagDef>()
            if (kind.isGame) defs["guias"] = MagDef("Guías") { GuidesSection(p.id, p.name, inCell = true) }
            if (kind.isMovie || kind.isTv) defs["critica"] = MagDef("Crítica") { MagCritics(kind, p.id, p.name) }
            if (kind.isBook) defs["lectura"] = MagDef("Lectura") { BookFileBar(p.id, p.name) }
            if (kind.isBook) defs["subrayados"] = MagDef("Subrayados y notas") { BookNotesBox(p.id, p.name) }
            if (kind.isBook) defs["capitulos"] = MagDef("Capítulos") {
                if (p.status == 1) {
                    Text(if (p.chapter > 0) "${p.chapter}" else "—", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 56.sp)
                    Text("CAPÍTULO ACTUAL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("− 1", fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.clickable { upd(p.copy(chapter = maxOf(0, p.chapter - 1))) }.padding(6.dp))
                        Text("+ 1", color = ColGeneral, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.clickable { upd(p.copy(chapter = p.chapter + 1)) }.padding(6.dp))
                    }
                    Text(if (due) "Llevas ${p.pending()} capítulos sin apuntar." else "Apuntas cada ${p.every} cap.", color = if (due) ColGeneral else Muted, fontSize = 13.sp)
                } else Text("Márcalo como «${kind.statuses[1]}» para llevar la cuenta.", color = Muted, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 15.sp)
            }
            defs["estado"] = MagDef(if (b != null) "Nota" else "Estado") {
                if (b != null) MagScore(b.general, b.scores(), kind)
                else FitTitle(kind.statuses.getOrElse(p.status) { "" }, 30.sp, 32.sp)
                if (b == null) Text("Aún sin nota", color = Muted, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                MagLink("Apuntar notas", if (b != null) "media de ${p.entries.size} apuntes" else null, accent = due) { onNote(p, null) }
                MagLink(if (b != null) "Pasar a valorados" else "Terminado: valorar") { onFinish(p) }
            }
            if (kind.isGame) defs["sesion"] = MagDef("Sesión") {
                if (p.status != 1) Text("Márcalo como «${kind.statuses[1]}» para empezar una sesión.", color = Muted, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 15.sp)
                else if (sessId == p.id) {
                    var now by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
                    val mins = ((now - sessStart) / 60_000L).toInt().coerceAtLeast(0)
                    Text(hoursText(mins), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                    Text("EN MARCHA · AVISO CADA $sessHours H", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    MagLink("Terminar sesión", accent = true) { onStop() }
                } else {
                    Text("Empezar con aviso cada:", color = Muted, fontSize = 13.sp)
                    MagLink("1 hora") { onStart(p, 1) }
                    MagLink("2 horas") { onStart(p, 2) }
                    MagLink("3 horas") { onStart(p, 3) }
                }
            }
            val hasPc = remember(p.id, p.name, Suite.libs) { Guides.pcGameFor(p.id, p.name) != null }
            if (kind.isGame && hasPc) defs["jugar"] = MagDef("Jugar") { FichaPlayButtons(p.id, p.name) }
            if (!kind.isBook) defs["progreso"] = MagDef(null) {
                val fe = findEpisodes
                if (kind.isGame) ProgressPanel(p, kind, readOnly = true, find = findTtb, onPartDone = { onPartDone(p, it) }) { upd(it) }
                else ProgressPanel(p, kind, readOnly = true, episodes = if (fe != null) ({ fe(p.id, p.name) }) else null, onPartDone = { onPartDone(p, it) }) { upd(it) }
            }
            defs["partes"] = MagDef(null) {
                PlaySuitePanel(
                    p, kind,
                    onRate = { onRateSeg(p, it) }, onOpenEntry = { onNote(p, it) }, onAddSeg = { onAddSeg(p) },
                    onMemo = { onMemo(p) }, onDeleteMemo = { onDeleteMemo(p, it) }, onOpenShot = onOpenShot,
                    onParts = { on -> upd(p.copy(noParts = !on, updated = System.currentTimeMillis())) }
                )
            }
            defs["apuntes"] = MagDef("Apuntes", "Nuevo" to { onNote(p, null) }) {
                if (p.entries.isEmpty()) Text("Aún no has apuntado nada.", color = Muted, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 15.sp)
                p.entries.sortedByDescending { it.time }.forEach { e ->
                    val vals = e.scores()
                    Column(Modifier.fillMaxWidth().clickable { onNote(p, e) }.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${vals[0]}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.padding(end = 10.dp))
                            Text((fmt(e.time) + if (kind.isBook && e.chapter > 0) " · cap. ${e.chapter}" else "").uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        if (e.note.isNotBlank()) Text(e.note, fontFamily = Display, fontSize = 17.sp, lineHeight = 25.sp)
                    }
                }
            }
            if (kind.isGame) defs["horas"] = MagDef("Horas") { MagHours(p.minutes, p.ttbMain, p.ttbExtra, p.ttbFull) }
            defs["enlaces"] = MagDef("Enlaces") {
                if (kind.hasSoundtrack) MagLink("Banda sonora", "YouTube Music") { openSoundtrack(ctx, p.name) }
                if (onRecommend != null) MagLink("Recomendar", "a $partnerName", accent = true) { recommend = true }
                MagLink("Datos de la ficha") { editing = true }
            }
            val defaults = when {
                kind.isGame -> magRows(listOf("guias", "estado"), listOf("sesion", "jugar"), listOf("progreso"), listOf("partes"), listOf("apuntes"), listOf("horas", "enlaces"))
                kind.isMovie || kind.isTv -> magRows(listOf("critica", "estado"), listOf("progreso"), listOf("partes"), listOf("apuntes"), listOf("enlaces"))
                kind.isBook -> magRows(listOf("lectura"), listOf("capitulos", "estado"), listOf("subrayados"), listOf("partes"), listOf("apuntes"), listOf("enlaces"))
                else -> magRows(listOf("estado"), listOf("progreso"), listOf("partes"), listOf("apuntes"), listOf("enlaces"))
            }
            MagPage("p:${kind.id}:${p.id}", "tpl:p:${kind.id}", kind.title, defaults, defs, kind, p.id, p.name, designing)
            if (designing) MagFooter("Listo") { designing = false } else MagFooter { designing = true }
        } else {
        HeroHeader(
            p.cover, p.hero, p.name, listOfNotNull(kind.title, p.creator.ifBlank { null }).joinToString(" · "), coverKey(kind.id, p.id), onBack,
            actions = {
                if (partner != null) RoundIcon(Icons.Rounded.Favorite, "Ver la ficha de $partnerName", onImage = true) { withReveal { viewP = true } }
                RoundIcon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, if (editing) "Listo" else "Editar", onImage = true) { editing = !editing }
            },
            chips = {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    kind.statuses.getOrNull(p.status)?.let { InfoChip(it) }
                    if (p.platform.isNotBlank()) InfoChip(p.platform, Icons.Rounded.Devices)
                    if (!kind.isBook && p.minutes > 0) InfoChip(hoursText(p.minutes), Icons.Rounded.Schedule)
                    if (kind.isGame && p.ttbMain > 0) InfoChip("Media ${hoursText(p.ttbMain)}", Icons.Rounded.HourglassBottom)
                    if (kind.isBook && p.pages > 0) InfoChip("${p.pages} págs.", Icons.Rounded.AutoStories)
                    if (kind.isBook && p.status == 1 && p.chapter > 0) InfoChip("cap. ${p.chapter}")
                    if (p.start > 0L) InfoChip("Desde ${fmtDay(p.start)}", Icons.Rounded.Event)
                    if (kind.isMovie || kind.isTv) RtChips(kind, p.id, p.name)
                }
            }
        )
        Column(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (kind.isBook) BookFileBar(p.id, p.name)
            val theirs = partner?.reactsToMe?.get(p.id)
            if (theirs != null) {
                Spacer(Modifier.height(8.dp))
                TheirReaction(partnerName, theirs)
            }
            Spacer(Modifier.height(4.dp))
            if (editing)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(kind.statuses[0], p.status == 0, Charcoal) { upd(p.copy(status = 0)) }
                Chip(kind.statuses[1], p.status == 1, Charcoal) {
                    upd(p.copy(status = 1, start = if (p.start == 0L) today() else p.start, end = 0L))
                }
                Chip(kind.statuses[2], p.status == 2, Muted) {
                    upd(p.copy(status = 2, end = if (p.end == 0L) today() else p.end))
                }
            }
            // Guías a mano, lo primero: para tenerlas en el móvil mientras juegas en el PC
            if (kind.isGame) {
                Spacer(Modifier.height(18.dp))
                FichaPlayButtons(p.id, p.name, Modifier.padding(bottom = 16.dp))
                GuidesSection(p.id, p.name)
            }
            if (editing) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(p.creator, { upd(p.copy(creator = it)) },
                label = { Text(kind.creatorWord) }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
            if (kind.isGame && p.creator.isBlank()) {
                Spacer(Modifier.height(6.dp))
                DetectStudio(p.name) { upd(p.copy(creator = it)) }
            }
            Spacer(Modifier.height(12.dp))
            PlatformPicker(kind, p.platform) { upd(p.copy(platform = it)) }
            Spacer(Modifier.height(16.dp))
            DatesRow(p.start, p.end, { upd(p.copy(start = it)) }, { upd(p.copy(end = it)) })
            }
            Spacer(Modifier.height(16.dp))

            if (kind.isBook) {
                if (editing) {
                    PagesField(p.pages) { upd(p.copy(pages = it)) }
                    Spacer(Modifier.height(14.dp))
                }
                if (p.status == 1 && !editing) {
                    if (due) Text("Llevas ${p.pending()} capítulos desde tu último apunte: toca apuntar.",
                        color = ColGeneral, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    else Text("Apuntas cada ${p.every} cap.", color = UiSoft, fontSize = 15.sp, textAlign = TextAlign.Center)
                } else if (p.status == 1) {
                    Text("Capítulo actual", color = UiSoft, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Stepper(p.chapter, "", { upd(p.copy(chapter = maxOf(0, p.chapter - 1))) }, { upd(p.copy(chapter = p.chapter + 1)) })
                    Spacer(Modifier.height(12.dp))
                    Text("Apuntar cada", color = UiSoft, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Stepper(p.every, " cap.", { upd(p.copy(every = maxOf(1, p.every - 1))) }, { upd(p.copy(every = p.every + 1)) })
                    if (due) {
                        Spacer(Modifier.height(8.dp))
                        Text("Llevas ${p.pending()} capítulos desde tu último apunte: toca apuntar.",
                            color = ColGeneral, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                } else {
                    Text("Marca el libro como «${kind.statuses[1]}» para llevar la cuenta de capítulos.",
                        color = UiSoft, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            } else if (!kind.isGame) {
                val fe = findEpisodes
                ProgressPanel(p, kind, readOnly = !editing, episodes = if (fe != null) ({ fe(p.id, p.name) }) else null, onPartDone = { onPartDone(p, it) }) { upd(it) }
            } else {
                ProgressPanel(p, kind, readOnly = !editing, find = findTtb, onPartDone = { onPartDone(p, it) }) { upd(it) }
                if (editing) {
                    Spacer(Modifier.height(16.dp))
                    MinutesEditor(p.minutes) { upd(p.copy(minutes = it)) }
                }
                Spacer(Modifier.height(14.dp))
                if (p.status == 1) {
                    if (sessId == p.id) {
                        var now by remember { mutableStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
                        val mins = ((now - sessStart) / 60_000L).toInt().coerceAtLeast(0)
                        Row(
                            Modifier.border(1.dp, Line, shp(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Timer, null, tint = Charcoal)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Sesión en marcha: ${hoursText(mins)}", fontWeight = FontWeight.SemiBold)
                                Text("Aviso cada $sessHours h. Al terminarla se suma a tus horas.", color = UiSoft, fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button({ onStop() }, shape = pillShape()) { Text("Terminar sesión") }
                    } else {
                        Text("Empezar sesión con aviso cada:", color = UiSoft, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SoftChip("1 hora", false, onPanel = true) { onStart(p, 1) }
                            SoftChip("2 horas", false, onPanel = true) { onStart(p, 2) }
                            SoftChip("3 horas", false, onPanel = true) { onStart(p, 3) }
                        }
                    }
                } else {
                    Text("Marca el juego como «${kind.statuses[1]}» para poder empezar una sesión.",
                        color = UiSoft, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(16.dp))
            PlaySuitePanel(
                p, kind,
                onRate = { onRateSeg(p, it) }, onOpenEntry = { onNote(p, it) }, onAddSeg = { onAddSeg(p) },
                onMemo = { onMemo(p) }, onDeleteMemo = { onDeleteMemo(p, it) }, onOpenShot = onOpenShot,
                onParts = { on -> upd(p.copy(noParts = !on, updated = System.currentTimeMillis())) }
            )

            Spacer(Modifier.height(18.dp))
            Button({ onNote(p, null) }, shape = pillShape(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (due) ColGeneral else Charcoal)) {
                Text("Apuntar notas", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            if (b != null) {
                Spacer(Modifier.height(24.dp))
                Overline("Baremo · media de ${p.entries.size} apuntes")
                Spacer(Modifier.height(12.dp))
                StaticGauge(kind.labels[0], b.general, ColGeneral, 110.dp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val bs = b.scores()
                    val cols = critColors()
                    for (i in 1..kind.crit) StaticGauge(kind.labels[i], bs[i], cols[i - 1], 64.dp, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton({ onFinish(p) }, shape = pillShape()) {
                Text(if (b != null) "Pasar a valorados" else "Terminado: valorar ahora")
            }

            if (p.entries.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Overline("Apuntes", Modifier.align(Alignment.Start).padding(bottom = 4.dp))
                p.entries.sortedByDescending { it.time }.forEach { e ->
                    val vals = e.scores()
                    val line = kind.labels.mapIndexed { i, l -> "${l.take(3)} ${vals[i]}" }.joinToString(" · ")
                    val chap = if (kind.isBook && e.chapter > 0) " · cap. ${e.chapter}" else ""
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        .clip(shp(14.dp)).border(1.dp, Line, shp(14.dp))
                        .clickable { onNote(p, e) }.padding(14.dp)) {
                        Text(fmt(e.time) + chap, color = UiSoft, fontSize = 14.sp)
                        Text(line, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        if (e.note.isNotBlank()) Text(e.note, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing) ActionPill(Icons.Rounded.Image, "Portada") { onArt(p, false) }
                if (editing && !kind.isBook) ActionPill(Icons.Rounded.Wallpaper, "Fondo") { onArt(p, true) }
                if (kind.hasSoundtrack) SoundtrackButton(p.name)
                if (onRecommend != null) ActionPill(Icons.Rounded.Favorite, "Recomendar") { recommend = true }
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Spacer(Modifier.height(16.dp))
            // «Editar» abre los campos (estado, estudio, plataforma, fechas, progreso, horas, portada); «Listo» los cierra
            OutlinedButton({ editing = !editing }, shape = pillShape(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
                Icon(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (editing) "Listo" else "Editar", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            if (editing) {
                Spacer(Modifier.height(6.dp))
                TextButton({ onDelete(p) }) { Text("Eliminar", color = UiSoft) }
            }
        }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
    }
    }
    if (recommend && onRecommend != null) {
        RecommendDialog(partnerName, p.name, { note -> recommend = false; onRecommend(p, note) }) { recommend = false }
    }
}
