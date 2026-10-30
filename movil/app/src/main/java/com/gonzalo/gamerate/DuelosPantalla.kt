package com.gonzalo.gamerate

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** Lo que llevas hecho de los duelos de libros: se guarda solo para poder dejarlo y seguir otro día. */
class DuelosEstado(ctx: Context) {
    private val p = ctx.getSharedPreferences("duelos", Context.MODE_PRIVATE)

    var pool: List<Long>
        get() = ids("pool")
        set(v) { p.edit().putString("pool", JSONArray(v).toString()).commit() }
    /** Libros que ya pasaron por unos duelos y se aplicaron: no se vuelven a ofrecer. */
    var ordenados: Set<Long>
        get() = ids("ordenados").toSet()
        set(v) { p.edit().putString("ordenados", JSONArray(v.toList()).toString()).commit() }
    var duelos: List<Duelos.Duelo>
        get() = try { JSONArray(p.getString("duelos", "[]")).let { a -> (0 until a.length()).map { val o = a.getJSONArray(it); Duelos.Duelo(o.getLong(0), o.getLong(1), o.getInt(2)) } } } catch (e: Exception) { emptyList() }
        set(v) { p.edit().putString("duelos", JSONArray().apply { v.forEach { put(JSONArray().put(it.a).put(it.b).put(it.res)) } }.toString()).commit() }
    var saltados: Set<Pair<Long, Long>>
        get() = try { JSONArray(p.getString("saltados", "[]")).let { a -> (0 until a.length()).map { val o = a.getJSONArray(it); o.getLong(0) to o.getLong(1) }.toSet() } } catch (e: Exception) { emptySet() }
        set(v) { p.edit().putString("saltados", JSONArray().apply { v.forEach { put(JSONArray().put(it.first).put(it.second)) } }.toString()).commit() }

    /** Las notas de antes del último reparto (id → las cinco), para poder deshacerlo. */
    var copia: Map<Long, List<Int>>
        get() = try { JSONObject(p.getString("copia", "{}")).let { o -> o.keys().asSequence().associate { k -> k.toLong() to o.getJSONArray(k).let { a -> (0 until a.length()).map { i -> a.getInt(i) } } } } } catch (e: Exception) { emptyMap() }
        set(v) { p.edit().putString("copia", JSONObject().apply { v.forEach { (k, l) -> put(k.toString(), JSONArray(l)) } }.toString()).commit() }

    fun reiniciar() { p.edit().remove("pool").remove("duelos").remove("saltados").commit() }

    private fun ids(k: String): List<Long> = try { JSONArray(p.getString(k, "[]")).let { a -> (0 until a.length()).map { a.getLong(it) } } } catch (e: Exception) { emptyList() }

    companion object {
        /** Un libro con nota de estrellas: redonda (10, 20… 100) y sin notas sueltas aparte. */
        fun redonda(g: Game) = g.general in 10..100 && g.general % 10 == 0 && listOf(g.gfx, g.story, g.music, g.gameplay).all { it == 50 || it == g.general }

        /** El libro con la nota nueva (las cinco, como llegaron al importar). */
        fun conNota(g: Game, n: Int) = g.copy(
            general = n,
            gfx = if (g.gfx == g.general || g.gfx == 50) n else g.gfx,
            story = if (g.story == g.general || g.story == 50) n else g.story,
            music = if (g.music == g.general || g.music == 50) n else g.music,
            gameplay = if (g.gameplay == g.general || g.gameplay == 50) n else g.gameplay,
            updated = System.currentTimeMillis()
        )
    }
}

private enum class Fase { INTRO, DUELO, NOTAS }

/**
 * Ordenar los libros por duelos (Perfil y Ajustes): dos libros, tocas el que más te gustó. Ver [Duelos].
 */
@Composable
fun DuelosScreen(books: List<Game>, onSave: (List<Game>) -> Unit, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val estado = remember { DuelosEstado(ctx) }
    var fase by remember { mutableStateOf(Fase.INTRO) }
    var duelos by remember { mutableStateOf(estado.duelos) }
    var saltados by remember { mutableStateOf(estado.saltados) }
    var pool by remember { mutableStateOf(estado.pool) }
    var hecho by remember { mutableStateOf(0) }   // lo aplicado, para el mensaje

    val porId = remember(books) { books.associateBy { it.id } }
    // Lo que se ordena: lo elegido al empezar (los que sigan existiendo); si no, lo redondo que aún no se ha ordenado
    val libros = remember(books, pool) {
        if (pool.isNotEmpty()) pool.mapNotNull { porId[it] }
        else books.filter { DuelosEstado.redonda(it) && it.id !in estado.ordenados }
    }
    val notas = remember(libros) { libros.associate { it.id to it.general } }
    val enCurso = pool.isNotEmpty() && duelos.isNotEmpty()

    BackHandler(enabled = fase != Fase.INTRO) { fase = Fase.INTRO }

    when (fase) {
        Fase.INTRO -> DuelosIntro(libros.size, duelos.size, enCurso, estado.copia.isNotEmpty(), hecho, onBack,
            onEmpezar = { de0 ->
                if (de0 || pool.isEmpty()) { estado.reiniciar(); duelos = emptyList(); saltados = emptySet(); pool = libros.map { it.id }; estado.pool = pool }
                fase = Fase.DUELO
            },
            onDeshacer = {
                val c = estado.copia
                val volver = books.filter { it.id in c.keys }.map { g -> val l = c[g.id]!!; g.copy(general = l[0], gfx = l[1], story = l[2], music = l[3], gameplay = l[4], updated = System.currentTimeMillis()) }
                if (volver.isNotEmpty()) onSave(volver)
                estado.ordenados = estado.ordenados - c.keys; estado.copia = emptyMap(); hecho = 0
            })
        Fase.DUELO -> DuelosJuego(libros, notas, duelos, saltados,
            onResponder = { d -> duelos = duelos + d; estado.duelos = duelos },
            onSaltar = { s -> saltados = saltados + s; estado.saltados = saltados },
            onDeshacer = { if (duelos.isNotEmpty()) { duelos = duelos.dropLast(1); estado.duelos = duelos } },
            onTerminar = { fase = Fase.NOTAS },
            onSalir = { fase = Fase.INTRO })
        Fase.NOTAS -> DuelosNotas(libros, notas, duelos, onBack = { fase = Fase.DUELO },
            onAplicar = { nuevas ->
                val cambiados = libros.map { DuelosEstado.conNota(it, nuevas[it.id] ?: it.general) }
                estado.copia = libros.associate { it.id to listOf(it.general, it.gfx, it.story, it.music, it.gameplay) }
                estado.ordenados = estado.ordenados + libros.map { it.id }
                onSave(cambiados)
                estado.reiniciar(); duelos = emptyList(); saltados = emptySet(); pool = emptyList()
                hecho = cambiados.size
                fase = Fase.INTRO
            })
    }
}

@Composable
private fun DuelosIntro(
    n: Int, hechos: Int, enCurso: Boolean, hayCopia: Boolean, aplicados: Int, onBack: () -> Unit, onEmpezar: (deCero: Boolean) -> Unit, onDeshacer: () -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        TopBar("Ajustes", onBack, "Duelos de libros")
        Text(
            "Para libros con nota de estrellas (muchos 100, 90, 80…). Te enseño dos libros y tocas el que más te gustó; con las respuestas se ordenan " +
                "y después eliges cómo repartir las notas. Sin empates, y puedes dejarlo cuando quieras: se guarda solo.",
            color = Muted, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 16.dp)
        )
        if (aplicados > 0) Text("Notas cambiadas en $aplicados libros.", color = ColGeneral, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
        if (n < 3) {
            Text(if (aplicados > 0) "Ya no quedan libros con nota de estrellas por ordenar." else "No hay libros con nota de estrellas (10, 20… 100) para ordenar.",
                fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp, color = Muted, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            val total = n * Duelos.OBJETIVO / 2
            Text("$n libros", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 40.sp)
            Text("Unos $total duelos en total (cada uno, 3 o 4 segundos): se hacen a ratos, de 20 en 20.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 18.dp))
            Text(if (enCurso) "SEGUIR · LLEVAS $hechos DUELOS\u00A0→" else "EMPEZAR\u00A0→", color = ColGeneral, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.press { onEmpezar(false) }.padding(vertical = 10.dp))
            if (enCurso) Text("EMPEZAR DE CERO", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.press { onEmpezar(true) }.padding(vertical = 10.dp))
        }
        if (hayCopia) {
            Spacer(Modifier.height(16.dp))
            Text("DESHACER EL ÚLTIMO REPARTO", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.press { onDeshacer() }.padding(vertical = 10.dp))
            Text("Devuelve a esos libros las notas que tenían antes.", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DuelosJuego(
    libros: List<Game>, notas: Map<Long, Int>, duelos: List<Duelos.Duelo>, saltados: Set<Pair<Long, Long>>,
    onResponder: (Duelos.Duelo) -> Unit, onSaltar: (Pair<Long, Long>) -> Unit, onDeshacer: () -> Unit, onTerminar: () -> Unit, onSalir: () -> Unit
) {
    val porId = remember(libros) { libros.associateBy { it.id } }
    var ultimo by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var sesion by remember { mutableStateOf(0) }
    var descanso by remember { mutableStateOf(false) }
    val par = remember(duelos.size, saltados.size, libros) { Duelos.siguiente(notas, duelos, saltados, ultimo) }
    val avance = Duelos.avance(notas, duelos)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            TopBar("Duelos de libros", onSalir, "${duelos.size} duelos")
            Box(Modifier.fillMaxWidth().height(4.dp).background(Line)) { Box(Modifier.fillMaxWidth(avance).height(4.dp).background(ColGeneral)) }
            Text(if (avance >= 1f) "Ya está bien ordenado: puedes ver las notas o seguir afinando." else "Faltan unos ${Duelos.faltan(notas, duelos)} duelos.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            val a = par?.let { porId[it.first] }; val b = par?.let { porId[it.second] }
            when {
                descanso -> Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Llevas $sesion seguidos", fontFamily = Display, fontSize = 26.sp, textAlign = TextAlign.Center)
                    Text("Todo está guardado. Puedes dejarlo por hoy y seguir otro día.", color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
                    Text("SEGUIR UN POCO MÁS\u00A0→", color = ColGeneral, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press { descanso = false }.padding(10.dp))
                    Text("POR HOY YA ESTÁ", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press { onSalir() }.padding(10.dp))
                }
                a == null || b == null -> Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No queda nada útil que preguntar.", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = Muted)
                }
                else -> {
                    Text("¿Cuál te gustó más?", fontFamily = Display, fontSize = 26.sp, modifier = Modifier.padding(bottom = 12.dp))
                    fun responde(res: Int) {
                        onResponder(Duelos.Duelo(a.id, b.id, res)); ultimo = a.id to b.id; sesion++
                        if (sesion % 20 == 0) descanso = true
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DueloCarta(a, Modifier.weight(1f)) { responde(1) }
                        DueloCarta(b, Modifier.weight(1f)) { responde(-1) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ME GUSTAN IGUAL", color = Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, modifier = Modifier.press { responde(0) }.padding(vertical = 10.dp))
                        Text("NO LOS CONOZCO", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp,
                            modifier = Modifier.press { onSaltar(a.id to b.id); ultimo = a.id to b.id }.padding(vertical = 10.dp))
                    }
                    if (duelos.isNotEmpty()) Text("DESHACER LA ÚLTIMA", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp,
                        modifier = Modifier.press { onDeshacer(); if (sesion > 0) sesion-- }.padding(vertical = 10.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val listo = avance >= 0.25f
            Text(if (listo) "Se guarda solo. Puedes parar cuando quieras." else "Haz unos cuantos duelos para ver las notas.", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("VER NOTAS\u00A0→", color = if (listo) ColGeneral else Muted, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.press(haptic = listo) { if (listo) onTerminar() }.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun DueloCarta(g: Game, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.press { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Sand)) {
            if (g.cover.isNotBlank()) coil.compose.AsyncImage(g.cover, g.name, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, colorFilter = Ediciones.filtro)
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(g.name.take(1).uppercase(), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 48.sp, color = Muted) }
        }
        Text(g.name, fontFamily = Display, fontSize = 18.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (g.creator.isNotBlank()) Text(g.creator.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DuelosNotas(libros: List<Game>, notas: Map<Long, Int>, duelos: List<Duelos.Duelo>, onBack: () -> Unit, onAplicar: (Map<Long, Int>) -> Unit) {
    val nombres = remember(libros) { libros.associate { it.id to it.name } }
    val porId = remember(libros) { libros.associateBy { it.id } }
    val orden = remember(duelos, libros) { Duelos.orden(notas, duelos, nombres) }
    var baremo by remember { mutableStateOf(Duelos.Baremo.EQUILIBRADO) }
    val nuevas = remember(orden, baremo) { Duelos.notas(orden, baremo) }
    var confirmar by remember { mutableStateOf(false) }
    val repetidas = nuevas.values.groupBy { it }.count { it.value.size > 1 }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp)) {
            item {
                TopBar("Duelos de libros", onBack, "Las notas")
                Text("Este es el orden que sale de tus respuestas. Elige cómo repartir las notas y mira cómo quedan antes de aplicarlas.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    SoftChip("Exigente", baremo == Duelos.Baremo.EXIGENTE) { baremo = Duelos.Baremo.EXIGENTE }
                    SoftChip("Equilibrado", baremo == Duelos.Baremo.EQUILIBRADO) { baremo = Duelos.Baremo.EQUILIBRADO }
                    SoftChip("Generoso", baremo == Duelos.Baremo.GENEROSO) { baremo = Duelos.Baremo.GENEROSO }
                }
                Text("La mejor: ${baremo.tope}", fontSize = 13.sp, color = Muted)
                Slider(baremo.tope.toFloat(), { baremo = baremo.copy(tope = it.toInt().coerceAtLeast(baremo.suelo + 1)) }, valueRange = 60f..100f,
                    colors = SliderDefaults.colors(thumbColor = ColGeneral, activeTrackColor = ColGeneral, inactiveTrackColor = Line))
                Text("La peor: ${baremo.suelo}", fontSize = 13.sp, color = Muted)
                Slider(baremo.suelo.toFloat(), { baremo = baremo.copy(suelo = it.toInt().coerceAtMost(baremo.tope - 1)) }, valueRange = 1f..80f,
                    colors = SliderDefaults.colors(thumbColor = ColGeneral, activeTrackColor = ColGeneral, inactiveTrackColor = Line))
                Text("Muchas notas altas ←  → pocas notas altas", fontSize = 12.sp, color = Muted)
                Slider(baremo.exigencia.toFloat(), { baremo = baremo.copy(exigencia = it.toDouble()) }, valueRange = 0.6f..2.2f, modifier = Modifier.padding(bottom = 4.dp),
                    colors = SliderDefaults.colors(thumbColor = ColGeneral, activeTrackColor = ColGeneral, inactiveTrackColor = Line))
                val sobre90 = nuevas.values.count { it >= 90 }; val entre = nuevas.values.count { it in 70..89 }; val bajo = nuevas.values.count { it < 70 }
                Text("$sobre90 de 90 o más · $entre de 70 a 89 · $bajo por debajo de 70" + if (repetidas > 0) " · $repetidas notas repetidas (no caben todas)" else " · sin repetidas",
                    color = Charcoal, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
            }
            itemsIndexed(orden, key = { _, id -> id }) { i, id ->
                val g = porId[id] ?: return@itemsIndexed
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = Muted, fontSize = 13.sp, modifier = Modifier.width(34.dp))
                    if (Skin.listCovers) { ListCover(g.cover, g.name, 36.dp, 52.dp); Spacer(Modifier.width(10.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(g.name, fontFamily = Display, fontSize = 17.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (g.creator.isNotBlank()) Text(g.creator.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${g.general}", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    Text("→", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    Text("${nuevas[id] ?: g.general}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = ColGeneral, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("← SEGUIR CON DUELOS", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, modifier = Modifier.weight(1f).press { onBack() }.padding(vertical = 8.dp))
            Text("APLICAR\u00A0→", color = ColGeneral, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press { confirmar = true }.padding(vertical = 8.dp))
        }
    }
    if (confirmar) AlertDialog(
        onDismissRequest = { confirmar = false },
        title = { Text("Cambiar ${libros.size} notas", fontFamily = Display) },
        text = { Text("Se les pondrá la nota nueva. Después podrás deshacerlo desde esta misma pantalla (devuelve las de antes).") },
        confirmButton = { TextButton({ confirmar = false; onAplicar(nuevas) }) { Text("Aplicar") } },
        dismissButton = { TextButton({ confirmar = false }) { Text("Cancelar") } }
    )
}
