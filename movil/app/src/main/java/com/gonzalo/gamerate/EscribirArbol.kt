package com.gonzalo.gamerate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/*
 * Escribir: el árbol de relaciones. Las fichas en óvalos sobre un lienzo libre, unidas por líneas con la palabra que tú
 * quieras en medio (enemigos, amantes, hogar, pertenencia…). Pulsar una ficha la abre; pulsar la palabra de una unión
 * enseña lo que escribiste de ella. Las fichas se colocan arrastrando (la posición viaja con la cuenta: ax, ay) y «Ordenar»
 * las reparte solas. Igual que el PC (escribir-arbol.js).
 */

object ArbolEscribir {
    /** Tamaño de cada ficha (semiejes y letra, en dp): el que elijas o, si no, según el tipo. */
    val TAM = mapOf(1 to Triple(60f, 31f, 14f), 2 to Triple(80f, 41f, 16.5f), 3 to Triple(104f, 55f, 20f))
    /** Letras por línea del nombre, según el tamaño (como el PC). */
    fun letras(t: Int) = when (t) { 1 -> 11; 2 -> 13; else -> 14 }
    /** El nombre en una o dos líneas, cortando por palabras, para que quepa en el óvalo (igual que el PC). */
    fun lineasNombre(nombre: String, max: Int): List<String> {
        val out = mutableListOf("")
        for (w in nombre.ifBlank { "Sin nombre" }.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val cur = out.last()
            when {
                cur.isEmpty() -> out[out.lastIndex] = w
                (cur + " " + w).length <= max -> out[out.lastIndex] = "$cur $w"
                else -> out += w
            }
        }
        if (out.size > 2) { while (out.size > 2) out.removeAt(out.lastIndex); out[1] = out[1].take(max - 1) + "…" }
        return out.map { if (it.length > max + 2) it.take(max + 1) + "…" else it }
    }
    fun tam(f: Doc): Int = f.i("tam").takeIf { it in 1..3 } ?: when (f.s("tipo")) { "lugar", "mundo" -> 3; "personaje" -> 2; else -> 1 }

    /** Dónde va cada ficha: la suya o, si aún no tiene, en círculo (sin guardar hasta que la muevas u ordenes). */
    fun posiciones(fs: List<Doc>): Map<String, Offset> {
        fun tiene(f: Doc) = f.o.has("ax") && f.o.opt("ax")?.toString()?.toDoubleOrNull() != null
        val sin = fs.filter { !tiene(it) }
        val p = LinkedHashMap<String, Offset>()
        fs.filter { tiene(it) }.forEach { p[it.id] = Offset(it.d("ax").toFloat(), it.d("ay").toFloat()) }
        val r = 140f + sin.size * 28f
        sin.forEachIndexed { i, f -> val t = (i.toDouble() / max(1, sin.size)) * PI * 2 - PI / 2; p[f.id] = Offset((r * cos(t)).roundToInt().toFloat(), (r * sin(t) * 0.7).roundToInt().toFloat()) }
        return p
    }

    /** «Ordenar»: muelles entre lo unido y repulsión entre todo, centrado en el origen (como el PC). */
    fun ordena(ids: List<String>, rels: List<Pair<String, String>>, ini: Map<String, Offset>): Map<String, Offset> {
        val px = DoubleArray(ids.size) { (ini[ids[it]]?.x ?: 0f).toDouble() }
        val py = DoubleArray(ids.size) { (ini[ids[it]]?.y ?: 0f).toDouble() }
        val idx = ids.withIndex().associate { it.value to it.index }
        val k = 270.0
        for (it in 0 until 400) {
            val t = 30 * (1 - it / 400.0) + 1
            val dx = DoubleArray(ids.size); val dy = DoubleArray(ids.size)
            for (i in ids.indices) for (j in i + 1 until ids.size) {
                var x = px[i] - px[j]; var y = py[i] - py[j]; var d = hypot(x, y)
                if (d < 0.01) { x = (i - j) * 0.1 + 0.05; y = 0.07; d = 0.1 }
                val f = k * k / d
                dx[i] += x / d * f; dy[i] += y / d * f; dx[j] -= x / d * f; dy[j] -= y / d * f
            }
            for ((a, b) in rels) {
                val i = idx[a] ?: continue; val j = idx[b] ?: continue
                val x = px[i] - px[j]; val y = py[i] - py[j]; val d = max(0.01, hypot(x, y))
                val f = d * d / k
                dx[i] -= x / d * f; dy[i] -= y / d * f; dx[j] += x / d * f; dy[j] += y / d * f
            }
            for (i in ids.indices) {
                dx[i] -= px[i] * 0.02 * k / 10; dy[i] -= py[i] * 0.03 * k / 10
                val m = max(0.01, hypot(dx[i], dy[i]))
                px[i] += dx[i] / m * min(m, t * 3); py[i] += dy[i] / m * min(m, t * 3)
            }
        }
        val cx = px.average(); val cy = py.average()
        return ids.withIndex().associate { (i, id) -> id to Offset((px[i] - cx).roundToInt().toFloat(), (py[i] - cy).roundToInt().toFloat()) }
    }
}

/** El desplazamiento y el zoom del lienzo de cada obra, mientras la app está abierta. */
private class VistaArbol(var x: Float = 0f, var y: Float = 0f, var k: Float = 1f, var puesta: Boolean = false)
private val vistas = HashMap<String, VistaArbol>()

@Composable
fun ArbolObra(e: Escritos, obra: Doc, ver: Int, a: AbreEscribir, foco: String?) {
    val pid = obra.id
    val fs = remember(ver, pid) { e.fichas(pid) }
    val rels = remember(ver, pid) { e.relaciones(pid).filter { e.get(it.s("a")) != null && e.get(it.s("b")) != null } }
    // Las posiciones se rehacen cuando cambia Escribir (otro dispositivo, o el guardado del arrastre anterior). La ficha que
    // llevas cogida conserva su posición de ahora: si no, durante un fotograma se dibujaba en la guardada (la de antes de
    // moverla) y daba un parpadeo al empezar a arrastrar.
    val cogida = remember(pid) { arrayOf<String?>(null) }
    val ultimo = remember(pid) { arrayOfNulls<Map<String, Offset>>(1) }
    val pos = remember(ver, pid) {
        mutableStateMapOf<String, Offset>().also { m ->
            m.putAll(ArbolEscribir.posiciones(fs))
            cogida[0]?.let { id -> ultimo[0]?.get(id)?.let { m[id] = it } }
            ultimo[0] = m
        }
    }
    val v = remember(pid) { vistas.getOrPut(pid) { VistaArbol() } }
    var tick by remember { mutableIntStateOf(0) } // repinta al mover el lienzo
    var lienzo by remember { mutableStateOf(IntSize.Zero) }
    var uniendo by remember { mutableStateOf(false) }
    var primera by remember { mutableStateOf<String?>(null) }
    var union by remember { mutableStateOf<Pair<String, String>?>(null) }
    var nueva by remember { mutableStateOf(false) }
    var ordenar by remember { mutableStateOf(false) }
    val dens = LocalDensity.current.density
    val medidor = rememberTextMeasurer()
    val tinta = Charcoal; val papel = Paper; val acento = ColGeneral
    val porId = fs.associateBy { it.id }
    // La ficha bajo el dedo (mientras la tocas o la arrastras): como el ratón encima en el PC, apaga lo que no está unido a ella
    var tocada by remember { mutableStateOf<String?>(null) }

    // Del mundo (dp, centrado en 0) a la pantalla (px) y al revés
    fun aPantalla(p: Offset) = Offset(v.x + p.x * dens * v.k, v.y + p.y * dens * v.k)
    fun aMundo(p: Offset) = Offset((p.x - v.x) / (dens * v.k), (p.y - v.y) / (dens * v.k))
    fun centra(soloId: String? = null) {
        if (lienzo.width == 0) return
        val ps = if (soloId != null && pos[soloId] != null) listOf(pos[soloId]!!) else pos.values.toList()
        if (ps.isEmpty()) { v.x = lienzo.width / 2f; v.y = lienzo.height / 2f; tick++; return }
        val minX = ps.minOf { it.x } - 110; val maxX = ps.maxOf { it.x } + 110
        val minY = ps.minOf { it.y } - 70; val maxY = ps.maxOf { it.y } + 70
        v.k = if (soloId != null) 1.2f else min(1.4f, max(0.3f, min(lienzo.width / dens / (maxX - minX), lienzo.height / dens / (maxY - minY))))
        v.x = lienzo.width / 2f - (minX + maxX) / 2 * dens * v.k; v.y = lienzo.height / 2f - (minY + maxY) / 2 * dens * v.k
        tick++
    }
    fun zoom(f: Float, c: Offset = Offset(lienzo.width / 2f, lienzo.height / 2f)) {
        val k = (v.k * f).coerceIn(0.25f, 3f)
        v.x = c.x - (c.x - v.x) * k / v.k; v.y = c.y - (c.y - v.y) * k / v.k; v.k = k; tick++
    }
    // Al entrar (o tras «Ordenar»), que se vea todo; si se viene de «Ver en el árbol», esa ficha
    var focoUsado by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lienzo, foco, ver) {
        if (lienzo.width == 0) return@LaunchedEffect
        if (foco != null && foco != focoUsado) { focoUsado = foco; v.puesta = true; centra(foco) }
        else if (!v.puesta) { v.puesta = true; centra() }
    }

    // Las uniones: su curva (varias entre las mismas dos fichas se curvan hacia lados distintos, separadas lo que miden sus
    // palabras; la normal es siempre la del par ordenado, así «a→b» y «b→a» no se anulan) y dónde va su palabra
    val estiloEtq = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
    val ALTO = 22f
    // Los textos se miden una sola vez (no en cada fotograma: al arrastrar con muchas fichas no daba tiempo y la ficha no
    // seguía al dedo); al acercar o alejar se dibujan escalados
    val etqs = remember(rels, tinta) { rels.associate { r -> val c = colorEscrito(r.s("color"), tinta); r.id to medidor.measure(r.s("titulo").ifBlank { "unión" }.uppercase(), estiloEtq.copy(color = c)) } }
    fun anchoEtq(r: Doc) = max(34f, (etqs[r.id]?.size?.width ?: 0) / dens + 22f)
    val textosNodo = remember(fs, tinta) {
        fs.associate { f ->
            val t = ArbolEscribir.tam(f); val fz = ArbolEscribir.TAM.getValue(t).third; val base = colorEscrito(f.s("color"))
            val ls2 = ArbolEscribir.lineasNombre(f.s("nombre"), ArbolEscribir.letras(t))
            val estilo = TextStyle(fontSize = fz.sp, fontWeight = FontWeight.Bold, color = tinta, fontFamily = Display, lineHeight = (fz * 1.08f).sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            f.id to (medidor.measure(ls2.joinToString("\n"), estilo) to medidor.measure(singularFicha(f.s("tipo")).uppercase(), TextStyle(fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = androidx.compose.ui.graphics.lerp(base, tinta, 0.35f))))
        }
    }
    class Linea(val r: Doc, val a: Offset, val b: Offset, val ctrl: Offset, val etq: Offset, val ancho: Float)
    fun lineas(): List<Linea> {
        val pares = rels.groupBy { listOf(it.s("a"), it.s("b")).sorted().joinToString("|") }
        return rels.mapNotNull { r ->
            val A = pos[r.s("a")] ?: return@mapNotNull null; val B = pos[r.s("b")] ?: return@mapNotNull null
            val fa = porId[r.s("a")] ?: return@mapNotNull null; val fb = porId[r.s("b")] ?: return@mapNotNull null
            val g = pares.getValue(listOf(r.s("a"), r.s("b")).sorted().joinToString("|")); val n = g.size; val i = g.indexOf(r)
            val lo = listOf(r.s("a"), r.s("b")).sorted()[0]
            val (P0, Q0) = if (lo == r.s("a")) A to B else B to A
            val dx = Q0.x - P0.x; val dy = Q0.y - P0.y; val len = max(1f, hypot(dx, dy))
            val nx = -dy / len; val ny = dx / len
            val sep = g.maxOf { kotlin.math.abs(nx) * anchoEtq(it) / 2 + kotlin.math.abs(ny) * ALTO / 2 } * 2 + 10
            val off = if (n > 1) (i - (n - 1) / 2f) * sep * 2 else 0f
            val m = Offset((A.x + B.x) / 2 + nx * off, (A.y + B.y) / 2 + ny * off)
            // (las líneas acaban en el borde de cada óvalo, un poco antes si llevan flecha)
            fun borde(f: Doc, d: Offset, extra: Float): Offset { val (rx, ry, _) = ArbolEscribir.TAM.getValue(ArbolEscribir.tam(f)); val l = hypot(d.x / (rx + extra), d.y / (ry + extra)).takeIf { it > 0 } ?: 1f; return Offset(d.x / l, d.y / l) }
            Linea(r, A + borde(fa, m - A, 3f), B + borde(fb, m - B, if (r.i("flecha") == 1) 6f else 3f), m, Offset((A.x + B.x) / 4 + m.x / 2, (A.y + B.y) / 4 + m.y / 2), anchoEtq(r))
        }
    }
    fun rectEtq(l: Linea) = Rect(l.etq.x - l.ancho / 2, l.etq.y - ALTO / 2, l.etq.x + l.ancho / 2, l.etq.y + ALTO / 2)
    fun nodoEn(p: Offset): Doc? {
        val m = aMundo(p)
        return fs.lastOrNull { f -> val c = pos[f.id] ?: return@lastOrNull false; val (rx, ry, _) = ArbolEscribir.TAM.getValue(ArbolEscribir.tam(f)); val dx = (m.x - c.x) / rx; val dy = (m.y - c.y) / ry; dx * dx + dy * dy <= 1f }
    }
    fun etqEn(p: Offset): Doc? { val m = aMundo(p); return lineas().lastOrNull { rectEtq(it).inflate(4f).contains(m) }?.r }
    fun pulsa(p: Offset) {
        etqEn(p)?.let { a.ver = it.id; return }
        val f = nodoEn(p) ?: return
        if (uniendo) {
            val x = primera
            when {
                x == null -> primera = f.id
                x == f.id -> primera = null
                else -> { union = x to f.id; primera = null; uniendo = false }
            }
        } else a.ver = f.id
    }
    val slop = LocalViewConfiguration.current.touchSlop
    val scope = rememberCoroutineScope()
    // «Ordenar»: las fichas van a su sitio nuevo con una animación corta y luego se guarda
    fun ordenarAhora() {
        val destino = ArbolEscribir.ordena(fs.map { it.id }, rels.map { it.s("a") to it.s("b") }, pos)
        val desde = pos.toMap()
        scope.launch {
            val t0 = withFrameNanos { it }
            while (true) {
                val t = withFrameNanos { it }
                val u = ((t - t0) / 650_000_000f).coerceIn(0f, 1f); val q = 1 - (1 - u) * (1 - u) * (1 - u)
                fs.forEach { f -> val a0 = desde[f.id] ?: return@forEach; val b0 = destino[f.id] ?: return@forEach; pos[f.id] = a0 + (b0 - a0) * q }
                if (u >= 1f) break
            }
            centra()
            fs.forEach { f -> destino[f.id]?.let { q -> e.edita(f.id) { put("ax", q.x.roundToInt()); put("ay", q.y.roundToInt()) } } }
        }
    }
    // El gesto dura más que una composición: lee siempre el mapa de posiciones y las funciones de ahora (si no, al
    // cambiar algo en Escribir movía una copia vieja y la ficha no seguía al dedo hasta soltarla)
    val ahora by rememberUpdatedState(Triple(pos, ::nodoEn, ::etqEn))
    val pulsaAhora by rememberUpdatedState(::pulsa)
    // Lo unido a la ficha elegida (lo demás se apaga un poco). Se calcula al dibujar, no al componer: tocar una ficha no
    // recompone todo el árbol (eso metía un tirón justo al empezar a arrastrarla)
    fun vecinosDe(id: String?): Set<String>? = id?.let { x -> (rels.filter { it.s("a") == x || it.s("b") == x }.flatMap { listOf(it.s("a"), it.s("b")) } + x).toSet() }

    Column(Modifier.fillMaxSize()) {
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonEscribir("Nueva ficha") { nueva = true }
            if (fs.size >= 2) BotonEscribir(if (uniendo) "Dejar de unir" else "Unir dos fichas", suave = !uniendo) { uniendo = !uniendo; primera = null }
            if (fs.size >= 2) EnlaceRevista("Ordenar") { ordenar = true }
        }
        if (fs.isNotEmpty()) Text(
            when {
                uniendo && primera == null -> "Pulsa la primera ficha."
                uniendo -> "Ahora pulsa la ficha con la que unir a ${porId[primera]?.s("nombre").orEmpty()}."
                else -> "Arrastra las fichas para colocarlas y el fondo para moverte; pellizca para acercar. Pulsa una ficha para verla y la palabra de una línea para leer la unión."
            }, color = Muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 8.dp)
        )
        if (fs.isEmpty()) {
            Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("El árbol de tu historia", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Charcoal)
                Text("Crea fichas (personajes, lugares, objetos…) y únelas: «enemigos», «amantes», «hogar», «pertenencia»… Con todo lo que quieras contar de cada unión.", color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            }
        } else Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = dockSpace(8.dp)).clipToBounds().background(Crudo).border(1.dp, Line).onSizeChanged { lienzo = it }
            .semantics { contentDescription = "Árbol de relaciones" }
            // (no se reinicia cuando cambian las fichas —una sincronización, un guardado—: cortaría el arrastre a medias; lo que
            // cambia lo lee de «ahora» y «pulsaAhora». Sí se reinicia al activar o quitar «Unir», y cada gesto está protegido:
            // si uno muriera o se quedara a medias, el árbol dejaría de responder hasta cambiar de pestaña)
            .pointerInput(uniendo) {
                // Un dedo: arrastrar una ficha (la mueve y se guarda) o el lienzo; dos: acercar; pulsar sin moverse: abrir
                awaitEachGesture {
                  try {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val nodo = ahora.second(down.position)?.takeIf { ahora.third(down.position) == null }
                    var arrastre = Offset.Zero
                    var inicio = Offset.Zero
                    var sujeta = false
                    tocada = nodo?.id
                    var movido = false; var dos = false; var recorrido = 0f
                    while (true) {
                        val ev = awaitPointerEvent()
                        val activos = ev.changes.filter { it.pressed }
                        if (activos.isEmpty()) break
                        if (activos.size >= 2) {
                            dos = true; movido = true
                            val z = ev.calculateZoom(); val c = ev.calculateCentroid(); val pan = ev.calculatePan()
                            v.x += pan.x; v.y += pan.y
                            if (z != 1f && c != Offset.Unspecified) zoom(z, c) else tick++
                        } else if (!dos) {
                            val d = activos[0].positionChange()
                            recorrido += d.getDistance()
                            if (!movido && recorrido > slop) movido = true
                            if (movido) {
                                // La ficha se coge en el momento en que el dedo pasa el margen del toque: desde ahí, lo que
                                // arrastres (sin saltar de golpe hacia el dedo). Parte de la posición de ahora, así que sigue
                                // al dedo aunque el mapa se renueve a mitad.
                                if (nodo != null) {
                                    if (!sujeta) { sujeta = true; cogida[0] = nodo.id; inicio = ahora.first[nodo.id] ?: Offset.Zero; arrastre = Offset.Zero }
                                    else arrastre += d
                                    ahora.first[nodo.id] = inicio + arrastre / (dens * v.k)
                                } else { v.x += d.x; v.y += d.y; tick++ }
                            }
                        }
                        ev.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                    }
                    tocada = null
                    if (!movido) pulsaAhora(down.position)
                    else if (nodo != null && !dos) ahora.first[nodo.id]?.let { p -> e.edita(nodo.id) { put("ax", p.x.roundToInt()); put("ay", p.y.roundToInt()) } }
                    cogida[0] = null
                  } catch (x: kotlinx.coroutines.CancellationException) { tocada = null; cogida[0] = null; throw x
                  } catch (x: Exception) { tocada = null; cogida[0] = null }
                }
            }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                tick // (lee el contador: repinta al mover el lienzo)
                val k = dens * v.k
                fun P(p: Offset) = Offset(v.x + p.x * k, v.y + p.y * k)
                val esc = v.k.coerceAtLeast(0.6f)
                // La rejilla de puntos, que se mueve y crece con el lienzo (como papel cuadriculado)
                val paso = 24f * k
                if (paso >= 8f) {
                    val puntos = ArrayList<Offset>()
                    var x = ((v.x % paso) + paso) % paso
                    while (x < size.width) { var y = ((v.y % paso) + paso) % paso; while (y < size.height) { puntos += Offset(x, y); y += paso }; x += paso }
                    drawPoints(puntos, androidx.compose.ui.graphics.PointMode.Points, tinta.copy(alpha = 0.16f), strokeWidth = 2.2f * dens * 0.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                val elegida = primera ?: tocada ?: foco
                val vecinos = vecinosDe(elegida)
                fun apagado(id: String) = vecinos != null && id !in vecinos
                val ls = lineas()
                ls.forEach { l ->
                    val conColor = l.r.s("color").isNotBlank()
                    val tenue = vecinos != null && (apagado(l.r.s("a")) || apagado(l.r.s("b")))
                    val resaltada = vecinos != null && !tenue
                    val c = colorEscrito(l.r.s("color"), tinta).copy(alpha = when { tenue -> 0.12f; resaltada -> 1f; conColor -> 0.85f; else -> 0.62f })
                    val path = Path().apply { moveTo(P(l.a).x, P(l.a).y); quadraticTo(P(l.ctrl).x, P(l.ctrl).y, P(l.b).x, P(l.b).y) }
                    drawPath(path, c, style = Stroke((if (resaltada) 2.4f else 1.7f) * dens * esc, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    if (l.r.i("flecha") == 1) {
                        // la punta, en la dirección de la curva al llegar a «b»
                        val ang = atan2(l.b.y - l.ctrl.y, l.b.x - l.ctrl.x)
                        val t = P(l.b); val s = 9f * dens * esc
                        val pa = Path().apply {
                            moveTo(t.x, t.y)
                            lineTo(t.x - s * cos(ang - 0.42f), t.y - s * sin(ang - 0.42f))
                            lineTo(t.x - s * 0.72f * cos(ang), t.y - s * 0.72f * sin(ang))
                            lineTo(t.x - s * cos(ang + 0.42f), t.y - s * sin(ang + 0.42f)); close()
                        }
                        drawPath(pa, c)
                    }
                }
                // Las fichas: óvalos con un tinte de su color, borde de su color, sombra suave, el nombre y el tipo
                fs.forEach { f ->
                    val c = pos[f.id] ?: return@forEach
                    val t = ArbolEscribir.tam(f)
                    val (rx, ry, fz) = ArbolEscribir.TAM.getValue(t)
                    val alfa = if (apagado(f.id)) 0.28f else 1f
                    val base = colorEscrito(f.s("color"))
                    val centro = P(c)
                    val tl = Offset(centro.x - rx * k, centro.y - ry * k); val sz = Size(rx * 2 * k, ry * 2 * k)
                    // Sombra suave en capas (un desenfoque de verdad se calcula con el procesador en cada fotograma: muy lento)
                    for ((ext, a0) in listOf(3.5f to 0.05f, 2f to 0.07f, 0.8f to 0.08f)) {
                        val e2 = ext * k
                        drawOval(Color.Black.copy(alpha = a0 * alfa), Offset(tl.x - e2, tl.y + 3f * k - e2 * 0.6f), Size(sz.width + 2 * e2, sz.height + 2 * e2 * 0.8f))
                    }
                    drawOval(androidx.compose.ui.graphics.lerp(base, papel, 0.84f).copy(alpha = alfa), tl, sz)
                    val on = f.id == elegida
                    drawOval((if (on) acento else base).copy(alpha = alfa), tl, sz, style = Stroke((if (on) 4f else 2.2f) * dens * esc))
                    if (t == 3) {
                        val d = 6f * k
                        drawOval(base.copy(alpha = 0.45f * alfa), Offset(tl.x + d, tl.y + d), Size(sz.width - 2 * d, sz.height - 2 * d),
                            style = Stroke(1f * dens * esc, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2f * dens, 3f * dens))))
                    }
                    // (el nombre, centrado, en la letra de la revista; el tipo, en versalitas debajo)
                    val (nombre, tipo) = textosNodo[f.id] ?: return@forEach
                    val alto = nombre.size.height + tipo.size.height
                    scale(v.k, v.k, pivot = centro) {
                        drawText(nombre, topLeft = Offset(centro.x - nombre.size.width / 2f, centro.y - alto / 2f), alpha = alfa)
                        drawText(tipo, topLeft = Offset(centro.x - tipo.size.width / 2f, centro.y - alto / 2f + nombre.size.height), alpha = alfa)
                    }
                }
                // Las palabras, en una píldora, encima de las fichas (que no las tape ninguna)
                ls.forEach { l ->
                    val tenue = vecinos != null && (apagado(l.r.s("a")) || apagado(l.r.s("b")))
                    val alfa = if (tenue) 0.28f else 1f
                    val c = colorEscrito(l.r.s("color"), tinta)
                    val r = rectEtq(l)
                    val tl = P(Offset(r.left, r.top)); val sz = Size(r.width * k, r.height * k); val rad = CornerRadius(sz.height / 2)
                    drawRoundRect(papel.copy(alpha = alfa), tl, sz, rad)
                    drawRoundRect(c.copy(alpha = 0.55f * alfa), tl, sz, rad, style = Stroke(1f * dens))
                    val res = etqs[l.r.id] ?: return@forEach
                    val centro = P(l.etq)
                    scale(v.k, v.k, pivot = centro) { drawText(res, topLeft = Offset(centro.x - res.size.width / 2f, centro.y - res.size.height / 2f), alpha = alfa) }
                }
            }
        }
    }
    if (nueva) FichaDialogo(e, pid, null, onCerrar = { nueva = false }, onCreada = { f ->
        // La ficha nueva, en el centro de lo que se ve
        val m = aMundo(Offset(lienzo.width / 2f, lienzo.height / 2f))
        e.edita(f.id) { put("ax", m.x.roundToInt()); put("ay", m.y.roundToInt()) }
    })
    union?.let { (x, y) -> RelacionDialogo(e, pid, null, onCerrar = { union = null }, aNueva = x, bNueva = y) }
    if (ordenar) HojaEscribir("¿Ordenar el árbol?", { ordenar = false }) {
        Text("Atrio colocará todas las fichas solas (lo unido, cerca). Después puedes moverlas a mano.", color = Muted, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonEscribir("Ordenar") { ordenar = false; ordenarAhora() }
            BotonEscribir("No", suave = true) { ordenar = false }
        }
    }
}
