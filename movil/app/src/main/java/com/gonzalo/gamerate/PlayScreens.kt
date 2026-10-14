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
    kind: Kind, partner: PartnerSide?, p0: Play, sessId: Long, sessHours: Int, sessStart: Long,
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
    LaunchedEffect(viewP) { Mode.pageP = viewP }
    DisposableEffect(Unit) { onDispose { Mode.pageP = false } }
    Crossfade(viewP && partner != null, animationSpec = tween(450), label = "pareja") { pv ->
    val ps = if (pv) partner else null
    if (ps != null) {
        PartnerPage(ps, p.id, kind, p.name, p.cover, p.hero, { withReveal { viewP = false } }, { withReveal { viewP = false } }, myReact, onReact)
    } else {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        ArtBanner(p.hero, p.cover, p.name, true, onBack, if (partner != null) ({ withReveal { viewP = true } }) else null)
        Column(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 760.dp).fillMaxWidth().padding(16.dp)
                .card(24).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (p.creator.isNotBlank()) Overline(p.creator, Modifier.padding(bottom = 4.dp))
            Text(p.name, fontSize = 28.sp, lineHeight = 32.sp, fontFamily = Display, textAlign = TextAlign.Center)
            val theirs = partner?.reactsToMe?.get(p.id)
            if (theirs != null) {
                Spacer(Modifier.height(8.dp))
                TheirReaction(partnerName, theirs)
            }
            if (partner != null) {
                Spacer(Modifier.height(4.dp))
                Text("Toca dos veces la portada para ver la ficha de $partnerName.", color = UiSoft, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(p.creator, { upd(p.copy(creator = it)) },
                label = { Text(kind.creatorWord) }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            if (kind.isGame && p.creator.isBlank()) {
                Spacer(Modifier.height(6.dp))
                DetectStudio(p.name) { upd(p.copy(creator = it)) }
            }
            Spacer(Modifier.height(12.dp))
            PlatformPicker(kind, p.platform) { upd(p.copy(platform = it)) }
            Spacer(Modifier.height(16.dp))
            DatesRow(p.start, p.end, { upd(p.copy(start = it)) }, { upd(p.copy(end = it)) })
            Spacer(Modifier.height(16.dp))

            if (kind.isBook) {
                PagesField(p.pages) { upd(p.copy(pages = it)) }
                Spacer(Modifier.height(14.dp))
                if (p.status == 1) {
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
                ProgressPanel(p, kind, episodes = if (fe != null) ({ fe(p.id, p.name) }) else null, onPartDone = { onPartDone(p, it) }) { upd(it) }
            } else {
                ProgressPanel(p, kind, find = findTtb, onPartDone = { onPartDone(p, it) }) { upd(it) }
                Spacer(Modifier.height(16.dp))
                MinutesEditor(p.minutes) { upd(p.copy(minutes = it)) }
                Spacer(Modifier.height(14.dp))
                if (p.status == 1) {
                    if (sessId == p.id) {
                        var now by remember { mutableStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
                        val mins = ((now - sessStart) / 60_000L).toInt().coerceAtLeast(0)
                        Row(
                            Modifier.border(1.dp, Line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
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
                        Button({ onStop() }, shape = RoundedCornerShape(50)) { Text("Terminar sesión") }
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
            Button({ onNote(p, null) }, shape = RoundedCornerShape(50),
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
            OutlinedButton({ onFinish(p) }, shape = RoundedCornerShape(50)) {
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
                        .clip(RoundedCornerShape(14.dp)).border(1.dp, Line, RoundedCornerShape(14.dp))
                        .clickable { onNote(p, e) }.padding(14.dp)) {
                        Text(fmt(e.time) + chap, color = UiSoft, fontSize = 14.sp)
                        Text(line, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        if (e.note.isNotBlank()) Text(e.note, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(Icons.Rounded.Image, "Portada") { onArt(p, false) }
                if (!kind.isBook) ActionPill(Icons.Rounded.Wallpaper, "Fondo") { onArt(p, true) }
                if (kind.hasSoundtrack) SoundtrackButton(p.name)
                if (onRecommend != null) ActionPill(Icons.Rounded.Favorite, "Recomendar") { recommend = true }
            }
            Spacer(Modifier.height(6.dp))
            TextButton({ onDelete(p) }) { Text("Eliminar", color = UiSoft) }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
    }
    }
    if (recommend && onRecommend != null) {
        RecommendDialog(partnerName, p.name, { note -> recommend = false; onRecommend(p, note) }) { recommend = false }
    }
}
