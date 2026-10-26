@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ArtBanner(
    hero: String, cover: String, name: String, blurCover: Boolean, onBack: () -> Unit,
    onDouble: (() -> Unit)? = null
) {
    val dbl by rememberUpdatedState(onDouble)
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(230.dp).background(UiTint)) {
            if (hero.isNotEmpty()) {
                AsyncImage(hero, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (cover.isNotEmpty()) {
                // Sin fondo propio: la portada difuminada. Se carga diminuta y se estira, así queda borrosa
                // también en Android 11 o anterior (donde el desenfoque de Compose no hace nada).
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val req = remember(cover) {
                    coil.request.ImageRequest.Builder(ctx).data(cover).size(36, 54)
                        .precision(coil.size.Precision.EXACT).memoryCacheKey("$cover#fondo").crossfade(250).build()
                }
                AsyncImage(req, null, Modifier.fillMaxSize().blur(18.dp), contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                Box(Modifier.fillMaxSize().background(Color(0x22000000)))
            }
            // Fundido suave hacia el papel para que la imagen no corte en seco.
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(0.55f to Color.Transparent, 1f to Paper)
                )
            )
        }
        AsyncImage(cover, name,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 50.dp).width(150.dp).aspectRatio(2f / 3f)
                .shadow(6.dp, shp(12.dp)).clip(shp(12.dp)).background(Sand)
                .pointerInput(onDouble != null) {
                    if (onDouble != null) detectTapGestures(onDoubleTap = { dbl?.invoke() })
                },
            contentScale = ContentScale.Crop)
        Box(
            Modifier.statusBarsPadding().padding(12.dp).size(40.dp)
                .background(Color(0x55000000), androidx.compose.foundation.shape.CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** v: general + 4 categorías. */
@Composable
fun ScoresFor(v: List<Int>, labels: List<String>, onSet: (Int, Int) -> Unit) {
    Gauge(labels[0], v[0], ColGeneral, 150.dp) { onSet(0, it) }
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        val cols = critColors()
        for (i in 1 until labels.size.coerceAtMost(5)) {
            Gauge(labels[i], v[i], cols[i - 1], 72.dp, Modifier.weight(1f)) { onSet(i, it) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Arrastra el dedo por los anillos o escribe el número.", color = UiSoft, fontSize = 14.sp)
}

/** Formas de exportar una ficha. */
enum class ExportMode { Jpg, Pdf, Share }

@Composable
fun EditScreen(
    g0: Game, isNew: Boolean, kind: Kind, partner: PartnerSide?,
    myReact: String?, onReact: (String?) -> Unit,
    onSave: (Game) -> Unit, onDelete: () -> Unit,
    onArt: (Game, Boolean) -> Unit, onExport: (Game, ExportMode) -> Unit,
    onRecommend: ((Game, String) -> Unit)?,
    /** Abrir directamente en edición (si no, las ya valoradas se ven en modo lectura). */
    editing: Boolean = false,
    /** Juegos: busca las horas habituales si no las tiene. */
    findTtb: (suspend (String) -> TtbResult)? = null,
    /** Guarda sin salir (para las horas habituales que llegan solas). */
    onQuiet: (Game) -> Unit = {},
    onBack: () -> Unit
) {
    var g by remember { mutableStateOf(g0) }
    var viewP by remember { mutableStateOf(false) }
    // Juegos ya valorados: se abren en modo lectura; abajo del todo, «Editar».
    var reading by remember { mutableStateOf(!isNew && !editing) }
    // Horas habituales: si es un juego y no las tiene, se buscan una vez al abrirlo.
    var ttbMsg by remember { mutableStateOf("") }
    LaunchedEffect(g0.id) {
        // Solo al abrirlo en modo lectura: así nunca se guardan a medias cambios que estés haciendo.
        if (kind.isGame && !isNew && !editing && findTtb != null && g.ttbMain == 0 && g.ttbExtra == 0 && g.ttbFull == 0) {
            ttbMsg = "Buscando las horas habituales…"
            ttbMsg = try {
                val r = findTtb(g.name)
                val ng = g.copy(ttbMain = r.main, ttbExtra = r.extra, ttbFull = r.full, ttbSrc = r.src)
                g = ng
                onQuiet(ng)
                ""
            } catch (e: Exception) { "Horas habituales: ${e.message}" }
        }
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val partnerName = partner?.name ?: ""
    var recommend by remember { mutableStateOf(false) }
    LaunchedEffect(viewP) { Mode.pageP = viewP }
    DisposableEffect(Unit) { onDispose { Mode.pageP = false } }
    Crossfade(viewP && partner != null, animationSpec = tween(450), label = "pareja") { pv ->
    val ps = if (pv) partner else null
    if (ps != null) {
        PartnerPage(ps, g.id, kind, g.name, g.cover, g.hero, { withReveal { viewP = false } }, { withReveal { viewP = false } }, myReact, onReact)
    } else {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(scroll)) {
        if (Skin.revista && reading) {
            // Revista: la página en casillas (sin imágenes)
            RevistaGameRead(
                g, kind, partnerName, partner?.reactsToMe?.get(g.id), ttbMsg, onBack,
                onFav = { g = g.copy(fav = !g.fav); onQuiet(g) },
                onPartner = if (partner != null) ({ withReveal { viewP = true } }) else null,
                onExport = onExport,
                onRecommend = if (onRecommend != null) ({ recommend = true }) else null,
                onDelete = if (!isNew) onDelete else null,
                onArt = { hero -> onArt(g, hero) }
            ) {
                reading = false
                scope.launch { scroll.animateScrollTo(0) }
            }
        } else {
        HeroHeader(
            g.cover, g.hero, g.name, listOfNotNull(kind.title, g.creator.ifBlank { null }).joinToString(" · "), coverKey(kind.id, g.id), onBack,
            actions = {
                if (partner != null) RoundIcon(Icons.Rounded.Favorite, "Ver la ficha de $partnerName", onImage = true) { withReveal { viewP = true } }
                RoundIcon(if (g.fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorito", onImage = true, active = g.fav) {
                    g = g.copy(fav = !g.fav); if (reading) onQuiet(g)
                }
            },
            chips = {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!isNew) Row(Modifier.clip(pillShape()).background(ColGeneral).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${g.general}", color = Paper, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(" / 100", color = Paper.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                    if (g.platform.isNotBlank()) InfoChip(g.platform, Icons.Rounded.Devices)
                    if (kind.isGame && g.minutes > 0) InfoChip(hoursText(g.minutes), Icons.Rounded.Schedule)
                    if (kind.isGame && g.ttbMain > 0) InfoChip("Media ${hoursText(g.ttbMain)}", Icons.Rounded.HourglassBottom)
                    if (kind.isBook && g.pages > 0) InfoChip("${g.pages} págs.", Icons.Rounded.AutoStories)
                    if (g.end > 0L) InfoChip(fmtDay(g.end), Icons.Rounded.Event)
                    if (kind.isMovie || kind.isTv) RtChips(kind, g.id, g.name)
                }
            }
        )
        if (kind.isBook && !isNew) Box(Modifier.padding(horizontal = 18.dp)) { BookFileBar(g.id, g.name) }
        if (reading) {
            ReadCard(g, kind, partnerName, partner?.reactsToMe?.get(g.id), ttbMsg, onExport,
                onRecommend = if (onRecommend != null) ({ recommend = true }) else null
            ) {
                reading = false
                scope.launch { scroll.animateScrollTo(0) }
            }
        } else
        Column(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (kind.crit > 1) Text("Media de criterios " + String.format(java.util.Locale("es", "ES"), "%.1f", g.avgFor(kind)), color = UiSoft, fontSize = 15.sp)
            val theirs = partner?.reactsToMe?.get(g.id)
            if (theirs != null) {
                Spacer(Modifier.height(8.dp))
                TheirReaction(partnerName, theirs)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconPill(if (g.fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorito", g.fav) { g = g.copy(fav = !g.fav) }
                IconPill(Icons.Rounded.Share, "Compartir") { onExport(g, ExportMode.Share) }
                IconPill(Icons.Rounded.Image, "Exportar JPG") { onExport(g, ExportMode.Jpg) }
                IconPill(Icons.Rounded.PictureAsPdf, "Exportar PDF") { onExport(g, ExportMode.Pdf) }
                if (onRecommend != null) IconPill(Icons.Rounded.Favorite, "Recomendar") { recommend = true }
            }
            if (partner != null) {
                Spacer(Modifier.height(6.dp))
                Text("Toca dos veces la portada para ver la ficha de $partnerName.", color = UiSoft, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            // El nombre también se puede corregir (la ficha sigue siendo la misma: notas, apuntes y fotos se quedan)
            OutlinedTextField(g.name, { g = g.copy(name = it) },
                label = { Text("Nombre") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(g.creator, { g = g.copy(creator = it) },
                label = { Text(kind.creatorWord) }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
            if (kind.isGame && g.creator.isBlank()) {
                Spacer(Modifier.height(6.dp))
                DetectStudio(g.name) { g = g.copy(creator = it) }
            }
            Spacer(Modifier.height(12.dp))
            PlatformPicker(kind, g.platform) { g = g.copy(platform = it) }
            Spacer(Modifier.height(14.dp))
            if (kind.isBook) PagesField(g.pages) { g = g.copy(pages = it) }
            else if (kind.isGame) MinutesEditor(g.minutes) { g = g.copy(minutes = it) }
            Spacer(Modifier.height(16.dp))
            DatesRow(g.start, g.end, { g = g.copy(start = it) }, { g = g.copy(end = it) })
            if (kind.isGame) BotonListaNegra(g.name)
            Spacer(Modifier.height(16.dp))
            ScoresFor(g.scores(), kind.labels) { i, v -> g = g.setScore(i, v) }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(g.review, { g = g.copy(review = it) }, label = { Text("Tu opinión") },
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
            Spacer(Modifier.height(8.dp))
            VoiceTools(g.review, g.name) { g = g.copy(review = it) }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(Icons.Rounded.Image, "Portada") { onArt(g, false) }
                if (!kind.isBook) ActionPill(Icons.Rounded.Wallpaper, "Fondo") { onArt(g, true) }
                if (kind.hasSoundtrack) SoundtrackButton(g.name)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // (sin nombre no se queda: vuelve el que tenía)
                Button({ onSave(g.copy(name = g.name.trim().ifBlank { g0.name })) }, shape = pillShape(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
                    Text("Guardar", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                if (!isNew) TextButton(onDelete) { Text("Eliminar", color = UiSoft) }
            }
            Spacer(Modifier.height(12.dp))
        }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
    }
    }
    if (recommend && onRecommend != null) {
        RecommendDialog(partnerName, g.name, { note -> recommend = false; onRecommend(g, note) }) { recommend = false }
    }
}

/** Ficha valorada en modo lectura: sin campos, con las notas, la opinión y la banda sonora. */
@Composable
private fun ColumnScope.ReadCard(
    g: Game, kind: Kind, partnerName: String, theirs: String?, ttbMsg: String,
    onExport: (Game, ExportMode) -> Unit, onRecommend: (() -> Unit)?, onEdit: () -> Unit
) {
    Column(
        Modifier.align(Alignment.CenterHorizontally).widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val info = listOfNotNull(
            g.platform.ifBlank { null },
            if (kind.isGame && g.minutes > 0) hoursText(g.minutes) else null,
            if (kind.isBook && g.pages > 0) "${g.pages} págs." else null
        ).joinToString(" · ")
        if (info.isEmpty() && g.start > 0L) Text("Desde " + fmtDay(g.start), color = UiSoft, fontSize = 13.sp)
        if (theirs != null) {
            Spacer(Modifier.height(8.dp))
            TheirReaction(partnerName, theirs)
        }
        Spacer(Modifier.height(20.dp))
        PartnerGauges(g.scores(), kind.labels)
        if (kind.crit > 1) {
            Spacer(Modifier.height(8.dp))
            Text("Media de criterios " + String.format(java.util.Locale("es", "ES"), "%.1f", g.avgFor(kind)), color = UiSoft, fontSize = 15.sp)
        }
        if (kind.isGame && (g.ttbMain > 0 || g.ttbExtra > 0 || g.ttbFull > 0)) {
            Spacer(Modifier.height(22.dp))
            Overline("Horas habituales", Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            // Tus horas, en una casilla igual que las de abajo y centrada encima de ellas.
            if (g.minutes > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    Column(
                        Modifier.weight(1f).border(1.dp, Charcoal, shp(12.dp)).padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(shortHours(g.minutes), fontFamily = Display, fontSize = 20.sp, maxLines = 1, softWrap = false)
                        Text("Tus horas", color = Muted, fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            TtbTiles(g.ttbMain, g.ttbExtra, g.ttbFull)
        } else if (kind.isGame && ttbMsg.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(ttbMsg, color = UiSoft, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        // La opinión solo aparece si la hay.
        if (g.review.isNotBlank()) {
            Spacer(Modifier.height(22.dp))
            Overline("Tu opinión", Modifier.align(Alignment.Start))
            Spacer(Modifier.height(6.dp))
            Text(g.review, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.align(Alignment.Start))
        }
        if (kind.isGame) {
            Spacer(Modifier.height(22.dp))
            FichaPlayButtons(g.id, g.name, Modifier.padding(bottom = 16.dp))
            GuidesSection(g.id, g.name)
        }
        if (kind.hasSoundtrack) {
            Spacer(Modifier.height(20.dp))
            SoundtrackButton(g.name)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconPill(Icons.Rounded.Share, "Compartir") { onExport(g, ExportMode.Share) }
            IconPill(Icons.Rounded.Image, "Exportar JPG") { onExport(g, ExportMode.Jpg) }
            IconPill(Icons.Rounded.PictureAsPdf, "Exportar PDF") { onExport(g, ExportMode.Pdf) }
            if (onRecommend != null) IconPill(Icons.Rounded.Favorite, "Recomendar") { onRecommend() }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onEdit, shape = pillShape(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Editar", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun NoteScreen(
    play: Play, entry: Entry?, kind: Kind, onSave: (Entry) -> Unit, onDelete: () -> Unit, onBack: () -> Unit
) {
    val last = play.entries.maxByOrNull { it.time }
    var e by remember {
        mutableStateOf(
            entry ?: Entry(System.currentTimeMillis(), last?.general ?: 50, last?.gfx ?: 50,
                last?.story ?: 50, last?.music ?: 50, last?.gameplay ?: 50, "", play.chapter)
        )
    }
    Column(Modifier.fillMaxSize().imePadding().wrapContentWidth().widthIn(max = 760.dp).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (Skin.revista) TopBar(if (entry != null) "Apunte" else "Nuevo apunte", onBack, play.name) else {
            BackButton(onBack)
            Spacer(Modifier.height(12.dp))
        }
        Column(
            if (Skin.revista) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().card(24).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!Skin.revista) Text(play.name, fontSize = 28.sp, lineHeight = 32.sp, fontFamily = Display, textAlign = TextAlign.Center)
            Text(if (Skin.revista) fmt(e.time).uppercase() else fmt(e.time), color = UiSoft, fontSize = if (Skin.revista) 11.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                fontWeight = if (Skin.revista) FontWeight.Black else null, letterSpacing = if (Skin.revista) 1.6.sp else androidx.compose.ui.unit.TextUnit.Unspecified)
            if (kind.isBook) Text("Capítulo ${e.chapter}", color = UiSoft)
            Spacer(Modifier.height(16.dp))
            ScoresFor(e.scores(), kind.labels) { i, v -> e = e.setScore(i, v) }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(e.note, { e = e.copy(note = it) }, label = { Text("Anotaciones") },
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp))
            Spacer(Modifier.height(8.dp))
            VoiceTools(e.note, play.name) { e = e.copy(note = it) }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ onSave(e) }, shape = pillShape(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
                    Text("Guardar apunte", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                if (entry != null) TextButton(onDelete) { Text("Eliminar", color = UiSoft) }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

/** Resumen de una sección para el menú principal. */
class MenuInfo(val cover: String?, val line: String, val done: Int, val goal: Int)

/** Algo que ha hecho tu pareja hace poco. */
class Activity(val kind: Int, val id: Long, val name: String, val cover: String, val text: String, val time: Long)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MenuScreen(
    title: String, tiles: List<Pair<Kind, MenuInfo>>, partnerName: String?,
    recs: List<Rec>, acts: List<Activity>,
    readOnly: Boolean = false, hint: String? = null,
    onTitleDouble: () -> Unit = {},
    onSettings: (() -> Unit)?, onEnter: (Int) -> Unit,
    onNewKind: (() -> Unit)? = null, onEditKind: ((Kind) -> Unit)? = null,
    onRecAdd: (Rec) -> Unit, onRecDismiss: (Rec) -> Unit, onAct: (Activity) -> Unit,
    /** Lo de la suite (en directo y capturas): va entre el título y las secciones. */
    top: @Composable () -> Unit = {}
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).appear(60)) {
                    val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", java.util.Locale("es", "ES")))
                    Overline(if (readOnly) "Solo lectura · $date" else date)
                    // Doble toque en el nombre: cambia entre tu app y la de tu pareja.
                    Text(
                        title, fontFamily = Display, fontSize = 38.sp, lineHeight = 42.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onTitleDouble()
                            })
                        }
                    )
                    if (hint != null) Text(hint, color = Muted, fontSize = 12.sp)
                }
                if (onSettings != null) Box(Modifier.appear(120)) { IconPill(Icons.Rounded.Settings, "Ajustes", onClick = onSettings) }
                else Box(Modifier.appear(120)) { IconPill(Icons.Rounded.Close, "Volver a lo tuyo", onClick = onTitleDouble) }
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.appear(90)) { Column { top() } }
            Spacer(Modifier.height(4.dp))
            val cells: List<Any> = tiles + listOfNotNull(onNewKind)
            cells.chunked(2).forEachIndexed { r, row ->
                if (r > 0) Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEachIndexed { c, cell ->
                        val d = 100 + (r * 2 + c) * 60
                        if (cell is Pair<*, *>) {
                            val k = cell.first as Kind
                            val info = cell.second as MenuInfo
                            Tile(
                                k.title, info, d, Modifier.weight(1f), tint = if (k.custom) k.color else 0,
                                onLongClick = if (k.custom && onEditKind != null) ({ onEditKind(k) }) else null
                            ) { onEnter(k.id) }
                        } else {
                            NewKindTile(d, Modifier.weight(1f)) { onNewKind?.invoke() }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            if (partnerName != null && recs.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                Overline("$partnerName te recomienda", Modifier.appear(300))
                Spacer(Modifier.height(10.dp))
                Column(Modifier.appear(300)) {
                    recs.forEachIndexed { i, r ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            MiniCover(r.cover, 42.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(kindOf(r.kind).title, r.creator.ifBlank { null }).joinToString(" · "),
                                    color = Muted, fontSize = 12.sp
                                )
                                if (r.note.isNotBlank()) {
                                    Text("“${r.note}”", fontStyle = FontStyle.Italic, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            IconPill(Icons.Rounded.PlaylistAdd, "Añadir a mi lista") { onRecAdd(r) }
                            Spacer(Modifier.width(6.dp))
                            IconPill(Icons.Rounded.Close, "Descartar") { onRecDismiss(r) }
                        }
                    }
                }
            }

            if (partnerName != null && acts.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Overline(if (readOnly) "Lo último" else "Lo último de $partnerName", Modifier.appear(360))
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.appear(360)) {
                    items(acts, key = { "${it.kind}:${it.id}" }) { a ->
                        Column(Modifier.width(92.dp).pop { onAct(a) }) {
                            AsyncImage(
                                a.cover, a.name,
                                Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shp(8.dp)).background(Sand),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(a.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(a.text, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Casilla para crear una categoría propia. */
@Composable
private fun NewKindTile(delayMs: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = shp(16.dp)
    Column(modifier.appear(delayMs).pop(onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).border(1.dp, Line, shape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+", fontFamily = Display, fontSize = 52.sp, color = Muted)
                Text("Nueva categoría", color = Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Películas, series, discos…", fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Tile(
    label: String, info: MenuInfo, delayMs: Int, modifier: Modifier = Modifier,
    tint: Int = 0, onLongClick: (() -> Unit)? = null, onClick: () -> Unit
) {
    val shape = shp(16.dp)
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.96f else 1f, label = "tile")
    Column(
        modifier.appear(delayMs).graphicsLayerScale(sc)
            .combinedClickable(interactionSource = src, indication = null, onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape)
                .background(if (tint != 0) Color(tint).copy(alpha = 0.16f) else Sand).border(1.dp, Line, shape),
            contentAlignment = Alignment.Center
        ) {
            if (!info.cover.isNullOrEmpty()) {
                AsyncImage(info.cover, label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(label.take(1), fontFamily = Display, fontSize = 64.sp, color = if (tint != 0) Color(tint) else Muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(label, fontFamily = Display, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(info.line, fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (info.goal > 0) {
            Spacer(Modifier.height(8.dp))
            Bar(info.done.toFloat() / info.goal, if (info.done >= info.goal) Gold else Charcoal, height = 3.dp)
            Spacer(Modifier.height(4.dp))
            Text(
                if (info.done >= info.goal) "Objetivo cumplido · ${info.done}/${info.goal}" else "Objetivo del año · ${info.done}/${info.goal}",
                fontSize = 11.sp, color = Muted
            )
        }
    }
}

private fun Modifier.graphicsLayerScale(s: Float) = this.then(
    Modifier.graphicsLayer { scaleX = s; scaleY = s }
)
