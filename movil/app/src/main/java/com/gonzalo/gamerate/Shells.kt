@file:OptIn(ExperimentalFoundationApi::class)

package com.gonzalo.gamerate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/*
 * Tres interfaces radicalmente distintas para la pantalla principal (para elegir una y llevarla al detalle):
 *   1 · Feed: cada cosa a pantalla completa; arriba/abajo entre títulos, a los lados entre categorías.
 *   2 · Revista: papel, tipografía enorme, listas numeradas y un sumario como índice.
 *   3 · Escritorio: bloques de colores de distintos tamaños; cada bloque lleva a su sitio.
 * Las fichas, el lector, el Diario, Descubrir, el PC y la pareja son los mismos en las tres.
 */

/** Un título de la colección, venga de «en curso / en lista» (Play) o de «valorado» (Game). */
class ShellEntry(val kind: Kind, val play: Play?, val game: Game?) {
    val id: Long get() = play?.id ?: game!!.id
    val name: String get() = play?.name ?: game!!.name
    val cover: String get() = play?.cover ?: game!!.cover
    val hero: String get() = (play?.hero ?: game?.hero).orEmpty()
    val creator: String get() = (play?.creator ?: game?.creator).orEmpty()
    val minutes: Int get() = play?.minutes ?: game?.minutes ?: 0
    val score: Int? get() = game?.general
    val activity: Long get() = play?.activity ?: game!!.activity
    val status: String get() = play?.let { kind.statuses[it.status.coerceIn(0, 2)] } ?: kind.doneWord.removeSuffix("s").let { if (kind.isGame) "Pasado" else it }
}

/** Todo lo que una interfaz necesita saber y puede hacer. */
class ShellModel(
    val name: String,
    val readOnly: Boolean,
    val kinds: List<Kind>,
    val entries: (Kind) -> List<ShellEntry>,
    val ongoing: List<Ongoing>,
    val paired: Boolean,
    val partnerName: String?,
    val open: (ShellEntry) -> Unit,
    val primary: (Ongoing) -> Unit,
    val primaryLabel: (Ongoing) -> Pair<String, ImageVector>,
    val library: (Kind) -> Unit,
    val diary: () -> Unit,
    val discover: ((Int) -> Unit)?,
    val profile: (() -> Unit)?,
    val add: ((Kind) -> Unit)?,
    val partner: (() -> Unit)?,
    val stats: (Kind) -> Unit,
    /** La tarjeta del PC en directo (la misma de siempre). */
    val live: @Composable () -> Unit
)

/** El orden de siempre: lo que llevas entre manos, lo apuntado y lo terminado (lo último primero). */
fun ShellModel.feed(k: Kind): List<ShellEntry> = entries(k).sortedWith(
    compareBy<ShellEntry> { when (it.play?.status) { 1 -> 0; 0 -> 1; null -> 2; else -> 3 } }.thenByDescending { it.activity }
)

@Composable
fun ShellHome(m: ShellModel) {
    AnimatedContent(Skin.shell, transitionSpec = { fadeIn(tween(Motion.ms(320))) togetherWith fadeOut(tween(Motion.ms(160))) }, label = "interfaz") { sh ->
        when (sh) {
            2 -> RevistaHome(m)
            3 -> EscritorioHome(m)
            else -> FeedHome(m)
        }
    }
}

/** Selector de interfaz (de prueba, hasta que elijas una). */
@Composable
fun ShellSwitcher(ink: Color, bg: Color, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Row(modifier.clip(RoundedCornerShape(50)).background(bg).padding(3.dp)) {
        listOf(1 to "Feed", 2 to "Revista", 3 to "Escritorio").forEach { (i, n) ->
            val on = Skin.shell == i
            Text(
                n, color = if (on) (if (ink.luminance() > 0.5f) Color.Black else Color.White) else ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) ink else Color.Transparent)
                    .press { Skin.shell = i; Skin.save(ctx) }.padding(horizontal = 11.dp, vertical = 6.dp)
            )
        }
    }
}

private fun kindShort(k: Kind) = when { k.isMovie -> "Pelis"; k.isTv -> "Series"; k.isGame -> "Juegos"; k.isBook -> "Libros"; else -> k.title }

// ================================================================ 1 · FEED

@Composable
private fun FeedHome(m: ShellModel) {
    val kinds = m.kinds
    val hp = rememberPagerState { kinds.size }
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(hp, Modifier.fillMaxSize(), key = { kinds[it].id }) { page ->
            val k = kinds[page]
            val list = remember(k, m.entries(k)) { m.feed(k) }
            if (list.isEmpty()) FeedEmpty(k, m)
            else {
                val vp = rememberPagerState { list.size }
                VerticalPager(vp, Modifier.fillMaxSize(), key = { list[it].id }) { i ->
                    FeedCard(list[i], m, i, list.size)
                }
            }
        }
        // Arriba: categorías como texto (la elegida en blanco) y los botones de siempre
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent))).statusBarsPadding().padding(top = 6.dp, bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                ShellSwitcher(Color.White, Color.White.copy(alpha = 0.16f))
                Spacer(Modifier.weight(1f))
                if (m.partner != null) FeedRound(Icons.Rounded.Favorite, "Pareja") { m.partner.invoke() }
                FeedRound(Icons.Rounded.Menu, "Menú") { menu = true }
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(kinds.size) { i ->
                    val on = hp.currentPage == i
                    val scope = rememberCoroutineScope()
                    Text(
                        kindShort(kinds[i]), color = if (on) Color.White else Color.White.copy(alpha = 0.5f),
                        fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = if (on) 22.sp else 17.sp,
                        modifier = Modifier.press { scope.launch { hp.animateScrollToPage(i) } }
                    )
                }
            }
        }
        if (menu) FeedMenu(m, kinds.getOrNull(hp.currentPage) ?: GAMES) { menu = false }
    }
}

@Composable
private fun FeedRound(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.padding(start = 8.dp).size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).press(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun FeedCard(e: ShellEntry, m: ShellModel, index: Int, total: Int) {
    val art = e.hero.ifBlank { e.cover }
    Box(Modifier.fillMaxSize().press(haptic = false) { m.open(e) }) {
        if (art.isNotBlank()) AsyncImage(
            art, null, Modifier.fillMaxSize().then(if (e.hero.isBlank()) Modifier.blur(40.dp) else Modifier),
            contentScale = ContentScale.Crop, alpha = if (e.hero.isBlank()) 0.55f else 0.85f
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.92f))))
        // La portada, grande y centrada
        if (e.cover.isNotBlank() && e.hero.isBlank()) {
            AsyncImage(
                e.cover, e.name,
                Modifier.align(Alignment.Center).offset(y = (-60).dp).fillMaxWidth(0.62f).aspectRatio(if (e.kind.isGame) 0.67f else 0.66f)
                    .shadow(30.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
        } else if (e.cover.isBlank()) {
            Text(e.name.take(1).uppercase(), color = Color.White.copy(alpha = 0.12f), fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 260.sp,
                modifier = Modifier.align(Alignment.Center))
        }
        // Texto abajo a la izquierda
        Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 20.dp, end = 86.dp, bottom = 28.dp)) {
            Text(
                listOf(e.status, e.creator).filter { it.isNotBlank() }.joinToString(" · ").uppercase(),
                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(e.name, color = Color.White, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 40.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                e.score?.let { FeedTag("$it", Color(0xFFFF5A36), Color.White) }
                if (e.minutes > 0 && !e.kind.isBook) FeedTag(hoursText(e.minutes), Color.White.copy(alpha = 0.16f), Color.White)
                if (e.kind.isBook) {
                    val ctx = LocalContext.current
                    val pr = BookFiles.progress(ctx, e.id)
                    if (pr > 0f) FeedTag("${(pr * 100).toInt()} % leído", Color.White.copy(alpha = 0.16f), Color.White)
                }
                if (e.kind.isMovie || e.kind.isTv) Box { RtChipsSmall(e) }
            }
            val pl = e.play
            if (pl != null && pl.status == 1 && !m.readOnly) {
                Spacer(Modifier.height(14.dp))
                val o = Ongoing(e.kind, pl)
                val (label, icon) = m.primaryLabel(o)
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White).press { m.primary(o) }.padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = Color.Black, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
                    Text(label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
        // Columna de acciones a la derecha
        Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FeedSide(Icons.Rounded.Info, "Ficha") { m.open(e) }
            if (m.add != null) FeedSide(Icons.Rounded.Add, "Añadir") { m.add.invoke(e.kind) }
            if (m.discover != null && (e.kind.isGame || e.kind.isBook || e.kind.isMovie || e.kind.isTv))
                FeedSide(Icons.Rounded.AutoAwesome, "Descubrir") { m.discover.invoke(if (e.kind.isGame) 0 else if (e.kind.isBook) 1 else 2) }
            FeedSide(Icons.Rounded.GridView, "Todo") { m.library(e.kind) }
            Text("${index + 1}/$total", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RtChipsSmall(e: ShellEntry) {
    // La nota de Rotten Tomatoes (si está guardada) en pequeño
    Box(Modifier.size(width = 64.dp, height = 28.dp)) { RtBadgeFor(e.kind, e.id, e.name) }
}

@Composable
private fun FeedTag(t: String, bg: Color, fg: Color) {
    Text(t, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 11.dp, vertical = 5.dp))
}

@Composable
private fun FeedSide(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.press(onClick = onClick)) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun FeedEmpty(k: Kind, m: ShellModel) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
        Text("Aquí no hay nada todavía.", color = Color.White, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 42.sp)
        Spacer(Modifier.height(18.dp))
        if (m.add != null) Text("+ Añadir a ${k.title.lowercase()}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).press { m.add.invoke(k) }.padding(horizontal = 20.dp, vertical = 12.dp))
    }
}

@Composable
private fun FeedMenu(m: ShellModel, k: Kind, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).press(haptic = false, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(Color(0xFF16161A))
                .press(haptic = false) {}.navigationBarsPadding().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            @Composable
            fun item(icon: ImageVector, t: String, f: () -> Unit) = Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).press { onClose(); f() }.padding(vertical = 14.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(16.dp))
                Text(t, color = Color.White, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            item(Icons.Rounded.GridView, "Toda la colección de ${kindShort(k).lowercase()}") { m.library(k) }
            item(Icons.Rounded.CalendarMonth, "Diario") { m.diary() }
            item(Icons.Rounded.Insights, "Estadísticas") { m.stats(k) }
            if (m.profile != null) item(Icons.Rounded.Person, "Perfil, estilo y ajustes") { m.profile.invoke() }
            if (m.paired) Box(Modifier.padding(top = 8.dp)) { m.live() }
        }
    }
}

// ================================================================ 2 · REVISTA

private val Paper2 = Color(0xFFF3EEE4)
private val Ink2 = Color(0xFF15120F)
private val Red2 = Color(0xFFC8372D)
private val Grey2 = Color(0xFF7C746A)

@Composable
private fun RevistaHome(m: ShellModel) {
    val today = LocalDate.now()
    val all = m.kinds.flatMap { m.entries(it) }
    val best = all.filter { it.score != null }.sortedByDescending { it.score }.take(5)
    Column(Modifier.fillMaxSize().background(Paper2).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ShellSwitcher(Ink2, Color(0x14000000))
            Spacer(Modifier.weight(1f))
            if (m.partner != null) RevLink("Pareja", m.partner)
            if (m.profile != null) RevLink("Perfil", m.profile)
        }
        // Cabecera de revista
        Text("ATRIO", color = Ink2, fontFamily = AtrioDisplay, fontWeight = FontWeight.Bold, fontSize = 92.sp, lineHeight = 92.sp, letterSpacing = (-3).sp, modifier = Modifier.padding(top = 8.dp))
        Rule(3.dp)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Nº ${today.dayOfYear}", color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES"))).uppercase(), color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Rule(1.dp)
        Text(if (m.readOnly) "La colección de ${m.name}" else "La colección de ${m.name}, al día", color = Grey2, fontFamily = AtrioDisplay, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))

        if (m.paired) { Spacer(Modifier.height(16.dp)); m.live() }

        // En portada: lo primero que llevas entre manos
        m.ongoing.firstOrNull()?.let { o ->
            val e = ShellEntry(o.kind, o.play, null)
            Spacer(Modifier.height(22.dp))
            RevSection("En portada")
            val art = e.hero.ifBlank { e.cover }
            if (art.isNotBlank()) AsyncImage(art, e.name, Modifier.fillMaxWidth().aspectRatio(1.35f).press { m.open(e) }, contentScale = ContentScale.Crop)
            Text(e.name, color = Ink2, fontFamily = AtrioDisplay, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 42.sp, modifier = Modifier.padding(top = 12.dp).press { m.open(e) })
            Text(
                listOf(kindShort(e.kind), e.creator, if (e.minutes > 0 && !e.kind.isBook) hoursText(e.minutes) else "").filter { it.isNotBlank() }.joinToString(" · "),
                color = Grey2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)
            )
            if (!m.readOnly) {
                val (label, _) = m.primaryLabel(o)
                Text("$label →", color = Red2, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp).press { m.primary(o) })
            }
        }

        // Ahora mismo: lista numerada
        if (m.ongoing.size > 1) {
            Spacer(Modifier.height(28.dp))
            RevSection("Ahora mismo")
            m.ongoing.drop(1).take(8).forEachIndexed { i, o ->
                val e = ShellEntry(o.kind, o.play, null)
                Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Text("%02d".format(i + 1), color = Red2, fontFamily = AtrioDisplay, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.width(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = Ink2, fontFamily = AtrioDisplay, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 25.sp)
                        Text(listOf(kindShort(e.kind), e.creator).filter { it.isNotBlank() }.joinToString(" · "), color = Grey2, fontSize = 13.sp)
                    }
                }
                Rule(1.dp, Color(0x22000000))
            }
        }

        // Las notas más altas: números gigantes
        if (best.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            RevSection("Las notas más altas")
            best.forEach { e ->
                Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.score}", color = Ink2, fontFamily = AtrioDisplay, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 58.sp, modifier = Modifier.width(100.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = Ink2, fontFamily = AtrioDisplay, fontSize = 20.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(kindShort(e.kind).uppercase(), color = Red2, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    }
                }
            }
        }

        // Sumario: el índice de toda la app
        Spacer(Modifier.height(30.dp))
        RevSection("Sumario")
        m.kinds.forEach { k ->
            val n = m.entries(k).size
            SumarioRow(k.title, "$n") { m.library(k) }
        }
        SumarioRow("Diario", "cal.") { m.diary() }
        m.discover?.let { d ->
            SumarioRow("Descubrir un juego", "→") { d(0) }
            SumarioRow("Descubrir un libro", "→") { d(1) }
            SumarioRow("Descubrir una peli o serie", "→") { d(2) }
        }
        SumarioRow("Estadísticas", "→") { m.stats(GAMES) }
        Spacer(Modifier.height(40.dp))
        Text("Atrio · hecho en casa", color = Grey2, fontFamily = AtrioDisplay, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Rule(h: androidx.compose.ui.unit.Dp, c: Color = Ink2) = Box(Modifier.fillMaxWidth().height(h).background(c))

@Composable
private fun RevSection(t: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        Box(Modifier.size(10.dp).background(Red2)); Spacer(Modifier.width(8.dp))
        Text(t.uppercase(), color = Ink2, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}

@Composable
private fun RevLink(t: String, f: () -> Unit) =
    Text(t, color = Ink2, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp).press(onClick = f))

@Composable
private fun SumarioRow(t: String, page: String, f: () -> Unit) {
    Row(Modifier.fillMaxWidth().press(haptic = false, onClick = f).padding(vertical = 9.dp), verticalAlignment = Alignment.Bottom) {
        Text(t, color = Ink2, fontFamily = AtrioDisplay, fontSize = 22.sp)
        // Puntos guía, como el índice de una revista
        Box(Modifier.weight(1f).height(16.dp).padding(horizontal = 8.dp).drawBehind {
            drawLine(Ink2.copy(alpha = 0.45f), Offset(0f, size.height - 3), Offset(size.width, size.height - 3), 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 7f)))
        })
        Text(page, color = Red2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ================================================================ 3 · ESCRITORIO (bloques)

private val Desk = Color(0xFF0D0F1C)

@Composable
private fun EscritorioHome(m: ShellModel) {
    val ctx = LocalContext.current
    val games = m.kinds.firstOrNull { it.isGame }
    val valued = games?.let { m.entries(it).mapNotNull { e -> e.score } }.orEmpty()
    val avg = if (valued.isEmpty()) null else valued.average().toInt()
    val book = m.ongoing.firstOrNull { it.kind.isBook }
    val now = m.ongoing.firstOrNull()
    val month = LocalDate.now()
    val doneMonth = m.kinds.sumOf { k -> m.entries(k).count { e -> e.game != null && e.game.end > 0 && LocalDate.ofEpochDay(e.game.end).let { it.year == month.year && it.month == month.month } } }
    Column(Modifier.fillMaxSize().background(Desk).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hola,", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
                Text(m.name, color = Color.White, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 30.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (m.profile != null) Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).press { m.profile.invoke() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, "Perfil", tint = Color.White)
            }
        }
        ShellSwitcher(Color.White, Color.White.copy(alpha = 0.1f))

        // Fila 1: lo de ahora (grande) + nota media
        Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(2f).fillMaxHeight(), listOf(Color(0xFFFF6B3D), Color(0xFFB0306A)), onClick = { now?.let { m.open(ShellEntry(it.kind, it.play, null)) } }) {
                if (now != null) {
                    val art = now.play.hero.ifBlank { now.play.cover }
                    if (art.isNotBlank()) AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        TileLabel("AHORA")
                        Spacer(Modifier.weight(1f))
                        Text(now.play.name, color = Color.White, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!m.readOnly) {
                            val (label, icon) = m.primaryLabel(now)
                            Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).background(Color.White).press { m.primary(now) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = Color.Black, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp))
                                Text(label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else Column(Modifier.padding(16.dp)) { TileLabel("AHORA"); Spacer(Modifier.height(8.dp)); Text("Nada entre manos", color = Color.White, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
            }
            Tile(Modifier.weight(1f).fillMaxHeight(), listOf(Color(0xFF2E3BFF), Color(0xFF7A2BE2)), onClick = { games?.let { m.stats(it) } }) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    TileLabel("MEDIA")
                    Spacer(Modifier.weight(1f))
                    Text(avg?.toString() ?: "–", color = Color.White, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 56.sp)
                    Text("${valued.size} juegos", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
            }
        }

        // Fila 2: lectura + diario
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f).fillMaxHeight(), listOf(Color(0xFF14B88A), Color(0xFF0B6E73)), onClick = { book?.let { m.open(ShellEntry(it.kind, it.play, null)) } ?: m.kinds.firstOrNull { it.isBook }?.let { m.library(it) } }) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    TileLabel("LEYENDO")
                    Spacer(Modifier.weight(1f))
                    if (book != null) {
                        val pr = BookFiles.progress(ctx, book.play.id)
                        Text(if (pr > 0f) "${(pr * 100).toInt()} %" else "—", color = Color.White, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                        Text(book.play.name, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else Text("Ningún libro", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Tile(Modifier.weight(1f).fillMaxHeight(), listOf(Color(0xFFFFC23D), Color(0xFFFF8A00)), onClick = m.diary) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    TileLabel("DIARIO", Color.Black)
                    Spacer(Modifier.weight(1f))
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM", Locale("es", "ES"))).replaceFirstChar { it.uppercase() }, color = Color.Black, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    Text("$doneMonth terminados este mes", color = Color.Black.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }

        // Fila 3: el PC
        if (m.paired) m.live()

        // Fila 4: las colecciones, en bloques
        val palette = listOf(
            listOf(Color(0xFF3A3F58), Color(0xFF23263A)), listOf(Color(0xFF6C2BD9), Color(0xFF3B1B86)),
            listOf(Color(0xFFE0245E), Color(0xFF8E1240)), listOf(Color(0xFF0091FF), Color(0xFF0053A6))
        )
        m.kinds.chunked(2).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { c, k ->
                    val list = m.feed(k)
                    Tile(Modifier.weight(1f).fillMaxHeight(), palette[(r * 2 + c) % palette.size], onClick = { m.library(k) }) {
                        Row(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy((-26).dp)) {
                            list.mapNotNull { it.cover.ifBlank { null } }.take(3).forEach {
                                AsyncImage(it, null, Modifier.width(48.dp).aspectRatio(0.68f).shadow(10.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            }
                        }
                        Column(Modifier.fillMaxSize().padding(14.dp)) {
                            TileLabel(kindShort(k).uppercase())
                            Spacer(Modifier.weight(1f))
                            Text("${list.size}", color = Color.White, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // Fila 5: descubrir + pareja
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (m.discover != null) Tile(Modifier.weight(1f).fillMaxHeight(), listOf(Color(0xFFFF4FD8), Color(0xFF5B5BFF)), onClick = { m.discover.invoke(2) }) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text("Descubrir", color = Color.White, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            if (m.partner != null) Tile(Modifier.weight(1f).fillMaxHeight(), listOf(Color(0xFFFF3B5C), Color(0xFFFF7A59)), onClick = { m.partner.invoke() }) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Icon(Icons.Rounded.Favorite, null, tint = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text(if (m.readOnly) "Volver a lo mío" else (m.partnerName ?: "Pareja"), color = Color.White, fontFamily = VitrinaDisplay, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Tile(modifier: Modifier, colors: List<Color>, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(colors)).press(onClick = onClick), content = content)
}

@Composable
private fun TileLabel(t: String, c: Color = Color.White) =
    Text(t, color = c.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
