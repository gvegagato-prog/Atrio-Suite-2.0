package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * El Diario: cada día, lo que escribiste y lo que hiciste con cada cosa (lo terminaste, lo empezaste, leíste o
 * jugaste un rato, apuntaste algo, subrayaste, hiciste capturas). Lo escrito son las «notas» (lista «diario», que
 * viaja con la cuenta como las fichas); lo demás sale solo de lo que ya guarda la app.
 */

/** Una entrada escrita del diario (puede haber varias el mismo día). */
data class Nota(
    val id: Long, val dia: Long, val texto: String,
    /** Las fichas de las que habla: "categoría:id". */
    val sobre: List<String> = emptyList(),
    /** Una foto tuya (de la galería), como las portadas propias. */
    val foto: String = "",
    /** Tu pareja la ve en tu diario. */
    val comparte: Boolean = false,
    val updated: Long = 0L
)

fun notaToJson(n: Nota): JSONObject = JSONObject().put("id", n.id).put("dia", n.dia).put("x", n.texto)
    .put("updated", n.updated).apply {
        if (n.sobre.isNotEmpty()) put("sobre", JSONArray(n.sobre))
        if (n.foto.isNotBlank()) put("foto", n.foto)
        if (n.comparte) put("comp", true)
    }

fun notaFromJson(o: JSONObject): Nota? {
    val id = o.optLong("id", 0L)
    if (id == 0L || !o.has("dia")) return null
    val s = o.optJSONArray("sobre")
    return Nota(id, o.optLong("dia"), o.optString("x"), (0 until (s?.length() ?: 0)).mapNotNull { s!!.optString(it).ifBlank { null } },
        o.optString("foto", ""), o.optBoolean("comp", false), o.optLong("updated", 0L))
}

fun notasFromJson(a: JSONArray?): List<Nota> = (0 until (a?.length() ?: 0)).mapNotNull { i -> a!!.optJSONObject(i)?.let { notaFromJson(it) } }

/** Una ficha de la que se puede hablar en el diario. */
fun refDe(kind: Int, id: Long) = "$kind:$id"

/** El día (desde 1970) de un momento (ms), en la hora de este móvil. */
fun diaDe(ms: Long, zona: ZoneId = ZoneId.systemDefault()): Long = Instant.ofEpochMilli(ms).atZone(zona).toLocalDate().toEpochDay()

/** Lo que hiciste con una ficha un día. [id] 0: un juego del PC que no tienes en tu colección. */
class Toque(
    val kind: Kind, val id: Long, val nombre: String, val portada: String,
    /** Abre la ficha en curso (true) o la valorada (false). */
    val enCurso: Boolean
) {
    var terminado = false; var nota: Int? = null; var opinion = ""
    var empezado = false; var abandonado = false
    var leidoMs = 0L; var jugadoMs = 0L
    val apuntes = ArrayList<Memo>()
    val valoraciones = ArrayList<Entry>()
    val citas = ArrayList<BookNote>()
    val capturas = ArrayList<Shot>()
    /** El último momento (ms) de ese día, para ordenar; 0 si solo se sabe el día. */
    var t = 0L
    val clave: String get() = if (id != 0L) refDe(kind.id, id) else "pc:" + normName(nombre)

    /** «Lo terminaste · 2 h 10 min jugando · 4 capturas». */
    fun resumen(): String {
        val p = ArrayList<String>()
        if (terminado) p += when { kind.isMovie || kind.isTv -> "La terminaste"; else -> "Lo terminaste" }
        else if (abandonado) p += "Lo dejaste"
        if (empezado && !terminado) p += when { kind.isMovie || kind.isTv -> "La empezaste"; else -> "Lo empezaste" }
        if (jugadoMs >= 60_000L) p += "${readTime((jugadoMs + 30_000L) / 60_000L)} jugando"
        if (leidoMs >= 60_000L) p += "${readTime((leidoMs + 30_000L) / 60_000L)} leyendo"
        if (valoraciones.isNotEmpty()) p += if (valoraciones.size == 1) "un apunte con nota" else "${valoraciones.size} apuntes con nota"
        if (citas.isNotEmpty()) p += if (citas.size == 1) "un subrayado" else "${citas.size} subrayados"
        if (capturas.isNotEmpty()) p += if (capturas.size == 1) "una captura" else "${capturas.size} capturas"
        return p.joinToString(" · ").replaceFirstChar { it.uppercase() }
    }
}

/** Un día del diario: lo escrito y lo hecho. */
class Dia(val dia: Long, val notas: List<Nota>, val toques: List<Toque>) {
    val vacio: Boolean get() = notas.isEmpty() && toques.isEmpty()
    val leidoMs: Long get() = toques.sumOf { it.leidoMs }
    val jugadoMs: Long get() = toques.sumOf { it.jugadoMs }
    val terminados: List<Toque> get() = toques.filter { it.terminado }
}

/** Una partida del PC (de Atrio): el juego por su id de SteamGridDB (0 si no se sabe) y su nombre. */
data class JuegoPc(val sg: Long, val nombre: String)

/** Todo lo que el diario sabe de ti (o de tu pareja). */
class FuentesDiario(
    val fichas: List<Triple<Kind, List<Game>, List<Play>>>,
    val notas: List<Nota> = emptyList(),
    /** Lo leído con el lector: día → libro → ms. */
    val leido: Map<Long, Map<Long, Long>> = emptyMap(),
    /** Lo jugado en el PC: día → juego → ms. */
    val jugado: Map<Long, Map<JuegoPc, Long>> = emptyMap(),
    /** Los subrayados de cada libro. */
    val citas: Map<Long, List<BookNote>> = emptyMap(),
    val capturas: List<Shot> = emptyList(),
    /** Solo lo escrito sobre estas categorías (null: también lo que no habla de ninguna ficha). */
    val soloDe: Set<Int>? = null
)

/** Los días con algo, del más nuevo al más viejo. */
fun diasDelDiario(f0: FuentesDiario, zona: ZoneId = ZoneId.systemDefault()): List<Dia> {
    // Los juegos de la lista negra no salen (ni su ficha, ni sus horas del PC, ni sus capturas)
    val f = if (Negra.nombres.isEmpty()) f0 else FuentesDiario(
        f0.fichas.map { (k, gs, ps) -> if (k.isGame) Triple(k, gs.filterNot { Negra.vetado(it.name) }, ps.filterNot { Negra.vetado(it.name) }) else Triple(k, gs, ps) },
        f0.notas, f0.leido,
        f0.jugado.mapValues { (_, m) -> m.filterKeys { !Negra.vetado(it.nombre) } },
        f0.citas, f0.capturas.filterNot { Negra.vetado(it.game) }, f0.soloDe
    )
    val porDia = HashMap<Long, LinkedHashMap<String, Toque>>()
    // Cada ficha, por categoría e id (la de «en curso» si la hay: es la que tiene apuntes)
    val fichas = HashMap<String, Toque>()
    val porNombre = HashMap<String, Pair<Kind, Long>>()
    fun base(kind: Kind, id: Long): Toque? {
        val (k, gs, ps) = f.fichas.firstOrNull { it.first.id == kind.id } ?: return null
        val p = ps.find { it.id == id }
        val g = gs.find { it.id == id }
        return when {
            p != null -> Toque(k, id, p.name, p.cover, true)
            g != null -> Toque(k, id, g.name, g.cover, false)
            else -> null
        }
    }
    for ((k, gs, ps) in f.fichas) {
        for (g in gs) porNombre.putIfAbsent("${k.id}|${normName(g.name)}", k to g.id)
        for (p in ps) porNombre.putIfAbsent("${k.id}|${normName(p.name)}", k to p.id)
    }
    fun toque(dia: Long, kind: Kind, id: Long, nombre: String = "", portada: String = ""): Toque? {
        val m = porDia.getOrPut(dia) { LinkedHashMap() }
        val clave = if (id != 0L) refDe(kind.id, id) else "pc:" + normName(nombre)
        m[clave]?.let { return it }
        val b = if (id != 0L) (fichas.getOrPut(clave) { base(kind, id) ?: return null }) else Toque(kind, 0L, nombre, portada, false)
        // Uno nuevo cada día (el de la ficha solo da nombre y portada)
        val t = Toque(b.kind, b.id, b.nombre, b.portada, b.enCurso)
        m[clave] = t
        return t
    }
    for ((k, gs, ps) in f.fichas) {
        for (g in gs) {
            if (g.end > 0L) toque(g.end, k, g.id)?.apply { terminado = true; nota = g.general; opinion = g.review }
            if (g.start > 0L && g.start != g.end) toque(g.start, k, g.id)?.apply { empezado = true }
        }
        for (p in ps) {
            if (p.start > 0L) toque(p.start, k, p.id)?.apply { empezado = true }
            if (p.status == 2 && p.end > 0L) toque(p.end, k, p.id)?.apply { abandonado = true }
            for (m in p.liveMemos) if (m.t > 0L) toque(diaDe(m.t, zona), k, p.id)?.apply { apuntes += m; t = maxOf(t, m.t) }
            for (e in p.entries) if (e.time > 0L) toque(diaDe(e.time, zona), k, p.id)?.apply { valoraciones += e; t = maxOf(t, e.time) }
        }
    }
    val libros = f.fichas.firstOrNull { it.first.isBook }?.first
    if (libros != null) {
        for ((d, m) in f.leido) for ((id, ms) in m) if (ms >= 60_000L) toque(d, libros, id)?.apply { leidoMs += ms }
        for ((id, ns) in f.citas) for (n in ns) if (n.created > 0L) toque(diaDe(n.created, zona), libros, id)?.apply { citas += n; t = maxOf(t, n.created) }
    }
    val juegos = f.fichas.firstOrNull { it.first.isGame }?.first
    if (juegos != null) {
        fun deJuego(sg: Long, nombre: String): Long {
            val (k, gs, ps) = f.fichas.first { it.first.isGame }
            if (sg > 0L && (ps.any { it.id == sg } || gs.any { it.id == sg })) return sg
            return porNombre["${k.id}|${normName(nombre)}"]?.second ?: 0L
        }
        for ((d, m) in f.jugado) for ((j, ms) in m) if (ms >= 60_000L) toque(d, juegos, deJuego(j.sg, j.nombre), j.nombre)?.apply { jugadoMs += ms }
        for (s in f.capturas) if (s.time > 0L) {
            val nombre = s.game.ifBlank { "Escritorio" }
            toque(diaDe(s.time, zona), juegos, deJuego(s.sg, nombre), nombre)?.apply { capturas += s; t = maxOf(t, s.time) }
        }
    }
    // Lo escrito: lo de esas categorías, o todo
    val notas = f.notas.filter { n ->
        val s = f.soloDe ?: return@filter true
        n.sobre.any { r -> r.substringBefore(':').toIntOrNull() in s }
    }.groupBy { it.dia }
    val dias = (porDia.keys + notas.keys).distinct().sortedDescending()
    return dias.map { d ->
        val ts = porDia[d]?.values.orEmpty().sortedWith(
            compareByDescending<Toque> { it.terminado }.thenByDescending { it.jugadoMs + it.leidoMs }.thenByDescending { it.t }
        )
        Dia(d, notas[d].orEmpty().sortedBy { it.id }, ts)
    }.filter { !it.vacio }
}

/** Lo de un mes (o cualquier tramo de días): terminados, lo leído y jugado, lo escrito y los días con algo. */
class ResumenDiario(val terminados: Int, val leidoMs: Long, val jugadoMs: Long, val entradas: Int, val dias: Int) {
    fun texto(): String {
        val p = ArrayList<String>()
        if (terminados > 0) p += "$terminados terminado${if (terminados == 1) "" else "s"}"
        if (leidoMs >= 60_000L) p += "${readTime((leidoMs + 30_000L) / 60_000L)} leyendo"
        if (jugadoMs >= 60_000L) p += "${readTime((jugadoMs + 30_000L) / 60_000L)} jugando"
        if (entradas > 0) p += "$entradas entrada${if (entradas == 1) "" else "s"}"
        return p.joinToString(" · ")
    }
}

fun resumenDiario(dias: List<Dia>, desde: Long, hasta: Long): ResumenDiario {
    val ds = dias.filter { it.dia in desde..hasta }
    return ResumenDiario(ds.sumOf { it.terminados.size }, ds.sumOf { it.leidoMs }, ds.sumOf { it.jugadoMs }, ds.sumOf { it.notas.size }, ds.size)
}

/** Días seguidos con algo en el diario, contando hoy (o desde ayer, si hoy aún no hay nada). */
fun rachaDiario(dias: Collection<Long>, hoy: Long = today()): Int {
    val s = dias.toHashSet()
    var d = if (hoy in s) hoy else hoy - 1
    var n = 0
    while (d in s) { n++; d-- }
    return n
}

/** «Tal día como hoy»: el mismo día de años anteriores, con cuántos años hace (lo más reciente primero). */
fun talDiaComoHoy(dias: List<Dia>, hoy: LocalDate = LocalDate.now()): List<Pair<Int, Dia>> =
    dias.mapNotNull { d ->
        val f = LocalDate.ofEpochDay(d.dia)
        if (f.year < hoy.year && f.monthValue == hoy.monthValue && f.dayOfMonth == hoy.dayOfMonth) (hoy.year - f.year) to d else null
    }.sortedBy { it.first }

/** La primera frase (o el principio) de un texto, para el resumen de una ficha. */
fun extracto(s: String, max: Int = 160): String {
    val t = s.trim().replace(Regex("\\s+"), " ")
    if (t.length <= max) return t
    val corte = t.lastIndexOf(' ', max).takeIf { it > max / 2 } ?: max
    return t.take(corte).trimEnd(',', ';', ':', '.') + "…"
}

/**
 * Las partidas del PC (Atrio de Linux las sube a tu cuenta, en /users/<uid>/horas/<pc>): aquí se bajan y se
 * guardan para el diario. Cada PC sube sus sesiones con el juego (id de SteamGridDB y nombre); lo que borraste de
 * su historial («forgot») no cuenta.
 */
object HorasPc {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("horas_pc", Context.MODE_PRIVATE)

    /** Sube cuando llegan partidas nuevas (el diario se vuelve a hacer). */
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    private val mirado = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Baja lo nuevo de los PC de esa cuenta (la tuya o la de tu pareja), como mucho cada 5 minutos (o ya, con [ya]).
     * Devuelve true si ha cambiado algo.
     */
    fun bajar(ctx: Context, store: Store, uid: String = store.accUid, ya: Boolean = false): Boolean {
        if (uid.isBlank() || !store.paired) return false
        val ahora = System.currentTimeMillis()
        if (!ya && ahora - (mirado[uid] ?: 0L) < 5 * 60_000L) return false
        mirado[uid] = ahora
        val at = Account.db(store, "GET", "/users/$uid/horasAt", null) ?: return false
        val st = runCatching { JSONObject(at) }.getOrNull() ?: return false
        val p = prefs(ctx)
        val cur = runCatching { JSONObject(p.getString(uid, "{}") ?: "{}") }.getOrElse { JSONObject() }
        var cambio = false
        for (dev in st.keys()) {
            val ts = st.optLong(dev, 0L)
            if (ts <= 0L || ts <= (cur.optJSONObject(dev)?.optLong("t", 0L) ?: 0L)) continue
            val raw = Account.db(store, "GET", "/users/$uid/horas/$dev/d", null) ?: continue
            val d = JSONTokener(raw).nextValue() as? String ?: continue
            if (runCatching { JSONObject(d) }.isFailure) continue
            cur.put(dev, JSONObject().put("t", ts).put("d", d))
            cambio = true
        }
        if (cambio) {
            p.edit().putString(uid, cur.toString()).apply()
            android.os.Handler(android.os.Looper.getMainLooper()).post { version++ }
        }
        return cambio
    }

    /** Lo jugado en los PC de esa cuenta, por día y juego (ms). */
    fun porDia(ctx: Context, uid: String, zona: ZoneId = ZoneId.systemDefault()): Map<Long, Map<JuegoPc, Long>> {
        val cur = runCatching { JSONObject(prefs(ctx).getString(uid, "{}") ?: "{}") }.getOrElse { JSONObject() }
        val pcs = cur.keys().asSequence().mapNotNull { dev -> runCatching { JSONObject(cur.getJSONObject(dev).getString("d")) }.getOrNull() }.toList()
        return deSesiones(pcs, zona)
    }

    /** Las sesiones de cada PC (solo las de 5 minutos o más) ({ games: {clave: {n, sg}}, s: [[clave, inicio, fin]], forgot: {clave: ms} }), por día. */
    fun deSesiones(pcs: List<JSONObject>, zona: ZoneId = ZoneId.systemDefault()): Map<Long, Map<JuegoPc, Long>> {
        val out = HashMap<Long, HashMap<JuegoPc, Long>>()
        for (pc in pcs) {
            val games = pc.optJSONObject("games") ?: JSONObject()
            val forgot = pc.optJSONObject("forgot") ?: JSONObject()
            val s = pc.optJSONArray("s") ?: continue
            for (i in 0 until s.length()) {
                val x = s.optJSONArray(i) ?: continue
                val key = x.optString(0); val a = x.optLong(1); val b = x.optLong(2)
                // (menos de 5 minutos seguidos no cuenta como jugado ese día)
                if (key.isBlank() || b - a < ReadStats.SEGUIDO_MS || forgot.optLong(key, 0L) >= b) continue
                val g = games.optJSONObject(key)
                val j = JuegoPc(g?.optLong("sg", 0L) ?: 0L, g?.optString("n").orEmpty().ifBlank { key.substringAfter(':') })
                // Una partida que pasa de medianoche cuenta en los dos días
                var ini = a
                while (ini < b) {
                    val dia = diaDe(ini, zona)
                    val finDia = LocalDate.ofEpochDay(dia + 1).atStartOfDay(zona).toInstant().toEpochMilli()
                    val fin = minOf(b, finDia)
                    val m = out.getOrPut(dia) { HashMap() }
                    m[j] = (m[j] ?: 0L) + (fin - ini)
                    ini = fin
                }
            }
        }
        return out
    }
}

/** Lo leído de tu pareja (su lista «lectura»): día → libro → ms. */
fun leidoDeLectura(a: JSONArray?): Map<Long, Map<Long, Long>> {
    val out = HashMap<Long, HashMap<Long, Long>>()
    for (i in 0 until (a?.length() ?: 0)) {
        val o = a!!.optJSONObject(i) ?: continue
        val stats = o.optJSONObject("s")?.optJSONObject("stats") ?: continue
        val days = stats.optJSONObject("days") ?: continue
        for (d in days.keys()) {
            val ep = runCatching { LocalDate.parse(d).toEpochDay() }.getOrNull() ?: continue
            val ms = days.optLong(d, 0L)
            if (ms > 0L && ReadStats.cuenta(stats, d)) out.getOrPut(ep) { HashMap() }[o.optLong("id")] = ms
        }
    }
    return out
}

/** Lo escrito en el diario, a mano de las pantallas que lo enseñan (la ficha, el buscador). */
object DiarioHub {
    var notas by androidx.compose.runtime.mutableStateOf<List<Nota>>(emptyList())
    /** Escribir sobre una ficha (lo pone la app: abre el editor con esa ficha ya elegida). */
    var escribirSobre: ((Kind, Long) -> Unit)? = null
    /** Abrir una entrada para cambiarla (lo pone la app). */
    var editar: ((Nota) -> Unit)? = null
    fun de(kind: Int, id: Long): List<Nota> {
        val r = refDe(kind, id)
        return notas.filter { r in it.sobre }.sortedWith(compareByDescending<Nota> { it.dia }.thenByDescending { it.id })
    }
}
