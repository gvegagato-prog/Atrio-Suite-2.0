package com.gonzalo.gamerate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Biblioteca de una categoría (Atrio 6): un título grande, cuatro apartados (en curso, pendientes, terminados,
 * dejados) y una cuadrícula de portadas que se abren con la transición compartida.
 */

private fun libMatches(q: String, vararg fields: String): Boolean {
    if (q.isBlank()) return true
    val t = q.trim().lowercase()
    return fields.any { it.lowercase().contains(t) }
}

/** Un elemento de la cuadrícula: algo en curso (Play) o algo terminado y valorado (Game). */
private class Cell(val id: Long, val name: String, val cover: String, val sub: String, val play: Play?, val game: Game?)

@Composable
fun LibraryScreen(
    kind: Kind, games: List<Game>, plays: List<Play>, sessId: Long, partner: PartnerSide?, readOnly: Boolean,
    segment: Int, onSegment: (Int) -> Unit,
    onOpenPlay: (Play) -> Unit, onOpenGame: (Game) -> Unit,
    onAdd: ((Boolean) -> Unit)?, onStats: () -> Unit, onDiscover: (() -> Unit)?, onWheel: (() -> Unit)?,
    /** Sustituye al título (Cine: «Películas · Series»). */
    titleSwitch: (@Composable () -> Unit)? = null,
    badgeFor: (@Composable BoxScope.(Long, String) -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    var query by remember(kind.id) { mutableStateOf("") }
    var searching by remember(kind.id) { mutableStateOf(false) }
    var sort by remember(kind.id) { mutableStateOf(0) }
    var favOnly by remember(kind.id) { mutableStateOf(false) }
    val labels = listOf(kind.statuses[1], kind.statuses[0], kind.doneWord, kind.statuses[2])
    val counts = listOf(plays.count { it.status == 1 }, plays.count { it.status == 0 }, games.size, plays.count { it.status == 2 })
    val cells: List<Cell> = remember(kind.id, games, plays, segment, query, sort, favOnly, sessId) {
        when (segment) {
            2 -> {
                val f = games.filter { (!favOnly || it.fav) && libMatches(query, it.name, it.creator, it.platform) }
                when (sort) {
                    1 -> f.sortedByDescending { it.activity }
                    2 -> f.sortedBy { it.name.lowercase() }
                    else -> f.sortedByDescending { it.general }
                }.map { g -> Cell(g.id, g.name, g.cover, g.creator.ifBlank { g.platform }, null, g) }
            }
            else -> {
                val st = when (segment) { 0 -> 1; 1 -> 0; else -> 2 }
                plays.filter { it.status == st && libMatches(query, it.name, it.creator, it.platform) }
                    .sortedWith(compareByDescending<Play> { it.id == sessId }.thenByDescending { it.activity })
                    .map { p ->
                        val extra = when {
                            p.id == sessId -> "En sesión ahora"
                            kind.isBook && p.chapter > 0 -> "Capítulo ${p.chapter}"
                            !kind.isBook && p.minutes > 0 -> hoursText(p.minutes)
                            else -> p.creator
                        }
                        Cell(p.id, p.name, p.cover, extra, p, null)
                    }
            }
        }
    }
    val grid = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Adaptive(when (Skin.covers) { 1 -> 185.dp; 2 -> 108.dp; else -> 150.dp }), state = grid,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) { BackRound(onBack = onBack); Spacer(Modifier.width(10.dp)) }
                        Column(Modifier.weight(1f)) {
                            if (readOnly) Text("DE TU PAREJA · SOLO LECTURA", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp)
                            if (titleSwitch != null) titleSwitch()
                            else Text(kind.title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, maxLines = 1)
                        }
                        RoundIcon(Icons.Rounded.Search, "Buscar", active = searching) { searching = !searching; if (!searching) query = "" }
                        Spacer(Modifier.width(8.dp))
                        RoundIcon(Icons.Rounded.Insights, "Estadísticas", onClick = onStats)
                    }
                    AnimatedVisibility(searching, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        SearchPill(query, "Buscar en ${kind.title.lowercase()}", Modifier.fillMaxWidth().padding(bottom = 10.dp)) { query = it }
                    }
                    Segmented(labels, segment, counts = counts) { onSegment(it) }
                    Spacer(Modifier.height(10.dp))
                    // Atajos de cada apartado
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onDiscover != null) ActionPill(Icons.Rounded.AutoAwesome, if (kind.isGame) "Siguiente juego" else "Descubrir", dark = true, onClick = onDiscover)
                        if (segment == 1 && onWheel != null && counts[1] > 0) ActionPill(Icons.Rounded.Casino, "Ruleta", onClick = onWheel)
                        if (segment == 2) {
                            listOf("Nota", "Recientes", "Nombre").forEachIndexed { i, n -> SoftChip(n, sort == i) { sort = i } }
                            SoftChip("★ Favoritos", favOnly) { favOnly = !favOnly }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (cells.isEmpty()) EmptyLibrary(kind, segment, query.isNotBlank(), if (readOnly) null else onAdd)
                }
            }
            itemsIndexed(cells, key = { _, c -> "${segment}-${c.id}" }) { i, c ->
                val key = coverKey(kind.id, c.id)
                val flips = partner != null && !readOnly
                val inner: @Composable () -> Unit = {
                    CoverCard(
                        c.cover, c.name, c.sub, key,
                        progress = c.play?.percent()?.let { it / 100f },
                        dim = segment == 3,
                        badge = when {
                            c.game != null -> ({ ScoreBadge(c.game.general, ColGeneral) })
                            c.play?.id == sessId && sessId != 0L -> ({ LiveDot() })
                            else -> null
                        },
                        badgeStart = badgeFor?.let { b -> { b(c.id, c.name) } },
                        interactive = !flips
                    ) { if (c.play != null) onOpenPlay(c.play) else if (c.game != null) onOpenGame(c.game) }
                }
                Box(Modifier.rise(i, i < 14)) {
                    val p = partner
                    if (p != null && !readOnly) {
                        FlipCard(enabled = true, onClick = { if (c.play != null) onOpenPlay(c.play) else if (c.game != null) onOpenGame(c.game) },
                            modifier = Modifier, front = inner, back = { PartnerFace(p, c.id, kind) })
                    } else inner()
                }
            }
        }
        if (onAdd != null && !readOnly) {
            Fab6(Icons.Rounded.Add, "Añadir", Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 22.dp, bottom = 96.dp)) {
                onAdd(segment != 2)
            }
        }
    }
}

/** Punto rojo de «en directo». */
@Composable
fun LiveDot() {
    Row(Modifier.clip(pillShape()).background(Color(0xFFE5484D)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(5.dp))
        Text("EN SESIÓN", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun EmptyLibrary(kind: Kind, segment: Int, searching: Boolean, onAdd: ((Boolean) -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            when { kind.isBook -> Icons.Rounded.AutoStories; kind.isMovie -> Icons.Rounded.Movie; kind.isTv -> Icons.Rounded.Tv; kind.isGame -> Icons.Rounded.SportsEsports; else -> Icons.Rounded.Collections },
            null, tint = Muted, modifier = Modifier.size(46.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                searching -> "Nada coincide con la búsqueda."
                segment == 0 -> "No tienes nada ${kind.statuses[1].lowercase()} ahora mismo."
                segment == 1 -> "Tu lista está vacía."
                segment == 2 -> "Aún no has valorado nada aquí."
                else -> "Nada abandonado. ¡Bien!"
            },
            color = Muted, textAlign = TextAlign.Center
        )
        if (onAdd != null && !searching && segment != 3) {
            Spacer(Modifier.height(14.dp))
            BigAction(Icons.Rounded.Add, "Añadir", accent = true) { onAdd(segment != 2) }
        }
    }
}
