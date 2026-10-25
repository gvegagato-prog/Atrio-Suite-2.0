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
    val zoom: Float = 1f, val ox: Float = 0f, val oy: Float = 0f,
    /** Forma de la imagen: "llenar" (toda la casilla), "original" (la de la foto), "vertical", "cuadrada", "apaisada"; "" las de antes. */
    val shape: String = ""
)

/** Las formas que puede tener una imagen en su casilla. */
val MAG_SHAPES = listOf("llenar" to "Llenar la casilla", "original" to "La de la foto", "vertical" to "Vertical", "cuadrada" to "Cuadrada", "apaisada" to "Apaisada")

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
                        .put("z", c.zoom.toDouble()).put("ox", c.ox.toDouble()).put("oy", c.oy.toDouble()).put("sh", c.shape))
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
                        c.optDouble("z", 1.0).toFloat(), c.optDouble("ox", 0.0).toFloat(), c.optDouble("oy", 0.0).toFloat(), c.optString("sh"))
                }.take(2), o.optDouble("s", 0.5).toFloat(), o.optDouble("a", 0.0).toFloat())
            }
        }
    } catch (e: Exception) { null }

    /** La maqueta de esta ficha; si no tiene, la de su categoría; si no, null (la de siempre). */
    fun load(ctx: Context, key: String, tpl: String): List<MagRowSpec>? = fromJson(prefs(ctx).getString(key, null)) ?: fromJson(prefs(ctx).getString(tpl, null))
    /** Sube un número cuando llega de otro dispositivo una maqueta nueva: las páginas abiertas la vuelven a leer. */
    private val ver = androidx.compose.runtime.mutableIntStateOf(0)
    val version: Int get() = ver.intValue
    fun cambiaron() { android.os.Handler(android.os.Looper.getMainLooper()).post { ver.intValue++ } }

    /** Guarda la maqueta de esta ficha: aquí y en tu cuenta (se ve igual en tus demás dispositivos, con sus fotos). */
    fun save(ctx: Context, key: String, rows: List<MagRowSpec>) {
        val j = toJson(rows)
        prefs(ctx).edit().putString(key, j).commit()
        runCatching { Store(ctx).saveMaqueta(key, j) }
        // Una foto tuya en una casilla: a tu nube, para que las demás la tengan
        if (rows.any { r -> r.cells.any { it.image.startsWith("file:") } }) runCatching { NubeSync.enqueue(ctx) }
    }
    fun saveTemplate(ctx: Context, tpl: String, rows: List<MagRowSpec>) {
        // Para la categoría no se copian los textos ni las imágenes de esta ficha: solo dónde va cada cosa
        val clean = rows.map { r -> r.copy(cells = r.cells.map { c -> if (c.type == "texto" || c.type == "imagen") MagCell("vacio") else c }) }
        val j = toJson(clean)
        prefs(ctx).edit().putString(tpl, j).commit()
        runCatching { Store(ctx).saveMaqueta(tpl, j) }
    }
    fun reset(ctx: Context, key: String, tpl: String) {
        prefs(ctx).edit().remove(key).remove(tpl).commit()
        runCatching { Store(ctx).removeMaquetas(listOf(key, tpl)) }
    }

    /** ¿Tiene esta ficha una disposición propia? (de «Leído» o «En curso») */
    fun tiene(ctx: Context, kindId: Int, id: Long) = prefs(ctx).let { it.contains("p:$kindId:$id") || it.contains("v:$kindId:$id") }

    /**
     * Copia la disposición de una ficha en otras (casillas, textos, fotos, encuadre, todo): la de la página de «en curso»
     * y la de la página de «leído», cada una en la suya. Si la de origen solo tiene una, vale para las dos.
     * Guarda lo que había para poder [deshacerCopia]. Devuelve cuántas fichas han cambiado, o -1 si el origen no tiene nada.
     */
    fun copiar(ctx: Context, kindId: Int, from: Long, to: List<Long>, soloSinDiseno: Boolean, undo: String): Int {
        val p = prefs(ctx)
        val sp = p.getString("p:$kindId:$from", null); val sv = p.getString("v:$kindId:$from", null)
        if (sp == null && sv == null) return -1
        val srcP = sp ?: sv!!; val srcV = sv ?: sp!!
        val destinos = to.filter { it != from && !(soloSinDiseno && tiene(ctx, kindId, it)) }
        val antes = JSONObject()
        val ed = p.edit()
        val nuevos = ArrayList<Pair<String, String>>()
        for (id in destinos) for ((pre, j) in listOf("p" to srcP, "v" to srcV)) {
            val k = "$pre:$kindId:$id"
            antes.put(k, p.getString(k, null) ?: JSONObject.NULL)
            ed.putString(k, j); nuevos += k to j
        }
        ed.putString("@undo:$undo", antes.toString()).commit()
        nuevos.forEach { (k, j) -> runCatching { Store(ctx).saveMaqueta(k, j) } }
        if (srcP.contains("\"file:") || srcV.contains("\"file:")) runCatching { NubeSync.enqueue(ctx) }
        cambiaron()
        return destinos.size
    }

    fun hayCopia(ctx: Context, undo: String) = prefs(ctx).contains("@undo:$undo")

    /** Devuelve a las fichas lo que tenían antes de la última copia. */
    fun deshacerCopia(ctx: Context, undo: String) {
        val p = prefs(ctx)
        val antes = try { JSONObject(p.getString("@undo:$undo", null) ?: return) } catch (e: Exception) { p.edit().remove("@undo:$undo").commit(); return }
        val ed = p.edit()
        val quitar = ArrayList<String>(); val poner = ArrayList<Pair<String, String>>()
        antes.keys().forEach { k -> if (antes.isNull(k)) { ed.remove(k); quitar += k } else { val j = antes.getString(k); ed.putString(k, j); poner += k to j } }
        ed.remove("@undo:$undo").commit()
        val st = Store(ctx)
        if (quitar.isNotEmpty()) runCatching { st.removeMaquetas(quitar) }
        poner.forEach { (k, j) -> runCatching { st.saveMaqueta(k, j) } }
        cambiaron()
    }

    /** Casillas nuevas de esta versión (p. ej. «subrayados»): a las maquetas ya guardadas se les añade una vez,
     *  en su propia fila detrás de [after]; si luego la quitas, no vuelve. */
    fun withNew(ctx: Context, key: String, rows: List<MagRowSpec>, mod: String, after: String): List<MagRowSpec> {
        val flag = "nuevo:$mod:$key"
        val p = prefs(ctx)
        if (p.getBoolean(flag, false)) return rows
        p.edit().putBoolean(flag, true).apply()
        if (rows.any { r -> r.cells.any { it.type == mod } }) return rows
        val i = rows.indexOfFirst { r -> r.cells.any { it.type == after } }
        val out = rows.toMutableList().apply { add(if (i >= 0) i + 1 else size, MagRowSpec(listOf(MagCell(mod)))) }
        save(ctx, key, out)
        return out
    }
}

/** Las imágenes que se pueden poner en una casilla (las pone la app, que tiene las claves). */
object MagImages {
    /** Categorías de imagen de una categoría de la colección (p. ej. Portada, Fondo, Logo). */
    var categories: (Kind) -> List<String> = { emptyList() }
    /** Carga las imágenes (miniatura, imagen) de una categoría. */
    var load: suspend (Kind, Long, String, String) -> List<Pair<String, String>> = { _, _, _, _ -> emptyList() }
    /** Una imagen del móvil: de tus archivos (true) o de la galería (false). Es la última pestaña, «Archivo». */
    var pick: ((files: Boolean, (String) -> Unit) -> Unit)? = null
    const val FILE = "Archivo"
    /** Proporción (ancho / alto) de cada imagen ya cargada, para la forma «la de la foto». */
    val ratios = androidx.compose.runtime.mutableStateMapOf<String, Float>()
}

/** Proporción del marco de una imagen según su forma ([cell]: tamaño de la casilla entera, para «llenar»). */
fun magAspect(c: MagCell, cell: androidx.compose.ui.unit.IntSize?): Float = when (c.shape) {
    "vertical" -> 0.75f
    "cuadrada" -> 1f
    "apaisada" -> 1.4f
    "original" -> MagImages.ratios[c.image] ?: 1.4f
    "llenar" -> if (cell != null && cell.width > 0 && cell.height > 0) cell.width.toFloat() / cell.height else 0.75f
    else -> if (c.fit) 2.2f else 1.4f
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
    // (de nuevo cuando llega una maqueta de otro dispositivo)
    var rows by remember(key, MagLayouts.version) {
        mutableStateOf(MagLayouts.load(ctx, key, tpl)?.let { saved ->
            if ("subrayados" in defs) MagLayouts.withNew(ctx, key, saved, "subrayados", "lectura") else saved
        } ?: defaults)
    }
    fun set(n: List<MagRowSpec>) { rows = n; MagLayouts.save(ctx, key, n) }
    var choosing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editingText by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pickingImage by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var msg by remember { mutableStateOf("") }
    var framing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val cellSizes = remember { mutableStateMapOf<String, androidx.compose.ui.unit.IntSize>() }
    val cellOuter = remember { mutableStateMapOf<String, androidx.compose.ui.unit.IntSize>() }

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
                // La forma la eliges al encuadrar: llenar la casilla (lo que mida la fila), la de la foto, vertical, cuadrada o apaisada
                val fixed = LocalRowFixed.current
                val split = LocalCellFraction.current < 0.99f
                val fill = fixed || (c.shape == "llenar" && split)
                val shape = when {
                    fill -> Modifier.weight(1f).heightIn(min = 140.dp)
                    c.shape == "llenar" -> Modifier.aspectRatio(MagImages.ratios[c.image] ?: 0.75f)   // sola en la fila: la forma de la foto
                    else -> Modifier.aspectRatio(magAspect(c, null).coerceIn(0.3f, 4f))
                }
                Box(
                    Modifier.fillMaxWidth().then(shape)
                        .clipToBounds().background(if (c.image.isBlank()) Sand else androidx.compose.ui.graphics.Color.Transparent)
                        .onSizeChanged { cellSizes[k] = it }
                ) {
                    if (c.image.isNotBlank()) FramedImage(c, Modifier.matchParentSize())
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
                    Box(Modifier.weight(w).fillMaxHeight().clipToBounds().onSizeChanged { cellOuter["$i:$j"] = it }) {
                        Column(
                            Modifier.fillMaxWidth().fillMaxHeight()
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
                    if (MagImages.categories(kind).isNotEmpty() || MagImages.pick != null) ChoiceRow("Imagen", cur?.type == "imagen") { choosing = null; pickingImage = p }
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
            // El marco tiene la forma de la imagen en su casilla (y cambia si eliges otra forma)
            val k = "${p.first}:${p.second}"
            val sz = cellSizes[k]
            val now = if (sz != null && sz.width > 0 && sz.height > 0) sz.width.toFloat() / sz.height else magAspect(cur, cellOuter[k])
            FrameEditor(cur, { c -> if (c.shape == cur.shape) now else magAspect(c, cellOuter[k]) },
                onSave = { setCell(p, it); framing = null }, onDismiss = { framing = null })
        }
    }
    // Imagen
    pickingImage?.let { p ->
        val pick = MagImages.pick
        val cats = MagImages.categories(kind) + listOfNotNull(if (pick != null) MagImages.FILE else null)
        // Lo creado a mano no tiene imágenes en internet: se empieza en «Archivo»
        var cat by remember(p) { mutableStateOf(if (pick != null && !isWebId(itemId)) MagImages.FILE else cats.firstOrNull() ?: "") }
        var list by remember(p, cat) { mutableStateOf<List<Pair<String, String>>?>(null) }
        var err by remember(p, cat) { mutableStateOf("") }
        LaunchedEffect(p, cat) {
            list = null
            if (cat == MagImages.FILE) return@LaunchedEffect
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
                        cat == MagImages.FILE && pick != null -> Column {
                            Text("Una imagen de tu móvil para esta casilla.", color = Muted, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            val use: (String) -> Unit = { url -> setCell(p, MagCell("imagen", image = url, shape = "llenar")); pickingImage = null }
                            Tool("ELEGIR DE MIS ARCHIVOS\u00A0→", accent = true) { pick(true, use) }
                            Spacer(Modifier.height(12.dp))
                            Tool("DESDE LA GALERÍA\u00A0→") { pick(false, use) }
                            val nube = NubeHub.pickFromCloud
                            Internet.pick?.let { web -> Spacer(Modifier.height(12.dp)); Tool("BUSCAR EN INTERNET\u00A0→") { web(itemName, use) } }
                            if (nube != null) { Spacer(Modifier.height(12.dp)); Tool("DE TU NUBE\u00A0→") { nube(use) } }
                            Spacer(Modifier.height(12.dp))
                            Text(if (nube != null) "Se guarda en este móvil y en tu nube, en la carpeta de esta ficha. Luego puedes encuadrarla." else "Se guarda en este móvil: tu pareja y el PC no la verán. Luego puedes encuadrarla.", color = Muted, fontSize = 12.sp)
                        }
                        l == null -> Text("Cargando…", color = Muted)
                        l.isEmpty() -> Text(err.ifBlank { "No hay imágenes de este tipo." }, color = Muted)
                        else -> LazyVerticalGrid(GridCells.Fixed(if (cat == cats.firstOrNull() && cats.size > 1 && !kind.isBook) 3 else 2), Modifier.heightIn(max = 420.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(l) { (thumb, url) ->
                                AsyncImage(thumb, null, Modifier.fillMaxWidth().background(Sand).clickable {
                                    setCell(p, MagCell("imagen", image = url, fit = cat.contains("Logo", true), shape = "llenar")); pickingImage = null
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
        model = c.image, contentDescription = null,
        modifier = modifier.graphicsLayer {
            scaleX = c.zoom; scaleY = c.zoom
            translationX = c.ox * size.width; translationY = c.oy * size.height
        },
        contentScale = if (c.fit) ContentScale.Fit else ContentScale.Crop,
        colorFilter = Ediciones.filtro,
        onSuccess = { st: coil.compose.AsyncImagePainter.State.Success ->
            val sz = st.painter.intrinsicSize
            if (sz.width > 0f && sz.height > 0f && sz.width.isFinite()) MagImages.ratios[c.image] = sz.width / sz.height
        }
    )
}

/**
 * Encuadrar: la imagen dentro de un marco con la forma que tendrá; dos dedos amplían o reducen, uno la mueve.
 * Se elige la forma (llenar la casilla, la de la foto, vertical, cuadrada, apaisada) y si se ve la foto entera o recortada.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FrameEditor(c: MagCell, aspectOf: (MagCell) -> Float, onSave: (MagCell) -> Unit, onDismiss: () -> Unit) {
    var zoom by remember { mutableStateOf(c.zoom) }
    var ox by remember { mutableStateOf(c.ox) }
    var oy by remember { mutableStateOf(c.oy) }
    var shape by remember { mutableStateOf(c.shape.ifBlank { "llenar" }) }
    var whole by remember { mutableStateOf(c.fit) }
    var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }
    fun current() = c.copy(zoom = zoom, ox = ox, oy = oy, shape = shape, fit = whole)
    androidx.compose.ui.window.Dialog(onDismiss, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).background(Paper).verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Encuadrar", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Text("Pellizca para ampliar o reducir (puedes alejarla hasta verla entera). Arrastra para colocarla. Lo que quede dentro del marco es lo que se verá.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            Text("FORMA", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MAG_SHAPES.forEach { (id, name) -> Tool(name.uppercase(), accent = shape == id) { shape = id } }
            }
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tool("RECORTADA", accent = !whole) { whole = false }
                Tool("FOTO ENTERA", accent = whole) { whole = true }
            }
            val aspect = aspectOf(current()).coerceIn(0.3f, 4f)
            Box(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 0.9f)
                    .align(Alignment.CenterHorizontally).clipToBounds().background(Sand).border(2.dp, Charcoal)
                    .onSizeChanged { box = it }
                    .semantics { contentDescription = "Marco de la imagen" }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, z, _ ->
                            zoom = (zoom * z).coerceIn(0.2f, 6f)
                            ox = (ox + pan.x / box.width).coerceIn(-3f, 3f)
                            oy = (oy + pan.y / box.height).coerceIn(-3f, 3f)
                        }
                    }
            ) { FramedImage(current(), Modifier.matchParentSize()) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Tool("− REDUCIR") { zoom = (zoom / 1.15f).coerceIn(0.2f, 6f) }
                Tool("+ AMPLIAR") { zoom = (zoom * 1.15f).coerceIn(0.2f, 6f) }
                Tool("CENTRAR") { zoom = 1f; ox = 0f; oy = 0f }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.End)) {
                Tool("CANCELAR", onClick = onDismiss)
                Tool("GUARDAR", accent = true) { onSave(current()) }
            }
        }
    }
}

/** ¿La fila tiene alto fijo? (las imágenes llenan entonces la casilla) */
val LocalRowFixed = compositionLocalOf { false }

/** Nombres de los módulos (para el selector, cuando el módulo no trae antetítulo). */
val MAG_NAMES = mapOf("progreso" to "Progreso", "partes" to "Apuntes rápidos")

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
