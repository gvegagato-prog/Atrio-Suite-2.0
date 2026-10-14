package com.gonzalo.gamerate

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

/*
 * Inicio (Atrio 6): todo lo que tienes entre manos, de cualquier categoría, en un carrusel grande; tu PC;
 * ideas para descubrir; tus colecciones; y lo de tu pareja.
 */

/** Algo en curso para el carrusel de Inicio. */
class Ongoing(val kind: Kind, val play: Play)

/** Una colección (categoría propia) en Inicio. */
class CollectionInfo(val kind: Kind, val covers: List<String>, val line: String)

private fun greeting(): String = when (LocalTime.now().hour) {
    in 6..13 -> "Buenos días"
    in 14..20 -> "Buenas tardes"
    else -> "Buenas noches"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InicioScreen(
    name: String, readOnly: Boolean, partnerName: String?, hint: String?,
    ongoing: List<Ongoing>, sessId: Long,
    collections: List<CollectionInfo>, recs: List<Rec>, acts: List<Activity>,
    goals: List<Triple<String, Int, Int>>,
    onTitleDouble: () -> Unit, onPartner: (() -> Unit)?, onProfile: (() -> Unit)?,
    onOpen: (Ongoing) -> Unit, onPrimary: (Ongoing) -> Unit, primaryLabel: (Ongoing) -> Pair<String, androidx.compose.ui.graphics.vector.ImageVector>,
    onCollection: (Kind) -> Unit, onNewCollection: (() -> Unit)?, onEditCollection: ((Kind) -> Unit)?,
    onDiscover: ((Int) -> Unit)?,
    onRecAdd: (Rec) -> Unit, onRecDismiss: (Rec) -> Unit, onAct: (Activity) -> Unit,
    top: @Composable () -> Unit = {}
) {
    val hf = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = dockSpace(24.dp))
    ) {
        // Cabecera: fecha, saludo y tu nombre (doble toque: lo de tu pareja)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).rise(0), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES")))
                Text((if (readOnly) "Solo lectura · $date" else date).uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp)
                Text(
                    if (readOnly) name else "${greeting()},\n$name",
                    fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 35.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { hf.performHapticFeedback(HapticFeedbackType.LongPress); onTitleDouble() })
                    }
                )
                if (hint != null) Text(hint, color = Muted, fontSize = 12.sp)
            }
            if (onPartner != null) {
                Box(
                    Modifier.size(46.dp).press(onClick = onPartner).clip(roundShape()).background(if (readOnly) Charcoal else Crudo).border(1.dp, Line, roundShape()),
                    contentAlignment = Alignment.Center
                ) { CatFace(if (readOnly) Paper else Charcoal, if (readOnly) Charcoal else Crudo, Modifier.size(24.dp)) }
                Spacer(Modifier.width(8.dp))
            }
            if (onProfile != null) RoundIcon(Icons.Rounded.Person, "Perfil y ajustes", size = 46.dp, onClick = onProfile)
        }

        // Sigue donde lo dejaste
        if (ongoing.isNotEmpty()) {
            SectionTitle(if (readOnly) "Ahora mismo" else "Sigue donde lo dejaste", Modifier.padding(horizontal = 20.dp).rise(1))
            val pager = rememberPagerState { ongoing.size }
            HorizontalPager(
                pager, contentPadding = PaddingValues(horizontal = 26.dp), pageSpacing = 12.dp,
                flingBehavior = PagerDefaults.flingBehavior(pager), modifier = Modifier.rise(2)
            ) { i ->
                val o = ongoing[i]
                val off = ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                OngoingCard(
                    o, o.play.id == sessId && o.kind.isGame, readOnly, primaryLabel(o),
                    Modifier.graphicsLayer {
                        val s = androidx.compose.ui.util.lerp(1f, 0.9f, off); scaleX = s; scaleY = s; alpha = androidx.compose.ui.util.lerp(1f, 0.6f, off)
                    },
                    onOpen = { onOpen(o) }, onPrimary = { onPrimary(o) }
                )
            }
            if (ongoing.size > 1) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(ongoing.size.coerceAtMost(12)) { d ->
                        val on = d == pager.currentPage
                        Box(Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (on) 18.dp else 6.dp).clip(pillShape()).background(if (on) Charcoal else Line))
                    }
                }
            }
        } else if (!readOnly) {
            Card6(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).rise(1)) {
                Text("Nada entre manos", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Empieza algo de tu lista o descubre qué jugar, leer o ver: aquí aparecerá para seguir con un toque.", color = Muted, fontSize = 14.sp)
            }
        }

        // La suite (PC en directo, partes por valorar, capturas)
        Column(Modifier.padding(horizontal = 20.dp).rise(3)) { Spacer(Modifier.height(18.dp)); top() }

        // Descubrir
        if (onDiscover != null) {
            SectionTitle("¿Qué empiezo?", Modifier.padding(horizontal = 20.dp).rise(4))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).rise(4), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DiscoverTile("Pelis y series", "Unas preguntas y te propongo", Icons.Rounded.Movie, Color(0xFFE85A2E), Modifier.weight(1f)) { onDiscover(2) }
                DiscoverTile("Libros", "Tu próximo libro, a medida", Icons.Rounded.AutoStories, Color(0xFF8B5CF6), Modifier.weight(1f)) { onDiscover(1) }
                DiscoverTile("Juegos", "Desliza y elige", Icons.Rounded.SportsEsports, Color(0xFF3E6BE0), Modifier.weight(1f)) { onDiscover(0) }
            }
        }

        // Objetivos del año
        if (goals.any { it.third > 0 }) {
            SectionTitle("Objetivos de ${LocalDate.now().year}", Modifier.padding(horizontal = 20.dp).rise(5))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).rise(5), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                goals.filter { it.third > 0 }.forEach { (n, done, goal) ->
                    Card6(Modifier.weight(1f), padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ring(((done * 100) / goal.coerceAtLeast(1)).coerceAtMost(100), if (done >= goal) Gold else ColGeneral, 46.dp, "$done")
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(n, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(if (done >= goal) "¡Cumplido!" else "de $goal este año", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Colecciones propias
        if (collections.isNotEmpty() || onNewCollection != null) {
            SectionTitle("Tus colecciones", Modifier.padding(horizontal = 20.dp).rise(6))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.rise(6)) {
                items(collections, key = { it.kind.id }) { c ->
                    CollectionTile(c, onLong = onEditCollection?.let { e -> { e(c.kind) } }) { onCollection(c.kind) }
                }
                if (onNewCollection != null) item {
                    Column(Modifier.width(150.dp).press(onClick = onNewCollection)) {
                        Box(Modifier.fillMaxWidth().height(150.dp).clip(shp(22.dp)).border(1.5.dp, Line, shp(22.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, null, tint = Muted, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Nueva colección", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Discos, series, viajes…", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }

        // Tu pareja
        if (partnerName != null && recs.isNotEmpty()) {
            SectionTitle("$partnerName te recomienda", Modifier.padding(horizontal = 20.dp).rise(7))
            Column(Modifier.padding(horizontal = 20.dp).rise(7), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recs.forEach { r ->
                    Card6(padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MiniCover(r.cover, 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(kindOf(r.kind).title, r.creator.ifBlank { null }).joinToString(" · "), color = Muted, fontSize = 12.sp)
                                if (r.note.isNotBlank()) Text("“${r.note}”", fontStyle = FontStyle.Italic, fontSize = 13.sp, color = Muted)
                            }
                            RoundIcon(Icons.Rounded.PlaylistAdd, "Añadir a mi lista", size = 38.dp) { onRecAdd(r) }
                            Spacer(Modifier.width(6.dp))
                            RoundIcon(Icons.Rounded.Close, "Descartar", size = 38.dp) { onRecDismiss(r) }
                        }
                    }
                }
            }
        }
        if (partnerName != null && acts.isNotEmpty()) {
            SectionTitle(if (readOnly) "Lo último" else "Lo último de $partnerName", Modifier.padding(horizontal = 20.dp).rise(8))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.rise(8)) {
                items(acts, key = { "${it.kind}:${it.id}" }) { a ->
                    Column(Modifier.width(108.dp).press { onAct(a) }) {
                        AsyncImage(a.cover, a.name, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shp(16.dp)).background(Sand), contentScale = ContentScale.Crop)
                        Spacer(Modifier.height(6.dp))
                        Text(a.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.text, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Tarjeta grande del carrusel: arte de fondo, portada, título, progreso y el botón principal. */
@Composable
private fun OngoingCard(
    o: Ongoing, live: Boolean, readOnly: Boolean, primary: Pair<String, androidx.compose.ui.graphics.vector.ImageVector>,
    modifier: Modifier, onOpen: () -> Unit, onPrimary: () -> Unit
) {
    val p = o.play
    val art = p.hero.ifBlank { p.cover }
    Box(modifier.fillMaxWidth().height(232.dp).press(onClick = onOpen).shadow(18.dp, shp(28.dp)).clip(shp(28.dp)).background(Charcoal)) {
        if (art.isNotBlank()) AsyncImage(
            art, null,
            Modifier.fillMaxSize().then(if (p.hero.isBlank()) Modifier.blur(18.dp) else Modifier),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.78f), 0.75f to Color.Black.copy(alpha = 0.25f), 1f to Color.Transparent)))
        Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.width(92.dp).aspectRatio(2f / 3f).sharedCover(coverKey(o.kind.id, p.id)).shadow(12.dp, shp(14.dp)).clip(shp(14.dp)).background(Sand)) {
                if (p.cover.isNotBlank()) AsyncImage(p.cover, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.kind.title.uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    if (live) { Spacer(Modifier.width(8.dp)); LiveDot() }
                }
                Text(p.name, color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val read = if (o.kind.isBook && BookFiles.get(ctx, p.id) != null) BookFiles.progress(ctx, p.id) else null
                val line = listOfNotNull(
                    read?.let { "${(it * 100).toInt()} % leído" } ?: p.percent()?.let { "$it %" },
                    if (o.kind.isBook && p.chapter > 0) "cap. ${p.chapter}" else null,
                    if (!o.kind.isBook && p.minutes > 0) hoursText(p.minutes) else null,
                    p.creator.ifBlank { null }
                ).joinToString(" · ")
                if (line.isNotBlank()) Text(line, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                (read?.let { (it * 100).toInt() } ?: p.percent())?.let { pc ->
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(pillShape()).background(Color.White.copy(alpha = 0.22f))) {
                        Box(Modifier.fillMaxWidth((pc / 100f).coerceIn(0.02f, 1f)).fillMaxHeight().background(ColGeneral))
                    }
                }
                if (!readOnly) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.press(onClick = onPrimary).clip(pillShape()).background(Color.White).padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(primary.second, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(primary.first, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverTile(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(138.dp).press(onClick = onClick).clip(shp(22.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.92f), lerp(tint, Color.Black, 0.45f))))
            .padding(13.dp)
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 17.sp, maxLines = 2)
        Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2)
    }
}

/** Colección: tres portadas en abanico. */
@Composable
private fun CollectionTile(c: CollectionInfo, onLong: (() -> Unit)?, onClick: () -> Unit) {
    val tint = if (c.kind.color != 0) Color(c.kind.color) else ColGeneral
    Column(Modifier.width(150.dp).press(onLong = onLong, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(shp(22.dp)).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            val cs = c.covers.take(3)
            if (cs.isEmpty()) Text(c.kind.title.take(1).uppercase(), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 56.sp, color = tint)
            cs.forEachIndexed { i, url ->
                val rot = listOf(-10f, 8f, 0f)[i]
                val dx = listOf(-26, 26, 0)[i]
                AsyncImage(
                    url, null,
                    Modifier.offset(x = dx.dp).width(70.dp).aspectRatio(2f / 3f).graphicsLayer { rotationZ = if (cs.size == 1) 0f else rot }
                        .shadow(8.dp, shp(10.dp)).clip(shp(10.dp)).background(Sand),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(c.kind.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(c.line, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
