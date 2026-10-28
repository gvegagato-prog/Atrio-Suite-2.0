@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Un sprint ya terminado: cuándo empezó, cuántos minutos, cuántas palabras y la meta (0 si no había). */
class Sprint(val pid: String, val desde: Long, val minutos: Int, val palabras: Int, val meta: Int) {
    val porMinuto: Int get() = if (minutos > 0) palabras / minutos else 0
}

/**
 * Sprints de escritura: escribir sin parar durante un rato (10, 25, 45 min…), con una meta de palabras si quieres. Cuentan
 * las palabras nuevas de la obra (en cualquier escena). Sigue aunque salgas del editor o cierres la app, y lo hecho se
 * guarda en este dispositivo (Cifras de la obra).
 */
object SprintEscribir {
    var pid by mutableStateOf("")
        private set
    var hasta by mutableLongStateOf(0L)
        private set
    private var desde = 0L
    private var base = 0
    private var dia = 0L
    var meta by mutableIntStateOf(0)
        private set
    var minutos = 0
        private set
    /** El último que terminó (para enseñarlo); se pone a null al verlo. */
    var terminado by mutableStateOf<Sprint?>(null)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("escribir", Context.MODE_PRIVATE)

    /** Lo recupera si se cerró la app con uno en marcha. */
    fun recupera(ctx: Context, e: Escritos) {
        if (hasta > 0L) return
        val o = runCatching { JSONObject(prefs(ctx).getString("sprint", "") ?: "") }.getOrNull() ?: return
        pid = o.optString("pid"); desde = o.optLong("desde"); hasta = o.optLong("hasta"); base = o.optInt("base"); dia = o.optLong("dia")
        meta = o.optInt("meta"); minutos = o.optInt("min")
        if (hasta in 1..System.currentTimeMillis()) termina(ctx, e)
    }

    fun activo(p: String? = null) = hasta > System.currentTimeMillis() && (p == null || p == pid)

    fun empieza(ctx: Context, e: Escritos, obra: String, min: Int, metaPalabras: Int) {
        pid = obra; minutos = min; meta = metaPalabras
        desde = System.currentTimeMillis(); hasta = desde + min * 60_000L
        dia = Escritos.hoy(); base = e.escritas(dia, obra)
        prefs(ctx).edit().putString("sprint", JSONObject().put("pid", pid).put("desde", desde).put("hasta", hasta).put("base", base).put("dia", dia).put("meta", meta).put("min", minutos).toString()).apply()
    }

    /** Palabras escritas en la obra desde que empezó. */
    fun llevas(e: Escritos): Int {
        if (hasta == 0L) return 0
        val hoy = Escritos.hoy()
        return ((e.escritas(dia, pid) + if (hoy != dia) e.escritas(hoy, pid) else 0) - base).coerceAtLeast(0)
    }

    /** Lo da por terminado (antes de tiempo si [ya]) y lo guarda en el historial. */
    fun termina(ctx: Context, e: Escritos, ya: Boolean = false) {
        if (hasta == 0L) return
        val fin = if (ya) System.currentTimeMillis().coerceAtMost(hasta) else hasta
        val min = (((fin - desde) + 30_000L) / 60_000L).toInt().coerceAtLeast(if (ya) 0 else minutos)
        val s = Sprint(pid, desde, min, llevas(e), meta)
        hasta = 0L
        prefs(ctx).edit().remove("sprint").apply()
        if (s.minutos >= 1) {
            val a = runCatching { JSONArray(prefs(ctx).getString("sprints", "[]")) }.getOrDefault(JSONArray())
            a.put(JSONObject().put("pid", s.pid).put("desde", s.desde).put("min", s.minutos).put("pal", s.palabras).put("meta", s.meta))
            val ult = JSONArray().also { n -> (maxOf(0, a.length() - 200) until a.length()).forEach { n.put(a.get(it)) } }
            prefs(ctx).edit().putString("sprints", ult.toString()).apply()
        }
        if (s.minutos >= 1 || s.palabras > 0) terminado = s
    }

    fun historial(ctx: Context, obra: String? = null): List<Sprint> = runCatching {
        val a = JSONArray(prefs(ctx).getString("sprints", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { Sprint(it.optString("pid"), it.optLong("desde"), it.optInt("min"), it.optInt("pal"), it.optInt("meta")) }
            .filter { obra == null || it.pid == obra }.sortedByDescending { it.desde }
    }.getOrDefault(emptyList())

    fun reiniciaParaPruebas() { hasta = 0L; pid = ""; terminado = null }
}

/** Mira cada segundo si el sprint en marcha ha terminado (va en la pantalla de Escribir, para que siga fuera del editor). */
@Composable
fun VigilaSprint(e: Escritos, ctx: Context) {
    LaunchedEffect(Unit) { SprintEscribir.recupera(ctx, e) }
    val hasta = SprintEscribir.hasta
    LaunchedEffect(hasta) {
        if (hasta == 0L) return@LaunchedEffect
        delay((hasta - System.currentTimeMillis()).coerceAtLeast(0))
        SprintEscribir.termina(ctx, e)
    }
    SprintEscribir.terminado?.let { s -> ResultadoSprint(s, e, ctx) { SprintEscribir.terminado = null } }
}

/** Elegir cuánto dura y la meta, y empezar. */
@Composable
fun SprintDialogo(e: Escritos, pid: String, onCerrar: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var min by remember { mutableIntStateOf(25) }
    var meta by remember { mutableStateOf("") }
    val mejor = remember { SprintEscribir.historial(ctx, pid).maxByOrNull { it.porMinuto } }
    HojaEscribir("Sprint de escritura", onCerrar) {
        Text("Escribe sin parar, sin corregir, hasta que suene. Cuentan las palabras nuevas de toda la obra.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        EtiquetaEscribir("Cuánto")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 20, 25, 30, 45, 60).forEach { m -> SoftChip("$m min", min == m) { min = m } }
        }
        CampoEscribir(meta, { meta = it.filter(Char::isDigit).take(5) }, "Meta de palabras (opcional)", numero = true)
        if (mejor != null && mejor.porMinuto > 0) Text("Tu mejor ritmo en esta obra: ${mejor.porMinuto} palabras por minuto.", color = Muted, fontSize = 13.sp)
        BotonEscribir("Empezar") { SprintEscribir.empieza(ctx, e, pid, min, meta.toIntOrNull() ?: 0); onCerrar() }
    }
}

/** La tira del sprint en marcha (en los contadores del editor): el tiempo que queda y lo escrito. */
@Composable
fun TiraSprint(e: Escritos, onTerminar: () -> Unit) {
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(SprintEscribir.hasta) { while (SprintEscribir.hasta > 0L) { ahora = System.currentTimeMillis(); delay(1000) } }
    val queda = ((SprintEscribir.hasta - ahora) / 1000).coerceAtLeast(0)
    val llevas = SprintEscribir.llevas(e)
    val meta = SprintEscribir.meta
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SPRINT %d:%02d".format(queda / 60, queda % 60), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Text("+$llevas" + if (meta > 0) " de $meta" else "", color = if (meta in 1..llevas) androidx.compose.ui.graphics.Color(0xFF5B8C5A) else Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("TERMINAR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.press(haptic = false, onClick = onTerminar).padding(4.dp))
    }
}

@Composable
private fun ResultadoSprint(s: Sprint, e: Escritos, ctx: Context, onCerrar: () -> Unit) {
    val antes = remember(s) { SprintEscribir.historial(ctx, s.pid).filter { it.desde != s.desde } }
    val record = antes.isNotEmpty() && s.porMinuto > antes.maxOf { it.porMinuto }
    HojaEscribir("Sprint terminado", onCerrar) {
        Text(cifraE(s.palabras), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 66.sp, color = Charcoal)
        Text(if (s.minutos >= 1) "PALABRAS EN ${s.minutos} MIN · ${s.porMinuto} POR MINUTO" else "PALABRAS EN MENOS DE UN MINUTO", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        if (s.meta > 0) Text(if (s.palabras >= s.meta) "Meta de ${cifraE(s.meta)} conseguida." else "Te quedaste a ${cifraE(s.meta - s.palabras)} de la meta (${cifraE(s.meta)}).", fontFamily = Display, fontSize = 20.sp, color = Charcoal)
        if (record) Text("Es tu mejor ritmo en esta obra.", color = ColGeneral, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        e.get(s.pid)?.let { Text("En «${it.s("titulo")}»", color = Muted, fontSize = 13.sp) }
        BotonEscribir("Seguir") { onCerrar() }
    }
}

/** Los sprints de la obra (para Cifras). */
@Composable
fun SprintsObra(pid: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val l = remember(SprintEscribir.terminado, SprintEscribir.hasta) { SprintEscribir.historial(ctx, pid) }
    if (l.isEmpty()) return
    val f = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("es"))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EtiquetaEscribir("Sprints (en este dispositivo)")
        Text("${l.size} · ${cifraE(l.sumOf { it.palabras })} palabras en ${l.sumOf { it.minutos }} min · mejor ritmo ${l.maxOf { it.porMinuto }} por minuto", color = Muted, fontSize = 12.sp)
        l.take(8).forEach { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(f.format(java.util.Date(s.desde)), color = Muted, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                Text("${s.minutos} min", color = Charcoal, fontSize = 13.sp, modifier = Modifier.width(56.dp))
                Text("${cifraE(s.palabras)} palabras" + if (s.meta > 0) (if (s.palabras >= s.meta) " ✓" else " de ${cifraE(s.meta)}") else "", color = Charcoal, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${s.porMinuto}/min", color = Muted, fontSize = 13.sp)
            }
        }
    }
}
