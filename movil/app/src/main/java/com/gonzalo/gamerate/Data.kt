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

    /** Admite un fondo propio (de tus archivos, de la galería o la propia portada): libros y lo creado a mano además. */
    val admiteFondo: Boolean get() = hasHero || isBook || custom

    /** Tiene horas y duración (solo videojuegos). */
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

/** Un apunte con notas. */
data class Entry(
    val time: Long, val general: Int, val gfx: Int, val story: Int,
    val music: Int, val gameplay: Int, val note: String, val chapter: Int = 0
)

/**
 * Apunte rápido mientras juegas: solo texto (y, si quieres, una captura del PC).
 * [del]: borrado (se queda la marca para que no reviva al mezclar con otro dispositivo); [u]: última edición.
 */
data class Memo(val t: Long, val x: String, val sh: String = "", val del: Boolean = false, val u: Long = 0L)

fun memoToJson(m: Memo): JSONObject = JSONObject().put("t", m.t).put("x", m.x).apply {
    if (m.sh.isNotEmpty()) put("sh", m.sh)
    if (m.del) put("del", 1)
    if (m.u > 0L) put("u", m.u)
}
fun memoFromJson(o: JSONObject) = Memo(o.optLong("t"), o.optString("x"), o.optString("sh", ""), o.optInt("del", 0) == 1 || o.optBoolean("del", false), o.optLong("u", 0L))

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
    val memos: List<Memo> = emptyList()
) {
    val liveMemos: List<Memo> get() = memos.filter { !it.del }

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
                e.getInt("music"), e.getInt("gameplay"), e.getString("note"), e.optInt("chapter", 0))
        },
        o.optInt("chapter", 0), o.optInt("every", 3),
        o.optLong("start", 0L), o.optLong("end", 0L), o.optString("creator", ""),
        o.optString("platform", ""), o.optInt("pages", 0), o.optInt("minutes", 0), o.optLong("updated", 0L),
        o.optInt("ttbMain", 0), o.optInt("ttbExtra", 0), o.optInt("ttbFull", 0), o.optString("ttbSrc", ""),
        (o.optJSONArray("parts") ?: JSONArray()).let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Part(it.optString("n"), it.optBoolean("d")) } }
        },
        o.optString("logo", ""),
        (o.optJSONArray("memos") ?: JSONArray()).let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { memoFromJson(it) } } }
    )
}

fun playToJson(pl: Play): JSONObject {
    val es = JSONArray()
    pl.entries.forEach { e ->
        es.put(JSONObject().put("time", e.time).put("general", e.general).put("gfx", e.gfx)
            .put("story", e.story).put("music", e.music).put("gameplay", e.gameplay)
            .put("note", e.note).put("chapter", e.chapter))
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
        }
}

/**
 * Al mezclar dos versiones de una ficha, los apuntes rápidos se suman (el móvil y Atrio
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
        val sorted = m.values.sortedBy { it.optLong("t") }
        win.put(key, JSONArray().apply { sorted.forEach { put(it) } })
    }
    union("memos", "t") { p, q ->
        val pd = p.optInt("del", 0) == 1 || p.optBoolean("del", false)
        val qd = q.optInt("del", 0) == 1 || q.optBoolean("del", false)
        if (qd && !pd) q else if (q.optLong("u", 0L) > p.optLong("u", 0L)) q else p
    }
    // Las partidas para valorar ya no existen: lo que quede de ellas de otras versiones se descarta
    win.remove("segs"); win.remove("noParts")
    return win
}

/**
 * Un cambio hecho sobre una lista que ya no es la guardada (mientras tanto llegó algo de otro dispositivo): de [nueva] solo
 * cuenta lo que se cambió respecto a [antes] (lo añadido, lo modificado y lo quitado); lo demás se toma de lo guardado
 * [ahora]. El orden es el de [nueva]; lo que llegó de fuera va al final.
 */
fun <T> rebase(antes: List<T>, nueva: List<T>, ahora: List<T>, id: (T) -> Long): List<T> {
    val a = antes.associateBy(id)
    val g = ahora.associateBy(id)
    val out = ArrayList<T>()
    val puestos = HashSet<Long>()
    for (x in nueva) {
        val k = id(x)
        // Tocado aquí (nuevo o cambiado): lo tuyo. Sin tocar: lo guardado (si otro dispositivo lo borró, fuera)
        val v = if (a[k] != x) x else g[k] ?: continue
        if (puestos.add(k)) out += v
    }
    for (x in ahora) { val k = id(x); if (k !in puestos && k !in a) { puestos.add(k); out += x } }
    return out
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
    companion object {
        /** Guardar y mezclar lo de otro dispositivo, nunca a la vez (si no, una mezcla podría pisar algo recién guardado). */
        val cerrojo = Any()
        /** Sube cada vez que llega algo de otro dispositivo (la app sabe así si sus listas en memoria siguen al día). */
        @Volatile var generacion = 0L
            private set
        internal fun otraGeneracion() { generacion++ }

        /** Texto canónico de un valor JSON (claves en orden, sin la versión de sincronización) para comparar fichas. */
        internal fun canon(v: Any?): String = when (v) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> v.keys().asSequence().filter { it != "sv" }.sorted().joinToString(",", "{", "}") { "$it:${canon(v.opt(it))}" }
            is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.opt(it)) }
            is Double, is Float -> (v as Number).toDouble().let { d -> if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString() else d.toString() }
            is Number -> v.toLong().toString()
            is Boolean -> v.toString()
            else -> JSONObject.quote(v.toString())
        }

        /** Lo que solo se guarda si no está vacío (en el diario y en los apuntes rápidos): quitarlo también es un cambio. */
        private val OPCIONALES = setOf("sobre", "foto", "comp", "memos")
        private fun vacio(v: Any?) = v == null || v == JSONObject.NULL || v == "" || v == false || (v is JSONArray && v.length() == 0)

        /** ¿[nuevo] dice lo mismo que [antes]? (lo que [antes] tenga de más, de otra versión de la app, no cuenta) */
        internal fun mismaFicha(antes: JSONObject, nuevo: JSONObject): Boolean {
            for (k in nuevo.keys()) if (k != "sv" && canon(nuevo.opt(k)) != canon(antes.opt(k))) return false
            for (k in OPCIONALES) if (!nuevo.has(k) && antes.has(k) && !vacio(antes.opt(k))) return false
            return true
        }
    }

    private val app = ctx.applicationContext
    /** El de la app (para lo que se guarda fuera de estas preferencias). */
    val contexto: Context get() = app
    private val p = ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE)

    var key: String
        get() = p.getString("key", "") ?: ""
        set(v) { p.edit().putString("key", v.trim()).apply() }

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
        set(v) { p.edit().putInt("theme31", v).apply(); ajustesCambiados(app); Widgets.pedir(app) }

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

    // ---- Tus ajustes en todos tus dispositivos (salvo la maqueta de cada ficha y el brillo del lector) ----
    /** Apariencia (claro/oscuro, acento, portadas en las listas…), ajustes del lector y capturas fuera de la nube. */
    fun ajustesJson(): JSONObject {
        val skin = JSONObject()
        for ((k, v) in p.all) if (k in AJUSTES_SKIN && v != null) skin.put(k, v)
        val lector = runCatching { JSONObject(app.getSharedPreferences("lector", Context.MODE_PRIVATE).getString("ajustes", "{}") ?: "{}") }.getOrElse { JSONObject() }
        lector.remove("brightness"); lector.remove("size")
        return JSONObject().put("at", p.getLong("ajustesAt", 0L)).put("skin", skin).put("lector", lector)
            .put("noCloud", JSONArray((p.getStringSet("shotsNoCloud", emptySet()) ?: emptySet()).toList()))
    }

    /** Los ajustes de otro dispositivo, si son más nuevos (y las capturas fuera de la nube, de los dos). */
    private fun applyAjustes(r: JSONObject) {
        val nc = (p.getStringSet("shotsNoCloud", emptySet()) ?: emptySet()) + (0 until (r.optJSONArray("noCloud")?.length() ?: 0)).map { r.getJSONArray("noCloud").getString(it) }
        p.edit().putStringSet("shotsNoCloud", nc).apply()
        val at = r.optLong("at", 0L)
        if (at <= p.getLong("ajustesAt", 0L)) return
        val ed = p.edit()
        r.optJSONObject("skin")?.let { sk ->
            for (k in sk.keys()) if (k in AJUSTES_SKIN) when (val v = sk.get(k)) {
                is Boolean -> ed.putBoolean(k, v)
                is Int -> if (k == "skin_accent") ed.putLong(k, v.toLong()) else ed.putInt(k, v)
                is Long -> ed.putLong(k, v)
                is Number -> ed.putLong(k, v.toLong())
                is String -> ed.putString(k, v)
            }
        }
        ed.putLong("ajustesAt", at).commit()
        r.optJSONObject("lector")?.let { l ->
            val lp = app.getSharedPreferences("lector", Context.MODE_PRIVATE)
            val mine = runCatching { JSONObject(lp.getString("ajustes", "{}") ?: "{}") }.getOrElse { JSONObject() }
            for (k in listOf("brightness", "size")) mine.opt(k)?.let { l.put(k, it) }
            lp.edit().putString("ajustes", l.toString()).apply()
        }
        // La app aplica lo nuevo enseguida (Skin y el tema están en memoria)
        if (Skin.revista) Skin.textScale = p.getInt("skin_escala", 100).coerceIn(80, 140) / 100f
        AjustesHub.version++
    }

    // ---- Lo que hay que subir aunque no hayas tocado ninguna ficha (lo leído, lo que está en tu nube) ----
    /** Cuándo cambió por última vez algo de la cuenta que no es una ficha (hora de este dispositivo). */
    var dirtyAt: Long
        get() = p.getLong("dirtyAt", 0L)
        set(v) { p.edit().putLong("dirtyAt", v).apply() }
    /** Cuándo se subió por última vez todo a la cuenta (hora de este dispositivo). */
    var pushedAt: Long
        get() = p.getLong("pushedAt", 0L)
        set(v) { p.edit().putLong("pushedAt", v).apply() }
    val pendiente: Boolean get() = dirtyAt > pushedAt

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
     * Deja en orden las secciones: una sola de Películas y una sola de Series (si faltan, se crean; si ya tenías una
     * categoría tuya con ese nombre, esa misma pasa a buscar en internet y conserva sus fichas y criterios) y sin
     * categorías repetidas. Se puede llamar las veces que haga falta: si todo está bien, no toca nada.
     */
    fun ensureMediaKinds() {
        val peli = setOf("peliculas", "pelis", "pelicula", "cine")
        val serie = setOf("series", "serie")
        var ks = kinds
        // 1) Una con el nombre de Películas (o Series) que busca en el sitio equivocado (p. ej. «Películas» que quedó
        //    como Series al juntar dispositivos) vuelve a su buscador, antes de juntar repetidas
        for ((names, src) in listOf(peli to SRC_MOVIE, serie to SRC_TV)) {
            if (ks.any { it.source == src } || tombs.has("media:$src")) continue
            val same = ks.firstOrNull { normName(it.title) in names && it.source != src } ?: continue
            ks = ks.map { if (it.id == same.id) customKind(it.id, it.title, it.labels.drop(1), it.statuses[1], it.color, src) else it }
            kinds = ks
        }
        repairKinds()
        ks = kinds
        // 2) Las que falten, se crean
        for ((title, src, crit, color) in listOf(
            listOf<Any>("Películas", SRC_MOVIE, listOf("Guion", "Actuación", "Dirección", "Música"), 0xFF2F4B7C.toInt()),
            listOf<Any>("Series", SRC_TV, listOf("Guion", "Personajes", "Actuación", "Ritmo"), 0xFF2E6B4F.toInt())
        )) {
            if (ks.any { it.source == src } || tombs.has("media:$src")) continue
            @Suppress("UNCHECKED_CAST")
            ks = ks + customKind(nextKindId(), title as String, crit as List<String>, "Viendo", color as Int, src as String)
            kinds = ks
        }
        p.edit().putBoolean("mediakinds", true).apply()
    }

    /**
     * Categorías repetidas (mismo buscador de internet, o mismo nombre a mano): se juntan en la de id más bajo, con
     * las fichas de todas. Pasaba cuando dos dispositivos creaban cada uno sus «Películas» y «Series» con los mismos
     * números y al juntar las cuentas una categoría pisaba a otra. Devuelve cuántas juntó.
     */
    fun repairKinds(): Int {
        val ks = kinds
        fun key(k: Kind) = if (k.source.isNotEmpty()) "src:${k.source}" else "name:${normName(k.title)}"
        val groups = ks.groupBy { key(it) }.filter { it.value.size > 1 }
        if (groups.isEmpty()) return 0
        val drop = HashSet<Int>()
        val t = tombs
        val now = System.currentTimeMillis()
        for ((_, g) in groups) {
            val keep = g.minByOrNull { it.id }!!
            for (o in g) {
                if (o.id == keep.id) continue
                for ((a, b) in listOf(gk(keep.id) to gk(o.id), pk(keep.id) to pk(o.id))) {
                    val x = try { JSONArray(p.getString(a, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
                    val y = try { JSONArray(p.getString(b, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
                    val have = (0 until x.length()).mapNotNull { x.optJSONObject(it)?.optLong("id") }.toSet()
                    for (i in 0 until y.length()) y.optJSONObject(i)?.let { if (it.optLong("id") !in have) x.put(it) }
                    p.edit().putString(a, x.toString()).remove(b).apply()
                }
                // Las sagas de la categoría que se va pasan a la que se queda
                val sg = try { JSONArray(p.getString("sagas", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
                for (i in 0 until sg.length()) sg.optJSONObject(i)?.let { if (it.optInt("kind", 1) == o.id) it.put("kind", keep.id) }
                p.edit().putString("sagas", sg.toString()).apply()
                t.put("kind:${o.id}", now)
                drop += o.id
            }
        }
        tombs = t
        kinds = ks.filter { it.id !in drop }
        return drop.size
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
        marca()
    }

    /** Cartas descartadas (solo las claves). */
    fun nopes(kind: Int): Set<String> =
        (p.getString("nopes_$kind", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

    fun setNopes(kind: Int, v: Set<String>) { p.edit().putString("nopes_$kind", v.toList().takeLast(600).joinToString(",")).apply() }

    // ---- Objetivos anuales ----
    fun goal(kind: Int, year: Int): Int = p.getInt("goal_${kind}_$year", 0)
    /** Minutos de lectura al día que te propones (0 = sin objetivo). Viaja con tus ajustes a tus otros dispositivos. */
    var metaLectura: Int
        get() = p.getInt("meta_lectura", 0)
        set(v) { p.edit().putInt("meta_lectura", v.coerceIn(0, 600)).apply(); ajustesCambiados(app) }
    /** A qué hora avisar si aún no has cumplido el objetivo de lectura (-1 = sin aviso). Solo en este dispositivo. */
    var avisoLectura: Int
        get() = p.getInt("aviso_lectura", -1)
        set(v) { p.edit().putInt("aviso_lectura", v).apply() }

    /** A qué hora recordarte escribir en el diario si ese día aún no has escrito (-1 = nunca). Solo en este dispositivo. */
    var avisoDiario: Int
        get() = p.getInt("aviso_diario", -1)
        set(v) { p.edit().putInt("aviso_diario", v).apply() }
    fun setGoal(kind: Int, year: Int, v: Int) { p.edit().putInt("goal_${kind}_$year", v.coerceIn(0, 999)).apply(); marca() }

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
        set(v) { p.edit().putString("myname", v.trim()).apply(); marca() }

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

    /** Lo que salió en tu último test de descubrir de cada tipo (0 juegos, 1 libros, 2 pelis y series): se comparte con tu pareja. */
    val quizzes: Map<Int, QuizRun>
        get() = quizRunsFromJson(runCatching { JSONObject(p.getString("quizzes", "{}") ?: "{}") }.getOrNull())

    fun saveQuiz(domain: Int, items: List<QuizItem>) {
        val o = runCatching { JSONObject(p.getString("quizzes", "{}") ?: "{}") }.getOrElse { JSONObject() }
        o.put("$domain", quizRunToJson(QuizRun(System.currentTimeMillis(), items.take(60))))
        p.edit().putString("quizzes", o.toString()).apply()
        marca()
    }

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
            marca()
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
            marca()
        }

    /** Lo que se comparte con la pareja: tus datos, recomendaciones y reacciones, sin la API key. */
    fun syncData(): String {
        val o = JSONObject()
        for (n in dataNames) {
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
        o.put("quiz", runCatching { JSONObject(p.getString("quizzes", "{}") ?: "{}") }.getOrElse { JSONObject() })
        o.put("ajustes", ajustesJson())
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
            marca()
        }

    /** Última subida de tu cuenta que ya tienes mezclada aquí (hora del servidor). */
    var ownSeen: Long
        get() = p.getLong("ownseen", 0L)
        set(v) { p.edit().putLong("ownseen", v).apply() }

    /**
     * Lo reciente que es una versión de una ficha: su última edición o, si es más nueva, la última vez que cambió en algún
     * dispositivo («sv», la pone [writeList]): así un cambio que no toca la hora de edición (una portada que se buscó sola,
     * las horas del PC…) también gana al mezclar y llega a todos tus dispositivos.
     */
    private fun itemUpdated(o: JSONObject): Long = maxOf(o.optLong("updated", 0L), o.optLong("sv", 0L))

    /** Lo más lejos que has llegado en un libro (en cualquier dispositivo): lo que viaja y a lo que se ponen todos. */
    private val LEJOS = listOf("bestCfi", "bestFrac", "bestLabel")

    /** Une lo leído de un libro en dos dispositivos: lo demás, de la versión más nueva; lo más lejos, el que más lejos llegó. */
    private fun uniteLectura(a: JSONObject, b: JSONObject): JSONObject {
        val (base, otra) = if (itemUpdated(b) > itemUpdated(a)) b to a else a to b
        val out = JSONObject(base.toString())
        val so = otra.optJSONObject("s")
        val stats = ReadStats.merge(a.optJSONObject("s")?.optJSONObject("stats"), b.optJSONObject("s")?.optJSONObject("stats"), deviceId, "legacy")
        // Lo que solo tenga la otra versión (p. ej. los ajustes del libro) se conserva; en lo demás manda la más nueva
        val sb = JSONObject(so?.toString() ?: "{}").also { m -> base.optJSONObject("s")?.let { b -> for (k in b.keys()) m.put(k, b.get(k)) } }
        sb.put("stats", stats)
        out.put("s", sb)
        if (so != null && so.optDouble("bestFrac", 0.0) > (base.optJSONObject("s")?.optDouble("bestFrac", 0.0) ?: 0.0)) for (k in LEJOS) if (so.has(k)) sb.put(k, so.get(k))
        out.put("updated", maxOf(itemUpdated(a), itemUpdated(b)))
        return out
    }

    /**
     * Guarda una lista apuntando lo que desaparece (borrado) y, si vuelve algo que estaba borrado
     * (deshacer), le pone la hora de ahora para que gane a ese borrado en los otros dispositivos.
     */
    private fun writeList(name: String, arr: JSONArray, ed: android.content.SharedPreferences.Editor, espera: Long = Sincro.ESPERA) = synchronized(cerrojo) {
        val old = try { JSONArray(p.getString(name, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        val oldById = HashMap<Long, JSONObject>()
        for (i in 0 until old.length()) old.optJSONObject(i)?.let { oldById[it.optLong("id")] = it }
        val t = tombs
        var tChanged = false
        var cambio = false
        val now = System.currentTimeMillis()
        val newIds = HashSet<Long>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id")
            newIds.add(id)
            val antes = oldById[id]
            // La versión de sincronización: la de antes si no ha cambiado nada; si ha cambiado, la de ahora
            if (antes == null) cambio = true
            else if (mismaFicha(antes, o)) { if (!o.has("sv") && antes.has("sv")) o.put("sv", antes.optLong("sv")) }
            else { o.put("sv", maxOf(now, antes.optLong("sv", 0L) + 1)); cambio = true }
            val k = "$name:$id"
            if (t.has(k)) {
                if (t.optLong(k) >= itemUpdated(o)) o.put("updated", now)
                t.remove(k)
                tChanged = true
            }
        }
        for (id in oldById.keys) if (id !in newIds) { t.put("$name:$id", now); tChanged = true }
        if (tChanged) ed.putString("tombs", t.toString())
        ed.putString(name, arr.toString())
        if (cambio || tChanged) { ed.putLong("dirtyAt", now); Sincro.pronto(app, espera) }
    }

    /** Ha cambiado algo de lo que viaja con tu cuenta y no es una lista (categorías, reacciones, objetivos…): a subirlo. */
    private fun marca() {
        p.edit().putLong("dirtyAt", System.currentTimeMillis()).apply()
        Sincro.pronto(app)
    }

    /**
     * Mezcla aquí lo subido desde otro dispositivo de tu misma cuenta: de cada ficha se queda la versión
     * más reciente; lo borrado en cualquiera de los dos se borra. Devuelve true si ha cambiado algo.
     */
    fun mergeRemote(data: String): Boolean = synchronized(cerrojo) { mergeRemoteYa(data).also { if (it) otraGeneracion() } }.also {
        // Lo que llega de otro dispositivo también se ve en los widgets al momento
        if (it) Widgets.pedir(app)
    }

    private fun mergeRemoteYa(data: String): Boolean {
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
                else if (n == "lectura") byId[id] = uniteLectura(cur, o)
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
        Negra.poner(loadNegra())
        // Lo que se ha vetado en otro dispositivo: su ficha de aquí también se va
        Negra.nombres.takeIf { it.isNotEmpty() }?.let { v ->
            if (load(0).any { normName(it.name) in v }) save(load(0).filterNot { normName(it.name) in v }, 0)
            if (loadPlays(0).any { normName(it.name) in v }) savePlays(loadPlays(0).filterNot { normName(it.name) in v }, 0)
        }
        applyLecturas()
        applyMaquetas()
        r.optJSONObject("ajustes")?.let { applyAjustes(it) }
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
            marca()
        }

    /** Cuándo cambiaron tus categorías por última vez (para la cuenta compartida entre dispositivos). */
    val kindsAt: Long get() = p.getLong("kindsat", 0L)

    /** Id libre para una categoría nueva (nunca se reutiliza uno de una categoría borrada). */
    fun nextKindId(): Int {
        // Un número al azar (no el siguiente): dos dispositivos que crean una categoría cada uno por su cuenta no
        // pueden acabar con el mismo id, y al juntar tus datos una pisaría a la otra
        val used = kinds.map { it.id }.toSet() + tombs.keys().asSequence().mapNotNull { it.removePrefix("kind:").toIntOrNull() }.toSet()
        var n: Int
        do { n = 1000 + java.util.Random().nextInt(1_000_000_000) } while (n in used)
        p.edit().putInt("kindseq", n).apply()
        return n
    }

    /** Borra una categoría propia con todas sus fichas. */
    fun deleteKind(id: Int) {
        // Si era Películas o Series y la borras tú, no vuelve a crearse sola (la marca viaja con tu cuenta)
        kinds.find { it.id == id }?.source?.takeIf { it.isNotEmpty() }?.let { tombs = tombs.put("media:$it", System.currentTimeMillis()) }
        tombs = tombs.put("kind:$id", System.currentTimeMillis())
        kinds = kinds.filter { it.id != id }
        p.edit().remove(gk(id)).remove(pk(id)).apply()
    }

    private fun customNames(ks: List<Kind>) = ks.flatMap { listOf(gk(it.id), pk(it.id)) }

    fun load(kind: Int = 0): List<Game> = gamesFromJson(JSONArray(p.getString(gk(kind), "[]")))

    fun save(list: List<Game>, kind: Int = 0) {
        val a = JSONArray()
        list.forEach { a.put(gameToJson(it)) }
        synchronized(cerrojo) {
            val ed = p.edit()
            writeList(gk(kind), a, ed)
            ed.apply()
        }
        Widgets.pedir(app)
    }

    fun loadPlays(kind: Int = 0): List<Play> = playsFromJson(JSONArray(p.getString(pk(kind), "[]")))

    fun savePlays(list: List<Play>, kind: Int = 0) {
        val a = JSONArray()
        list.forEach { a.put(playToJson(it)) }
        synchronized(cerrojo) {
            val ed = p.edit()
            writeList(pk(kind), a, ed)
            ed.apply()
        }
        Widgets.pedir(app)
    }

    private val dataNames = listOf("games", "plays", "books", "bplays", "sagas", "nube", "lectura", "maquetas", "diario", "negra")

    // ---- La lista negra de juegos: no salen en ningún sitio (viaja con la cuenta) ----
    fun loadNegra(): List<Vetado> = try { vetadosFromJson(JSONArray(p.getString("negra", "[]") ?: "[]")) } catch (e: Exception) { emptyList() }

    private fun saveNegra(l: List<Vetado>) {
        val a = JSONArray(); l.forEach { a.put(vetadoToJson(it)) }
        synchronized(cerrojo) { val ed = p.edit(); writeList("negra", a, ed); ed.commit() }
        Negra.poner(l)
    }

    /** Un juego a la lista negra: su ficha (en curso o valorada) se borra, y no vuelve a salir. */
    fun addNegra(nombre: String) {
        val n = normName(nombre)
        if (n.isEmpty()) return
        saveNegra(loadNegra().filter { normName(it.nombre) != n } + Vetado(Negra.idDe(nombre), nombre.trim(), System.currentTimeMillis()))
        save(load(0).filterNot { normName(it.name) == n }, 0)
        savePlays(loadPlays(0).filterNot { normName(it.name) == n }, 0)
    }

    fun removeNegra(v: Vetado) = saveNegra(loadNegra().filter { it.id != v.id })

    // ---- El diario: lo que escribes cada día (viaja con la cuenta como las fichas) ----
    fun loadDiario(): List<Nota> = try { notasFromJson(JSONArray(p.getString("diario", "[]") ?: "[]")) } catch (e: Exception) { emptyList() }

    fun saveDiario(list: List<Nota>) {
        val a = JSONArray(); list.forEach { a.put(notaToJson(it)) }
        synchronized(cerrojo) { val ed = p.edit(); writeList("diario", a, ed); ed.commit() }
        DiarioHub.notas = list
    }

    // ---- Tu nube (kDrive): lo subido, para que tus otros dispositivos y tu pareja lo encuentren ----
    fun loadNube(): List<NubeFile> = try {
        val a = JSONArray(p.getString("nube", "[]") ?: "[]")
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> nubeFromJson(o) } }
    } catch (e: Exception) { emptyList() }

    private fun saveNube(l: List<NubeFile>) {
        val a = JSONArray(); l.forEach { a.put(nubeToJson(it)) }
        synchronized(cerrojo) { val ed = p.edit(); writeList("nube", a, ed); ed.commit() }
    }

    fun addNube(add: List<NubeFile>) {
        val ks = add.map { it.k }.toSet()
        saveNube(loadNube().filter { it.k !in ks } + add)
    }

    fun removeNube(k: String) = saveNube(loadNube().filter { it.k != k })

    /** Capturas que no quieres en la nube. */
    fun shotNoCloud(id: String): Boolean = p.getStringSet("shotsNoCloud", emptySet())?.contains(id) == true
    fun setShotNoCloud(id: String) { p.edit().putStringSet("shotsNoCloud", (p.getStringSet("shotsNoCloud", emptySet()) ?: emptySet()) + id).apply(); ajustesCambiados(app) }

    // ---- La maqueta de cada ficha (qué casilla va dónde, tus textos, tus fotos) y la de cada categoría: viaja con la cuenta ----
    // La copia que se usa es la de las preferencias «maqueta» (MagLayouts); la lista «maquetas» es la que viaja. Cada
    // ficha lleva su hora: gana la más nueva. Una maqueta restablecida (borrada) se borra en los demás dispositivos.
    private fun maquetaPrefs() = app.getSharedPreferences("maqueta", Context.MODE_PRIVATE)
    private fun claveMaqueta(k: String) = Regex("""^(tpl:)?[pv]:\d+(:\d+)?$""").matches(k)
    private fun maquetasLista(): JSONArray = try { JSONArray(p.getString("maquetas", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }

    /** Una maqueta que cambia aquí (ficha o categoría): se apunta para subirla con la cuenta. */
    fun saveMaqueta(key: String, json: String) {
        if (!claveMaqueta(key)) return
        val id = nubeId(key); val now = System.currentTimeMillis()
        val a = maquetasLista(); val out = JSONArray()
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { if (it.optLong("id") != id) out.put(it) }
        out.put(JSONObject().put("id", id).put("k", key).put("j", json).put("updated", now))
        synchronized(cerrojo) { val ed = p.edit(); writeList("maquetas", out, ed); ed.commit() }
        maquetaPrefs().edit().putLong("@at:$key", now).apply()
    }

    /** Maquetas restablecidas aquí: se borran también de la cuenta. */
    fun removeMaquetas(keys: List<String>) {
        val ids = keys.filter { claveMaqueta(it) }.map { nubeId(it) }.toSet()
        if (ids.isEmpty()) return
        val a = maquetasLista(); val out = JSONArray()
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { if (it.optLong("id") !in ids) out.put(it) }
        synchronized(cerrojo) { val ed = p.edit(); writeList("maquetas", out, ed); ed.commit() }
        val mp = maquetaPrefs().edit(); keys.forEach { mp.remove("@at:$it") }; mp.apply()
    }

    /**
     * Pone las maquetas de la cuenta en las preferencias que usa la pantalla (la más nueva de cada ficha) y sube las que
     * solo estaban aquí (las de antes de esta versión). Devuelve true si ha cambiado alguna de las que se ven.
     */
    fun applyMaquetas(): Boolean {
        val mp = maquetaPrefs()
        val a = maquetasLista()
        val ed = mp.edit()
        var changed = false
        val hay = HashSet<String>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val k = o.optString("k"); val j = o.optString("j")
            if (!claveMaqueta(k) || j.isBlank()) continue
            hay.add(k)
            val at = mp.getLong("@at:$k", 0L)
            val cur = mp.getString(k, null)
            if (cur == null || o.optLong("updated") > at) {
                if (cur != j) { ed.putString(k, j); changed = true }
                ed.putLong("@at:$k", o.optLong("updated"))
            }
        }
        val subir = ArrayList<Pair<String, String>>()
        for ((k, v) in mp.all) {
            if (k.startsWith("@at:") && k.substring(4) !in hay) {
                // Estaba en la cuenta y ya no: se restableció en otro dispositivo
                val key = k.substring(4)
                if (mp.contains(key)) { ed.remove(key); changed = true }
                ed.remove(k)
            } else if (v is String && claveMaqueta(k) && k !in hay && !mp.contains("@at:$k")) subir.add(k to v)
        }
        ed.apply()
        // Las de antes de esta versión (sin hora): suben tal cual
        for ((k, v) in subir) saveMaqueta(k, v)
        if (changed) MagLayouts.cambiaron()
        return changed
    }

    // ---- Lo leído de cada libro (posición, subrayados, notas, marcadores): viaja con la cuenta ----
    /** Copia el estado del lector de un libro a la cuenta (sin la dirección del archivo, que es de este dispositivo). */
    fun saveLectura(id: Long, st: JSONObject) {
        val s = JSONObject(st.toString()).apply { remove("uri"); remove("name"); remove("added"); optJSONObject("settings")?.let { it.remove("brightness"); it.remove("size") } }
        val a = try { JSONArray(p.getString("lectura", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        val out = JSONArray()
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { if (it.optLong("id") != id) out.put(it) }
        out.put(JSONObject().put("id", id).put("s", s).put("updated", System.currentTimeMillis()))
        // (por dónde vas cambia en cada página: se sube como mucho cada medio minuto mientras lees, y al cerrar el libro)
        synchronized(cerrojo) { val ed = p.edit(); writeList("lectura", out, ed, espera = 30_000L); ed.apply() }
    }

    /** Lleva al lector de este dispositivo lo leído en otro, si es más reciente. */
    fun applyLecturas() {
        val a = try { JSONArray(p.getString("lectura", "[]") ?: "[]") } catch (e: Exception) { return }
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val id = o.optLong("id")
            val cur = BookFiles.state(app, id)
            val s = o.optJSONObject("s") ?: continue
            // Se aplica si hay algo más nuevo, o si en otro dispositivo has llegado más lejos
            if (o.optLong("updated") <= cur.optLong("syncAt", 0L) && s.optDouble("bestFrac", 0.0) <= cur.optDouble("bestFrac", 0.0) &&
                ReadStats.total(s.optJSONObject("stats")) <= ReadStats.total(cur.optJSONObject("stats"))) continue
            // El tiempo leído: el de cada dispositivo (el de aquí también), sumado
            s.put("stats", ReadStats.merge(cur.optJSONObject("stats"), s.optJSONObject("stats"), deviceId, "legacy"))
            // Por dónde vas aquí se queda; pero si en otro dispositivo has llegado más lejos, se pone ahí
            for (k in listOf("cfi", "fraction", "label", "read")) if (cur.has(k)) s.put(k, cur.get(k))
            if (s.optDouble("bestFrac", 0.0) > cur.optDouble("bestFrac", 0.0) && s.optString("bestCfi").isNotBlank())
                s.put("cfi", s.getString("bestCfi")).put("fraction", s.getDouble("bestFrac")).put("label", s.optString("bestLabel"))
            for (k in listOf("uri", "name", "added")) if (cur.has(k)) s.put(k, cur.get(k))
            // El brillo y el tamaño de letra son de cada pantalla: se quedan los de aquí (el resto de ajustes del
            // libro, como leer un manga hacia abajo, viaja con él)
            val loc = cur.optJSONObject("settings")
            for (k in listOf("brightness", "size")) loc?.opt(k)?.let { v -> s.optJSONObject("settings")?.put(k, v) }
            BookFiles.putQuiet(app, id, s.put("syncAt", o.optLong("updated")))
        }
    }

    // ---- Sagas (libros agrupados bajo una portada) ----
    fun loadSagas(): List<Saga> = try { sagasFromJson(JSONArray(p.getString("sagas", "[]") ?: "[]")) } catch (e: Exception) { emptyList() }

    fun saveSagas(list: List<Saga>) {
        val a = JSONArray()
        list.forEach { a.put(sagaToJson(it)) }
        synchronized(cerrojo) {
            val ed = p.edit()
            writeList("sagas", a, ed)
            ed.apply()
        }
    }

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
        // Una copia antigua sin sagas no borra las tuyas
        names.forEach { if ((it != "sagas" || o.has("sagas")) && (it != "maquetas" || o.has("maquetas")) && (it != "diario" || o.has("diario"))) writeList(it, o.optJSONArray(it) ?: JSONArray(), ed) }
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
        // Al entrar con tu cuenta en un dispositivo nuevo: también lo leído (por dónde vas en cada libro) y tus ajustes
        applyLecturas()
        o.optJSONObject("ajustes")?.let { applyAjustes(it) }
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

/** Las preferencias de apariencia que viajan con tu cuenta (las de esta pantalla, como el tamaño de letra, no). */
internal val AJUSTES_SKIN = setOf("skin_fondo", "meta_lectura", "skin_escala", "theme31", "skin_accent", "skin_accent_id", "skin_edicion", "skin_listcovers", "skin_motion", "skin_shell", "skin_covers", "skin_docklabels")

/** Cambió un ajuste que viaja con tu cuenta: se apunta la hora y queda pendiente de subir. */
fun ajustesCambiados(ctx: Context) {
    val now = System.currentTimeMillis()
    ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE).edit().putLong("ajustesAt", now).putLong("dirtyAt", now).apply()
    Sincro.pronto(ctx)
}

/** Llegaron ajustes de otro dispositivo: la app los vuelve a leer. */
object AjustesHub { private val v = androidx.compose.runtime.mutableIntStateOf(0); var version: Int get() = v.intValue; set(x) { v.intValue = x } }

/**
 * El tiempo leído de un libro, por dispositivo: stats.dev = { <dispositivo>: { total, days } }. Cada dispositivo solo
 * suma en el suyo y al juntar se toma, de cada uno, lo más alto (solo crece); «total» y «days» son la suma de todos
 * (es lo que se enseña). Así el tiempo de un dispositivo nunca pisa al del otro, y se suman.
 */
object ReadStats {
    private fun dev(st: JSONObject?, legacyKey: String): JSONObject {
        st ?: return JSONObject()
        st.optJSONObject("dev")?.let { return it }
        // Antes del reparto por dispositivo: todo el tiempo era de un solo cubo
        val total = st.optLong("total", 0L)
        return if (total > 0L) JSONObject().put(legacyKey, JSONObject().put("total", total).put("days", st.optJSONObject("days") ?: JSONObject())) else JSONObject()
    }

    /** Une dos estados de tiempo. aKey/bKey: de qué dispositivo es un tiempo antiguo sin repartir (el de aquí, o «legacy»). */
    fun merge(a: JSONObject?, b: JSONObject?, aKey: String, bKey: String = "legacy"): JSONObject {
        val da = dev(a, aKey)
        val db = dev(b, bKey)
        val hasReal = (a?.has("dev") == true) || (b?.has("dev") == true)
        val out = JSONObject()
        for (src in listOf(da, db)) for (id in src.keys()) {
            if (hasReal && id == "legacy") continue   // lo antiguo ya está dentro del cubo de su dispositivo
            val x = src.getJSONObject(id)
            val cur = out.optJSONObject(id)
            if (cur == null) { out.put(id, JSONObject(x.toString())); continue }
            val days = JSONObject(cur.optJSONObject("days")?.toString() ?: "{}")
            x.optJSONObject("days")?.let { d -> for (k in d.keys()) days.put(k, maxOf(days.optLong(k, 0L), d.optLong(k, 0L))) }
            val best = JSONObject(cur.optJSONObject("best")?.toString() ?: "{}")
            x.optJSONObject("best")?.let { d -> for (k in d.keys()) best.put(k, maxOf(best.optLong(k, 0L), d.optLong(k, 0L))) }
            out.put(id, JSONObject().put("total", maxOf(cur.optLong("total", 0L), x.optLong("total", 0L))).put("days", days).apply { if (best.length() > 0) put("best", best) })
        }
        return derived(out)
    }

    /** total y days = la suma de todos los dispositivos. */
    fun derived(dev: JSONObject): JSONObject {
        var total = 0L
        val days = JSONObject()
        val best = JSONObject()
        for (id in dev.keys()) {
            val b = dev.getJSONObject(id)
            total += b.optLong("total", 0L)
            b.optJSONObject("days")?.let { d -> for (k in d.keys()) days.put(k, days.optLong(k, 0L) + d.optLong(k, 0L)) }
            // La racha más larga de un día: la mejor de entre los dispositivos (no se suman: no leíste a la vez)
            b.optJSONObject("best")?.let { d -> for (k in d.keys()) best.put(k, maxOf(best.optLong(k, 0L), d.optLong(k, 0L))) }
        }
        return JSONObject().put("dev", dev).put("total", total).put("days", days).apply { if (best.length() > 0) put("best", best) }
    }

    /** Lo mínimo seguido para que un día cuente como leído o jugado: 5 minutos. */
    const val SEGUIDO_MS = 5 * 60_000L

    /**
     * ¿Cuenta ese día como leído? Hace falta una racha de [SEGUIDO_MS] seguidos. Los días anteriores a esta versión no
     * guardaron su racha: ahí vale con haber leído ese tiempo en total.
     */
    fun cuenta(st: JSONObject?, dia: String): Boolean {
        st ?: return false
        val total = st.optJSONObject("days")?.optLong(dia, 0L) ?: 0L
        if (total <= 0L) return false
        val best = st.optJSONObject("best")
        return if (best != null && best.has(dia)) best.optLong(dia, 0L) >= SEGUIDO_MS else total >= SEGUIDO_MS
    }

    fun total(st: JSONObject?): Long = st?.optLong("total", 0L) ?: 0L
}
