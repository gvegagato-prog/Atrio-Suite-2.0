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
    val open: (Cell) -> Unit = { c -> if (c.play != null) onOpenPlay(c.play) else if (c.game != null) onOpenGame(c.game) }
    val lib = LibUi(
        kind, labels, counts, cells, segment, onSegment, query, { query = it }, searching, { searching = it }, sort, { sort = it },
        favOnly, { favOnly = it }, sessId, readOnly, open, onAdd, onStats, onDiscover, onWheel, titleSwitch, badgeFor, onBack
    )
    if (Skin.revista) { RevistaLibrary(lib); return }
    val grid = rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Adaptive(when (Skin.covers) { 1 -> 185.dp; 2 -> 108.dp; else -> 150.dp }), state = grid,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = dockSpace(56.dp)),
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
                        if (onDiscover != null) ActionPill(Icons.Rounded.AutoAwesome, "Descubrir", dark = true, onClick = onDiscover)
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


// ================================================================ Biblioteca de la interfaz Revista

/** Lo que necesita cualquier biblioteca para pintarse. */
private class LibUi(
    val kind: Kind, val labels: List<String>, val counts: List<Int>, val cells: List<Cell>,
    val segment: Int, val onSegment: (Int) -> Unit,
    val query: String, val setQuery: (String) -> Unit, val searching: Boolean, val setSearching: (Boolean) -> Unit,
    val sort: Int, val setSort: (Int) -> Unit, val favOnly: Boolean, val setFav: (Boolean) -> Unit,
    val sessId: Long, val readOnly: Boolean, val open: (Cell) -> Unit,
    val onAdd: ((Boolean) -> Unit)?, val onStats: () -> Unit, val onDiscover: (() -> Unit)?, val onWheel: (() -> Unit)?,
    val titleSwitch: (@Composable () -> Unit)?, val badgeFor: (@Composable BoxScope.(Long, String) -> Unit)?, val onBack: (() -> Unit)?
)

@Composable
private fun TextLink(t: String, color: Color = Charcoal, onClick: () -> Unit) =
    Text(t, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.press(onClick = onClick).padding(vertical = 6.dp))

/** Revista: una lista de texto como un índice, con portadas pequeñas y la nota en cifras enormes. */
@Composable
private fun RevistaLibrary(l: LibUi) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (l.onBack != null) TextLink("← ATRIO", onClick = l.onBack)
                Spacer(Modifier.weight(1f))
                TextLink(if (l.searching) "CERRAR" else "BUSCAR") { l.setSearching(!l.searching); if (l.searching) l.setQuery("") }
                TextLink("CIFRAS", onClick = l.onStats)
                if (l.onAdd != null && !l.readOnly) TextLink("+ AÑADIR", ColGeneral) { l.onAdd.invoke(l.segment != 2) }
            }
            if (l.titleSwitch != null) Box(Modifier.padding(top = 8.dp)) { l.titleSwitch.invoke() }
            else FitTitle(l.kind.title, 52.sp, 54.sp, letterSpacing = (-1.5).sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Box(Modifier.padding(top = 4.dp, bottom = 6.dp).fillMaxWidth().height(3.dp).background(Charcoal))
            if (l.searching) SearchPill(l.query, "Buscar en ${l.kind.title.lowercase()}", Modifier.fillMaxWidth().padding(vertical = 8.dp)) { l.setQuery(it) }
            Segmented(l.labels, l.segment, counts = l.counts) { l.onSegment(it) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (l.onDiscover != null) TextLink("DESCUBRIR →", ColGeneral, l.onDiscover)
                if (l.segment == 1 && l.onWheel != null && l.counts[1] > 0) TextLink("RULETA", onClick = l.onWheel)
                if (l.segment == 2) {
                    listOf("NOTA", "RECIENTES", "NOMBRE").forEachIndexed { i, n -> TextLink(n, if (l.sort == i) ColGeneral else Muted) { l.setSort(i) } }
                    TextLink("★ FAVORITOS", if (l.favOnly) ColGeneral else Muted) { l.setFav(!l.favOnly) }
                }
            }
            if (l.cells.isEmpty()) {
                Text(
                    if (l.query.isNotBlank()) "Nada coincide con la búsqueda." else "Esta sección está en blanco, de momento.",
                    fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 20.sp, color = Muted, modifier = Modifier.padding(vertical = 30.dp)
                )
                if (l.onAdd != null && !l.readOnly && l.segment != 3) TextLink("+ AÑADIR EL PRIMERO", ColGeneral) { l.onAdd.invoke(l.segment != 2) }
            }
        }
        items(l.cells.size, key = { "${l.segment}-${l.cells[it].id}" }) { i ->
            val c = l.cells[i]
            // Sin portadas: el título grande, la nota a la derecha y el estudio (o autor) debajo
            Row(Modifier.fillMaxWidth().press(haptic = false) { l.open(c) }.padding(vertical = 14.dp).rise(i, i < 12), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FitTitle(c.name, 30.sp, 32.sp, letterSpacing = (-0.5).sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    val who = (c.game?.creator ?: c.play?.creator).orEmpty()
                    val line = listOf(who, if (c.sub != who) c.sub else "").filter { it.isNotBlank() }.joinToString(" · ")
                    if (line.isNotBlank()) Text(line.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    val pct = c.play?.percent()
                    if (pct != null && pct > 0) Text("$pct % HECHO", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    if (c.play?.id == l.sessId && l.sessId != 0L) Box(Modifier.padding(top = 4.dp)) { LiveDot() }
                    if (l.badgeFor != null) Box(Modifier.padding(top = 4.dp).height(26.dp).width(120.dp)) { l.badgeFor.invoke(this, c.id, c.name) }
                }
                if (c.game != null) {
                    Spacer(Modifier.width(12.dp))
                    Text("${c.game.general}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 48.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        }
    }
}
