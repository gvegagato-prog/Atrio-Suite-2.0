package com.gonzalo.gamerate

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Cambiar notas en masa (Perfil y Ajustes): eliges lo que quieras de cualquier categoría (lo ya valorado) y luego
 * se repasa uno detrás de otro, con los mismos anillos que en la ficha. Pensado para después de importar de otra
 * app (Goodreads y Bookmory traen las estrellas como 20, 40, 60, 80 o 100).
 */

/** Una entrada elegida: de qué categoría es y cuál. */
private data class MassPick(val kind: Int, val id: Long)

/** Las notas que vienen de estrellas (1 a 5 → 20 a 100): lo típico de lo importado. */
private fun Game.fromStars() = general % 20 == 0 && listOf(gfx, story, music, gameplay).all { it == 50 || it == general }

@Composable
fun MassScoreScreen(
    kinds: List<Kind>, gamesOf: (Kind) -> List<Game>, onSave: (Kind, Game) -> Unit, onBack: () -> Unit
) {
    var picked by remember { mutableStateOf(listOf<MassPick>()) }
    var queue by remember { mutableStateOf<List<MassPick>?>(null) }
    var pos by remember { mutableStateOf(0) }
    var changed by remember { mutableStateOf(0) }
    val q = queue
    BackHandler(enabled = q != null) { if (pos > 0 && pos < q!!.size) pos-- else { queue = null; pos = 0 } }
    AnimatedContent(q == null, transitionSpec = { fadeIn(tween(Motion.ms(220))) togetherWith fadeOut(tween(Motion.ms(140))) }, label = "masa") { choosing ->
        if (choosing) MassChoose(kinds, gamesOf, picked, { picked = it }, onBack) { queue = picked; pos = 0; changed = 0 }
        else {
            val list = queue ?: emptyList()
            if (pos >= list.size) MassDone(changed, list.size) { queue = null; picked = emptyList(); onBack() }
            else MassReview(list, pos, kinds, gamesOf,
                onPrev = { if (pos > 0) pos-- },
                onNext = { k, g ->
                    if (g != null) { onSave(k, g); changed++ }
                    pos++
                },
                onStop = { queue = null; pos = 0 })
        }
    }
}

@Composable
private fun MassChoose(
    kinds: List<Kind>, gamesOf: (Kind) -> List<Game>, picked: List<MassPick>, setPicked: (List<MassPick>) -> Unit,
    onBack: () -> Unit, onStart: () -> Unit
) {
    var kid by remember { mutableStateOf(kinds.firstOrNull { gamesOf(it).isNotEmpty() }?.id ?: kinds.first().id) }
    var query by remember { mutableStateOf("") }
    val kind = kinds.find { it.id == kid } ?: kinds.first()
    val all = remember(kid, gamesOf(kind)) { gamesOf(kind).sortedBy { it.name.lowercase() } }
    val shown = all.filter { query.isBlank() || normName(it.name).contains(normName(query)) }
    val sel = picked.toSet()
    fun toggle(p: MassPick) = setPicked(if (p in sel) picked - p else picked + p)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)) {
            item {
                TopBar("Perfil", onBack, "Notas en masa")
                Text("Elige lo que quieras repasar, de una o de varias categorías. Luego irán saliendo uno detrás de otro para que les cambies la nota.",
                    color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEach { k ->
                        val n = picked.count { it.kind == k.id }
                        SoftChip(if (n > 0) "${k.title} · $n" else k.title, k.id == kid) { kid = k.id; query = "" }
                    }
                }
                SearchPill(query, "Buscar en ${kind.title.lowercase()}", Modifier.fillMaxWidth().padding(bottom = 8.dp)) { query = it }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    val mine = shown.map { MassPick(kind.id, it.id) }
                    MassLink("TODOS") { setPicked((picked + mine).distinct()) }
                    MassLink("NINGUNO") { setPicked(picked - mine.toSet()) }
                    // Lo importado de Goodreads o Bookmory: notas de estrellas (20, 40, 60, 80, 100)
                    MassLink("LOS DE ESTRELLAS (20, 40…)") { setPicked((picked + shown.filter { it.fromStars() }.map { MassPick(kind.id, it.id) }).distinct()) }
                }
                if (all.isEmpty()) Text("No hay nada valorado en ${kind.title.lowercase()}.", fontFamily = Display, fontStyle = FontStyle.Italic,
                    fontSize = 19.sp, color = Muted, modifier = Modifier.padding(vertical = 24.dp))
            }
            items(shown.size, key = { "${kind.id}-${shown[it].id}" }) { i ->
                val g = shown[i]
                val p = MassPick(kind.id, g.id)
                Row(Modifier.fillMaxWidth().press(haptic = false) { toggle(p) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(p in sel, { toggle(p) }, colors = CheckboxDefaults.colors(checkedColor = ColGeneral, uncheckedColor = Muted))
                    Spacer(Modifier.width(6.dp))
                    if (Skin.listCovers) { ListCover(g.cover, g.name, 40.dp, 56.dp); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(g.name, fontFamily = Display, fontSize = 19.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (g.creator.isNotBlank()) Text(g.creator.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${g.general}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.padding(start = 10.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
        }
        // Abajo, siempre a mano
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (picked.isEmpty()) "Nada elegido" else "${picked.size} elegido${if (picked.size == 1) "" else "s"}", color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("REPASAR\u00A0→", color = if (picked.isEmpty()) Muted else ColGeneral, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.press(haptic = picked.isNotEmpty()) { if (picked.isNotEmpty()) onStart() }.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun MassReview(
    list: List<MassPick>, pos: Int, kinds: List<Kind>, gamesOf: (Kind) -> List<Game>,
    onPrev: () -> Unit, onNext: (Kind, Game?) -> Unit, onStop: () -> Unit
) {
    val p = list[pos]
    val kind = kinds.find { it.id == p.kind } ?: return
    val g0 = gamesOf(kind).find { it.id == p.id }
    // Si ya no existe (borrado mientras tanto), se pasa al siguiente
    if (g0 == null) { LaunchedEffect(pos) { onNext(kind, null) }; return }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            TopBar("Notas en masa", onStop, "${pos + 1} de ${list.size}")
            // Una barra por cada uno: hechos en acento
            Row(Modifier.fillMaxWidth().height(4.dp).padding(bottom = 0.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                list.forEachIndexed { i, _ -> Box(Modifier.weight(1f).fillMaxHeight().background(if (i < pos) ColGeneral else if (i == pos) Charcoal else Line)) }
            }
            Spacer(Modifier.height(18.dp))
            AnimatedContent(g0, transitionSpec = { fadeIn(tween(Motion.ms(260))) togetherWith fadeOut(tween(Motion.ms(160))) },
                contentKey = { "${p.kind}-${it.id}" }, label = "nota") { g ->
                var v by remember(g.id, p.kind) { mutableStateOf(g.scores()) }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ListCover(g.cover, g.name, 74.dp, 104.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            FitTitle(g.name, 28.sp, 30.sp, letterSpacing = (-0.5).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(listOf(kind.title, g.creator).filter { it.isNotBlank() }.joinToString(" · ").uppercase(), color = Muted, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            Text("Tenía un ${g.general}", color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    ScoresFor(v, kind.labels) { i, x -> v = v.toMutableList().also { it[i] = x } }
                    Spacer(Modifier.height(14.dp))
                    // Abajo de la página, a mano del pulgar
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (pos > 0) MassLink("← ANTERIOR", Charcoal, onPrev)
                        Spacer(Modifier.weight(1f))
                        MassLink("SALTAR", Muted) { onNext(kind, null) }
                        Spacer(Modifier.width(22.dp))
                        val last = pos == list.size - 1
                        MassLink(if (last) "GUARDAR Y TERMINAR" else "GUARDAR\u00A0→", ColGeneral) {
                            onNext(kind, if (v != g.scores()) g.withScores(v).copy(updated = System.currentTimeMillis()) else null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MassDone(changed: Int, total: Int, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
        Text("Hecho", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp)
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(3.dp).background(Charcoal))
        Text(
            when (changed) {
                0 -> "Has repasado $total sin cambiar ninguna nota."
                1 -> "Has cambiado 1 nota de $total."
                else -> "Has cambiado $changed notas de $total."
            },
            fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = Muted
        )
        Spacer(Modifier.height(24.dp))
        MassLink("VOLVER\u00A0→", ColGeneral, onClose)
    }
}

@Composable
private fun MassLink(t: String, color: androidx.compose.ui.graphics.Color = Charcoal, onClick: () -> Unit) =
    Text(t, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press(haptic = false, onClick = onClick).padding(vertical = 8.dp))
