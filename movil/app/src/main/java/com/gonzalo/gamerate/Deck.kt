package com.gonzalo.gamerate

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Temas de Open Library demasiado genéricos para recomendar. */
private val NoiseSubjects = setOf(
    "fiction", "accessible_book", "protected_daisy", "in_library", "large_type_books", "open_library_staff_picks",
    "nyt:combined-print-and-e-book-fiction=2010-01-01", "english_literature", "american_literature", "novels",
    "fiction,_general", "lending_library", "long_now_manual_for_civilization", "reading_level-grade_11", "reading_level-grade_12"
)

/** Prepara el mazo: primero lo que le apetece a tu pareja, luego recomendaciones según vuestros gustos. */
private suspend fun buildDeck(
    kind: Kind, store: Store, games: List<Game>, partner: Partner?, owned: Set<String>,
    likes: Set<String>, nopes: Set<String>
): Pair<List<Card>, String> {
    val theirs = partner?.likes(kind).orEmpty().filter { it.key !in likes && it.key !in nopes }
    val tastes = store.tastes(kind.id)
    val liked = (games + partner?.games(kind).orEmpty()).filter { it.general >= 75 }
        .sortedByDescending { it.general }.distinctBy { normName(it.name) }.take(8)
    var basis: String
    val found: List<Card> = if (!kind.isBook) {
        val key = store.rawgKey
        if (key.isBlank()) {
            return theirs to "Para recomendarte juegos hace falta una clave gratuita de RAWG (Ajustes > Servicios)."
        }
        val genres = if (tastes.isNotEmpty()) tastes.toList() else {
            val count = HashMap<String, Int>()
            liked.forEach { g -> try { Rawg.genresOf(g.name, key).forEach { count[it] = (count[it] ?: 0) + 1 } } catch (e: Exception) { } }
            count.entries.sortedByDescending { it.value }.take(3).map { it.key }
        }
        basis = if (genres.isEmpty()) "Lo más popular" else genres.joinToString(" · ") { s -> GameGenres.firstOrNull { it.first == s }?.second ?: s }
        Rawg.deck(genres, key, (1..3).random())
    } else {
        val subjects = if (tastes.isNotEmpty()) tastes.toList() else {
            val count = HashMap<String, Int>()
            liked.take(6).forEach { b ->
                try { OpenLibDeck.subjectsOf(b.name).filter { it !in NoiseSubjects && ":" !in it }.forEach { count[it] = (count[it] ?: 0) + 1 } } catch (e: Exception) { }
            }
            count.entries.sortedByDescending { it.value }.take(3).map { it.key }.ifEmpty { listOf("fantasy", "science_fiction") }
        }
        basis = subjects.joinToString(" · ") { s -> BookSubjects.firstOrNull { it.first == s }?.second ?: s.replace('_', ' ') }
        val lists = subjects.take(3).map { s -> try { OpenLibDeck.bySubject(s, (0..4).random() * 20) } catch (e: Exception) { emptyList() } }
        val out = ArrayList<Card>()
        for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) lists.forEach { l -> if (i < l.size) out.add(l[i]) }
        out
    }
    val rest = found.filter { it.key !in likes && it.key !in nopes && normName(it.title) !in owned && theirs.none { t -> t.key == it.key } }
    return (theirs + rest).distinctBy { it.key } to basis
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DeckScreen(
    kind: Kind, games: List<Game>, plays: List<Play>, partner: Partner?,
    onMenu: () -> Unit, onAdd: (Card) -> Unit, onLikesChanged: () -> Unit
) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var likes by remember(kind.id) { mutableStateOf(store.likes(kind.id)) }
    var nopes by remember(kind.id) { mutableStateOf(store.nopes(kind.id)) }
    var tastes by remember(kind.id) { mutableStateOf(store.tastes(kind.id)) }
    var deck by remember(kind.id) { mutableStateOf<List<Card>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var msg by remember { mutableStateOf("") }
    var basis by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var showTastes by remember { mutableStateOf(false) }
    var showLikes by remember { mutableStateOf(false) }
    var match by remember { mutableStateOf<Card?>(null) }
    val partnerLikes = partner?.likes(kind).orEmpty().map { it.key }.toSet()
    val owned = remember(games, plays) { (games.map { normName(it.name) } + plays.map { normName(it.name) }).toSet() }

    LaunchedEffect(kind.id, reload) {
        loading = true
        msg = ""
        try {
            val (d, b) = buildDeck(kind, store, games, partner, owned, likes.map { it.key }.toSet(), nopes)
            deck = d
            basis = b
            if (d.isEmpty() && msg.isEmpty()) msg = if (b.startsWith("Para recomendarte")) b else "No he encontrado más. Prueba a cambiar tus gustos."
            if (b.startsWith("Para recomendarte")) { msg = b; basis = "" }
        } catch (e: Exception) {
            msg = "No se han podido cargar recomendaciones: ${e.message ?: "sin conexión"}"
        }
        loading = false
    }

    fun swipe(c: Card, like: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        deck = deck.filter { it.key != c.key }
        if (like) {
            likes = (likes.filter { it.key != c.key } + c).takeLast(300)
            store.setLikes(kind.id, likes)
            onLikesChanged()
            if (c.key in partnerLikes) match = c
        } else {
            nopes = nopes + c.key
            store.setNopes(kind.id, nopes)
        }
        if (deck.size < 4 && !loading) reload++
    }

    val matches = likes.filter { it.key in partnerLikes }.filter { normName(it.title) !in owned }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp)) {
        TopBar(kind.title, onMenu, "Siguiente") {
            IconPill(Icons.Rounded.Tune, "Gustos", tastes.isNotEmpty()) { showTastes = true }
            IconPill(Icons.Rounded.Favorite, "Lo que me apetece") { showLikes = true }
        }
        if (basis.isNotEmpty()) {
            Text(
                "Según: $basis", color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                loading && deck.isEmpty() -> Text("Barajando cartas…", color = Muted)
                deck.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(msg.ifEmpty { "No quedan cartas." }, color = UiSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(10.dp))
                    ActionPill(Icons.Rounded.Refresh, "Barajar otra vez") { reload++ }
                }
                else -> {
                    val shown = deck.take(2)
                    if (shown.size > 1) {
                        SwipeCard(shown[1], shown[1].key in partnerLikes, partner?.name, kind, behind = true, onSwiped = {})
                    }
                    key(shown[0].key) {
                        SwipeCard(shown[0], shown[0].key in partnerLikes, partner?.name, kind, behind = false) { like -> swipe(shown[0], like) }
                    }
                }
            }
        }

        if (deck.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                RoundButton(Icons.Rounded.Close, "Paso", Charcoal) { deck.firstOrNull()?.let { swipe(it, false) } }
                RoundButton(Icons.Rounded.Favorite, if (kind.isBook) "Me lo leo" else "Me apetece", ColGeneral) { deck.firstOrNull()?.let { swipe(it, true) } }
            }
        }
        if (matches.isNotEmpty()) {
            Overline("Matches con ${partner?.name ?: "tu pareja"}", Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                items(matches, key = { it.key }) { c ->
                    Column(Modifier.width(78.dp).pop { onAdd(c) }) {
                        AsyncImage(
                            c.image, c.title,
                            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shp(10.dp)).background(Sand),
                            contentScale = ContentScale.Crop
                        )
                        Text(c.title, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (!kind.isBook && deck.isNotEmpty()) {
            Text("Datos e imágenes de RAWG.io", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp))
        }
    }

    val m = match
    if (m != null) {
        AlertDialog(
            onDismissRequest = { match = null },
            title = { Text("¡Match!", fontFamily = Display) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        m.image, m.title,
                        Modifier.width(150.dp).aspectRatio(2f / 3f).clip(shp(16.dp)).background(Sand),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("A ti y a ${partner?.name ?: "tu pareja"} os apetece ${m.title}.", textAlign = TextAlign.Center)
                }
            },
            confirmButton = { TextButton({ match = null; onAdd(m) }) { Text("Añadir a mi lista") } },
            dismissButton = { TextButton({ match = null }) { Text("Seguir") } },
            shape = shp(24.dp)
        )
    }

    if (showTastes) {
        val options = if (kind.isBook) BookSubjects else GameGenres
        var sel by remember { mutableStateOf(tastes) }
        AlertDialog(
            onDismissRequest = { showTastes = false },
            title = { Text("¿Qué os apetece?", fontFamily = Display) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Si no marcas nada, lo deduzco de lo que mejor habéis valorado tú y tu pareja.",
                        color = Muted, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { (slug, name) ->
                            SoftChip(name, slug in sel, onPanel = true) { sel = if (slug in sel) sel - slug else sel + slug }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    tastes = sel
                    store.setTastes(kind.id, sel)
                    showTastes = false
                    deck = emptyList()
                    reload++
                }) { Text("Barajar") }
            },
            dismissButton = { TextButton({ sel = emptySet() }) { Text("Automático") } },
            shape = shp(24.dp)
        )
    }

    if (showLikes) {
        AlertDialog(
            onDismissRequest = { showLikes = false },
            title = { Text("Lo que te apetece", fontFamily = Display) },
            text = {
                if (likes.isEmpty()) {
                    Text("Todavía no has deslizado nada a la derecha.")
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        likes.reversed().forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Line, shp(14.dp)).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MiniCover(c.image, 40.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        (if (c.key in partnerLikes) "Match · " else "") + listOf(c.year, c.creator).filter { it.isNotBlank() }.joinToString(" · "),
                                        color = Muted, fontSize = 12.sp
                                    )
                                }
                                Text("+ Lista", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    modifier = Modifier.pop { onAdd(c) }.padding(6.dp))
                                Text("Quitar", color = ColGeneral, fontSize = 13.sp,
                                    modifier = Modifier.pop {
                                        likes = likes.filter { it.key != c.key }
                                        store.setLikes(kind.id, likes)
                                        onLikesChanged()
                                    }.padding(6.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showLikes = false }) { Text("Cerrar") } },
            shape = shp(24.dp)
        )
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.pop(onClick).size(62.dp).background(Crudo, CircleShape)
            .border(1.dp, Line, CircleShape),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = color, modifier = Modifier.size(32.dp)) }
}

/** Carta que se desliza: derecha = me apetece, izquierda = paso. */
@Composable
internal fun SwipeCard(
    c: Card, partnerLiked: Boolean, partnerName: String?, kind: Kind, behind: Boolean, onSwiped: (Boolean) -> Unit
) {
    val off = remember(c.key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val cb by rememberUpdatedState(onSwiped)
    val shape = shp(20.dp)
    Box(
        Modifier
            .widthIn(max = 420.dp).fillMaxWidth(0.92f).aspectRatio(0.7f)
            .graphicsLayer {
                if (behind) { scaleX = 0.94f; scaleY = 0.94f; translationY = 18.dp.toPx(); alpha = 0.9f }
                translationX = off.value
                rotationZ = off.value / 28f
            }
            .shadow(if (behind) 0.dp else 6.dp, shape).clip(shape).background(Sand)
            .then(
                if (behind) Modifier else Modifier.pointerInput(c.key) {
                    val limit = size.width * 0.28f
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(off.value) > limit) {
                                    val like = off.value > 0
                                    off.animateTo(if (like) size.width * 1.6f else -size.width * 1.6f, tween(220))
                                    cb(like)
                                } else off.animateTo(0f, tween(250))
                            }
                        },
                        onDragCancel = { scope.launch { off.animateTo(0f, tween(250)) } }
                    ) { change, drag ->
                        change.consume()
                        scope.launch { off.snapTo(off.value + drag.x) }
                    }
                }
            )
    ) {
        AsyncImage(c.image, c.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xDD000000))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(c.title, color = Color.White, fontFamily = Display, fontSize = 28.sp, lineHeight = 30.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            val line = listOf(c.year, c.creator, c.sub).filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotEmpty()) Text(line, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (c.score > 0) {
            Text(
                "${c.score}", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                    .background(Color(0x66000000), pillShape())
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (partnerLiked && partnerName != null) {
            Text(
                "A $partnerName le apetece", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Charcoal,
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).background(Crudo, pillShape())
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
        if (!behind) {
            val a = (off.value / 260f).coerceIn(-1f, 1f)
            if (a > 0.05f) Stamp(if (kind.isBook) "¡ME LO LEO!" else "¡ME APETECE!", ColMusic, a, Alignment.TopStart, -14f)
            if (a < -0.05f) Stamp("PASO", ColGeneral, -a, Alignment.TopEnd, 14f)
        }
    }
}

@Composable
private fun BoxScope.Stamp(text: String, color: Color, alpha: Float, align: Alignment, rot: Float) {
    Text(
        text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 26.sp,
        modifier = Modifier.align(align).padding(top = 60.dp, start = 24.dp, end = 24.dp)
            .graphicsLayer { this.alpha = alpha; rotationZ = rot }
            .border(2.dp, color, shp(8.dp)).background(Color.White.copy(alpha = 0.85f), shp(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}
