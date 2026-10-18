package com.gonzalo.gamerate

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/*
 * Sagas: libros agrupados bajo una portada, en su orden de lectura. Una saga no mueve los libros de su sitio
 * (cada uno sigue en su lista o en leídos); solo los junta: al abrirla salen todos, cada uno con su estado y,
 * si está leído, su nota.
 */

/** Una saga. [items]: los ids de sus libros en orden de lectura; [cover]: su portada (vacía = la de sus libros). */
data class Saga(
    val id: Long, val name: String, val items: List<Long>, val cover: String = "",
    val kind: Int = 1, val note: String = "", val updated: Long = 0L
)

fun sagaToJson(s: Saga): JSONObject = JSONObject().put("id", s.id).put("name", s.name).put("kind", s.kind)
    .put("items", JSONArray().apply { s.items.forEach { put(it) } }).put("updated", s.updated).apply {
        if (s.cover.isNotBlank()) put("cover", s.cover)
        if (s.note.isNotBlank()) put("note", s.note)
    }

fun sagaFromJson(o: JSONObject): Saga = Saga(
    o.getLong("id"), o.optString("name"),
    (o.optJSONArray("items") ?: JSONArray()).let { a -> (0 until a.length()).map { a.optLong(it) }.filter { it != 0L }.distinct() },
    o.optString("cover", ""), o.optInt("kind", 1), o.optString("note", ""), o.optLong("updated", 0L)
)

fun sagasFromJson(a: JSONArray): List<Saga> = (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { runCatching { sagaFromJson(it) }.getOrNull() } }

/** Un libro de una saga, esté donde esté: leído (con su nota) o en una lista (con su estado). */
class SagaBook(val id: Long, val name: String, val cover: String, val creator: String, val play: Play?, val game: Game?) {
    val read: Boolean get() = game != null && (play == null || play.status != 1)
    val reading: Boolean get() = play?.status == 1
    val dropped: Boolean get() = game == null && play?.status == 2
    val score: Int? get() = game?.general
}

/** Los libros de la saga en su orden (los que ya no están en la app se saltan). */
fun sagaBooks(s: Saga, plays: List<Play>, games: List<Game>): List<SagaBook> = s.items.mapNotNull { id ->
    val p = plays.find { it.id == id }
    val g = games.find { it.id == id }
    if (p == null && g == null) null
    else SagaBook(id, cleanTitle(g?.name ?: p!!.name), (g?.cover?.ifBlank { null } ?: p?.cover).orEmpty(), (g?.creator?.ifBlank { null } ?: p?.creator).orEmpty(), p, g)
}

/** Lo que dice la portada de una saga: «3 de 7 leídos · media 86». */
fun sagaLine(books: List<SagaBook>): String {
    val read = books.count { it.read }
    val avg = books.mapNotNull { if (it.read) it.score else null }.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() }
    return listOfNotNull(
        "${books.size} ${if (books.size == 1) "libro" else "libros"}",
        if (read > 0) "$read leído${if (read == 1) "" else "s"}" else null,
        if (books.any { it.reading }) "leyendo" else null,
        avg?.let { "media $it" }
    ).joinToString(" · ")
}

/** El que toca ahora: el que estás leyendo o, si no, el primero sin leer (ni abandonado). */
fun sagaNext(books: List<SagaBook>): SagaBook? = books.firstOrNull { it.reading } ?: books.firstOrNull { !it.read && !it.dropped }

/** Los autores de una saga («Patrick Rothfuss», o «A y B»). */
fun sagaAuthors(books: List<SagaBook>): String = books.map { it.creator.trim() }.filter { it.isNotEmpty() }.distinct().let {
    when (it.size) { 0 -> ""; 1 -> it[0]; 2 -> "${it[0]} y ${it[1]}"; else -> "${it[0]} y otros" }
}

/** Una saga que se podría crear: con nombre (si sale del título) y sus libros en orden. */
class SagaSuggestion(val name: String, val ids: List<Long>, val why: String)

private val SERIE = Regex("""\(([^()#]+?),?\s*#\s*(\d+(?:[.,]\d+)?)\)\s*$""")

/** El nombre de la saga y el número, si el título lo lleva como en Goodreads: «El nombre del viento (Crónica del asesino de reyes, #1)». */
fun seriesOf(title: String): Pair<String, Float>? = SERIE.find(title)?.let { m ->
    val n = m.groupValues[2].replace(',', '.').toFloatOrNull() ?: return null
    m.groupValues[1].trim() to n
}

/** El título sin la coletilla de la saga («El nombre del viento (Crónica…, #1)» → «El nombre del viento»): dentro de la saga ya se ve su número. */
fun cleanTitle(title: String): String = SERIE.replace(title, "").trim().ifBlank { title }

/** Sagas que se podrían crear: por el título (Goodreads) y por autor (2 o más libros suyos sin saga). */
fun sagaSuggestions(sagas: List<Saga>, plays: List<Play>, games: List<Game>): List<SagaSuggestion> {
    val taken = sagas.flatMap { it.items }.toSet()
    val all = (games.map { Triple(it.id, it.name, it.creator) } + plays.map { Triple(it.id, it.name, it.creator) }).distinctBy { it.first }.filter { it.first !in taken }
    val out = mutableListOf<SagaSuggestion>()
    val used = HashSet<Long>()
    all.mapNotNull { b -> seriesOf(b.second)?.let { (s, n) -> Triple(b.first, s, n) } }
        .groupBy { normName(it.second) }
        .values.filter { it.size >= 2 }
        .forEach { g ->
            val ord = g.sortedBy { it.third }
            if (sagas.none { normName(it.name) == normName(ord[0].second) }) {
                out += SagaSuggestion(ord[0].second, ord.map { it.first }, "Por el título")
                used += ord.map { it.first }
            }
        }
    all.filter { it.first !in used && it.third.isNotBlank() }
        .groupBy { normName(it.third) }
        .values.filter { it.size >= 2 }
        .sortedByDescending { it.size }
        .take(4)
        .forEach { g -> out += SagaSuggestion("", g.sortedBy { seriesOf(it.second)?.second ?: Float.MAX_VALUE }.map { it.first }, "Libros de ${g[0].third}") }
    return out
}

/** Lo que las fichas necesitan saber de las sagas (lo pone la app): así un libro sabe en qué saga está. */
object SagaHub {
    var list by mutableStateOf<List<Saga>>(emptyList())
    var open: ((Long) -> Unit)? = null
    var addTo: ((Long) -> Unit)? = null
    /** ¿Ese libro sigue en la app? (uno borrado no cuenta en su saga; si deshaces el borrado, vuelve a su sitio) */
    var exists: (Long) -> Boolean = { true }
    fun of(itemId: Long): Saga? = list.firstOrNull { itemId in it.items }
}

// ---------------------------------------------------------------- Portada de una saga

/** La portada: la elegida o, si no, las de sus libros abiertas en abanico, como sobre una mesa. */
@Composable
fun SagaCover(saga: Saga, books: List<SagaBook>, w: Dp, h: Dp, modifier: Modifier = Modifier) {
    val covers = books.map { it.cover }.filter { it.isNotBlank() }
    Box(modifier.size(w, h)) {
        if (saga.cover.isNotBlank() || covers.size <= 1) {
            val c = saga.cover.ifBlank { covers.firstOrNull().orEmpty() }
            Box(Modifier.fillMaxSize().clip(shp(6.dp)).background(Sand)) {
                if (c.isNotBlank()) AsyncImage(c, saga.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(saga.name.take(1).uppercase(), color = ColGeneral, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (h.value * 0.4f).sp, modifier = Modifier.align(Alignment.Center))
            }
            return@Box
        }
        // Hasta tres portadas, de la tercera a la primera (la primera, delante)
        val n = minOf(3, covers.size)
        // Cada portada con forma de libro (2:3); el abanico, centrado en su hueco
        val ch = h * 0.9f
        val cw = minOf(w * 0.7f, ch * 0.68f)
        val span = minOf(w - cw, cw * 0.55f * (n - 1))
        val x0 = (w - cw - span) / 2
        for (i in (n - 1) downTo 0) {
            val dx = x0 + span * (i / (n - 1).coerceAtLeast(1).toFloat())
            Box(
                Modifier.offset(x = dx, y = h * 0.04f * i).size(cw, ch).rotate((i - (n - 1) / 2f) * 3.5f)
                    .clip(shp(5.dp)).background(Sand).border(1.dp, Paper, shp(5.dp))
            ) { AsyncImage(covers[i], null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    }
}

// ---------------------------------------------------------------- La pestaña «Sagas» de la biblioteca

@Composable
fun SagaList(
    sagas: List<Saga>, plays: List<Play>, games: List<Game>, readOnly: Boolean,
    onOpen: (Saga) -> Unit, onCreate: (String, List<Long>) -> Unit
) {
    var creating by remember { mutableStateOf<SagaSuggestion?>(null) }
    val sugs = remember(sagas, plays, games) { if (readOnly) emptyList() else sagaSuggestions(sagas, plays, games) }
    Column(Modifier.fillMaxWidth()) {
        if (!readOnly) Text("+ NUEVA SAGA", color = ColGeneral, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
            modifier = Modifier.press { creating = SagaSuggestion("", emptyList(), "") }.padding(vertical = 8.dp))
        if (sagas.isEmpty()) Text(
            "Junta bajo una portada los libros de una saga: al abrirla salen todos en orden, estén en tu lista o ya leídos, cada uno con su nota.",
            fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 26.sp, color = Muted, modifier = Modifier.padding(vertical = 18.dp)
        )
        sagas.sortedByDescending { it.updated }.forEachIndexed { i, s ->
            val books = sagaBooks(s, plays, games)
            Row(Modifier.fillMaxWidth().press(haptic = false) { onOpen(s) }.padding(vertical = 14.dp).rise(i, i < 12), verticalAlignment = Alignment.CenterVertically) {
                SagaCover(s, books, 74.dp, 104.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    FitTitle(s.name, 28.sp, 30.sp, letterSpacing = (-0.5).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val who = sagaAuthors(books)
                    if (who.isNotBlank()) Text(who, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sagaLine(books).uppercase(), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 4.dp))
                    if (books.isNotEmpty()) SagaProgress(books, Modifier.padding(top = 8.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        }
        if (sugs.isNotEmpty()) {
            Text("SUGERENCIAS", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(top = 26.dp, bottom = 4.dp))
            sugs.forEach { sg ->
                val books = sg.ids.mapNotNull { id -> (games.find { it.id == id }?.name ?: plays.find { it.id == id }?.name)?.let { cleanTitle(it) } }
                Row(Modifier.fillMaxWidth().press(haptic = false) { creating = sg }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sg.name.ifBlank { sg.why }, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 22.sp)
                        Text(books.joinToString(" · ").uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text("CREAR →", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
        }
    }
    creating?.let { sg ->
        SagaPicker(
            title = "Nueva saga", name0 = sg.name, chosen0 = sg.ids, plays = plays, games = games, exclude = emptySet(), askName = true,
            onDone = { name, ids -> creating = null; if (ids.isNotEmpty()) onCreate(name.ifBlank { "Saga sin nombre" }, ids) },
            onDismiss = { creating = null }
        )
    }
}

/** Una barra por libro: leído (acento), leyendo (a medias), sin leer (filete). */
@Composable
fun SagaProgress(books: List<SagaBook>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        books.forEach { b ->
            Box(Modifier.weight(1f).fillMaxHeight().background(if (b.read) ColGeneral else Line)) {
                if (b.reading) Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f).background(ColGeneral))
            }
        }
    }
}

// ---------------------------------------------------------------- La página de una saga

@Composable
fun SagaScreen(
    saga: Saga, plays: List<Play>, games: List<Game>, kind: Kind,
    onBack: () -> Unit, onOpenBook: (SagaBook) -> Unit, onStart: (Play) -> Unit,
    onSave: (Saga) -> Unit, onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val books = remember(saga, plays, games) { sagaBooks(saga, plays, games) }
    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var name by remember(saga.id) { mutableStateOf(saga.name) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { runCatching { Photos.import(ctx, uri) }.getOrNull()?.let { onSave(saga.copy(cover = it)) } }
    }
    // Se reordena lo que se ve (un libro borrado de la app ya no está en la lista)
    fun move(i: Int, d: Int) {
        val l = books.map { it.id }.toMutableList()
        val j = i + d
        if (i !in l.indices || j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t
        onSave(saga.copy(items = l))
    }
    // El nombre se guarda solo, sin tener que pulsar «Listo»
    LaunchedEffect(name) {
        if (name.isNotBlank() && name.trim() != saga.name) { kotlinx.coroutines.delay(700); onSave(saga.copy(name = name.trim())) }
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        RevistaTopBar(onBack) {
            Text(if (editing) "LISTO" else "EDITAR", color = if (editing) ColGeneral else Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.clickable {
                    if (editing && name.isNotBlank() && name.trim() != saga.name) onSave(saga.copy(name = name.trim()))
                    editing = !editing
                }.padding(8.dp))
        }
        Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(3.dp).background(Charcoal))
        Spacer(Modifier.height(14.dp))
        val who = sagaAuthors(books)
        RevistaHeadline(saga.name, listOfNotNull("Saga", kind.title, who.ifBlank { null }).joinToString(" · "))
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            if (editing) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre de la saga") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                Text("PORTADA", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                // La de uno de sus libros, una foto tuya o la automática (las de sus libros en abanico)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSave(saga.copy(cover = "")) }) {
                        SagaCover(saga.copy(cover = ""), books, 64.dp, 92.dp, if (saga.cover.isBlank()) Modifier.border(2.dp, ColGeneral, shp(6.dp)) else Modifier)
                        Text("AUTOMÁTICA", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    books.filter { it.cover.isNotBlank() }.forEach { b ->
                        Box(Modifier.size(64.dp, 92.dp).clip(shp(6.dp)).then(if (saga.cover == b.cover) Modifier.border(2.dp, ColGeneral, shp(6.dp)) else Modifier).clickable { onSave(saga.copy(cover = b.cover)) }) {
                            AsyncImage(b.cover, b.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        pick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Box(Modifier.size(64.dp, 92.dp).border(1.dp, Line, shp(6.dp)), contentAlignment = Alignment.Center) { Text("+", fontFamily = Display, fontSize = 30.sp, color = Muted) }
                        Text("TU FOTO", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))
                SagaCover(saga, books, 300.dp, 260.dp, Modifier.align(Alignment.CenterHorizontally))
            }
            // Las cifras de la saga
            if (books.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).background(Charcoal))
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    val read = books.count { it.read }
                    val avg = books.mapNotNull { if (it.read) it.score else null }.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() }
                    SagaFigure("$read/${books.size}", "Leídos", Modifier.weight(1f))
                    SagaFigure(avg?.toString() ?: "—", "Tu media", Modifier.weight(1f))
                    val pend = books.count { !it.read && !it.dropped }
                    SagaFigure("$pend", if (pend == 1) "Te queda" else "Te quedan", Modifier.weight(1f))
                }
                SagaProgress(books)
                // El siguiente que toca
                val next = sagaNext(books)
                if (next != null && !editing) {
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.fillMaxWidth().background(Crudo).padding(16.dp)) {
                        Column {
                            Text(if (next.reading) "AHORA MISMO" else "EL SIGUIENTE", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            FitTitle(next.name, 26.sp, 29.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            val p = next.play
                            // Con su archivo en el móvil, directamente al lector
                            if (next.reading) MagLink("Seguir leyendo", accent = true) {
                                if (BookFiles.get(ctx, next.id) != null) ReaderActivity.open(ctx, next.id, next.name) else onOpenBook(next)
                            }
                            else if (p != null) MagLink("Empezar a leer", accent = true) { onStart(p) }
                            else MagLink("Abrir su ficha") { onOpenBook(next) }
                        }
                    }
                }
            }
            // Los libros, en orden
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("EN ORDEN DE LECTURA", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                Text("+ AÑADIR", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.clickable { adding = true }.padding(6.dp))
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(1.dp).background(Charcoal))
            if (books.isEmpty()) Text("Aún no tiene libros: añade los que quieras.", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, color = Muted, modifier = Modifier.padding(vertical = 16.dp))
            books.forEachIndexed { i, b ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !editing) { onOpenBook(b) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d".format(i + 1), color = ColGeneral, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.width(40.dp))
                    Box(Modifier.size(42.dp, 62.dp).clip(shp(4.dp)).background(Sand)) {
                        if (b.cover.isNotBlank()) AsyncImage(b.cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        FitTitle(b.name, 20.sp, 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = if (b.dropped) Muted else Color.Unspecified)
                        val st = when {
                            b.reading -> listOfNotNull(kind.statuses[1], b.play?.chapter?.takeIf { it > 0 }?.let { "cap. $it" }).joinToString(" · ")
                            b.read -> kind.doneWord.removeSuffix("s")
                            b.dropped -> kind.statuses[2]
                            else -> kind.statuses[0]
                        }
                        Text(st.uppercase(), color = if (b.reading) ColGeneral else Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (editing) {
                        Text("↑", fontSize = 20.sp, color = if (i > 0) Charcoal else Line, modifier = Modifier.clickable(enabled = i > 0) { move(i, -1) }.padding(8.dp))
                        Text("↓", fontSize = 20.sp, color = if (i < books.size - 1) Charcoal else Line, modifier = Modifier.clickable(enabled = i < books.size - 1) { move(i, 1) }.padding(8.dp))
                        Text("QUITAR", color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                            modifier = Modifier.clickable { onSave(saga.copy(items = saga.items.filter { it != b.id })) }.padding(8.dp))
                    } else b.score?.let {
                        Text("$it", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 34.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
            if (editing) {
                Text("BORRAR LA SAGA", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                    modifier = Modifier.clickable { confirmDelete = true }.padding(vertical = 22.dp))
            }
            Spacer(Modifier.navigationBarsPadding().height(30.dp))
        }
    }
    if (adding) SagaPicker(
        title = "Añadir a ${saga.name}", name0 = saga.name, chosen0 = emptyList(), plays = plays, games = games, exclude = saga.items.toSet(), askName = false,
        hintAuthor = books.firstOrNull()?.creator.orEmpty(), hintSeries = saga.name,
        onDone = { _, ids -> adding = false; if (ids.isNotEmpty()) onSave(saga.copy(items = saga.items + ids)) },
        onDismiss = { adding = false }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("¿Borrar la saga?", fontFamily = Display) },
        text = { Text("Sus libros se quedan donde están; solo desaparece la saga.") },
        confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("Borrar", color = ColGeneral) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun SagaFigure(v: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(v, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp)
        Text(label.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
    }
}

// ---------------------------------------------------------------- Elegir libros

/**
 * Elegir libros (de cualquier lista: leídos, en curso, en lista…). Arriba, los que más pintan:
 * los de la misma saga por el título y los del mismo autor.
 */
@Composable
fun SagaPicker(
    title: String, name0: String, chosen0: List<Long>, plays: List<Play>, games: List<Game>, exclude: Set<Long>, askName: Boolean,
    hintAuthor: String = "", hintSeries: String = "",
    onDone: (String, List<Long>) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(name0) }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(chosen0) }
    val all = remember(plays, games) {
        (games.map { Triple(it.id, it.name, it.creator) } + plays.map { Triple(it.id, it.name, it.creator) }).distinctBy { it.first }
    }
    val author = hintAuthor.ifBlank {
        chosen.firstNotNullOfOrNull { id -> all.find { it.first == id }?.third?.ifBlank { null } }.orEmpty()
    }
    val list = all.filter { it.first !in exclude && libMatches(query, it.second, it.third) }
        .sortedWith(compareByDescending<Triple<Long, String, String>> { it.first in chosen0 }
            .thenByDescending { hintSeries.isNotBlank() && seriesOf(it.second)?.first?.let { s -> normName(s) == normName(hintSeries) } == true }
            .thenByDescending { author.isNotBlank() && normName(it.third) == normName(author) }
            .thenBy { seriesOf(it.second)?.second ?: Float.MAX_VALUE }
            .thenBy { it.second.lowercase() })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = Display) },
        text = {
            Column {
                if (askName) OutlinedTextField(name, { name = it }, label = { Text("Nombre de la saga") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(query, { query = it }, label = { Text("Buscar un libro") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("${chosen.size} elegido${if (chosen.size == 1) "" else "s"} · en el orden en que los marques".uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(list.size) { i ->
                        val (id, n, c) = list[i]
                        val on = id in chosen
                        Row(Modifier.fillMaxWidth().clickable { chosen = if (on) chosen - id else chosen + id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { chosen = if (on) chosen - id else chosen + id })
                            Column(Modifier.weight(1f)) {
                                Text(cleanTitle(n), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (c.isNotBlank()) Text(c, color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                            if (on) Text("${chosen.indexOf(id) + 1}", color = ColGeneral, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onDone(name.trim(), chosen) }, enabled = chosen.isNotEmpty() && (!askName || name.isNotBlank())) { Text(if (askName) "Crear" else "Añadir") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}

/** «Añadir a una saga» desde la ficha de un libro: a una que ya tienes, o una nueva con este libro. */
@Composable
fun AddToSagaDialog(itemName: String, sagas: List<Saga>, onPick: (Saga) -> Unit, onNew: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(seriesOf(itemName)?.first.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a una saga", fontFamily = Display) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                sagas.sortedByDescending { it.updated }.forEach { s ->
                    Text(s.name, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                }
                Text("UNA NUEVA", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Nombre de la saga") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton({ onNew(name.trim()) }, enabled = name.isNotBlank()) { Text("Crear con este libro") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}

/** En la ficha de un libro: en qué saga está (y su número), o para añadirlo a una. */
@Composable
fun SagaLink(itemId: Long) {
    val s = SagaHub.of(itemId)
    if (s != null) {
        val vivos = s.items.filter { it == itemId || SagaHub.exists(it) }
        MagLink("Saga · ${s.name}", "Nº ${vivos.indexOf(itemId) + 1} de ${vivos.size}") { SagaHub.open?.invoke(s.id) }
    }
    else if (SagaHub.addTo != null) MagLink("Añadir a una saga") { SagaHub.addTo?.invoke(itemId) }
}
