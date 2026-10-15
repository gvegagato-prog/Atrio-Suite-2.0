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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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
data class MagCell(val type: String, val title: String = "", val text: String = "", val image: String = "", val fit: Boolean = false)

/** Una fila: una o dos casillas; [split] es dónde va la línea (lo que ocupa la de la izquierda, 0–1). */
data class MagRowSpec(val cells: List<MagCell>, val split: Float = 0.5f)

fun magRows(vararg rows: List<String>): List<MagRowSpec> = rows.map { r -> MagRowSpec(r.map { MagCell(it) }) }

object MagLayouts {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("maqueta", Context.MODE_PRIVATE)

    private fun toJson(rows: List<MagRowSpec>) = JSONArray().apply {
        rows.forEach { r ->
            put(JSONObject().put("s", r.split.toDouble()).put("c", JSONArray().apply {
                r.cells.forEach { c -> put(JSONObject().put("t", c.type).put("h", c.title).put("x", c.text).put("i", c.image).put("f", c.fit)) }
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
                    MagCell(c.optString("t", "vacio"), c.optString("h"), c.optString("x"), c.optString("i"), c.optBoolean("f"))
                }.take(2), o.optDouble("s", 0.5).toFloat())
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

    fun visible(c: MagCell) = c.type == "texto" || c.type == "imagen" || c.type == "vacio" || defs.containsKey(c.type)
    fun cellAt(p: Pair<Int, Int>) = rows.getOrNull(p.first)?.cells?.getOrNull(p.second)
    fun setCell(p: Pair<Int, Int>, c: MagCell) = set(rows.mapIndexed { i, r -> if (i != p.first) r else r.copy(cells = r.cells.mapIndexed { j, x -> if (j == p.second) c else x }) })

    @Composable
    fun ColumnScope.CellView(c: MagCell) {
        when (c.type) {
            "texto" -> {
                if (c.title.isNotBlank()) MagLabel(c.title)
                if (c.text.isNotBlank()) Text(c.text, fontFamily = Display, fontSize = 18.sp, lineHeight = 27.sp)
                else if (designing) Text("Texto libre", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
            }
            "imagen" -> {
                if (c.title.isNotBlank()) MagLabel(c.title)
                if (c.image.isNotBlank()) AsyncImage(c.image, null, Modifier.fillMaxWidth().padding(top = 2.dp), contentScale = if (c.fit) ContentScale.FillWidth else ContentScale.FillWidth)
                else if (designing) Text("Imagen", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
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
                    Tool("QUITAR", accent = true) { set(rows.filterIndexed { k, _ -> k != i }) }
                }
            }
            var widthPx by remember { mutableStateOf(1) }
            val shown = if (designing) r.cells.withIndex().toList() else cells
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).onSizeChanged { widthPx = it.width.coerceAtLeast(1) }) {
                shown.forEachIndexed { n, (j, c) ->
                    val w = if (shown.size == 2) (if (n == 0) r.split else 1f - r.split) else 1f
                    if (n == 1) {
                        // La línea: en edición se arrastra
                        Box(
                            Modifier.fillMaxHeight().width(if (designing) 28.dp else 1.5.dp)
                                .then(if (designing) Modifier.pointerInput(i, rows.size) {
                                    detectHorizontalDragGestures(onDragEnd = { MagLayouts.save(ctx, key, rows) }) { ch, dx ->
                                        ch.consume()
                                        val ns = (rows[i].split + dx / widthPx).coerceIn(0.22f, 0.78f)
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
                    Box(Modifier.weight(w).fillMaxHeight()) {
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp, start = if (n == 1) 12.dp else 0.dp, end = if (n == 0 && shown.size == 2) 12.dp else 0.dp)) {
                            CellView(c)
                        }
                        if (designing) Box(
                            Modifier.matchParentSize().padding(vertical = 6.dp, horizontal = 2.dp)
                                .drawBehind {
                                    drawRoundRect(ColGeneral, style = androidx.compose.ui.graphics.drawscope.Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                                }
                                .clickable { choosing = i to j }
                        )
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
