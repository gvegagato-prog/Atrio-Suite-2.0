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
 * La portada de la interfaz Revista: papel, tipografía enorme, «En portada» (la única imagen de la app, que eliges
 * tú), listas numeradas y un sumario que hace de índice de toda la app.
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
    val live: @Composable () -> Unit,
    /** Elegir la imagen de «En portada» (SteamGridDB, TMDB, TVmaze u Open Library, según lo que sea). */
    val pickImage: ((Ongoing) -> Unit)? = null
)

/** El orden de siempre: lo que llevas entre manos, lo apuntado y lo terminado (lo último primero). */
fun ShellModel.feed(k: Kind): List<ShellEntry> = entries(k).sortedWith(
    compareBy<ShellEntry> { when (it.play?.status) { 1 -> 0; 0 -> 1; null -> 2; else -> 3 } }.thenByDescending { it.activity }
)

@Composable
fun ShellHome(m: ShellModel) {
    RevistaHome(m)
}

private fun kindShort(k: Kind) = when { k.isMovie -> "Pelis"; k.isTv -> "Series"; k.isGame -> "Juegos"; k.isBook -> "Libros"; else -> k.title }

// ================================================================ 2 · REVISTA

private val Paper2: Color get() = Paper
private val Ink2: Color get() = Charcoal
private val Red2: Color get() = ColGeneral
private val Grey2: Color get() = Muted

@Composable
private fun RevistaHome(m: ShellModel) {
    val today = LocalDate.now()
    val all = m.kinds.flatMap { m.entries(it) }
    val best = all.filter { it.score != null }.sortedByDescending { it.score }.take(5)
    Column(Modifier.fillMaxSize().background(Paper2).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (m.partner != null) RevLink("Pareja", m.partner)
            if (m.profile != null) RevLink("Perfil", m.profile)
        }
        // Cabecera de revista
        Text("ATRIO", color = Ink2, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 92.sp, lineHeight = 92.sp, letterSpacing = (-3).sp, modifier = Modifier.padding(top = 8.dp))
        Rule(3.dp)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Nº ${today.dayOfYear}", color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES"))).uppercase(), color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Rule(1.dp)
        Text(if (m.readOnly) "La colección de ${m.name}" else "La colección de ${m.name}, al día", color = Grey2, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))

        RevistaLectura(all.filter { it.kind.isBook })
        if (m.paired) { Spacer(Modifier.height(16.dp)); m.live() }

        // En portada: lo que elijas de lo que llevas entre manos (si no, lo primero). Es la única imagen de la app.
        val ctx = LocalContext.current
        val prefs = remember { ctx.getSharedPreferences("gamerate", android.content.Context.MODE_PRIVATE) }
        var coverId by remember { mutableStateOf(prefs.getLong("portada_id", 0L)) }
        var choosing by remember { mutableStateOf(false) }
        val front = m.ongoing.firstOrNull { it.play.id == coverId } ?: m.ongoing.firstOrNull()
        val rest = m.ongoing.filter { it !== front }
        front?.let { o ->
            val e = ShellEntry(o.kind, o.play, null)
            Spacer(Modifier.height(22.dp))
            RevSection("En portada")
            val art = e.hero.ifBlank { e.cover }
            if (art.isNotBlank()) AsyncImage(art, e.name, Modifier.fillMaxWidth().aspectRatio(1.35f).press { m.open(e) }, contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxWidth().aspectRatio(1.35f).background(Sand).press { m.open(e) }, contentAlignment = Alignment.Center) {
                Text("Sin imagen", color = Grey2, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
            }
            if (!m.readOnly) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                if (m.pickImage != null) Text("CAMBIAR IMAGEN", color = Ink2, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press { m.pickImage.invoke(o) }.padding(vertical = 4.dp))
                if (m.ongoing.size > 1) Text("CAMBIAR TÍTULO", color = Ink2, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press { choosing = true }.padding(vertical = 4.dp))
            }
            val (fs, lh) = headlineSize(e.name)
            FitTitle(e.name, fs, lh, color = Ink2, letterSpacing = (-1.5).sp, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).press { m.open(e) })
            Text(
                listOf(kindShort(e.kind), e.creator, if (e.minutes > 0 && !e.kind.isBook) hoursText(e.minutes) else "").filter { it.isNotBlank() }.joinToString(" · "),
                color = Grey2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)
            )
            if (!m.readOnly) {
                val (label, _) = m.primaryLabel(o)
                Text("$label →", color = Red2, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp).press { m.primary(o) })
            }
        }
        if (choosing) androidx.compose.material3.AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("¿Qué va en portada?", fontFamily = Display) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    m.ongoing.forEach { o ->
                        Text(o.play.name, fontFamily = Display, fontSize = 20.sp, fontWeight = if (o === front) FontWeight.Bold else FontWeight.Normal,
                            color = if (o === front) Red2 else Ink2,
                            modifier = Modifier.fillMaxWidth().press { coverId = o.play.id; prefs.edit().putLong("portada_id", o.play.id).apply(); choosing = false }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton({ choosing = false }) { Text("Cerrar") } },
            containerColor = Paper2, shape = RoundedCornerShape(4.dp)
        )

        // Ahora mismo: lista numerada
        if (rest.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            RevSection("Ahora mismo")
            rest.take(8).forEachIndexed { i, o ->
                val e = ShellEntry(o.kind, o.play, null)
                Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Text("%02d".format(i + 1), color = Red2, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.width(56.dp))
                    Column(Modifier.weight(1f)) {
                        FitTitle(e.name, 22.sp, 25.sp, color = Ink2, fontWeight = FontWeight.SemiBold)
                        Text(listOf(kindShort(e.kind), e.creator).filter { it.isNotBlank() }.joinToString(" · "), color = Grey2, fontSize = 13.sp)
                    }
                }
                Rule(1.dp, Line)
            }
        }

        // Las notas más altas: números gigantes
        if (best.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            RevSection("Las notas más altas")
            best.forEach { e ->
                Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.score}", color = Ink2, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 58.sp, modifier = Modifier.width(100.dp))
                    Column(Modifier.weight(1f)) {
                        FitTitle(e.name, 20.sp, 23.sp, color = Ink2, fontWeight = FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(kindShort(e.kind).uppercase(), color = Red2, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    }
                }
            }
        }

        // La cita (un subrayado tuyo, distinto cada día) y lo que terminaste tal día como hoy otros años
        RevistaCita(all.filter { it.kind.isBook })
        RevistaEfemerides(all) { m.open(it) }

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
        var especial by remember { mutableStateOf(false) }
        SumarioRow("Tu ${today.year}, número especial", "★") { especial = true }
        if (especial) NumeroEspecial(m.name, m.kinds, m.entries, onOpen = { e -> especial = false; m.open(e) }) { especial = false }
        Spacer(Modifier.height(40.dp))
        Text("Atrio · hecho en casa", color = Grey2, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Rule(h: androidx.compose.ui.unit.Dp, c: Color = Charcoal) = Box(Modifier.fillMaxWidth().height(h).background(c))

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
        Text(t, color = Ink2, fontFamily = Display, fontSize = 22.sp)
        // Puntos guía, como el índice de una revista
        Box(Modifier.weight(1f).height(16.dp).padding(horizontal = 8.dp).drawBehind {
            drawLine(Ink2.copy(alpha = 0.45f), Offset(0f, size.height - 3), Offset(size.width, size.height - 3), 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 7f)))
        })
        Text(page, color = Red2, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
