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
import androidx.compose.foundation.horizontalScroll
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
 * La portada de la interfaz Revista: papel, tipografía enorme, las secciones arriba como el menú de una web,
 * «En portada» (la única imagen de la app, que va pasando por lo que llevas entre manos) y listas numeradas.
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
    val pickImage: ((Ongoing) -> Unit)? = null,
    /** La página del PC (en directo, biblioteca, capturas y equipo). */
    val pc: (() -> Unit)? = null
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

/** La entrada de la portada se anima solo la primera vez que se abre la app (no al volver de una ficha). */
private object RevistaIntro { var done = false }

@Composable
private fun RevistaHome(m: ShellModel) {
    val today = LocalDate.now()
    val all = m.kinds.flatMap { m.entries(it) }
    val best = all.filter { it.score != null }.sortedByDescending { it.score }.take(5)
    // Al abrir la app: las piezas de la portada llegan una tras otra, en muy poco tiempo, en lugar de todo de golpe
    val intro = remember { !RevistaIntro.done }
    LaunchedEffect(Unit) { RevistaIntro.done = true }
    fun Modifier.llega(n: Int) = appear(n * 110, intro)
    Column(Modifier.fillMaxSize().background(Paper2).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).llega(0), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (m.partner != null) RevLink("Pareja", m.partner)
            if (m.profile != null) RevLink("Perfil", m.profile)
        }
        // Tableta (OPPO Pad Mini…): con sitio de sobra, cabecera más grande y «En portada» a dos columnas
        val ancho = minOf(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp, 1080)
        val ancha = ancho >= 900
        // Cabecera de revista
        Column(Modifier.llega(0)) {
            Text("ATRIO", color = Ink2, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = if (ancho >= 700) 132.sp else 92.sp, lineHeight = if (ancho >= 700) 132.sp else 92.sp, letterSpacing = (-3).sp, modifier = Modifier.padding(top = 8.dp))
            Rule(3.dp)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Nº ${today.dayOfYear}", color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text(today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES"))).uppercase(), color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Rule(1.dp)
        }
        Text(if (m.readOnly) "La colección de ${m.name}" else "La colección de ${m.name}, al día", color = Grey2, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp).llega(1))

        Box(Modifier.llega(1)) { RevistaLectura(all.filter { it.kind.isBook }) }

        // Las secciones de la revista, como el menú de una web: sin título, justo antes de la portada
        Box(Modifier.llega(2)) { RevistaMenu(m) }

        // En portada: va pasando por lo que llevas entre manos ahora mismo (leyendo, jugando, viendo), con un fundido
        val ongoing = m.ongoing
        var turn by remember { mutableStateOf(0) }
        val ctx = LocalContext.current
        // Las imágenes de todas, cargadas de antemano: al cambiar no se queda el hueco en blanco
        LaunchedEffect(ongoing.map { it.play.id }) {
            ongoing.forEach { o ->
                val art = o.play.hero.ifBlank { o.play.cover }
                if (art.isNotBlank()) coil.Coil.imageLoader(ctx).enqueue(coil.request.ImageRequest.Builder(ctx).data(art).build())
            }
        }
        LaunchedEffect(ongoing.size) {
            if (ongoing.size < 2) return@LaunchedEffect
            while (true) { kotlinx.coroutines.delay(7_000); turn++ }
        }
        val front = ongoing.getOrNull(if (ongoing.isEmpty()) 0 else turn % ongoing.size)
        if (front != null) Column(Modifier.llega(3)) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RevSection("En portada")
                Spacer(Modifier.weight(1f))
                // Puntos: cuántas hay y cuál se ve (tocar uno la pone)
                if (ongoing.size > 1) Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ongoing.forEachIndexed { i, o ->
                        Box(Modifier.size(if (o === front) 8.dp else 6.dp).clip(CircleShape).background(if (o === front) Red2 else Grey2.copy(alpha = 0.5f))
                            .press(haptic = false) { turn = i })
                    }
                }
            }
            AnimatedContent(
                front,
                transitionSpec = { fadeIn(tween(Motion.ms(650))) togetherWith fadeOut(tween(Motion.ms(450))) },
                contentKey = { it.play.id }, label = "portada"
            ) { o ->
                val e = ShellEntry(o.kind, o.play, null)
                val art = e.hero.ifBlank { e.cover }
                val imagen: @Composable (Modifier) -> Unit = { mod ->
                    if (art.isNotBlank()) AsyncImage(art, e.name, mod.press { m.open(e) }, contentScale = ContentScale.Crop)
                    else Box(mod.background(Sand).press { m.open(e) }, contentAlignment = Alignment.Center) {
                        Text("Sin imagen", color = Grey2, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
                    }
                }
                val textos: @Composable ColumnScope.() -> Unit = {
                    if (!m.readOnly && m.pickImage != null) Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("CAMBIAR IMAGEN", color = Ink2, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press { m.pickImage.invoke(o) }.padding(vertical = 4.dp))
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
                if (ancha) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        imagen(Modifier.weight(1.35f).aspectRatio(1.45f))
                        Spacer(Modifier.width(28.dp))
                        Column(Modifier.weight(1f), content = textos)
                    }
                } else Column(Modifier.fillMaxWidth()) {
                    imagen(Modifier.fillMaxWidth().aspectRatio(if (ancho >= 700) 1.6f else 1.35f))
                    textos()
                }
            }
        }

        // Ahora mismo: todo lo que llevas entre manos, en lista numerada (fija: no cambia con la portada)
        if (ongoing.size > 1) Column(Modifier.llega(4)) {
            Spacer(Modifier.height(28.dp))
            RevSection("Ahora mismo")
            ongoing.take(8).forEachIndexed { i, o ->
                val e = ShellEntry(o.kind, o.play, null)
                Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 10.dp), verticalAlignment = if (Skin.listCovers) Alignment.CenterVertically else Alignment.Top) {
                    Text("%02d".format(i + 1), color = Red2, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.width(56.dp))
                    if (Skin.listCovers) { ListCover(e.cover, e.name, 52.dp, 74.dp); Spacer(Modifier.width(14.dp)) }
                    Column(Modifier.weight(1f)) {
                        FitTitle(e.name, 22.sp, 25.sp, color = Ink2, fontWeight = FontWeight.SemiBold)
                        Text(listOf(kindShort(e.kind), e.creator).filter { it.isNotBlank() }.joinToString(" · "), color = Grey2, fontSize = 13.sp)
                    }
                }
                Rule(1.dp, Line)
            }
        }

        // Las notas más altas: números gigantes
        if (best.isNotEmpty()) Column(Modifier.llega(5)) {
            Spacer(Modifier.height(28.dp))
            RevSection("Las notas más altas")
            // La columna de los números, tan ancha como la nota más ancha (un 100 no cabía y se partía en «10» y «0»)
            val numStyle = androidx.compose.ui.text.TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 58.sp)
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val density = androidx.compose.ui.platform.LocalDensity.current
            val numW = with(density) { best.maxOf { measurer.measure("${it.score}", numStyle).size.width }.toDp() } + 16.dp
            best.forEach { e ->
                Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.score}", color = Ink2, style = numStyle, maxLines = 1, softWrap = false, modifier = Modifier.width(maxOf(numW, 100.dp)))
                    if (Skin.listCovers) { ListCover(e.cover, e.name, 52.dp, 74.dp); Spacer(Modifier.width(14.dp)) }
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

        Spacer(Modifier.height(40.dp))
        Text("Atrio · hecho en casa", color = Grey2, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
    }
}

/** Las secciones (cada categoría, Diario, Estadísticas y PC): una fila entre filetes, como el menú de una web. */
@Composable
private fun RevistaMenu(m: ShellModel) {
    val items = m.kinds.map { k -> kindShort(k) to { m.library(k) } } +
        listOf("Diario" to m.diary, "Estadísticas" to { m.stats(GAMES) }) +
        listOfNotNull(m.pc?.let { "PC" to it })
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Rule(1.dp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, (t, f) ->
                Text(t, color = Ink2, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.press(haptic = false, onClick = f).padding(start = if (i == 0) 0.dp else 9.dp, end = 9.dp, top = 10.dp, bottom = 10.dp))
                if (i < items.size - 1) Box(Modifier.size(4.dp).background(Red2))
            }
        }
        Rule(1.dp)
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
