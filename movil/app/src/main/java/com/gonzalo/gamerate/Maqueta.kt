package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONArray
import org.json.JSONObject

/*
 * La maqueta de las fichas de revista: la página es una lista de filas; cada fila tiene una casilla a todo lo
 * ancho o dos separadas por una línea que tú colocas. En cada casilla eliges qué va (guías, nota, opinión,
 * horas, enlaces…), un texto libre (título y texto) o una imagen (SteamGridDB: portada, fondo o logo; en pelis,
 * series y libros, sus propias fuentes). Se guarda por ficha, y se puede usar como diseño de toda la categoría.
 */

/** Una casilla: qué módulo enseña (o texto libre / imagen) y lo suyo. */
data class MagCell(
    val type: String, val title: String = "", val text: String = "", val image: String = "", val fit: Boolean = false,
    /** Encuadre de la imagen: zoom (1 = llena la casilla) y desplazamiento, en fracciones del tamaño de la casilla. */
    val zoom: Float = 1f, val ox: Float = 0f, val oy: Float = 0f
)

/** Una fila: una o dos casillas; [split] es dónde va la línea (lo que ocupa la de la izquierda, 0–1). */
data class MagRowSpec(val cells: List<MagCell>, val split: Float = 0.5f, /** Alto de la fila en dp (0: el que pida lo de dentro). */ val height: Float = 0f)

fun magRows(vararg rows: List<String>): List<MagRowSpec> = rows.map { r -> MagRowSpec(r.map { MagCell(it) }) }

object MagLayouts {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("maqueta", Context.MODE_PRIVATE)

    private fun toJson(rows: List<MagRowSpec>) = JSONArray().apply {
        rows.forEach { r ->
            put(JSONObject().put("s", r.split.toDouble()).put("a", r.height.toDouble()).put("c", JSONArray().apply {
                r.cells.forEach { c ->
                    put(JSONObject().put("t", c.type).put("h", c.title).put("x", c.text).put("i", c.image).put("f", c.fit)
                        .put("z", c.zoom.toDouble()).put("ox", c.ox.toDouble()).put("oy", c.oy.toDouble()))
                }
            }))
        }
    }.toString()

    private fun fromJson(s: String?): List<MagRowSpec>? = try {
        if (s.isNullOrBlank()) null else {
            val a = JSONArray(s)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val cs = o.getJSONArray("c")
                MagRowSpec((0 until cs.length()).map { j ->
                    val c = cs.getJSONObject(j)
                    MagCell(c.optString("t", "vacio"), c.optString("h"), c.optString("x"), c.optString("i"), c.optBoolean("f"),
                        c.optDouble("z", 1.0).toFloat(), c.optDouble("ox", 0.0).toFloat(), c.optDouble("oy", 0.0).toFloat())
                }.take(2), o.optDouble("s", 0.5).toFloat(), o.optDouble("a", 0.0).toFloat())
            }
        }
    } catch (e: Exception) { null }

    /** La maqueta de esta ficha; si no tiene, la de su categoría; si no, null (la de siempre). */
    fun load(ctx: Context, key: String, tpl: String): List<MagRowSpec>? = fromJson(prefs(ctx).getString(key, null)) ?: fromJson(prefs(ctx).getString(tpl, null))
    fun save(ctx: Context, key: String, rows: List<MagRowSpec>) { prefs(ctx).edit().putString(key, toJson(rows)).apply() }
    fun saveTemplate(ctx: Context, tpl: String, rows: List<MagRowSpec>) {
        // Para la categoría no se copian los textos ni las imágenes de esta ficha: solo dónde va cada cosa
        val clean = rows.map { r -> r.copy(cells = r.cells.map { c -> if (c.type == "texto" || c.type == "imagen") MagCell("vacio") else c }) }
        prefs(ctx).edit().putString(tpl, toJson(clean)).apply()
    }
    fun reset(ctx: Context, key: String, tpl: String) { prefs(ctx).edit().remove(key).remove(tpl).apply() }
}

/** Las imágenes que se pueden poner en una casilla (las pone la app, que tiene las claves). */
object MagImages {
    /** Categorías de imagen de una categoría de la colección (p. ej. Portada, Fondo, Logo). */
    var categories: (Kind) -> List<String> = { emptyList() }
    /** Carga las imágenes (miniatura, imagen) de una categoría. */
    var load: suspend (Kind, Long, String, String) -> List<Pair<String, String>> = { _, _, _, _ -> emptyList() }
}

/** Qué parte del ancho ocupa la casilla (para que las cifras grandes encojan si la casilla es estrecha). */
val LocalCellFraction = compositionLocalOf { 1f }

/** Un módulo que puede ir en una casilla: su antetítulo (null: el módulo ya trae el suyo) y lo que pinta. */
class MagDef(val label: String?, val action: Pair<String, () -> Unit>? = null, val content: @Composable ColumnScope.() -> Unit)

/**
 * La página: en lectura, las filas con sus líneas; en edición (designing), cada casilla se toca para cambiarla,
 * la línea se arrastra y las filas se suben, bajan, dividen, unen, añaden o quitan.
 */
@Composable
fun MagPage(
    key: String, tpl: String, kindName: String, defaults: List<MagRowSpec>, defs: Map<String, MagDef>,
    kind: Kind, itemId: Long, itemName: String, designing: Boolean
) {
    val ctx = LocalContext.current
    var rows by remember(key) { mutableStateOf(MagLayouts.load(ctx, key, tpl) ?: defaults) }
    fun set(n: List<MagRowSpec>) { rows = n; MagLayouts.save(ctx, key, n) }
    var choosing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editingText by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pickingImage by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var msg by remember { mutableStateOf("") }
    var framing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val cellSizes = remember { mutableStateMapOf<String, androidx.compose.ui.unit.IntSize>() }

    fun visible(c: MagCell) = c.type == "texto" || c.type == "imagen" || c.type == "vacio" || defs.containsKey(c.type)
    fun cellAt(p: Pair<Int, Int>) = rows.getOrNull(p.first)?.cells?.getOrNull(p.second)
    fun setCell(p: Pair<Int, Int>, c: MagCell) = set(rows.mapIndexed { i, r -> if (i != p.first) r else r.copy(cells = r.cells.mapIndexed { j, x -> if (j == p.second) c else x }) })

    @Composable
    fun ColumnScope.CellView(c: MagCell, k: String) {
        when (c.type) {
            "texto" -> {
                if (c.title.isNotBlank()) MagLabel(c.title)
                if (c.text.isNotBlank()) Text(c.text, fontFamily = Display, fontSize = 18.sp, lineHeight = 27.sp)
                else if (designing) Text("Texto libre", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
            }
            "imagen" -> {
                if (c.title.isNotBlank()) MagLabel(c.title)
                // Con alto de fila fijo, la imagen llena lo que queda; si no, un marco 7:5 (los logos, más apaisado)
                val fixed = LocalRowFixed.current
                Box(
                    Modifier.fillMaxWidth().then(if (fixed) Modifier.weight(1f) else Modifier.aspectRatio(if (c.fit) 2.2f else 1.4f))
                        .clipToBounds().background(if (c.image.isBlank()) Sand else androidx.compose.ui.graphics.Color.Transparent)
                        .onSizeChanged { cellSizes[k] = it }
                ) {
                    if (c.image.isNotBlank()) FramedImage(c, Modifier.fillMaxSize())
                    else if (designing) Text("Imagen", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center))
                }
            }
            "vacio" -> if (designing) Text("Vacía: toca para elegir", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
            else -> defs[c.type]?.let { d ->
                if (d.label != null) MagLabel(d.label, d.action?.first, d.action?.second)
                d.content(this)
            }
        }
    }

    rows.forEachIndexed { i, r ->
        val cells = r.cells.withIndex().filter { visible(it.value) }
        if (cells.isEmpty() && !designing) return@forEachIndexed
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            // Sin antetítulo propio (paneles de progreso, partes…) el filete lo ponen ellos
            val ownRule = cells.size == 1 && defs[cells[0].value.type]?.label == null && cells[0].value.type !in listOf("texto", "imagen", "vacio")
            if (!ownRule || designing) Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
            if (designing) {
                // Herramientas de la fila
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("FILA ${i + 1}", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                    Tool("↑", i > 0) { set(rows.toMutableList().also { val t = it[i]; it[i] = it[i - 1]; it[i - 1] = t }) }
                    Tool("↓", i < rows.size - 1) { set(rows.toMutableList().also { val t = it[i]; it[i] = it[i + 1]; it[i + 1] = t }) }
                    if (r.cells.size == 1) Tool("DIVIDIR") { set(rows.mapIndexed { k, x -> if (k == i) x.copy(cells = x.cells + MagCell("vacio"), split = 0.5f) else x }) }
                    else Tool("UNIR") { set(rows.mapIndexed { k, x -> if (k == i) x.copy(cells = listOf(x.cells.firstOrNull { it.type != "vacio" } ?: x.cells[0])) else x }) }
                    if (r.height > 0f) Tool("ALTO AUTO") { set(rows.mapIndexed { k, x -> if (k == i) x.copy(height = 0f) else x }) }
                    Tool("QUITAR", accent = true) { set(rows.filterIndexed { k, _ -> k != i }) }
                }
            }
            var widthPx by remember { mutableStateOf(1) }
            var heightPx by remember { mutableStateOf(1) }
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            val shown = if (designing) r.cells.withIndex().toList() else cells
            val fixed = r.height > 0f
            Row(
                Modifier.fillMaxWidth().then(if (fixed) Modifier.height(r.height.dp) else Modifier.height(IntrinsicSize.Min))
                    .onSizeChanged { widthPx = it.width.coerceAtLeast(1); heightPx = it.height.coerceAtLeast(1) }
            ) {
                shown.forEachIndexed { n, (j, c) ->
                    val w = if (shown.size == 2) (if (n == 0) r.split else 1f - r.split) else 1f
                    if (n == 1) {
                        // La línea: en edición se arrastra
                        Box(
                            Modifier.fillMaxHeight().width(if (designing) 28.dp else 1.5.dp)
                                .then(if (designing) Modifier.semantics { contentDescription = "Línea de la fila ${i + 1}" }.pointerInput(i, rows.size) {
                                    detectHorizontalDragGestures(onDragEnd = { MagLayouts.save(ctx, key, rows) }) { ch, dx ->
                                        ch.consume()
                                        val ns = (rows[i].split + dx / widthPx).coerceIn(0.3f, 0.7f)
                                        rows = rows.mapIndexed { k, x -> if (k == i) x.copy(split = ns) else x }
                                    }
                                } else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.fillMaxHeight().width(1.5.dp).background(Charcoal))
                            if (designing) Box(Modifier.size(26.dp).clip(CircleShape).background(ColGeneral), contentAlignment = Alignment.Center) {
                                Text("⇔", color = Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Box(Modifier.weight(w).fillMaxHeight().clipToBounds()) {
                        Column(
                            Modifier.fillMaxWidth().then(if (fixed) Modifier.fillMaxHeight() else Modifier)
                                .padding(top = 12.dp, bottom = 18.dp, start = if (n == 1) 12.dp else 0.dp, end = if (n == 0 && shown.size == 2) 12.dp else 0.dp)
                        ) {
                            CompositionLocalProvider(LocalCellFraction provides w, LocalRowFixed provides fixed) { CellView(c, "$i:$j") }
                        }
                        if (designing) Box(
                            Modifier.matchParentSize()
                                .drawBehind {
                                    drawRoundRect(ColGeneral, style = androidx.compose.ui.graphics.drawscope.Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                                }
                                .clickable(onClickLabel = "Cambiar") { choosing = i to j }
                                // Lo de dentro queda tapado: la casilla se anuncia con su nombre (TalkBack y pruebas)
                                .semantics { contentDescription = "Casilla " + when (c.type) {
                                    "texto" -> c.title.ifBlank { "Texto libre" }
                                    "imagen" -> c.title.ifBlank { "Imagen" }
                                    "vacio" -> "vacía"
                                    else -> defs[c.type]?.label ?: MAG_NAMES[c.type] ?: c.type
                                } }
                        )
                    }
                }
            }
            if (designing) {
                // El alto de la fila: se arrastra el círculo ⇕ hacia arriba o hacia abajo
                Box(
                    Modifier.fillMaxWidth().height(30.dp)
                        .semantics { contentDescription = "Alto de la fila ${i + 1}" }
                        .pointerInput(i, rows.size) {
                            detectVerticalDragGestures(onDragEnd = { MagLayouts.save(ctx, key, rows) }) { ch, dy ->
                                ch.consume()
                                val base = if (rows[i].height > 0f) rows[i].height else heightPx / density
                                val nh = (base + dy / density).coerceIn(56f, 1400f)
                                rows = rows.mapIndexed { k, x -> if (k == i) x.copy(height = nh) else x }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    Box(Modifier.size(26.dp).clip(CircleShape).background(ColGeneral), contentAlignment = Alignment.Center) {
                        Text("⇕", color = Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (designing) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Tool("+ AÑADIR FILA", accent = true) { set(rows + MagRowSpec(listOf(MagCell("vacio")))); choosing = rows.size - 1 to 0 }
            }
            Tool("USAR ESTE DISEÑO EN TODOS LOS ${kindName.uppercase()}") { MagLayouts.saveTemplate(ctx, tpl, rows); msg = "Guardado: lo verás en las demás fichas de ${kindName.lowercase()} (sin tus textos ni imágenes)." }
            Spacer(Modifier.height(10.dp))
            Tool("RESTABLECER EL DISEÑO DE SIEMPRE") { MagLayouts.reset(ctx, key, tpl); rows = defaults; msg = "Diseño de siempre." }
            if (msg.isNotBlank()) Text(msg, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }

    // ¿Qué va en esta casilla?
    choosing?.let { p ->
        val cur = cellAt(p)
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("¿Qué va aquí?", fontFamily = Display) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (cur?.type == "imagen" && cur.image.isNotBlank()) {
                        ChoiceRow("Encuadrar la imagen", false) { choosing = null; framing = p }
                        ChoiceRow("Cambiar la imagen", false) { choosing = null; pickingImage = p }
                        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Line))
                    }
                    defs.forEach { (t, d) ->
                        ChoiceRow(d.label ?: MAG_NAMES[t] ?: t, cur?.type == t) { setCell(p, MagCell(t)); choosing = null }
                    }
                    ChoiceRow("Texto libre (título y texto)", cur?.type == "texto") { choosing = null; editingText = p }
                    if (MagImages.categories(kind).isNotEmpty()) ChoiceRow("Imagen", cur?.type == "imagen") { choosing = null; pickingImage = p }
                    ChoiceRow("Vacía", cur?.type == "vacio") { setCell(p, MagCell("vacio")); choosing = null }
                }
            },
            confirmButton = { TextButton({ choosing = null }) { Text("Cerrar") } },
            containerColor = Paper, shape = shp(4.dp)
        )
    }
    // Texto libre
    editingText?.let { p ->
        val cur = cellAt(p)
        var title by remember(p) { mutableStateOf(if (cur?.type == "texto") cur.title else "") }
        var text by remember(p) { mutableStateOf(if (cur?.type == "texto") cur.text else "") }
        AlertDialog(
            onDismissRequest = { editingText = null },
            title = { Text("Texto libre", fontFamily = Display) },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text("Título (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(text, { text = it }, label = { Text("Texto") }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp))
                }
            },
            confirmButton = { TextButton({ setCell(p, MagCell("texto", title.trim(), text.trim())); editingText = null }) { Text("Guardar") } },
            dismissButton = { TextButton({ editingText = null }) { Text("Cancelar") } },
            containerColor = Paper, shape = shp(4.dp)
        )
    }
    // Encuadrar
    framing?.let { p ->
        val cur = cellAt(p)
        if (cur == null || cur.image.isBlank()) { framing = null } else {
            // El marco tiene la forma exacta de la imagen en su casilla
            val sz = cellSizes["${p.first}:${p.second}"]
            val aspect = if (sz != null && sz.width > 0 && sz.height > 0) sz.width.toFloat() / sz.height else if (cur.fit) 2.2f else 1.4f
            FrameEditor(cur, aspect,
                onSave = { setCell(p, it); framing = null }, onDismiss = { framing = null })
        }
    }
    // Imagen
    pickingImage?.let { p ->
        val cats = MagImages.categories(kind)
        var cat by remember(p) { mutableStateOf(cats.firstOrNull() ?: "") }
        var list by remember(p, cat) { mutableStateOf<List<Pair<String, String>>?>(null) }
        var err by remember(p, cat) { mutableStateOf("") }
        LaunchedEffect(p, cat) {
            list = null
            list = try { MagImages.load(kind, itemId, itemName, cat) } catch (e: Exception) { err = e.message ?: "No se han podido cargar."; emptyList() }
        }
        AlertDialog(
            onDismissRequest = { pickingImage = null },
            title = { Text("Imagen", fontFamily = Display) },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        cats.forEach { c -> Tool(c.uppercase(), accent = c == cat) { cat = c } }
                    }
                    Spacer(Modifier.height(10.dp))
                    val l = list
                    when {
                        l == null -> Text("Cargando…", color = Muted)
                        l.isEmpty() -> Text(err.ifBlank { "No hay imágenes de este tipo." }, color = Muted)
                        else -> LazyVerticalGrid(GridCells.Fixed(if (cat == cats.firstOrNull() && cats.size > 1 && !kind.isBook) 3 else 2), Modifier.heightIn(max = 420.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(l) { (thumb, url) ->
                                AsyncImage(thumb, null, Modifier.fillMaxWidth().background(Sand).clickable {
                                    setCell(p, MagCell("imagen", image = url, fit = cat.contains("Logo", true))); pickingImage = null
                                }, contentScale = ContentScale.FillWidth)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ pickingImage = null }) { Text("Cerrar") } },
            containerColor = Paper, shape = shp(4.dp)
        )
    }
}

/** Una imagen encuadrada: recortada a la casilla, con su zoom y su posición. */
@Composable
fun FramedImage(c: MagCell, modifier: Modifier) {
    AsyncImage(
        c.image, null,
        modifier.graphicsLayer {
            scaleX = c.zoom; scaleY = c.zoom
            translationX = c.ox * size.width; translationY = c.oy * size.height
        },
        contentScale = if (c.fit) ContentScale.Fit else ContentScale.Crop
    )
}

/** Encuadrar: la imagen dentro de un marco con la forma de la casilla; dos dedos amplían o reducen, uno la mueve. */
@Composable
fun FrameEditor(c: MagCell, aspect: Float, onSave: (MagCell) -> Unit, onDismiss: () -> Unit) {
    var zoom by remember { mutableStateOf(c.zoom) }
    var ox by remember { mutableStateOf(c.ox) }
    var oy by remember { mutableStateOf(c.oy) }
    var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }
    androidx.compose.ui.window.Dialog(onDismiss, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).background(Paper).padding(18.dp)) {
            Text("Encuadrar", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text("Pellizca para ampliar o reducir. Arrastra para colocarla. Lo que quede dentro del marco es lo que se verá.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(0.3f, 4f)).clipToBounds().background(Sand).border(2.dp, Charcoal)
                    .onSizeChanged { box = it }
                    .semantics { contentDescription = "Marco de la imagen" }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, z, _ ->
                            zoom = (zoom * z).coerceIn(0.3f, 6f)
                            ox = (ox + pan.x / box.width).coerceIn(-3f, 3f)
                            oy = (oy + pan.y / box.height).coerceIn(-3f, 3f)
                        }
                    }
            ) { FramedImage(c.copy(zoom = zoom, ox = ox, oy = oy), Modifier.fillMaxSize()) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Tool("− REDUCIR") { zoom = (zoom / 1.15f).coerceIn(0.3f, 6f) }
                Tool("+ AMPLIAR") { zoom = (zoom * 1.15f).coerceIn(0.3f, 6f) }
                Tool("CENTRAR") { zoom = 1f; ox = 0f; oy = 0f }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.End)) {
                Tool("CANCELAR", onClick = onDismiss)
                Tool("GUARDAR", accent = true) { onSave(c.copy(zoom = zoom, ox = ox, oy = oy)) }
            }
        }
    }
}

/** ¿La fila tiene alto fijo? (las imágenes llenan entonces la casilla) */
val LocalRowFixed = compositionLocalOf { false }

/** Nombres de los módulos (para el selector, cuando el módulo no trae antetítulo). */
val MAG_NAMES = mapOf("progreso" to "Progreso", "partes" to "Partes y apuntes rápidos")

@Composable
private fun Tool(t: String, enabled: Boolean = true, accent: Boolean = false, onClick: () -> Unit) =
    Text(t, color = if (!enabled) Line else if (accent) ColGeneral else Charcoal, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp))

@Composable
private fun ChoiceRow(t: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, fontFamily = Display, fontSize = 19.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) ColGeneral else Charcoal, modifier = Modifier.weight(1f))
        if (on) Text("●", color = ColGeneral)
    }
}
