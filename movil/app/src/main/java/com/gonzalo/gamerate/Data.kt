package com.gonzalo.gamerate

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

class Kind(
    val id: Int, val title: String, val labels: List<String>,
    val statuses: List<String>, val isBook: Boolean,
    /** Categoría creada por ti (películas, series, discos…). */
    val custom: Boolean = false,
    /** Color de fondo de la sección (0 = el del menú). */
    val color: Int = 0,
    /** Dónde se buscan portadas en internet: "" (a mano), "movie" (películas, TMDB) o "tv" (series, TVmaze). */
    val source: String = ""
) {
    /** Cuántos criterios se valoran además de la nota general (1 a 4). */
    val crit: Int get() = (labels.size - 1).coerceIn(1, 4)

    val isMovie: Boolean get() = source == SRC_MOVIE
    val isTv: Boolean get() = source == SRC_TV

    /** "Estudio", "Autor", "Director"… */
    val creatorWord: String
        get() = if (isMovie) "Director" else if (custom) "Creador" else if (isBook) "Autor" else "Estudio"

    /** Plural bien escrito: "Estudios", "Autores", "Creadores". */
    val creatorPlural: String
        get() = if (isMovie) "Directores" else if (custom) "Creadores" else if (isBook) "Autores" else "Estudios"

    /** "Plataforma", "Formato" o "Dónde" (películas y series). */
    val platformWord: String get() = if (isMovie || isTv) "Dónde" else if (isBook || custom) "Formato" else "Plataforma"

    /** Opciones rápidas de plataforma o formato. */
    val platforms: List<String>
        get() = when {
            isMovie -> listOf("Cine", "Netflix", "Prime Video", "Disney+", "HBO Max", "Apple TV+", "Filmin")
            isTv -> listOf("Netflix", "Prime Video", "Disney+", "HBO Max", "Apple TV+", "Filmin", "Tele")
            custom -> emptyList()
            isBook -> listOf("Papel", "Ebook", "Kindle", "Audiolibro")
            else -> listOf("PC", "PS5", "PS4", "Switch", "Switch 2", "Xbox", "Steam Deck", "Móvil")
        }

    /** "juego", "libro", "película"… */
    val unit: String get() = if (isMovie) "película" else if (isTv) "serie" else if (custom) "ficha" else if (isBook) "libro" else "juego"

    /** "Pasados", "Leídos", "Vistas"… */
    val doneWord: String get() = if (isMovie || isTv) "Vistas" else if (custom) "Terminados" else if (isBook) "Leídos" else "Pasados"

    /** Tiene buscador en internet (juegos, libros, películas y series) o se crea a mano. */
    val online: Boolean get() = !custom || source.isNotEmpty()

    /** Tiene fondos en internet para elegir (juegos, películas y series). */
    val hasHero: Boolean get() = isGame || isMovie || isTv

    /** Tiene sesiones, horas y duración (solo videojuegos). */
    val isGame: Boolean get() = id == 0
}

const val SRC_MOVIE = "movie"
const val SRC_TV = "tv"

/** Las fichas añadidas a mano en una categoría propia tienen ids enormes; las de internet, el de su web. */
fun isWebId(id: Long) = id in 1 until 100_000_000_000L

val GAMES = Kind(
    0, "Videojuegos",
    listOf("Valoración", "Gráficos", "Historia", "Música", "Jugabilidad"),
    listOf("En lista", "Jugando", "Dropeado"), false
)

val BOOKS = Kind(
    1, "Libros",
    listOf("Valoración", "Personajes", "Historia", "Escritura", "Ritmo"),
    listOf("En lista", "Leyendo", "Abandonado"), true
)

/** Crea una categoría propia. criteria: lo que se valora (1 a 4); progress: palabra para "en curso"; source: buscador. */
fun customKind(id: Int, title: String, criteria: List<String>, progress: String, color: Int, source: String = "") = Kind(
    id, title.trim(), listOf("Valoración") + criteria.map { it.trim() }.filter { it.isNotEmpty() }.take(4).ifEmpty { listOf("Disfrute") },
    listOf("En lista", progress.trim().ifEmpty { "En curso" }, "Abandonado"), false, true, color,
    source.takeIf { it == SRC_MOVIE || it == SRC_TV } ?: ""
)

fun kindToJson(k: Kind): JSONObject = JSONObject().put("id", k.id).put("title", k.title)
    .put("criteria", JSONArray(k.labels.drop(1))).put("progress", k.statuses[1]).put("color", k.color)
    .put("source", k.source)

fun kindsFromJson(a: JSONArray): List<Kind> = (0 until a.length()).mapNotNull { i ->
    try {
        val o = a.getJSONObject(i)
        val cr = o.optJSONArray("criteria") ?: JSONArray()
        customKind(o.getInt("id"), o.getString("title"), (0 until cr.length()).map { cr.optString(it) },
            o.optString("progress", "En curso"), o.optInt("color", 0), o.optString("source", ""))
    } catch (e: Exception) { null }
}

/** Busca una categoría por id: juegos, libros o una tuya (las tuyas viven en Mode.kinds). */
fun kindOf(id: Int) = when (id) {
    0 -> GAMES
    1 -> BOOKS
    else -> Mode.kinds.find { it.id == id } ?: GAMES
}

/**
 * start y end son días desde 1970 (LocalDate.toEpochDay); 0 = sin fecha.
 * minutes: tiempo jugado. pages: páginas del libro. updated: última modificación (ms).
 */
data class Game(
    val id: Long, val name: String, val cover: String,
    val general: Int = 50, val gfx: Int = 50, val story: Int = 50,
    val music: Int = 50, val gameplay: Int = 50, val review: String = "",
    val hero: String = "", val start: Long = 0L, val end: Long = 0L,
    val creator: String = "",
    val fav: Boolean = false, val platform: String = "", val pages: Int = 0,
    val minutes: Int = 0, val updated: Long = 0L,
    /** Horas habituales en minutos (solo juegos): historia, historia + extras y completo; y de dónde salen. */
    val ttbMain: Int = 0, val ttbExtra: Int = 0, val ttbFull: Int = 0, val ttbSrc: String = "",
    /** Logo del juego (lo elige Atrio, el lanzador del PC); aquí solo se conserva. */
    val logo: String = ""
) {
    val avg: Float get() = (gfx + story + music + gameplay) / 4f
}

/** Media de los criterios que tiene esa categoría. */
fun Game.avgFor(k: Kind): Float = scores().subList(1, 1 + k.crit).average().toFloat()

/** Cambia las 5 notas de una vez (general + 4 criterios). */
fun Game.withScores(v: List<Int>) = copy(general = v[0], gfx = v[1], story = v[2], music = v[3], gameplay = v[4])
fun Entry.withScores(v: List<Int>) = copy(general = v[0], gfx = v[1], story = v[2], music = v[3], gameplay = v[4])

/**
 * Al editar los criterios de una categoría, cada nota sigue a su criterio.
 * slots: de qué casilla antigua (0..3) viene cada criterio nuevo; -1 = criterio nuevo (empieza en 50).
 */
fun remapScores(v: List<Int>, slots: List<Int>): List<Int> =
    (listOf(v[0]) + slots.map { s -> if (s in 0..3) v[s + 1] else 50 }).let { it + List(5 - it.size) { 50 } }.take(5)

/** Id de una ficha de categoría propia: sale del nombre, así coincide en tu móvil y en el de tu pareja. */
fun customItemId(name: String): Long {
    var h = -0x340d631b7bdddcdbL // FNV-1a de 64 bits
    for (c in normName(name)) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
    return (h and Long.MAX_VALUE) % 9_000_000_000_000L + 100_000_000_000L
}

/** Un capítulo, acto o misión de algo que estás jugando. */
data class Part(val name: String, val done: Boolean = false)

/**
 * Un apunte con notas. Si valora una parte concreta: [seg] es la parte jugada (sesión) y [part] el capítulo.
 */
data class Entry(
    val time: Long, val general: Int, val gfx: Int, val story: Int,
    val music: Int, val gameplay: Int, val note: String, val chapter: Int = 0,
    val seg: String = "", val part: String = ""
)

/**
 * Apunte rápido mientras juegas: solo texto (y, si quieres, una captura del PC).
 * [del]: borrado (se queda la marca para que no reviva al mezclar con otro dispositivo); [u]: última edición.
 */
data class Memo(val t: Long, val x: String, val sh: String = "", val del: Boolean = false, val u: Long = 0L)

/**
 * Una parte jugada: una partida en el PC (la apunta Atrio al cerrar el juego), una sesión del temporizador
 * o una que añades a mano. Se valora con un apunte que la lleva en [Entry.seg].
 * src: "atrio", "timer" o "manual".
 */
/** Una parte jugada. q: guardada sin pedir valoración (el juego tiene «Valorar por partes» apagado). */
data class Seg(val id: String, val s: Long, val e: Long, val min: Int, val src: String, val pc: String = "", val name: String = "", val q: Boolean = false)

fun memoToJson(m: Memo): JSONObject = JSONObject().put("t", m.t).put("x", m.x).apply {
    if (m.sh.isNotEmpty()) put("sh", m.sh)
    if (m.del) put("del", 1)
    if (m.u > 0L) put("u", m.u)
}
fun memoFromJson(o: JSONObject) = Memo(o.optLong("t"), o.optString("x"), o.optString("sh", ""), o.optInt("del", 0) == 1 || o.optBoolean("del", false), o.optLong("u", 0L))

fun segToJson(g: Seg): JSONObject = JSONObject().put("id", g.id).put("s", g.s).put("e", g.e).put("min", g.min).put("src", g.src).apply {
    if (g.pc.isNotEmpty()) put("pc", g.pc)
    if (g.name.isNotEmpty()) put("name", g.name)
    if (g.q) put("q", 1)
}
fun segFromJson(o: JSONObject) = Seg(o.optString("id"), o.optLong("s"), o.optLong("e"), o.optInt("min"), o.optString("src"), o.optString("pc", ""), o.optString("name", ""), o.optInt("q", 0) == 1)

/** "Partida en el PC", "Sesión con temporizador"… */
fun Seg.label(): String = name.ifBlank {
    when (src) { "atrio" -> if (pc.isNotBlank()) "Partida en $pc" else "Partida en el PC"; "timer" -> "Sesión con temporizador"; else -> "Parte añadida a mano" }
}

/** status: 0 = en lista, 1 = en progreso, 2 = dropeado/abandonado */
data class Play(
    val id: Long, val name: String, val cover: String, val hero: String,
    val status: Int, val entries: List<Entry>, val chapter: Int = 0, val every: Int = 3,
    val start: Long = 0L, val end: Long = 0L, val creator: String = "",
    val platform: String = "", val pages: Int = 0, val minutes: Int = 0, val updated: Long = 0L,
    /** Duración media en minutos: historia, historia + extras y completo (0 = no se sabe). */
    val ttbMain: Int = 0, val ttbExtra: Int = 0, val ttbFull: Int = 0,
    /** De dónde sale la duración. */
    val ttbSrc: String = "",
    /** Capítulos o actos para ir marcando. */
    val parts: List<Part> = emptyList(),
    /** Logo del juego (lo elige Atrio); aquí solo se conserva. */
    val logo: String = "",
    /** Apuntes rápidos (los borrados se quedan marcados: usa [liveMemos]). */
    val memos: List<Memo> = emptyList(),
    /** Partes jugadas (sesiones), para valorarlas. */
    val segs: List<Seg> = emptyList(),
    /** «Valorar por partes» apagado (juegos online como Deadlock): las partidas suman horas pero no piden nota. */
    val noParts: Boolean = false
) {
    val liveMemos: List<Memo> get() = memos.filter { !it.del }

    /** Partes jugadas que aún no tienen apunte. */
    fun unrated(): List<Seg> = if (noParts) emptyList() else segs.filter { s -> !s.q && entries.none { it.seg == s.id } }

    /** Porcentaje completado: por capítulos si los hay; si no, por horas frente a la duración media. */
    fun percent(): Int? = when {
        parts.isNotEmpty() -> (parts.count { it.done } * 100 / parts.size)
        ttbMain > 0 && minutes > 0 -> (minutes * 100 / ttbMain).coerceAtMost(100)
        else -> null
    }

    fun baremo(): Entry? {
        if (entries.isEmpty()) return null
        fun m(f: (Entry) -> Int) = entries.map(f).average().roundToInt()
        return Entry(0L, m { it.general }, m { it.gfx }, m { it.story }, m { it.music }, m { it.gameplay }, "")
    }

    /** Capítulos leídos desde el último apunte. */
    fun pending(): Int = chapter - (entries.maxOfOrNull { it.chapter } ?: 0)

    /** Momento de la última actividad (para ordenar). */
    val activity: Long
        get() = maxOf(updated, entries.maxOfOrNull { it.time } ?: 0L, start * 86_400_000L)
}

/** Momento de la última actividad de un valorado (para ordenar). */
val Game.activity: Long get() = maxOf(updated, end * 86_400_000L, start * 86_400_000L)

fun Game.scores() = listOf(general, gfx, story, music, gameplay)
fun Entry.scores() = listOf(general, gfx, story, music, gameplay)

fun Game.setScore(i: Int, v: Int) = when (i) {
    0 -> copy(general = v)
    1 -> copy(gfx = v)
    2 -> copy(story = v)
    3 -> copy(music = v)
    else -> copy(gameplay = v)
}

fun Entry.setScore(i: Int, v: Int) = when (i) {
    0 -> copy(general = v)
    1 -> copy(gfx = v)
    2 -> copy(story = v)
    3 -> copy(music = v)
    else -> copy(gameplay = v)
}

/** Recomendación que le haces a tu pareja. */
data class Rec(
    val id: Long, val kind: Int, val itemId: Long, val name: String, val cover: String,
    val creator: String, val note: String, val time: Long
)

fun recToJson(r: Rec): JSONObject = JSONObject().put("id", r.id).put("kind", r.kind).put("item", r.itemId)
    .put("name", r.name).put("cover", r.cover).put("creator", r.creator).put("note", r.note).put("time", r.time)

fun recFromJson(o: JSONObject) = Rec(
    o.getLong("id"), o.optInt("kind", 0), o.optLong("item", 0L), o.optString("name"), o.optString("cover"),
    o.optString("creator"), o.optString("note"), o.optLong("time", 0L)
)

fun recsFromJson(a: JSONArray): List<Rec> = (0 until a.length()).mapNotNull {
    try { recFromJson(a.getJSONObject(it)) } catch (e: Exception) { null }
}

fun gameFromJson(o: JSONObject) = Game(
    o.getLong("id"), o.getString("name"), o.getString("cover"),
    o.getInt("general"), o.getInt("gfx"), o.getInt("story"),
    o.getInt("music"), o.getInt("gameplay"), o.getString("review"),
    o.optString("hero", ""), o.optLong("start", 0L), o.optLong("end", 0L),
    o.optString("creator", ""),
    o.optBoolean("fav", false), o.optString("platform", ""), o.optInt("pages", 0),
    o.optInt("minutes", 0), o.optLong("updated", 0L),
    o.optInt("ttbMain", 0), o.optInt("ttbExtra", 0), o.optInt("ttbFull", 0), o.optString("ttbSrc", ""),
    o.optString("logo", "")
)

fun gameToJson(g: Game): JSONObject = JSONObject().put("id", g.id).put("name", g.name).put("cover", g.cover)
    .put("general", g.general).put("gfx", g.gfx).put("story", g.story)
    .put("music", g.music).put("gameplay", g.gameplay).put("review", g.review)
    .put("hero", g.hero).put("start", g.start).put("end", g.end).put("creator", g.creator)
    .put("fav", g.fav).put("platform", g.platform).put("pages", g.pages)
    .put("minutes", g.minutes).put("updated", g.updated)
    .put("ttbMain", g.ttbMain).put("ttbExtra", g.ttbExtra).put("ttbFull", g.ttbFull).put("ttbSrc", g.ttbSrc)
    .put("logo", g.logo)

fun playFromJson(o: JSONObject): Play {
    val es = o.optJSONArray("entries") ?: JSONArray()
    return Play(
        o.getLong("id"), o.getString("name"), o.getString("cover"), o.getString("hero"), o.getInt("status"),
        (0 until es.length()).map { i ->
            val e = es.getJSONObject(i)
            Entry(e.getLong("time"), e.getInt("general"), e.getInt("gfx"), e.getInt("story"),
                e.getInt("music"), e.getInt("gameplay"), e.getString("note"), e.optInt("chapter", 0),
                e.optString("seg", ""), e.optString("part", ""))
        },
        o.optInt("chapter", 0), o.optInt("every", 3),
        o.optLong("start", 0L), o.optLong("end", 0L), o.optString("creator", ""),
        o.optString("platform", ""), o.optInt("pages", 0), o.optInt("minutes", 0), o.optLong("updated", 0L),
        o.optInt("ttbMain", 0), o.optInt("ttbExtra", 0), o.optInt("ttbFull", 0), o.optString("ttbSrc", ""),
        (o.optJSONArray("parts") ?: JSONArray()).let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Part(it.optString("n"), it.optBoolean("d")) } }
        },
        o.optString("logo", ""),
        (o.optJSONArray("memos") ?: JSONArray()).let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { memoFromJson(it) } } },
        (o.optJSONArray("segs") ?: JSONArray()).let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { segFromJson(it) } } },
        o.optBoolean("noParts", false)
    )
}

fun playToJson(pl: Play): JSONObject {
    val es = JSONArray()
    pl.entries.forEach { e ->
        es.put(JSONObject().put("time", e.time).put("general", e.general).put("gfx", e.gfx)
            .put("story", e.story).put("music", e.music).put("gameplay", e.gameplay)
            .put("note", e.note).put("chapter", e.chapter)
            .apply { if (e.seg.isNotEmpty()) put("seg", e.seg); if (e.part.isNotEmpty()) put("part", e.part) })
    }
    return JSONObject().put("id", pl.id).put("name", pl.name).put("cover", pl.cover)
        .put("hero", pl.hero).put("status", pl.status).put("entries", es)
        .put("chapter", pl.chapter).put("every", pl.every)
        .put("start", pl.start).put("end", pl.end).put("creator", pl.creator)
        .put("platform", pl.platform).put("pages", pl.pages).put("minutes", pl.minutes)
        .put("updated", pl.updated)
        .put("ttbMain", pl.ttbMain).put("ttbExtra", pl.ttbExtra).put("ttbFull", pl.ttbFull).put("ttbSrc", pl.ttbSrc)
        .put("parts", JSONArray().apply { pl.parts.forEach { put(JSONObject().put("n", it.name).put("d", it.done)) } })
        .put("logo", pl.logo)
        .apply {
            if (pl.memos.isNotEmpty()) put("memos", JSONArray().apply { pl.memos.forEach { put(memoToJson(it)) } })
            if (pl.segs.isNotEmpty()) put("segs", JSONArray().apply { pl.segs.forEach { put(segToJson(it)) } })
            if (pl.noParts) put("noParts", true)
        }
}

/**
 * Al mezclar dos versiones de una ficha, los apuntes rápidos y las partes jugadas se suman (el móvil y Atrio
 * pueden añadirlos a la vez sin pisarse). Igual que unite() en Atrio.
 */
fun uniteLists(win: JSONObject, lose: JSONObject): JSONObject {
    fun union(key: String, idKey: String, better: (JSONObject, JSONObject) -> JSONObject) {
        val a = win.optJSONArray(key)
        val b = lose.optJSONArray(key)
        if (a == null && b == null) return
        val m = LinkedHashMap<String, JSONObject>()
        for (arr in listOf(a, b)) {
            if (arr == null) continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val k = o.opt(idKey)?.toString() ?: continue
                val cur = m[k]
                m[k] = if (cur == null) o else better(cur, o)
            }
        }
        val sorted = m.values.sortedBy { it.optLong(if (key == "memos") "t" else "s") }
        win.put(key, JSONArray().apply { sorted.forEach { put(it) } })
    }
    union("memos", "t") { p, q ->
        val pd = p.optInt("del", 0) == 1 || p.optBoolean("del", false)
        val qd = q.optInt("del", 0) == 1 || q.optBoolean("del", false)
        if (qd && !pd) q else if (q.optLong("u", 0L) > p.optLong("u", 0L)) q else p
    }
    union("segs", "id") { p, _ -> p }
    return win
}

fun gamesFromJson(a: JSONArray): List<Game> = (0 until a.length()).map { gameFromJson(a.getJSONObject(it)) }
fun playsFromJson(a: JSONArray): List<Play> = (0 until a.length()).map { playFromJson(a.getJSONObject(it)) }

/** Una carta de "Siguiente": algo que te podría gustar. key: "rawg:123" o "ol:456". */
data class Card(
    val key: String, val kind: Int, val title: String, val image: String, val year: String,
    val sub: String, val creator: String, val itemId: Long, val score: Int = 0
)

fun cardToJson(c: Card): JSONObject = JSONObject().put("key", c.key).put("kind", c.kind).put("title", c.title)
    .put("image", c.image).put("year", c.year).put("sub", c.sub).put("creator", c.creator)
    .put("item", c.itemId).put("score", c.score)

fun cardFromJson(o: JSONObject) = Card(
    o.getString("key"), o.optInt("kind", 0), o.optString("title"), o.optString("image"), o.optString("year"),
    o.optString("sub"), o.optString("creator"), o.optLong("item", 0L), o.optInt("score", 0)
)

fun cardsFromJson(a: JSONArray): List<Card> = (0 until a.length()).mapNotNull {
    try { cardFromJson(a.getJSONObject(it)) } catch (e: Exception) { null }
}

/** Aspecto de la app: patrón de fondo, intensidad y color de cada zona. */
data class Look(
    val pattern: Int = 5, val intensity: Int = 0,
    val menu: Int = 0xFFB0102F.toInt(), val games: Int = 0xFF6FB1E8.toInt(), val books: Int = 0xFF8BA777.toInt()
)

class Store(ctx: Context) {
    private val app = ctx.applicationContext
    private val p = ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE)

    var key: String
        get() = p.getString("key", "") ?: ""
        set(v) { p.edit().putString("key", v.trim()).apply() }

    var sessionId: Long
        get() = p.getLong("sid", 0L)
        set(v) { p.edit().putLong("sid", v).apply() }

    var sessionStart: Long
        get() = p.getLong("sstart", 0L)
        set(v) { p.edit().putLong("sstart", v).apply() }

    var sessionHours: Int
        get() = p.getInt("shours", 3)
        set(v) { p.edit().putInt("shours", v).apply() }

    // ---- Aspecto ----
    var look: Look
        get() = Look(
            // Desde la 3.0 el fondo por defecto es liso; el patrón antiguo se guarda con otra clave.
            // Atrio 6: el fondo por defecto es la aurora (antes, liso); se guarda con otra clave
            p.getInt("look6_pattern", 5), p.getInt("look3_int", 0),
            p.getInt("look_menu", 0xFFB0102F.toInt()), p.getInt("look_games", 0xFF6FB1E8.toInt()),
            p.getInt("look_books", 0xFF8BA777.toInt())
        )
        set(v) {
            p.edit().putInt("look6_pattern", v.pattern).putInt("look3_int", v.intensity)
                .putInt("look_menu", v.menu).putInt("look_games", v.games).putInt("look_books", v.books).apply()
        }

    /** Tema: 0 como el sistema, 1 claro, 2 oscuro. Desde la 3.1, oscuro por defecto (lo de tu pareja va en claro). */
    var themeMode: Int
        get() = p.getInt("theme31", 2)
        set(v) { p.edit().putInt("theme31", v).apply() }

    /** Ya has usado el doble toque para ver lo de tu pareja (para no repetir la pista). */
    var knowsDoubleTap: Boolean
        get() = p.getBoolean("knows2tap", false)
        set(v) { p.edit().putBoolean("knows2tap", v).apply() }

    // ---- Copia automática en Google Drive ----
    var driveUri: String
        get() = p.getString("driveuri", "") ?: ""
        set(v) { p.edit().putString("driveuri", v).apply() }

    var driveLast: Long
        get() = p.getLong("drivelast", 0L)
        set(v) { p.edit().putLong("drivelast", v).apply() }

    var driveErr: String
        get() = p.getString("driveerr", "") ?: ""
        set(v) { p.edit().putString("driveerr", v).apply() }

    // ---- Claves de servicios externos ----
    /** IGDB (de Twitch): Client ID y secreto de tu aplicación de Twitch, para la duración de los juegos. */
    var igdbId: String
        get() = p.getString("igdbid", "") ?: ""
        set(v) { p.edit().putString("igdbid", v.trim()).putString("igdbtok", "").apply() }

    var igdbSecret: String
        get() = p.getString("igdbsecret", "") ?: ""
        set(v) { p.edit().putString("igdbsecret", v.trim()).putString("igdbtok", "").apply() }

    /** Pase temporal que da Twitch (dura semanas) y cuándo caduca. */
    var igdbToken: String
        get() = p.getString("igdbtok", "") ?: ""
        set(v) { p.edit().putString("igdbtok", v).apply() }

    var igdbExp: Long
        get() = p.getLong("igdbexp", 0L)
        set(v) { p.edit().putLong("igdbexp", v).apply() }

    // ---- Estado de la copia en la nube (Firebase) ----
    /** Hora (del servidor) de la última subida comprobada. */
    var cloudLast: Long
        get() = p.getLong("cloudlast", 0L)
        set(v) { p.edit().putLong("cloudlast", v).apply() }

    /** Fichas que había en la última subida comprobada. */
    var cloudCount: Int
        get() = p.getInt("cloudcount", 0)
        set(v) { p.edit().putInt("cloudcount", v).apply() }

    /** Último error al subir ("" si fue bien). */
    var cloudErr: String
        get() = p.getString("clouderr", "") ?: ""
        set(v) { p.edit().putString("clouderr", v).apply() }

    /** Borra la clave de Gemini que se guardaba en versiones anteriores (ya no se usa). */
    fun forgetGemini() { if (p.contains("gemini") || p.contains("gmodel")) p.edit().remove("gemini").remove("gmodel").apply() }

    /** Cuántas fichas hay en total (todas las secciones). */
    fun itemCount(): Int = (listOf(0, 1) + kinds.map { it.id }).sumOf { load(it).size + loadPlays(it).size }

    /** Clave de TMDB (películas: buscador, portadas, fondos y director). */
    var tmdbKey: String
        get() = p.getString("tmdb", "") ?: ""
        set(v) { p.edit().putString("tmdb", v.trim()).apply() }

    /**
     * Crea una sola vez las secciones de Películas y Series. Si ya tenías una categoría tuya con ese
     * nombre, no se duplica: esa misma pasa a buscar en internet y conserva sus fichas y criterios.
     */
    fun ensureMediaKinds() {
        if (p.getBoolean("mediakinds", false)) return
        var ks = kinds
        fun add(names: Set<String>, src: String, title: String, crit: List<String>, color: Int) {
            if (ks.any { it.source == src }) return
            val same = ks.firstOrNull { normName(it.title) in names && it.source.isEmpty() }
            ks = if (same != null) {
                ks.map { if (it.id == same.id) customKind(it.id, it.title, it.labels.drop(1), it.statuses[1], it.color, src) else it }
            } else {
                ks + customKind(nextKindId(), title, crit, "Viendo", color, src)
            }
            kinds = ks
        }
        add(setOf("peliculas", "pelis", "pelicula", "cine"), SRC_MOVIE, "Películas",
            listOf("Guion", "Actuación", "Dirección", "Música"), 0xFF2F4B7C.toInt())
        add(setOf("series", "serie"), SRC_TV, "Series",
            listOf("Guion", "Personajes", "Actuación", "Ritmo"), 0xFF2E6B4F.toInt())
        p.edit().putBoolean("mediakinds", true).apply()
    }

    /** Clave de RAWG (estudios y recomendaciones de juegos). */
    var rawgKey: String
        get() = p.getString("rawg", "") ?: ""
        set(v) { p.edit().putString("rawg", v.trim()).apply() }

    /** Clave de la API web de Steam y tu cuenta (SteamID64 o nombre de perfil). */
    var steamKey: String
        get() = p.getString("steamkey", "") ?: ""
        set(v) { p.edit().putString("steamkey", v.trim()).apply() }

    /** Última vez que se trajeron las horas de Steam. */
    var steamLast: Long
        get() = p.getLong("steamlast", 0L)
        set(v) { p.edit().putLong("steamlast", v).apply() }

    var steamUser: String
        get() = p.getString("steamuser", "") ?: ""
        set(v) { p.edit().putString("steamuser", v.trim()).apply() }

    // ---- La suite (Atrio en el PC) ----
    /** Capturas del PC guardadas en este móvil (JSON: id, uri, juego, tamaño…). */
    var shotsRaw: String
        get() = p.getString("shots", "[]") ?: "[]"
        set(v) { p.edit().putString("shots", v).apply() }

    /** Partes jugadas de las que ya se ha avisado (para no repetir la notificación). */
    var segsNotified: Set<String>
        get() = (p.getString("segsnot", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()
        set(v) { p.edit().putString("segsnot", v.toList().takeLast(400).joinToString(",")).apply() }

    /** Partes jugadas que has decidido no valorar (no vuelven a salir en «Por valorar»). */
    var segsSkipped: Set<String>
        get() = (p.getString("segskip", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()
        set(v) { p.edit().putString("segskip", v.toList().takeLast(400).joinToString(",")).apply() }

    /** Avisar cuando acabe una partida en el PC para valorarla. */
    var notifySegs: Boolean
        get() = p.getBoolean("notifysegs", true)
        set(v) { p.edit().putBoolean("notifysegs", v).apply() }

    /** Guardar también las capturas en la galería del móvil (Imágenes/Ronrón). */
    var shotsToGallery: Boolean
        get() = p.getBoolean("shotsgal", true)
        set(v) { p.edit().putBoolean("shotsgal", v).apply() }

    /** Último PC elegido para mandar órdenes (si tienes varios con Atrio). */
    var suitePc: String
        get() = p.getString("suitepc", "") ?: ""
        set(v) { p.edit().putString("suitepc", v).apply() }

    // ---- Siguiente (cartas) ----
    /** Gustos marcados a mano (géneros o temas). */
    fun tastes(kind: Int): Set<String> =
        (p.getString("tastes_$kind", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

    fun setTastes(kind: Int, v: Set<String>) { p.edit().putString("tastes_$kind", v.joinToString(",")).apply() }

    /** Cartas que te han gustado. */
    fun likes(kind: Int): List<Card> =
        try { cardsFromJson(JSONArray(p.getString("likes_$kind", "[]"))) } catch (e: Exception) { emptyList() }

    fun setLikes(kind: Int, v: List<Card>) {
        val a = JSONArray()
        v.forEach { a.put(cardToJson(it)) }
        p.edit().putString("likes_$kind", a.toString()).apply()
    }

    /** Cartas descartadas (solo las claves). */
    fun nopes(kind: Int): Set<String> =
        (p.getString("nopes_$kind", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

    fun setNopes(kind: Int, v: Set<String>) { p.edit().putString("nopes_$kind", v.toList().takeLast(600).joinToString(",")).apply() }

    // ---- Objetivos anuales ----
    fun goal(kind: Int, year: Int): Int = p.getInt("goal_${kind}_$year", 0)
    fun setGoal(kind: Int, year: Int, v: Int) { p.edit().putInt("goal_${kind}_$year", v.coerceIn(0, 999)).apply() }

    // ---- Modo pareja ----
    private fun normUrl(v: String): String {
        var u = v.trim().trimEnd('/')
        if (u.isNotEmpty() && !u.startsWith("http")) u = "https://$u"
        return u
    }

    var dbUrl: String
        get() = (p.getString("dburl", "") ?: "").ifBlank { Account.DEFAULT_DB }
        set(v) { p.edit().putString("dburl", normUrl(v)).apply() }

    var coupleCode: String
        get() = p.getString("ccode", "") ?: ""
        set(v) { p.edit().putString("ccode", v.trim()).apply() }

    var myName: String
        get() = p.getString("myname", "") ?: ""
        set(v) { p.edit().putString("myname", v.trim()).apply() }

    var partnerRaw: String
        get() = p.getString("partner", "") ?: ""
        set(v) { p.edit().putString("partner", v).apply() }

    /** Identificador del dispositivo de tu pareja (para bajar solo sus datos). */
    var partnerId: String
        get() = p.getString("partnerid", "") ?: ""
        set(v) { p.edit().putString("partnerid", v).apply() }

    /** Avisos en segundo plano cuando tu pareja valora o recomienda algo. */
    var notifyPartner: Boolean
        get() = p.getBoolean("pnotify", false)
        set(v) { p.edit().putBoolean("pnotify", v).apply() }

    /** Firma de lo último que viste de tu pareja (para no avisar dos veces). */
    var partnerSeen: String
        get() = p.getString("pseen", "") ?: ""
        set(v) { p.edit().putString("pseen", v).apply() }

    /** Hay sesión iniciada en tu cuenta de Ronrón: tus datos se guardan en la nube y se mezclan entre dispositivos. */
    val paired: Boolean get() = dbUrl.isNotBlank() && accUid.isNotBlank()

    // ---- Cuenta de Ronrón (usuario y contraseña, con Firebase Authentication) ----
    /** Clave web del proyecto de Firebase (es pública: identifica el proyecto, no da acceso por sí sola). */
    var webKey: String
        get() = p.getString("fbkey", "") ?: ""
        set(v) { p.edit().putString("fbkey", v.trim()).apply() }

    var accUser: String
        get() = p.getString("accuser", "") ?: ""
        set(v) { p.edit().putString("accuser", v).apply() }

    var accUid: String
        get() = p.getString("accuid", "") ?: ""
        set(v) { p.edit().putString("accuid", v).apply() }

    /** Pase de larga duración para renovar la sesión sin volver a pedir la contraseña (la contraseña no se guarda). */
    var accRefresh: String
        get() = p.getString("accref", "") ?: ""
        set(v) { p.edit().putString("accref", v).apply() }

    var accToken: String
        get() = p.getString("acctok", "") ?: ""
        set(v) { p.edit().putString("acctok", v).apply() }

    var accExp: Long
        get() = p.getLong("accexp", 0L)
        set(v) { p.edit().putLong("accexp", v).apply() }

    /** Tu pareja: su nombre de usuario y su identificador en la nube. */
    var partnerUser: String
        get() = p.getString("puser", "") ?: ""
        set(v) { p.edit().putString("puser", v).apply() }

    var partnerUid: String
        get() = p.getString("puid", "") ?: ""
        set(v) { p.edit().putString("puid", v).apply() }

    /** Última subida que ya tienes mezclada de cada uno de tus otros dispositivos: id -> hora del servidor. */
    var devSeen: JSONObject
        get() = try { JSONObject(p.getString("devseen", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        set(v) { p.edit().putString("devseen", v.toString()).apply() }

    /** Solo para la prueba en el emulador: servidor de Firebase de mentira. */
    var authBase: String
        get() = p.getString("authbase", "") ?: ""
        set(v) { p.edit().putString("authbase", v).apply() }

    /**
     * Hueco de este dispositivo dentro de tu cuenta. Cada dispositivo escribe solo en el suyo y lee los de los demás,
     * así dos cambios a la vez (el móvil y Atrio, por ejemplo) nunca se pisan. Si los datos pasan a otro móvil
     * (copia de Android), se crea uno nuevo.
     */
    val accDevice: String
        get() {
            var d = p.getString("adev", "") ?: ""
            val aid = try {
                android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
            } catch (e: Exception) { "" }
            val savedAid = p.getString("adevaid", null)
            if (d.isNotEmpty() && savedAid != null && aid.isNotEmpty() && savedAid != aid) d = ""
            if (d.isEmpty()) {
                d = "m" + java.util.UUID.randomUUID().toString().replace("-", "").take(15)
                p.edit().putString("adev", d).putString("adevaid", aid).remove("devseen").apply()
            }
            return d
        }

    /**
     * Identificador de este móvil en la nube. Si los datos llegan a otro móvil (copia de Android),
     * se crea uno nuevo para no pisar la copia del móvil anterior; el viejo se recuerda como "tuyo".
     */
    val deviceId: String
        get() {
            var d = p.getString("devid", "") ?: ""
            val aid = try {
                android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
            } catch (e: Exception) { "" }
            val savedAid = p.getString("devaid", null)
            if (d.isNotEmpty() && savedAid != null && aid.isNotEmpty() && savedAid != aid) {
                ownIds = ownIds + d
                d = ""
            }
            if (d.isEmpty()) {
                d = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
                p.edit().putString("devid", d).putString("devaid", aid).apply()
            } else if (savedAid == null) {
                p.edit().putString("devaid", aid).apply()
            }
            return d
        }

    /** Identificadores de tus móviles anteriores (nunca son tu pareja). */
    var ownIds: Set<String>
        get() = (p.getString("ownids", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()
        set(v) { p.edit().putString("ownids", v.joinToString(",")).apply() }

    /** Recomendaciones que tú has hecho. */
    var recs: List<Rec>
        get() = try { recsFromJson(JSONArray(p.getString("recs", "[]"))) } catch (e: Exception) { emptyList() }
        set(v) {
            val a = JSONArray()
            v.forEach { a.put(recToJson(it)) }
            p.edit().putString("recs", a.toString()).apply()
        }

    /** Recomendaciones de tu pareja que ya has añadido o descartado. */
    var recsDone: Set<Long>
        get() = (p.getString("recsdone", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.toSet()
        set(v) { p.edit().putString("recsdone", v.joinToString(",")).apply() }

    /** Tus reacciones a lo de tu pareja: "tipo:id" -> emoji. */
    var reacts: Map<String, String>
        get() = try {
            val o = JSONObject(p.getString("reacts", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) { emptyMap() }
        set(v) {
            val o = JSONObject()
            v.forEach { (k, e) -> o.put(k, e) }
            p.edit().putString("reacts", o.toString()).apply()
        }

    /** Lo que se comparte con la pareja: tus datos, recomendaciones y reacciones, sin la API key. */
    fun syncData(): String {
        val o = JSONObject()
        for (n in listOf("games", "plays", "books", "bplays")) {
            o.put(n, JSONArray(p.getString(n, "[]")))
        }
        val ks = kinds
        o.put("kinds", JSONArray().apply { ks.forEach { put(kindToJson(it)) } })
        for (n in customNames(ks)) o.put(n, JSONArray(p.getString(n, "[]")))
        o.put("keys", keysJson())
        o.put("tombs", tombs)
        o.put("kindsAt", kindsAt)
        o.put("recs", JSONArray(p.getString("recs", "[]")))
        o.put("reacts", JSONObject(p.getString("reacts", "{}") ?: "{}"))
        o.put("goals", JSONObject()
            .put("g", goal(0, LocalDate.now().year)).put("b", goal(1, LocalDate.now().year)))
        o.put("likes", JSONObject()
            .put("0", JSONArray(p.getString("likes_0", "[]")))
            .put("1", JSONArray(p.getString("likes_1", "[]"))))
        return o.toString()
    }

    private fun gk(kind: Int) = when (kind) { 0 -> "games"; 1 -> "books"; else -> "items_$kind" }
    private fun pk(kind: Int) = when (kind) { 0 -> "plays"; 1 -> "bplays"; else -> "iplays_$kind" }

    // ---- Misma cuenta en varios dispositivos (p. ej. la tablet y Waydroid en Linux) ----

    /**
     * Fichas borradas: "lista:id" -> cuándo. Así un borrado en un dispositivo no vuelve a aparecer
     * al mezclar con otro que aún la tenía. Se olvidan a los 180 días.
     */
    var tombs: JSONObject
        get() = try { JSONObject(p.getString("tombs", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        set(v) {
            val lim = System.currentTimeMillis() - 180L * 86_400_000L
            v.keys().asSequence().toList().forEach { if (v.optLong(it, 0L) < lim) v.remove(it) }
            p.edit().putString("tombs", v.toString()).apply()
        }

    /** Última subida de tu cuenta que ya tienes mezclada aquí (hora del servidor). */
    var ownSeen: Long
        get() = p.getLong("ownseen", 0L)
        set(v) { p.edit().putLong("ownseen", v).apply() }

    private fun itemUpdated(o: JSONObject): Long = o.optLong("updated", 0L)

    /**
     * Guarda una lista apuntando lo que desaparece (borrado) y, si vuelve algo que estaba borrado
     * (deshacer), le pone la hora de ahora para que gane a ese borrado en los otros dispositivos.
     */
    private fun writeList(name: String, arr: JSONArray, ed: android.content.SharedPreferences.Editor) {
        val old = try { JSONArray(p.getString(name, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        val oldIds = (0 until old.length()).mapNotNull { old.optJSONObject(it)?.optLong("id") }.toSet()
        val t = tombs
        var tChanged = false
        val now = System.currentTimeMillis()
        val newIds = HashSet<Long>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id")
            newIds.add(id)
            val k = "$name:$id"
            if (t.has(k)) {
                if (t.optLong(k) >= itemUpdated(o)) o.put("updated", now)
                t.remove(k)
                tChanged = true
            }
        }
        for (id in oldIds) if (id !in newIds) { t.put("$name:$id", now); tChanged = true }
        if (tChanged) ed.putString("tombs", t.toString())
        ed.putString(name, arr.toString())
    }

    /**
     * Mezcla aquí lo subido desde otro dispositivo de tu misma cuenta: de cada ficha se queda la versión
     * más reciente; lo borrado en cualquiera de los dos se borra. Devuelve true si ha cambiado algo.
     */
    fun mergeRemote(data: String): Boolean {
        val r = JSONObject(data)
        val before = syncData()
        // Borrados de los dos lados.
        val t = tombs
        val rt = r.optJSONObject("tombs") ?: JSONObject()
        rt.keys().forEach { k -> if (rt.optLong(k, 0L) > t.optLong(k, 0L)) t.put(k, rt.optLong(k)) }
        // Categorías: las de los dos; si hay la misma en ambos, la versión que cambió más tarde.
        val lk = kinds
        val rk = kindsFromJson(r.optJSONArray("kinds") ?: JSONArray())
        val remoteNewer = r.optLong("kindsAt", 0L) > kindsAt
        val ids = (lk.map { it.id } + rk.map { it.id }).distinct().filter { !t.has("kind:$it") }
        val mk = ids.mapNotNull { id ->
            val a = lk.find { it.id == id }
            val b = rk.find { it.id == id }
            if (a != null && b != null) (if (remoteNewer) b else a) else a ?: b
        }
        val ed = p.edit()
        val names = dataNames + customNames(mk)
        for (n in names) {
            val loc = try { JSONArray(p.getString(n, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
            val rem = r.optJSONArray(n) ?: JSONArray()
            val byId = LinkedHashMap<Long, JSONObject>()
            for (i in 0 until loc.length()) loc.optJSONObject(i)?.let { byId[it.optLong("id")] = it }
            for (i in 0 until rem.length()) {
                val o = rem.optJSONObject(i) ?: continue
                val id = o.optLong("id")
                val cur = byId[id]
                if (cur == null) byId[id] = o
                else if (itemUpdated(o) > itemUpdated(cur)) byId[id] = uniteLists(o, cur)
                else byId[id] = uniteLists(cur, o)
            }
            val out = JSONArray()
            byId.forEach { (id, o) ->
                val k = "$n:$id"
                if (!(t.has(k) && t.optLong(k) >= itemUpdated(o))) out.put(o)
            }
            ed.putString(n, out.toString())
        }
        ed.putString("kinds", JSONArray().apply { mk.forEach { put(kindToJson(it)) } }.toString())
        ed.putLong("kindsat", maxOf(kindsAt, r.optLong("kindsAt", 0L)))
        ed.putString("tombs", t.toString())
        // Recomendaciones y reacciones: se juntan; nada se pierde.
        val recs = JSONArray(p.getString("recs", "[]") ?: "[]")
        val have = (0 until recs.length()).map { recs.optJSONObject(it)?.optLong("id") }.toSet()
        val rr = r.optJSONArray("recs") ?: JSONArray()
        for (i in 0 until rr.length()) rr.optJSONObject(i)?.let { if (it.optLong("id") !in have) recs.put(it) }
        ed.putString("recs", recs.toString())
        val reacts = JSONObject(p.getString("reacts", "{}") ?: "{}")
        val rre = r.optJSONObject("reacts") ?: JSONObject()
        rre.keys().forEach { k -> if (!reacts.has(k)) reacts.put(k, rre.optString(k)) }
        ed.putString("reacts", reacts.toString())
        ed.commit()
        applyKeys(r.optJSONObject("keys"), onlyEmpty = true)
        r.optJSONObject("goals")?.let { g ->
            val y = LocalDate.now().year
            if (goal(0, y) == 0 && g.optInt("g", 0) > 0) setGoal(0, y, g.optInt("g"))
            if (goal(1, y) == 0 && g.optInt("b", 0) > 0) setGoal(1, y, g.optInt("b"))
        }
        return syncData() != before
    }

    /** Este dispositivo pasa a ser el mismo de tu cuenta (mismo hueco en la nube). */
    fun adoptDevice(id: String) {
        val old = deviceId
        if (old != id) ownIds = ownIds + old
        val aid = try {
            android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) { "" }
        p.edit().putString("devid", id).putString("devaid", aid).apply()
    }

    // ---- Categorías propias ----
    var kinds: List<Kind>
        get() = try { kindsFromJson(JSONArray(p.getString("kinds", "[]"))) } catch (e: Exception) { emptyList() }
        set(v) {
            p.edit().putString("kinds", JSONArray().apply { v.forEach { put(kindToJson(it)) } }.toString())
                .putLong("kindsat", System.currentTimeMillis()).apply()
        }

    /** Cuándo cambiaron tus categorías por última vez (para la cuenta compartida entre dispositivos). */
    val kindsAt: Long get() = p.getLong("kindsat", 0L)

    /** Id libre para una categoría nueva (nunca se reutiliza uno de una categoría borrada). */
    fun nextKindId(): Int {
        val n = maxOf(p.getInt("kindseq", 1), kinds.maxOfOrNull { it.id } ?: 1) + 1
        p.edit().putInt("kindseq", n).apply()
        return n
    }

    /** Borra una categoría propia con todas sus fichas. */
    fun deleteKind(id: Int) {
        tombs = tombs.put("kind:$id", System.currentTimeMillis())
        kinds = kinds.filter { it.id != id }
        p.edit().remove(gk(id)).remove(pk(id)).apply()
    }

    private fun customNames(ks: List<Kind>) = ks.flatMap { listOf(gk(it.id), pk(it.id)) }

    fun load(kind: Int = 0): List<Game> = gamesFromJson(JSONArray(p.getString(gk(kind), "[]")))

    fun save(list: List<Game>, kind: Int = 0) {
        val a = JSONArray()
        list.forEach { a.put(gameToJson(it)) }
        val ed = p.edit()
        writeList(gk(kind), a, ed)
        ed.apply()
    }

    fun loadPlays(kind: Int = 0): List<Play> = playsFromJson(JSONArray(p.getString(pk(kind), "[]")))

    fun savePlays(list: List<Play>, kind: Int = 0) {
        val a = JSONArray()
        list.forEach { a.put(playToJson(it)) }
        val ed = p.edit()
        writeList(pk(kind), a, ed)
        ed.apply()
    }

    private val dataNames = listOf("games", "plays", "books", "bplays")

    /** Todos los datos en un JSON (incluye la clave de SteamGridDB). */
    /** Todas las claves de servicios (SteamGridDB, RAWG, IGDB y Steam), para copias y la nube. */
    fun keysJson(): JSONObject = JSONObject()
        .put("sgdb", key).put("rawg", rawgKey).put("igdbId", igdbId).put("igdbSecret", igdbSecret)
        .put("steamKey", steamKey).put("steamUser", steamUser).put("tmdb", tmdbKey)

    /** Recupera las claves de una copia. onlyEmpty: solo rellena las que no tengas puestas. */
    fun applyKeys(o: JSONObject?, onlyEmpty: Boolean) {
        if (o == null) return
        fun v(k: String) = o.optString(k, "").trim()
        if (v("sgdb").isNotEmpty() && (!onlyEmpty || key.isBlank())) key = v("sgdb")
        if (v("rawg").isNotEmpty() && (!onlyEmpty || rawgKey.isBlank())) rawgKey = v("rawg")
        if (v("igdbId").isNotEmpty() && (!onlyEmpty || igdbId.isBlank())) igdbId = v("igdbId")
        if (v("igdbSecret").isNotEmpty() && (!onlyEmpty || igdbSecret.isBlank())) igdbSecret = v("igdbSecret")
        if (v("steamKey").isNotEmpty() && (!onlyEmpty || steamKey.isBlank())) steamKey = v("steamKey")
        if (v("steamUser").isNotEmpty() && (!onlyEmpty || steamUser.isBlank())) steamUser = v("steamUser")
        if (v("tmdb").isNotEmpty() && (!onlyEmpty || tmdbKey.isBlank())) tmdbKey = v("tmdb")
    }

    fun exportAll(): String {
        val o = JSONObject()
        o.put("app", "valorin").put("version", 2).put("key", key).put("keys", keysJson())
        for (n in dataNames) {
            o.put(n, JSONArray(p.getString(n, "[]")))
        }
        val ks = kinds
        o.put("kinds", JSONArray().apply { ks.forEach { put(kindToJson(it)) } })
        for (n in customNames(ks)) o.put(n, JSONArray(p.getString(n, "[]")))
        return o.toString(2)
    }

    /** Copia automática de lo que había antes de la última importación. */
    val hasUndoImport: Boolean get() = (p.getString("undoimport", "") ?: "").isNotEmpty()

    /** Guarda cómo está todo ahora, para poder deshacer una importación que añade o completa fichas. */
    fun keepUndo() {
        val names = dataNames + customNames(kinds)
        val backup = JSONObject()
        names.forEach { backup.put(it, JSONArray(p.getString(it, "[]") ?: "[]")) }
        backup.put("kinds", JSONArray(p.getString("kinds", "[]") ?: "[]"))
        p.edit().putString("undoimport", backup.toString()).commit()
    }

    /** Sustituye los datos por los de [o]. Si está mal, deja todo como estaba. */
    private fun replaceData(o: JSONObject) {
        // Si el archivo trae categorías propias, también se sustituyen; si es antiguo (sin ellas), se dejan como están.
        val withKinds = o.has("kinds")
        val newKinds = if (withKinds) kindsFromJson(o.optJSONArray("kinds") ?: JSONArray()) else emptyList()
        val curKinds = kinds
        val names = dataNames + (if (withKinds) customNames(curKinds) + customNames(newKinds) else emptyList()).distinct()
        val oldKindsTxt = p.getString("kinds", "[]") ?: "[]"
        val old = names.map { p.getString(it, "[]") ?: "[]" }
        val backup = JSONObject()
        names.forEachIndexed { i, n -> backup.put(n, JSONArray(old[i])) }
        backup.put("kinds", JSONArray(oldKindsTxt))
        val ed = p.edit()
        names.forEach { writeList(it, o.optJSONArray(it) ?: JSONArray(), ed) }
        if (withKinds) ed.putString("kinds", (o.optJSONArray("kinds") ?: JSONArray()).toString())
        ed.apply()
        try {
            load(0); loadPlays(0); load(1); loadPlays(1)
            kinds.forEach { load(it.id); loadPlays(it.id) }
        } catch (e: Exception) {
            val back = p.edit()
            names.forEachIndexed { i, n -> back.putString(n, old[i]) }
            back.putString("kinds", oldKindsTxt)
            back.apply()
            throw IllegalArgumentException("archivo dañado")
        }
        p.edit().putString("undoimport", backup.toString()).apply()
    }

    /** Sustituye todos los datos por los del archivo. */
    fun importAll(text: String) {
        val o = JSONObject(text)
        if (o.optString("app") != "valorin") throw IllegalArgumentException("no es una copia de esta app")
        replaceData(o)
        val k = o.optString("key", "")
        if (k.isNotBlank()) key = k
        applyKeys(o.optJSONObject("keys"), onlyEmpty = false)
    }

    /** Sustituye todos los datos por los guardados en la nube (texto de syncData), con recomendaciones, reacciones y objetivos. */
    fun importSync(data: String) {
        val o = JSONObject(data)
        replaceData(o)
        applyKeys(o.optJSONObject("keys"), onlyEmpty = true)
        o.optJSONArray("recs")?.let { p.edit().putString("recs", it.toString()).apply() }
        o.optJSONObject("reacts")?.let { p.edit().putString("reacts", it.toString()).apply() }
        o.optJSONObject("goals")?.let { g ->
            val y = LocalDate.now().year
            if (g.optInt("g", 0) > 0) setGoal(0, y, g.optInt("g"))
            if (g.optInt("b", 0) > 0) setGoal(1, y, g.optInt("b"))
        }
    }

    /** Vuelve a lo que había antes de la última importación. */
    fun undoImport() {
        val t = p.getString("undoimport", "") ?: ""
        if (t.isEmpty()) return
        val o = JSONObject(t)
        val ed = p.edit()
        o.keys().forEach { n ->
            if (n == "kinds") ed.putString("kinds", (o.optJSONArray(n) ?: JSONArray()).toString())
            else writeList(n, o.optJSONArray(n) ?: JSONArray(), ed)
        }
        ed.putString("undoimport", "")
        ed.apply()
        // Lo de categorías que ya no existen tras deshacer se borra (si no, aparecería en otra con el mismo id).
        val alive = kinds.map { it.id }.toSet()
        val gone = p.all.keys.filter { k ->
            (k.startsWith("items_") || k.startsWith("iplays_")) && k.substringAfter("_").toIntOrNull()?.let { it !in alive } == true
        }
        if (gone.isNotEmpty()) p.edit().apply { gone.forEach { remove(it) } }.apply()
    }
}
