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
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.focus.focusRequester
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
/** Algo que llevas entre manos (jugando, leyendo, viendo), con su categoría. */
class Ongoing(val kind: Kind, val play: Play)

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
    /** Escribir (obras, capítulos, fichas…). */
    val write: (() -> Unit)? = null,
    val discover: ((Int) -> Unit)?,
    val profile: (() -> Unit)?,
    val add: ((Kind) -> Unit)?,
    val partner: (() -> Unit)?,
    val stats: (Kind) -> Unit,
    /** Elegir la imagen de «En portada» (SteamGridDB, TMDB, TVmaze u Open Library, según lo que sea). */
    val pickImage: ((Ongoing) -> Unit)? = null,
    /** La página del PC (en directo, biblioteca, capturas y equipo). */
    val pc: (() -> Unit)? = null,
    /** Lo que te recomienda tu pareja (añadir a tu lista o descartar) y lo último que ha hecho. */
    val recs: List<Rec> = emptyList(),
    val onRecAdd: (Rec) -> Unit = {},
    val onRecDismiss: (Rec) -> Unit = {},
    val acts: List<Activity> = emptyList(),
    val onAct: (Activity) -> Unit = {}
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
    Column(Modifier.fillMaxSize().background(Paper2).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        var buscando by remember { mutableStateOf(false) }
        if (buscando) BuscadorGeneral(m) { buscando = false }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).llega(0), verticalAlignment = Alignment.CenterVertically) {
            EnlaceRevista("Buscar") { buscando = true }
            Spacer(Modifier.weight(1f))
            if (m.partner != null) RevLink("Pareja", m.partner)
        }
        // Tableta (OPPO Pad Mini…): con sitio de sobra, cabecera más grande y «En portada» a dos columnas
        val ancho = minOf(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp, 1080)
        val ancha = ancho >= 900
        // Cabecera de revista
        Column(Modifier.llega(0)) {
            Cabecera(if (ancho >= 700) 132.sp else 92.sp, Modifier.padding(top = 8.dp), color = Ink2)
            FileteCabecera(3.dp)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Nº ${today.dayOfYear}", color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text(today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES"))).uppercase(), color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Rule(1.dp)
        }
        Box(Modifier.llega(1)) { RevistaLectura(all.filter { it.kind.isBook }) }

        // Las secciones de la revista, como el menú de una web: sin título, justo antes de la portada
        Box(Modifier.llega(2)) { RevistaMenu(m) }

        // En portada: lo que llevas entre manos ahora mismo (leyendo, jugando, viendo). Se desliza con el dedo entre
        // todas y, si no la tocas, pasa sola a la siguiente cada 7 s (después de deslizar, la cuenta vuelve a empezar)
        val ongoing = m.ongoing
        val ctx = LocalContext.current
        // Las imágenes de todas, cargadas de antemano: al cambiar no se queda el hueco en blanco
        LaunchedEffect(ongoing.map { it.play.id }) {
            ongoing.forEach { o ->
                val art = o.play.hero.ifBlank { o.play.cover }
                if (art.isNotBlank()) coil.Coil.imageLoader(ctx).enqueue(coil.request.ImageRequest.Builder(ctx).data(art).build())
            }
        }
        val n = ongoing.size
        // Muchas páginas «virtuales» que dan la vuelta: de la última se pasa a la primera hacia delante, sin rebobinar
        val vueltas = if (n > 1) n * 1000 else n
        val pager = androidx.compose.runtime.key(n) { rememberPagerState(initialPage = if (n > 1) n * 500 else 0) { vueltas } }
        LaunchedEffect(pager.settledPage, n) {
            if (n < 2) return@LaunchedEffect
            kotlinx.coroutines.delay(7_000)
            if (!pager.isScrollInProgress) pager.animateScrollToPage(pager.settledPage + 1, animationSpec = tween(Motion.ms(700)))
        }
        val actual = if (n > 0) pager.currentPage % n else 0
        val scope = rememberCoroutineScope()
        if (n > 0) Spacer(Modifier.height(30.dp))
        // La portada va enmarcada: un cuadrado del color del texto alrededor de todo
        if (n > 0) Column(Modifier.llega(3).fillMaxWidth().border(1.5.dp, Ink2).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RevSection("En portada")
                Spacer(Modifier.weight(1f))
                // Puntos: cuántas hay y cuál se ve (tocar uno la pone)
                if (n > 1) Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ongoing.forEachIndexed { i, _ ->
                        Box(Modifier.size(if (i == actual) 8.dp else 6.dp).clip(CircleShape).background(if (i == actual) Red2 else Grey2.copy(alpha = 0.5f))
                            .press(haptic = false) { scope.launch { pager.animateScrollToPage(pager.currentPage - actual + i) } })
                    }
                }
            }
            // Se componen todas las páginas a la vez: el marco mide lo de la más alta y no cambia de tamaño al pasar de una a otra.
            // Para que ninguna deje hueco abajo, se apunta lo que mide el texto de cada una y, en las que tienen menos (un
            // titular más corto), la imagen crece eso mismo: todas quedan llenas, con el texto siempre pegado abajo
            val textoAlto = remember(ongoing.map { it.play.id }) { mutableStateMapOf<Long, Int>() }
            val maxTexto = textoAlto.values.maxOrNull() ?: 0
            val densidad = androidx.compose.ui.platform.LocalDensity.current
            HorizontalPager(pager, Modifier.fillMaxWidth().testTag("portada"), pageSpacing = 18.dp, verticalAlignment = Alignment.Top,
                beyondViewportPageCount = (n - 1).coerceIn(0, 6),
                key = { p -> if (n > 1) p else ongoing.getOrNull(p)?.play?.id ?: p }) { page ->
                val o = ongoing[page % n]
                val e = ShellEntry(o.kind, o.play, null)
                /** Lo que le falta al texto de esta portada para medir lo del más alto (lo que crece su imagen). */
                fun hueco(id: Long) = with(densidad) { (maxTexto - (textoAlto[id] ?: maxTexto)).coerceAtLeast(0).toDp() }
                val art = e.hero.ifBlank { e.cover }
                val imagen: @Composable (Modifier) -> Unit = { mod ->
                    // Sin fondo propio, la portada (vertical) no se recorta: va entera sobre sí misma, desenfocada
                    if (art.isNotBlank() && e.hero.isBlank()) Box(mod.clipToBounds().press { m.open(e) }, contentAlignment = Alignment.Center) {
                        AsyncImage(art, null, Modifier.fillMaxSize().blur(24.dp), contentScale = ContentScale.Crop, colorFilter = Ediciones.filtro)
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
                        AsyncImage(art, e.name, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit, colorFilter = Ediciones.filtro)
                    }
                    else if (art.isNotBlank()) AsyncImage(art, e.name, mod.press { m.open(e) }, contentScale = ContentScale.Crop, colorFilter = Ediciones.filtro)
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
                        Text("$label\u00A0→", color = Red2, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp).press { m.primary(o) })
                    }
                }
                val ed = Ediciones.actual
                val (fs0, lh0) = headlineSize(e.name)
                val meta = listOf(kindShort(e.kind), e.creator, if (e.minutes > 0 && !e.kind.isBook) hoursText(e.minutes) else "").filter { it.isNotBlank() }.joinToString(" · ")
                val cambiar: @Composable () -> Unit = {
                    if (!m.readOnly && m.pickImage != null) Text("CAMBIAR IMAGEN", color = Grey2, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(top = 6.dp).press { m.pickImage.invoke(o) }.padding(vertical = 4.dp))
                }
                when {
                    // Moda, viajes, cine: la foto manda y el titular va encima, en blanco, sobre un degradado
                    ed.portada == Portada.SOBRE_FOTO -> Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().aspectRatio(if (ancha) 2.1f else if (ancho >= 700) 1.3f else 0.86f)) {
                            imagen(Modifier.matchParentSize())
                            Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.42f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
                            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                                FitTitle(e.name, fs0, lh0, color = Color.White, letterSpacing = (-1.5).sp, modifier = Modifier.fillMaxWidth().press { m.open(e) })
                                Text(meta, color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                                if (!m.readOnly) Text("${m.primaryLabel(o).first}\u00A0→", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                    modifier = Modifier.padding(top = 10.dp).press { m.primary(o) })
                            }
                        }
                        cambiar()
                    }
                    // Popular, música: el titular en un bloque del color de la edición, bajo la foto
                    ed.portada == Portada.BLOQUE -> Column(Modifier.fillMaxWidth()) {
                        val ratio = if (ancha) 2.4f else if (ancho >= 700) 1.7f else 1.3f
                        BoxWithConstraints(Modifier.fillMaxWidth()) { imagen(Modifier.fillMaxWidth().height(maxWidth / ratio + hueco(e.id))) }
                        val sobreColor = sobre(Red2, Paper2, Ink2)
                        Column(Modifier.fillMaxWidth().onSizeChanged { textoAlto[e.id] = it.height }.background(Red2).padding(horizontal = 16.dp, vertical = 14.dp)) {
                            FitTitle(e.name, fs0, lh0, color = sobreColor, letterSpacing = (-1).sp, modifier = Modifier.fillMaxWidth().press { m.open(e) })
                            Text(meta, color = sobreColor.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                            if (!m.readOnly) Text("${m.primaryLabel(o).first.uppercase()}\u00A0→", color = sobreColor, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 10.dp).press { m.primary(o) })
                        }
                        cambiar()
                    }
                    // En horizontal (foto a un lado, texto al otro): todas igual de altas y las dos columnas centradas
                    ancha -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val altoFoto = (maxWidth - 28.dp) * (1.35f / 2.35f) / 1.45f
                        val alto = maxOf(altoFoto, with(densidad) { maxTexto.toDp() })
                        Row(Modifier.fillMaxWidth().height(alto), verticalAlignment = Alignment.CenterVertically) {
                            imagen(Modifier.weight(1.35f).aspectRatio(1.45f))
                            Spacer(Modifier.width(28.dp))
                            Column(Modifier.weight(1f).onSizeChanged { textoAlto[e.id] = it.height }, content = textos)
                        }
                    }
                    else -> Column(Modifier.fillMaxWidth()) {
                        val ratio = if (ancho >= 700) 1.6f else 1.35f
                        BoxWithConstraints(Modifier.fillMaxWidth()) { imagen(Modifier.fillMaxWidth().height(maxWidth / ratio + hueco(e.id))) }
                        Column(Modifier.fillMaxWidth().onSizeChanged { textoAlto[e.id] = it.height }, content = textos)
                    }
                }
            }
        }

        // Ahora mismo: todo lo que llevas entre manos, en lista numerada (fija: no cambia con la portada)
        if (ongoing.size > 1) Column(Modifier.llega(4)) {
            Spacer(Modifier.height(38.dp))
            RevSection("Ahora mismo")
            ongoing.take(8).forEachIndexed { i, o ->
                val e = ShellEntry(o.kind, o.play, null)
                Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 14.dp), verticalAlignment = if (Skin.listCovers) Alignment.CenterVertically else Alignment.Top) {
                    Text("%02d".format(i + 1), color = Red2, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.width(56.dp))
                    if (Skin.listCovers) { ListCover(e.cover, e.name, 52.dp, 74.dp); Spacer(Modifier.width(14.dp)) }
                    Column(Modifier.weight(1f)) {
                        FitTitle(e.name, 22.sp, 25.sp, color = Ink2, fontWeight = FontWeight.SemiBold)
                        // (los libros con su archivo: por dónde vas)
                        val pct = if (e.kind.isBook && BookFiles.get(ctx, e.id) != null) (BookFiles.progress(ctx, e.id) * 100).toInt() else 0
                        Text(listOf(kindShort(e.kind), e.creator, if (pct in 1..99) "$pct %" else "").filter { it.isNotBlank() }.joinToString(" · "), color = Grey2, fontSize = 13.sp)
                    }
                }
                Rule(1.dp, Line)
            }
        }

        // Escribiendo: tu novela, lo de hoy y seguir con la última escena
        if (!m.readOnly && m.write != null) RevistaEscribiendo(m.write)

        // Tu pareja: lo que te recomienda y lo último que ha hecho
        RevistaPareja(m)

        // Próximos episodios de las series que ves o tienes en lista (los de las dos próximas semanas)
        Box(Modifier.llega(4)) { RevistaProximos(m) }

        // Las notas más altas: números gigantes
        if (best.isNotEmpty()) Column(Modifier.llega(5)) {
            Spacer(Modifier.height(38.dp))
            RevSection("Las notas más altas")
            // La columna de los números, tan ancha como la nota más ancha (un 100 no cabía y se partía en «10» y «0»)
            val numStyle = androidx.compose.ui.text.TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 58.sp, lineHeight = 58.sp)
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val density = androidx.compose.ui.platform.LocalDensity.current
            val numW = with(density) { best.maxOf { measurer.measure("${it.score}", numStyle).size.width }.toDp() } + 16.dp
            best.forEach { e ->
                Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.score}", color = ColorCifras, style = numStyle, maxLines = 1, softWrap = false, modifier = Modifier.width(maxOf(numW, 100.dp)))
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
        Text(Ediciones.actual.pie, color = Grey2, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
    }
}

/** «X te recomienda» (a tu lista o descartar) y «Lo último de X»: lo de tu pareja, en la portada. */
@Composable
private fun RevistaPareja(m: ShellModel) {
    val nombre = m.partnerName ?: return
    if (m.recs.isNotEmpty() && !m.readOnly) {
        Spacer(Modifier.height(38.dp))
        RevSection("$nombre te recomienda")
        m.recs.take(6).forEach { r ->
            val k = kindOf(r.kind)
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (Skin.listCovers) { ListCover(r.cover, r.name, 44.dp, 62.dp); Spacer(Modifier.width(12.dp)) }
                    Column(Modifier.weight(1f)) {
                        FitTitle(r.name, 22.sp, 25.sp, color = Ink2, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOf(kindShort(k), r.creator).filter { it.isNotBlank() }.joinToString(" · "), color = Grey2, fontSize = 13.sp)
                    }
                }
                if (r.note.isNotBlank()) Text("«${r.note}»", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 21.sp, color = Ink2, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    EnlaceRevista("A mi lista →", acento = true) { m.onRecAdd(r) }
                    EnlaceRevista("Descartar") { m.onRecDismiss(r) }
                }
            }
            Rule(1.dp, Line)
        }
    }
    if (m.acts.isNotEmpty()) {
        Spacer(Modifier.height(38.dp))
        RevSection(if (m.readOnly) "Lo último" else "Lo último de $nombre")
        m.acts.take(6).forEach { a ->
            Row(Modifier.fillMaxWidth().press(haptic = false) { m.onAct(a) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (Skin.listCovers) { ListCover(a.cover, a.name, 40.dp, 56.dp); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                    FitTitle(a.name, 19.sp, 22.sp, color = Ink2, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(kindShort(kindOf(a.kind)), a.text).joinToString(" · ").uppercase(), color = Red2, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
            }
            Rule(1.dp, Line)
        }
    }
}

/** «Escribiendo» en la portada: la obra en la que estás, lo escrito hoy y seguir con la última escena. */
@Composable
private fun RevistaEscribiendo(irAEscribir: () -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    val ver = e.version
    val s = remember(ver) { e.ultimaEscena() } ?: return
    val obra = e.get(s.pid) ?: return
    val hoy = remember(ver) { e.escritas(Escritos.hoy()) }
    val racha = remember(ver) { e.racha() }
    val total = remember(ver, obra.id) { e.palabrasObra(obra.id) }
    val meta = obra.i("meta")
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(38.dp))
        RevSection("Escribiendo")
        FitTitle(obra.s("titulo").ifBlank { "Sin título" }, 30.sp, 33.sp, color = Ink2, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().press(haptic = false, onClick = irAEscribir))
        Text(listOfNotNull(obra.s("genero").ifBlank { null }, cifraE(total) + (if (meta > 0) " de ${cifraE(meta)} palabras" else " palabras"), if (hoy > 0) "hoy ${cifraE(hoy)}" else null, if (racha > 1) "$racha días seguidos" else null).joinToString(" · "),
            color = Grey2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        if (meta > 0) Box(Modifier.padding(top = 8.dp)) { Bar((total.toFloat() / meta).coerceIn(0f, 1f), Red2, height = 3.dp) }
        Text("Seguir con «${s.s("titulo").ifBlank { "Sin título" }}»\u00A0→", color = Red2, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.padding(top = 10.dp).press { EscribirHub.abrir = s.id to System.currentTimeMillis(); irAEscribir() })
    }
}

/**
 * Las secciones, como el menú del PC y en el mismo orden: cada categoría (con su nombre), Diario, Escribir, Descubrir,
 * Cifras, Tu PC y Ajustes. En versalitas y en las líneas que hagan falta (todo a la vista, sin deslizar).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RevistaMenu(m: ShellModel) {
    val items = m.kinds.map { k -> k.title to { m.library(k) } } +
        listOfNotNull("Diario" to m.diary, m.write?.let { "Escribir" to it }, m.discover?.let { d -> "Descubrir" to { d(0) } }, "Cifras" to { m.stats(GAMES) },
            m.pc?.let { "Tu PC" to it }, m.profile?.let { "Ajustes" to it })
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Rule(1.dp)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items.forEach { (t, f) ->
                Text(t.uppercase(), color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1,
                    modifier = Modifier.press(haptic = false, onClick = f).padding(vertical = 9.dp))
            }
        }
        Rule(1.dp)
    }
}

@Composable
private fun Rule(h: androidx.compose.ui.unit.Dp, c: Color = Charcoal) = Filete(h, c)

@Composable
private fun RevSection(t: String) {
    Antetitulo(t, Modifier.padding(bottom = 10.dp), color = Ink2)
}

@Composable
private fun RevLink(t: String, f: () -> Unit) = EnlaceRevista(t, Modifier.padding(start = 14.dp), onClick = f)

/**
 * Buscar en todo lo tuyo desde la portada: juegos, libros, pelis, series y tus categorías a la vez (sin fijarse en
 * tildes), y también en lo que has subrayado en tus libros (tocar una cita abre el libro justo ahí).
 */
@Composable
private fun BuscadorGeneral(m: ShellModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val todo = remember(m.kinds) { m.kinds.flatMap { k -> m.entries(k) } }
    val libros = remember(todo) { todo.filter { it.kind.isBook }.distinctBy { it.id } }
    // Las citas de tus libros (con el lector), para buscar también en ellas
    val citas = remember(libros) { libros.flatMap { e -> bookNotes(ctx, e.id).map { e to it } } }
    val res = remember(q, todo) { if (q.isBlank()) emptyList() else todo.filter { libMatches(q, it.name, it.creator) }.sortedByDescending { it.activity }.take(80) }
    val resCitas = remember(q, citas) { if (q.trim().length < 3) emptyList() else citas.filter { (_, n) -> libMatches(q, n.text, n.note) }.take(40) }
    // Y en lo que has escrito en tu diario
    val resNotas = remember(q, DiarioHub.notas) { if (q.trim().length < 3 || m.readOnly) emptyList() else DiarioHub.notas.filter { libMatches(q, it.texto, "") }.sortedByDescending { it.dia }.take(30) }
    val recientes = remember(todo) { todo.sortedByDescending { it.activity }.distinctBy { it.kind.id to it.id }.take(8) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val foco = remember { androidx.compose.ui.focus.FocusRequester() }
        Column(Modifier.fillMaxSize().background(Paper2).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Buscar en todo", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Ink2, modifier = Modifier.weight(1f))
                Text("CERRAR", color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press { onClose() }.padding(8.dp))
            }
            Rule(3.dp)
            androidx.compose.foundation.text.BasicTextField(
                q, { q = it }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Ink2, fontSize = 22.sp, fontFamily = Display),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Red2),
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).focusRequester(foco),
                decorationBox = { inner -> Box { if (q.isEmpty()) Text("Título, autor, una frase que subrayaste o escribiste", color = Grey2, fontSize = 17.sp, fontFamily = Display, fontStyle = FontStyle.Italic); inner() } }
            )
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(150); runCatching { foco.requestFocus() } }
            Rule(1.dp)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (q.isBlank()) {
                    item { RevSectionPad("Lo último") }
                    items(recientes.size) { i -> ResultadoFila(recientes[i]) { onClose(); m.open(recientes[i]) } }
                } else {
                    if (res.isEmpty() && resCitas.isEmpty() && resNotas.isEmpty()) item {
                        Text("Nada con «${q.trim()}».", color = Grey2, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(vertical = 24.dp))
                    }
                    // Por categorías, en el orden de la portada
                    m.kinds.forEach { k ->
                        val deK = res.filter { it.kind.id == k.id }
                        if (deK.isNotEmpty()) {
                            item(key = "k${k.id}") { RevSectionPad("${k.title} · ${deK.size}") }
                            items(deK.size, key = { i -> "e${k.id}-${deK[i].id}-${deK[i].play != null}" }) { i -> ResultadoFila(deK[i]) { onClose(); m.open(deK[i]) } }
                        }
                    }
                    if (resNotas.isNotEmpty()) {
                        item(key = "notas") { RevSectionPad("En tu diario · ${resNotas.size}") }
                        items(resNotas.size) { i ->
                            val n = resNotas[i]
                            Column(Modifier.fillMaxWidth().press(haptic = false) { onClose(); DiarioHub.editar?.invoke(n) }.padding(vertical = 10.dp)) {
                                Text(fechaDiario(n.dia).uppercase(), color = Grey2, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(extracto(n.texto, 220), fontFamily = Display, fontSize = 17.sp, lineHeight = 22.sp, color = Ink2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                            Rule(1.dp, Line)
                        }
                    }
                    if (resCitas.isNotEmpty()) {
                        item(key = "citas") { RevSectionPad("En tus subrayados · ${resCitas.size}") }
                        items(resCitas.size) { i ->
                            val (e, n) = resCitas[i]
                            Column(Modifier.fillMaxWidth().press(haptic = false) { onClose(); ReaderActivity.open(ctx, e.id, e.name, n.value) }.padding(vertical = 10.dp)) {
                                Text("«${n.text}»", fontFamily = Display, fontSize = 17.sp, lineHeight = 22.sp, color = Ink2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                if (n.note.isNotBlank()) Text(n.note, color = Red2, fontStyle = FontStyle.Italic, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOf(e.name, n.label).filter { it.isNotBlank() }.joinToString(" · ").uppercase(), color = Grey2, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 3.dp))
                            }
                            Rule(1.dp, Line)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun RevSectionPad(t: String) { Box(Modifier.padding(top = 18.dp, bottom = 6.dp)) { RevSection(t) } }

@Composable
private fun ResultadoFila(e: ShellEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().press(haptic = false, onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        ListCover(e.cover, e.name, 40.dp, 58.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 22.sp, color = Ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(kindShort(e.kind), e.status, e.creator).filter { it.isNotBlank() }.joinToString(" · "), color = Grey2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        e.score?.let { Text("$it", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Ink2, modifier = Modifier.padding(start = 8.dp)) }
    }
    Rule(1.dp, Line)
}

/** En la portada: los próximos episodios (de aquí a dos semanas) de tus series en curso o en lista, según TVmaze. */
@Composable
private fun RevistaProximos(m: ShellModel) {
    val series = remember(m.kinds) {
        m.kinds.filter { it.isTv }.flatMap { k -> m.entries(k) }.filter { e -> e.play != null && e.play.status in 0..1 && isWebId(e.id) }
            .sortedBy { it.play?.status?.let { s -> if (s == 1) 0 else 1 } ?: 2 }.distinctBy { it.id }.take(15)
    }
    if (series.isEmpty()) return
    val hoy = LocalDate.now()
    val proximos by produceState<List<Pair<ShellEntry, Proximo>>>(emptyList(), series.map { it.id }) {
        value = series.mapNotNull { e -> Proximos.de(e.id)?.takeIf { !it.dia.isBefore(hoy) && it.dia.isBefore(hoy.plusDays(15)) }?.let { e to it } }.sortedBy { it.second.dia }
    }
    if (proximos.isEmpty()) return
    Column {
        Spacer(Modifier.height(38.dp))
        RevSection("Próximos episodios")
        proximos.take(6).forEach { (e, p) ->
            Row(Modifier.fillMaxWidth().press(haptic = false) { m.open(e) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(64.dp)) {
                    Text(when (p.dia) { hoy -> "HOY"; hoy.plusDays(1) -> "MAÑANA"; else -> p.dia.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("es", "ES")).uppercase().trimEnd('.') },
                        color = Red2, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text("${p.dia.dayOfMonth}", color = Ink2, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp)
                }
                Column(Modifier.weight(1f)) {
                    FitTitle(e.name, 20.sp, 23.sp, color = Ink2, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(p.num, p.titulo).filter { it.isNotBlank() }.joinToString(" · "), color = Grey2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Rule(1.dp, Line)
        }
    }
}
