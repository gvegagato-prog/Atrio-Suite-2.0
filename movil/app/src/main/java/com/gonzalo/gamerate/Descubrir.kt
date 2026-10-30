package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/*
 * Descubrir con preguntas: de un banco de 330 preguntas (pelis y series), 320 (libros) o 320 (juegos) se hacen solo unas pocas
 * (5, 7 o 10), variadas y sin repetir las de las últimas veces. Cada respuesta suma o resta géneros, temas, épocas,
 * duración, idioma, popularidad y nota mínima; al final se buscan muchos resultados (TMDB para pelis y series,
 * Open Library para libros, RAWG para juegos) y se ordenan por lo que encajan contigo.
 */

/** Algo descubierto, listo para añadir a tu lista. */
class QuizItem(
    val domain: Int, val tv: Boolean, val id: Long, val title: String, val original: String, val year: String,
    val cover: String, val creator: String, val overview: String = "", val rating: Double = 0.0, val match: Int = 0
)

/** Un test hecho: cuándo y lo que salió (lo primero, lo que más encaja). */
class QuizRun(val time: Long, val items: List<QuizItem>)

fun quizRunToJson(r: QuizRun): JSONObject = JSONObject().put("t", r.time).put("i", JSONArray().apply {
    r.items.forEach { put(JSONObject().put("d", it.domain).put("tv", it.tv).put("id", it.id).put("n", it.title).put("o", it.original)
        .put("y", it.year).put("c", it.cover).put("a", it.creator).put("r", it.rating).put("m", it.match)) }
})

fun quizRunsFromJson(o: JSONObject?): Map<Int, QuizRun> {
    if (o == null) return emptyMap()
    return o.keys().asSequence().mapNotNull { k ->
        val d = k.toIntOrNull() ?: return@mapNotNull null
        val r = o.optJSONObject(k) ?: return@mapNotNull null
        val a = r.optJSONArray("i") ?: JSONArray()
        d to QuizRun(r.optLong("t"), (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { x ->
            QuizItem(x.optInt("d", d), x.optBoolean("tv"), x.optLong("id"), x.optString("n"), x.optString("o"), x.optString("y"),
                x.optString("c"), x.optString("a"), "", x.optDouble("r", 0.0), x.optInt("m"))
        })
    }.toMap()
}

/** Lo que os ha salido a los dos (mismo id del mismo sitio, o el mismo título). */
fun quizMatches(a: List<QuizItem>, b: List<QuizItem>): List<QuizItem> {
    val ids = b.map { "${it.tv}:${it.id}" }.toSet()
    val names = b.map { normName(it.title) }.toSet()
    return a.filter { "${it.tv}:${it.id}" in ids || normName(it.title) in names }
}

/** Los filtros de juegos puestos, para el resumen de arriba de los resultados. */
fun filterSummary(years: IntRange?, plats: Set<String>): List<String> = listOfNotNull(
    years?.let { "${it.first}–${it.last}" },
    plats.takeIf { it.isNotEmpty() }?.joinToString(", ") { p -> when (p) { "pc" -> "PC"; "playstation" -> "PlayStation"; "xbox" -> "Xbox"; "nintendo" -> "Nintendo"; else -> "Móvil" } }
)

fun quizWhat(domain: Int) = when (domain) { 0 -> "juegos"; 1 -> "libros"; else -> "pelis y series" }

/** Una pregunta del banco. */
class QuizQ(val id: String, val q: String, val axis: String, val forType: String, val answers: List<Pair<String, JSONObject>>)

/** Lo que van diciendo tus respuestas. */
class Taste {
    val weights = HashMap<String, Double>()   // géneros (pelis y juegos) o temas (libros)
    val tags = HashMap<String, Double>()      // etiquetas de RAWG (juegos)
    val kws = LinkedHashMap<String, Int>()
    val votes = HashMap<String, HashMap<String, Int>>() // era, len, type, lang, pop, fic
    var rate = 0.0

    fun add(e: JSONObject) {
        for (k in e.keys()) when (k) {
            "g", "s" -> e.getJSONObject(k).let { o -> for (g in o.keys()) weights[g] = (weights[g] ?: 0.0) + o.getDouble(g) }
            "tg" -> e.getJSONObject(k).let { o -> for (g in o.keys()) tags[g] = (tags[g] ?: 0.0) + o.getDouble(g) }
            "kw" -> e.getJSONArray(k).let { a -> for (i in 0 until a.length()) a.getString(i).let { kws[it] = (kws[it] ?: 0) + 1 } }
            "rate" -> rate = maxOf(rate, e.getDouble(k))
            else -> votes.getOrPut(k) { HashMap() }.let { m -> val v = e.getString(k); m[v] = (m[v] ?: 0) + 1 }
        }
    }

    /** La opción más votada de algo (si hay empate, ninguna). */
    fun pick(k: String): String? {
        val m = votes[k] ?: return null
        val top = m.maxByOrNull { it.value } ?: return null
        return if (m.count { it.value == top.value } > 1) null else top.key
    }
    fun liked(min: Double = 1.5) = weights.filter { it.value >= min }.entries.sortedByDescending { it.value }.map { it.key }
    fun avoided() = weights.filter { it.value <= -2 }.keys
    fun likedTags(min: Double = 1.5) = tags.filter { it.value >= min }.entries.sortedByDescending { it.value }.map { it.key }
}

object Quiz {
    private fun bank(ctx: Context, domain: Int): List<QuizQ> {
        val file = when (domain) { 0 -> "descubrir/juegos.json"; 1 -> "descubrir/libros.json"; else -> "descubrir/pantalla.json" }
        val a = JSONArray(ctx.assets.open(file).bufferedReader().use { it.readText() })
        return (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            val ans = o.getJSONArray("a")
            QuizQ(o.getString("id"), o.getString("q"), o.getString("axis"), o.optString("for", "any"),
                (0 until ans.length()).map { ans.getJSONObject(it) }.map { it.getString("t") to (it.optJSONObject("e") ?: JSONObject()) })
        }
    }

    /** Elige [n] preguntas variadas (un eje distinto cada vez, mientras se pueda) evitando las de las últimas rondas. */
    fun pick(ctx: Context, domain: Int, n: Int): List<QuizQ> {
        val prefs = ctx.getSharedPreferences("descubrir", Context.MODE_PRIVATE)
        val recentKey = "recent_$domain"
        val recent = prefs.getString(recentKey, "")!!.split(',').filter { it.isNotBlank() }
        val all = bank(ctx, domain).shuffled()
        val fresh = all.filter { it.id !in recent }.ifEmpty { all }
        val out = ArrayList<QuizQ>()
        // Pelis y series: la primera, si quieres película o serie
        if (domain == 2) fresh.firstOrNull { it.axis == "format" }?.let { out += it }
        val axes = HashSet(out.map { it.axis })
        for (q in fresh) { if (out.size >= n) break; if (q !in out && q.axis !in axes) { out += q; axes += q.axis } }
        for (q in fresh) { if (out.size >= n) break; if (q !in out) out += q }
        prefs.edit().putString(recentKey, (out.map { it.id } + recent).take(if (domain == 1) 120 else 120).joinToString(",")).apply()
        return out
    }

    // ---------------------------------------------------------------- Pelis y series (TMDB)

    private val MOVIE_G = mapOf("action" to 28, "adventure" to 12, "animation" to 16, "comedy" to 35, "crime" to 80, "documentary" to 99,
        "drama" to 18, "family" to 10751, "fantasy" to 14, "history" to 36, "horror" to 27, "music" to 10402, "mystery" to 9648,
        "romance" to 10749, "scifi" to 878, "thriller" to 53, "war" to 10752, "western" to 37, "kids" to 10751, "politics" to 18, "reality" to 99)
    private val TV_G = mapOf("action" to 10759, "adventure" to 10759, "animation" to 16, "comedy" to 35, "crime" to 80, "documentary" to 99,
        "drama" to 18, "family" to 10751, "fantasy" to 10765, "history" to 18, "horror" to 9648, "music" to 18, "mystery" to 9648,
        "romance" to 18, "scifi" to 10765, "thriller" to 80, "war" to 10768, "western" to 37, "kids" to 10762, "politics" to 10768, "reality" to 10764)
    val G_NAMES = mapOf("action" to "Acción", "adventure" to "Aventura", "animation" to "Animación", "comedy" to "Comedia", "crime" to "Crimen",
        "documentary" to "Documental", "drama" to "Drama", "family" to "Familia", "fantasy" to "Fantasía", "history" to "Historia", "horror" to "Terror",
        "music" to "Música", "mystery" to "Misterio", "romance" to "Romance", "scifi" to "Ciencia ficción", "thriller" to "Suspense", "war" to "Bélico",
        "western" to "Western", "kids" to "Infantil", "reality" to "Reality", "politics" to "Política")
    private val ERA_S = mapOf("classic" to ("1900-01-01" to "1979-12-31"), "80s90s" to ("1980-01-01" to "1999-12-31"),
        "2000s" to ("2000-01-01" to "2014-12-31"), "recent" to ("2015-01-01" to ""))
    private val ERA_NAMES = mapOf("classic" to "Clásicos", "80s90s" to "Años 80 y 90", "2000s" to "Años 2000", "recent" to "Recientes",
        "20c" to "Siglo XX", "modern" to "2000–2014")

    private fun tmdb(path: String, key: String): JSONObject {
        val k = key.trim()
        val token = k.startsWith("eyJ")
        val sep = if ('?' in path) '&' else '?'
        return JSONObject(mget("https://api.themoviedb.org/3$path" + if (token) "" else "${sep}api_key=${menc(k)}", if (token) k else ""))
    }

    /** Resumen de lo que ha salido de tus respuestas (para enseñarlo arriba de los resultados). */
    fun summary(t: Taste, domain: Int): List<String> {
        val out = ArrayList<String>()
        if (domain == 1) out += t.liked().take(3).map { BOOK_NAMES[it] ?: it.replaceFirstChar { c -> c.uppercase() } }
        else if (domain == 0) {
            out += t.liked().take(3).mapNotNull { g -> GameGenres.firstOrNull { it.first == g }?.second }
            out += t.likedTags(2.0).take(3).mapNotNull { TAG_NAMES[it] }
            when (t.pick("plat")) { "pc" -> out += "PC"; "playstation" -> out += "PlayStation"; "xbox" -> out += "Xbox"; "nintendo" -> out += "Nintendo"; "mobile" -> out += "Móvil" }
            when (t.pick("mode")) { "solo" -> out += "Para ti solo"; "coop" -> out += "En cooperativo"; "online" -> out += "En línea" }
            t.pick("era")?.let { GAME_ERA_NAMES[it] }?.let { out += it }
        } else {
            when (t.pick("type")) { "movie" -> out += "Películas"; "tv" -> out += "Series" }
            out += t.liked().take(3).mapNotNull { G_NAMES[it] }
        }
        if (domain != 0) t.pick("era")?.let { ERA_NAMES[it] }?.let { out += it }
        when (t.pick("pop")) { "hidden" -> out += "Joyas ocultas"; "mainstream" -> out += "Éxitos"; "cult" -> out += "De culto" }
        when (t.pick("len")) { "short" -> out += "Cortas"; "long" -> out += "Largas" }
        when (t.pick("lang")) { "es", "spa" -> out += "En español"; "ja" -> out += "Japón"; "ko" -> out += "Corea"; "foreign" -> out += "Fuera de Hollywood"; "fr" -> out += "Francia" }
        if (t.rate >= 7.5 && domain != 0 || t.rate >= 85) out += "Solo lo mejor"
        return out.distinct()
    }

    suspend fun screen(t: Taste, key: String, mine: Set<String>): List<QuizItem> = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw IllegalStateException("Para descubrir pelis y series hace falta tu clave gratuita de TMDB (Ajustes → Servicios y copias).")
        val type = t.pick("type")
        val kinds = when (type) { "movie" -> listOf(false); "tv" -> listOf(true); else -> listOf(false, true) }
        val liked = t.liked()
        val avoid = t.avoided()
        // Temas: los nombres se buscan en TMDB (sus números no son fijos)
        val kwIds = t.kws.entries.sortedByDescending { it.value }.take(3).mapNotNull { (name, _) ->
            runCatching {
                val r = tmdb("/search/keyword?query=${URLEncoder.encode(name, "UTF-8")}", key).optJSONArray("results") ?: JSONArray()
                (0 until r.length()).map { r.getJSONObject(it) }.firstOrNull { it.optString("name").equals(name, true) }?.optInt("id")
            }.getOrNull()
        }
        val era = t.pick("era")?.let { ERA_S[it] }
        val len = t.pick("len")
        val lang = when (val l = t.pick("lang")) { null -> null; "foreign" -> "es|fr|it|ja|ko|de|hi|pt|sv|da|no|zh|tr"; else -> l }
        val pop = t.pick("pop")
        val minRate = if (t.rate > 0) t.rate else 6.2
        val seen = LinkedHashMap<String, QuizItem>()
        for (tv in kinds) {
            val gmap = if (tv) TV_G else MOVIE_G
            val gWith = liked.take(3).mapNotNull { gmap[it] }.distinct()
            val gWithout = avoid.mapNotNull { gmap[it] }.filter { it !in gWith }.distinct()
            fun query(withKw: Boolean, relaxed: Boolean): String = buildString {
                append(if (tv) "/discover/tv" else "/discover/movie")
                append("?language=es-ES&include_adult=false")
                if (gWith.isNotEmpty()) append("&with_genres=" + gWith.joinToString("|"))
                if (gWithout.isNotEmpty()) append("&without_genres=" + gWithout.joinToString(","))
                if (withKw && kwIds.isNotEmpty()) append("&with_keywords=" + kwIds.joinToString("|"))
                if (!relaxed && era != null) {
                    val (a, b) = era
                    val f = if (tv) "first_air_date" else "primary_release_date"
                    append("&$f.gte=$a"); if (b.isNotBlank()) append("&$f.lte=$b")
                }
                if (!relaxed && !tv) when (len) { "short" -> append("&with_runtime.lte=100"); "long" -> append("&with_runtime.gte=140"); "medium" -> append("&with_runtime.gte=95&with_runtime.lte=145") }
                if (lang != null) append("&with_original_language=$lang")
                when (pop) {
                    "hidden" -> append("&vote_count.gte=40&vote_count.lte=1500&sort_by=vote_average.desc")
                    "mainstream" -> append("&vote_count.gte=2000&sort_by=popularity.desc")
                    "cult" -> append("&vote_count.gte=300&vote_count.lte=6000&sort_by=vote_average.desc")
                    else -> append("&vote_count.gte=${if (tv) 80 else 200}&sort_by=popularity.desc")
                }
                append("&vote_average.gte=$minRate")
            }
            val broad = buildString {
                append(if (tv) "/discover/tv" else "/discover/movie")
                append("?language=es-ES&include_adult=false&vote_count.gte=150&sort_by=popularity.desc")
                if (gWith.isNotEmpty()) append("&with_genres=" + gWith.take(2).joinToString("|"))
            }
            val plans = listOfNotNull(if (kwIds.isNotEmpty()) query(true, false) else null, query(false, false), query(false, true), broad)
            for (q in plans) {
                for (page in 1..3) {
                    val res = runCatching { tmdb("$q&page=$page", key) }.getOrNull() ?: break
                    val a = res.optJSONArray("results") ?: break
                    for (i in 0 until a.length()) {
                        val o = a.getJSONObject(i)
                        val title = o.optString(if (tv) "name" else "title")
                        val orig = o.optString(if (tv) "original_name" else "original_title")
                        if (title.isBlank() || normName(title) in mine || normName(orig) in mine) continue
                        val k = (if (tv) "t" else "m") + o.optLong("id")
                        if (k in seen) continue
                        val genres = o.optJSONArray("genre_ids")?.let { g -> (0 until g.length()).map { g.getInt(it) } } ?: emptyList()
                        // Lo que encaja: géneros que te gustan, que no odias, temas, y su nota
                        var score = 0.0
                        for ((name, w) in t.weights) { val id = gmap[name] ?: continue; if (id in genres) score += w }
                        if (q.contains("with_keywords")) score += 3
                        score += o.optDouble("vote_average", 6.0) * 0.6
                        val poster = o.optString("poster_path").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" } ?: ""
                        seen[k] = QuizItem(2, tv, o.optLong("id"), title, orig, o.optString(if (tv) "first_air_date" else "release_date").take(4),
                            poster, "", o.optString("overview"), o.optDouble("vote_average", 0.0), score.toInt())
                    }
                    if (page >= res.optInt("total_pages", 1)) break
                }
                if (seen.size >= 90) break
                if (q == plans[plans.size - 2] && seen.size >= 60) break
            }
        }
        val list = seen.values.sortedByDescending { it.match }
        val top = list.firstOrNull()?.match?.coerceAtLeast(1) ?: 1
        list.map { QuizItem(it.domain, it.tv, it.id, it.title, it.original, it.year, it.cover, it.creator, it.overview, it.rating, (55 + 44 * it.match / top).coerceIn(40, 99)) }
    }

    // ---------------------------------------------------------------- Juegos (RAWG)

    private const val RAWG = "https://api.rawg.io/api"
    val TAG_NAMES = mapOf("story-rich" to "Con buena historia", "difficult" to "Difíciles", "relaxing" to "Relajantes", "atmospheric" to "Con atmósfera",
        "funny" to "Divertidos", "exploration" to "Explorar", "mystery" to "Misterio", "cute" to "Bonitos", "dark" to "Oscuros", "emotional" to "Emotivos",
        "open-world" to "Mundo abierto", "fantasy" to "Fantasía", "horror" to "Terror", "sci-fi" to "Ciencia ficción", "survival" to "Supervivencia",
        "roguelike" to "Roguelike", "souls-like" to "Soulslike", "co-op" to "Cooperativo", "competitive" to "Competitivos", "short" to "Cortos",
        "historical" to "Históricos", "stealth" to "Sigilo", "choices-matter" to "Decisiones", "space" to "Espacio", "cyberpunk" to "Cyberpunk",
        "pixel-graphics" to "Píxel", "farming" to "Granjas", "management" to "Gestión", "crafting" to "Construir y fabricar", "walking-simulator" to "Paseos",
        "turn-based" to "Por turnos", "metroidvania" to "Metroidvania", "visual-novel" to "Novela visual", "post-apocalyptic" to "Postapocalípticos")
    private val GAME_ERA = mapOf("retro" to ("1970-01-01" to "1999-12-31"), "classic" to ("2000-01-01" to "2009-12-31"),
        "modern" to ("2010-01-01" to "2019-12-31"), "recent" to ("2020-01-01" to ""))
    val GAME_ERA_NAMES = mapOf("retro" to "Retro", "classic" to "Años 2000", "modern" to "Años 2010", "recent" to "Recientes")

    /** Ids de las «plataformas padre» de RAWG (PC, PlayStation…): se piden una vez y se guardan, no se dan por supuestos. */
    private fun parentPlatforms(ctx: Context, key: String): Map<String, Int> {
        val prefs = ctx.getSharedPreferences("descubrir", Context.MODE_PRIVATE)
        prefs.getString("rawg_parents", null)?.let { s ->
            runCatching { JSONObject(s).let { o -> o.keys().asSequence().associateWith { o.getInt(it) } } }.getOrNull()?.let { if (it.isNotEmpty()) return it }
        }
        val a = runCatching { JSONObject(mget("$RAWG/platforms/lists/parents?key=${menc(key)}&page_size=40")).optJSONArray("results") }.getOrNull() ?: return emptyMap()
        val m = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.associate { it.optString("slug") to it.optInt("id") }.filter { it.key.isNotBlank() && it.value > 0 }
        if (m.isNotEmpty()) prefs.edit().putString("rawg_parents", JSONObject(m).toString()).apply()
        return m
    }

    /** [years] y [plats]: los filtros que pones tú (mandan sobre lo que digan las respuestas y se aplican a todas las búsquedas). */
    suspend fun games(ctx: Context, t: Taste, key: String, mine: Set<String>, years: IntRange? = null, plats: Set<String> = emptySet()): List<QuizItem> = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw IllegalStateException("Para descubrir juegos hace falta tu clave gratuita de RAWG (Ajustes → Servicios y copias).")
        val liked = t.liked().filter { g -> GameGenres.any { it.first == g } || g in setOf("educational") }
        val likedTags = t.likedTags()
        val badTags = t.tags.filter { it.value <= -2 }.keys
        // Cómo juegas también son etiquetas
        val modeTag = when (t.pick("mode")) { "solo" -> "singleplayer"; "coop" -> "co-op"; "online" -> "multiplayer"; else -> null }
        val parents = parentPlatforms(ctx, key)
        fun platIds(p: String?) = when (p) {
            "pc" -> listOfNotNull(parents["pc"]); "playstation" -> listOfNotNull(parents["playstation"]); "xbox" -> listOfNotNull(parents["xbox"])
            "nintendo" -> listOfNotNull(parents["nintendo"]); "mobile" -> listOfNotNull(parents["ios"], parents["android"]); else -> emptyList()
        }
        val userPlat = plats.isNotEmpty()
        val plat = if (userPlat) plats.flatMap { platIds(it) }.distinct() else platIds(t.pick("plat"))
        if (userPlat && plat.isEmpty()) throw IllegalStateException("No se han podido cargar las plataformas de RAWG. Prueba otra vez.")
        val userYears = years != null
        val era = if (years != null) "${years.first}-01-01" to "${years.last}-12-31" else t.pick("era")?.let { GAME_ERA[it] }
        val pop = t.pick("pop")
        val len = t.pick("len")
        val ordering = when (pop) { "mainstream" -> "-added"; "hidden" -> "-metacritic"; "cult" -> "-rating"; else -> "-added" }
        fun query(genres: List<String>, tags: List<String>, filters: Boolean, minRate: Boolean, order: String = ordering): String = buildString {
            append("$RAWG/games?key=${menc(key)}&page_size=40&exclude_additions=true&ordering=$order")
            if (genres.isNotEmpty()) append("&genres=" + menc(genres.joinToString(",")))
            if (tags.isNotEmpty()) append("&tags=" + menc(tags.joinToString(",")))
            if ((filters || userPlat) && plat.isNotEmpty()) append("&parent_platforms=" + plat.joinToString(","))
            if ((filters || userYears) && era != null) append("&dates=${era.first},${era.second.ifBlank { java.time.LocalDate.now().toString() }}")
            if (minRate && t.rate > 0) append("&metacritic=${t.rate.toInt()},100")
        }
        val tagQ = (likedTags.take(2) + listOfNotNull(modeTag)).distinct().take(2)
        val plans = listOfNotNull(
            if (tagQ.isNotEmpty()) query(liked.take(2), tagQ, true, true) else null,
            query(liked.take(2), emptyList(), true, true),
            query(liked.take(2), emptyList(), true, false),
            if (likedTags.isNotEmpty()) query(emptyList(), likedTags.take(1), true, false) else null,
            query(liked.take(1), emptyList(), false, false, "-added")   // reserva
        )
        val seen = LinkedHashMap<Long, QuizItem>()
        var err: Throwable? = null
        for ((pi, url) in plans.withIndex()) {
            if (pi == plans.size - 1 && seen.size >= 50) break
            for (page in 1..2) {
                val res = runCatching { JSONObject(mget("$url&page=$page")) }.onFailure { err = it }.getOrNull() ?: break
                val a = res.optJSONArray("results") ?: break
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val id = o.optLong("id")
                    val name = o.optString("name")
                    if (id == 0L || name.isBlank() || id in seen || normName(name) in mine) continue
                    // Tus años, sí o sí (por si RAWG devuelve alguno fuera)
                    if (years != null) { val y = o.optString("released").take(4).toIntOrNull(); if (y == null || y !in years) continue }
                    val gs = o.optJSONArray("genres")?.let { g -> (0 until g.length()).mapNotNull { g.optJSONObject(it) } } ?: emptyList()
                    val ts = o.optJSONArray("tags")?.let { g -> (0 until g.length()).mapNotNull { g.optJSONObject(it)?.optString("slug") } }?.toSet() ?: emptySet()
                    // Lo que encaja: géneros y etiquetas que te gustan (y las que no), su nota y lo que dura
                    var score = 0.0
                    for (g in gs) score += t.weights[g.optString("slug")] ?: 0.0
                    for ((tg, w) in t.tags) if (tg in ts) score += w * 0.8
                    if (modeTag != null && modeTag in ts) score += 2
                    if (badTags.any { it in ts }) score -= 4
                    val mc = o.optInt("metacritic", 0)
                    score += if (mc > 0) (mc - 60) * 0.12 else o.optDouble("rating", 3.0) * 0.8
                    val hours = o.optInt("playtime", 0)
                    if (hours > 0) when (len) {
                        "short" -> score += if (hours <= 12) 2.0 else -2.0
                        "long" -> score += if (hours >= 35) 2.0 else -1.0
                        "medium" -> score += if (hours in 8..40) 1.5 else -0.5
                    }
                    if (pop == "hidden") score -= kotlin.math.ln(1.0 + o.optInt("added", 0)) * 0.4
                    if (pi == 0) score += 2
                    seen[id] = QuizItem(0, false, id, name, name, o.optString("released").take(4), o.optString("background_image"),
                        gs.take(3).joinToString(" · ") { g -> GameGenres.firstOrNull { it.first == g.optString("slug") }?.second ?: g.optString("name") },
                        if (hours > 0) "Unas $hours h de media" else "", if (mc > 0) mc / 10.0 else 0.0, score.toInt())
                }
                if (res.optString("next").isBlank() || page >= 2) break
            }
            if (seen.size >= 100) break
        }
        val e = err
        if (seen.isEmpty() && e != null)
            throw IllegalStateException(if (e is IllegalArgumentException) "La clave de RAWG no es válida (revísala en Ajustes → Servicios y copias)." else "RAWG: ${e.message}")
        val list = seen.values.sortedByDescending { it.match }
        val top = list.firstOrNull()?.match?.coerceAtLeast(1) ?: 1
        list.map { QuizItem(0, false, it.id, it.title, it.original, it.year, it.cover, it.creator, it.overview, it.rating, (55 + 44 * it.match / top).coerceIn(40, 99)) }
    }

    /** La ficha de un juego en RAWG: su resumen (en inglés, como viene) y su nota de Metacritic (0 si no tiene). */
    suspend fun gameDetail(id: Long, key: String): Pair<String, Int> = withContext(Dispatchers.IO) {
        val o = JSONObject(mget("$RAWG/games/$id?key=${menc(key)}"))
        val raw = o.optString("description_raw").ifBlank {
            androidx.core.text.HtmlCompat.fromHtml(o.optString("description"), androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }.trim()
        raw to o.optInt("metacritic", 0)
    }

    // ---------------------------------------------------------------- Libros (Open Library)

    val BOOK_NAMES = mapOf(
        "adventure stories" to "Aventuras",
        "alternative history" to "Historia alternativa",
        "american fiction" to "Literatura estadounidense",
        "animals" to "Animales",
        "art" to "Arte",
        "artificial intelligence" to "Inteligencia artificial",
        "astronomy" to "Astronomía",
        "autobiography" to "Autobiografías",
        "biography" to "Biografías",
        "business" to "Empresa",
        "cats" to "Gatos",
        "classic literature" to "Clásicos",
        "comic books, strips" to "Cómic",
        "coming of age" to "Crecer",
        "cooking" to "Cocina",
        "crime" to "Crimen",
        "cyberpunk" to "Cyberpunk",
        "detective" to "Detectives",
        "detective and mystery stories" to "Novela policiaca",
        "dogs" to "Perros",
        "dragons" to "Dragones",
        "drama" to "Teatro",
        "dystopias" to "Distopías",
        "economics" to "Economía",
        "english fiction" to "Literatura inglesa",
        "epic fantasy" to "Fantasía épica",
        "essays" to "Ensayo",
        "fairy tales" to "Cuentos de hadas",
        "families" to "Familias",
        "family life" to "Vida familiar",
        "fantasy" to "Fantasía",
        "feminism" to "Feminismo",
        "fiction" to "Novela",
        "french fiction" to "Literatura francesa",
        "friendship" to "Amistad",
        "ghost stories" to "Fantasmas",
        "gothic fiction" to "Gótica",
        "graphic novels" to "Novela gráfica",
        "historical fiction" to "Novela histórica",
        "history" to "Historia",
        "horror" to "Terror",
        "humor" to "Humor",
        "japanese fiction" to "Literatura japonesa",
        "juvenile fiction" to "Infantil",
        "kings and rulers" to "Reyes y reinos",
        "latin american fiction" to "Literatura latinoamericana",
        "love stories" to "Historias de amor",
        "magic" to "Magia",
        "magical realism" to "Realismo mágico",
        "mathematics" to "Matemáticas",
        "medicine" to "Medicina",
        "memoirs" to "Memorias",
        "murder" to "Asesinatos",
        "music" to "Música",
        "mystery" to "Misterio",
        "mythology" to "Mitología",
        "nature" to "Naturaleza",
        "nonfiction" to "No ficción",
        "philosophy" to "Filosofía",
        "physics" to "Física",
        "pirates" to "Piratas",
        "plays" to "Teatro",
        "poetry" to "Poesía",
        "police" to "Policías",
        "politics" to "Política",
        "psychological fiction" to "Novela psicológica",
        "psychology" to "Psicología",
        "quests" to "Grandes misiones",
        "religion" to "Religión",
        "robots" to "Robots",
        "romance" to "Romántica",
        "russian fiction" to "Literatura rusa",
        "satire" to "Sátira",
        "school stories" to "Colegios",
        "science" to "Ciencia",
        "science fiction" to "Ciencia ficción",
        "self-help" to "Crecimiento personal",
        "short stories" to "Relatos",
        "sociology" to "Sociología",
        "space opera" to "Ópera espacial",
        "spanish fiction" to "Literatura española",
        "sports" to "Deporte",
        "spy stories" to "Espías",
        "superheroes" to "Superhéroes",
        "survival" to "Supervivencia",
        "suspense" to "Suspense",
        "thriller" to "Thriller",
        "time travel" to "Viajes en el tiempo",
        "travel" to "Viajes",
        "true crime" to "Crímenes reales",
        "utopias" to "Utopías",
        "vampires" to "Vampiros",
        "war stories" to "Guerra",
        "witches" to "Brujas",
        "women" to "Mujeres",
        "world war, 1939-1945" to "Segunda Guerra Mundial",
        "young adult fiction" to "Juvenil"
    )
    private val ERA_B = mapOf("classic" to "[* TO 1949]", "20c" to "[1950 TO 1999]", "modern" to "[2000 TO 2014]", "recent" to "[2015 TO *]")

    suspend fun books(t: Taste, mine: Set<String>): List<QuizItem> = withContext(Dispatchers.IO) {
        val liked = t.liked(1.0)
        val avoid = t.avoided()
        fun query(subjects: List<String>, relaxed: Boolean, sort: String): String {
            val parts = ArrayList<String>()
            if (subjects.isNotEmpty()) parts += "(" + subjects.joinToString(" OR ") { "subject:\"$it\"" } + ")"
            avoid.forEach { parts += "-subject:\"$it\"" }
            when (t.pick("fic")) { "nonfiction" -> parts += "-subject:\"fiction\""; "fiction" -> parts += "subject:\"fiction\"" }
            if (!relaxed) {
                t.pick("era")?.let { ERA_B[it] }?.let { parts += "first_publish_year:$it" }
                when (t.pick("len")) { "short" -> parts += "number_of_pages_median:[* TO 250]"; "long" -> parts += "number_of_pages_median:[500 TO *]"; "medium" -> parts += "number_of_pages_median:[250 TO 500]" }
                if (t.pick("lang") == "spa") parts += "language:spa"
            }
            parts += when (t.pick("pop")) { "hidden" -> "ratings_count:[5 TO 150]"; "mainstream" -> "ratings_count:[150 TO *]"; else -> "ratings_count:[15 TO *]" }
            if (subjects.isEmpty() && parts.size <= 1) parts += "subject:\"fiction\""
            return "https://openlibrary.org/search.json?q=" + URLEncoder.encode(parts.joinToString(" "), "UTF-8") + "&sort=$sort&limit=100" +
                "&fields=key,title,author_name,first_publish_year,cover_i,number_of_pages_median,ratings_average,ratings_count,subject"
        }
        val sort = if (t.pick("pop") == "mainstream") "readinglog" else "rating"
        // Reserva: solo los dos temas que más te gustan, sin más filtros (por si las respuestas se contradicen)
        val broad = "https://openlibrary.org/search.json?q=" + URLEncoder.encode(
            (if (liked.isEmpty()) "subject:\"fiction\"" else "(" + liked.take(2).joinToString(" OR ") { "subject:\"$it\"" } + ")") + " ratings_count:[20 TO *]", "UTF-8") +
            "&sort=rating&limit=100&fields=key,title,author_name,first_publish_year,cover_i,number_of_pages_median,ratings_average,ratings_count,subject"
        val plans = listOf(query(liked.take(4), false, sort), query(liked.take(2), false, "readinglog"), query(liked.take(4), true, sort), broad)
        val seen = LinkedHashMap<Long, QuizItem>()
        for ((pi, url) in plans.withIndex()) {
            if (pi == 3 && seen.size >= 60) break
            val docs = runCatching { JSONObject(mget(url)).optJSONArray("docs") }.getOrNull() ?: continue
            for (i in 0 until docs.length()) {
                val o = docs.getJSONObject(i)
                val id = o.optString("key").filter { it.isDigit() }.toLongOrNull() ?: continue
                val title = o.optString("title")
                if (id in seen || title.isBlank() || normName(title) in mine) continue
                val subj = o.optJSONArray("subject")?.let { a -> (0 until a.length()).map { a.getString(it).lowercase() } } ?: emptyList()
                var score = 0.0
                for ((s, w) in t.weights) if (subj.any { it == s || it.contains(s) }) score += w
                score += o.optDouble("ratings_average", 3.5) * 1.5
                val cover = o.optLong("cover_i", 0L).takeIf { it > 0 }?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" } ?: ""
                val author = o.optJSONArray("author_name")?.optString(0) ?: ""
                seen[id] = QuizItem(1, false, id, title, title, o.optInt("first_publish_year", 0).takeIf { it > 0 }?.toString() ?: "", cover, author,
                    o.optInt("number_of_pages_median", 0).takeIf { it > 0 }?.let { "$it páginas" } ?: "", o.optDouble("ratings_average", 0.0), score.toInt())
            }
            if (seen.size >= 120) break
        }
        val list = seen.values.filter { it.cover.isNotBlank() }.sortedByDescending { it.match }
        val top = list.firstOrNull()?.match?.coerceAtLeast(1) ?: 1
        list.map { QuizItem(1, false, it.id, it.title, it.original, it.year, it.cover, it.creator, it.overview, it.rating, (55 + 44 * it.match / top).coerceIn(40, 99)) }
    }
}

// ---------------------------------------------------------------- Pantalla

@Composable
fun QuizScreen(domain: Int, store: Store, mine: Set<String>, partner: Partner?, onAdd: (QuizItem) -> Unit, onBack: () -> Unit, onDomain: ((Int) -> Unit)? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mineN = remember(mine) { mine.map { normName(it) }.toSet() }
    var length by remember { mutableStateOf(0) }           // 0: aún no elegido
    var qs by remember { mutableStateOf<List<QuizQ>>(emptyList()) }
    var idx by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var results by remember { mutableStateOf<List<QuizItem>?>(null) }
    var error by remember { mutableStateOf("") }
    var taste by remember { mutableStateOf<Taste?>(null) }
    var added by remember { mutableStateOf(setOf<String>()) }
    var open by remember { mutableStateOf<QuizItem?>(null) }
    val accent = if (Skin.revista) ColGeneral else when (domain) { 0 -> Color(0xFF3E6BE0); 1 -> Color(0xFF8B5CF6); else -> Color(0xFFE85A2E) }
    // El último test de tu pareja de este mismo tipo (si lo ha hecho)
    val theirRun = partner?.quizzes?.get(domain)

    // Filtros de juegos (año y plataforma): se recuerdan para la próxima vez
    val fprefs = remember { ctx.getSharedPreferences("descubrir", Context.MODE_PRIVATE) }
    var fYears by remember { mutableStateOf(fprefs.getString("f_years", "")!!.split('-').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 }?.let { it[0]..it[1] }) }
    var fPlats by remember { mutableStateOf(fprefs.getString("f_plats", "")!!.split(',').filter { it.isNotBlank() }.toSet()) }
    var searchN by remember { mutableStateOf(0) }
    fun search(t: Taste) {
        error = ""
        results = null
        val n = ++searchN
        scope.launch {
            try {
                val r = when (domain) {
                    0 -> Quiz.games(ctx, t, store.rawgKey, mineN, fYears, fPlats)
                    1 -> Quiz.books(t, mineN)
                    else -> Quiz.screen(t, store.tmdbKey, mineN)
                }
                if (n == searchN) results = r
            }
            catch (e: Exception) { if (n == searchN) { error = e.message ?: "No se ha podido buscar."; results = emptyList() } }
            if (n != searchN) return@launch
            // Se guarda y se sube: a tu pareja le llega el aviso y, si os sale lo mismo, lo veis los dos
            results?.takeIf { it.isNotEmpty() }?.let { store.saveQuiz(domain, it); runCatching { CloudSave.enqueue(ctx) } }
        }
    }
    fun setFilters(y: IntRange?, p: Set<String>) {
        fYears = y; fPlats = p
        fprefs.edit().putString("f_years", y?.let { "${it.first}-${it.last}" } ?: "").putString("f_plats", p.joinToString(",")).apply()
        taste?.let { search(it) }
    }
    fun finish(all: List<JSONObject>) {
        val t = Taste().also { tt -> all.forEach { tt.add(it) } }
        taste = t
        search(t)
    }
    fun start(n: Int) { length = n; qs = Quiz.pick(ctx, domain, n); idx = 0; answers = emptyList(); results = null; taste = null }

    Box(Modifier.fillMaxSize()) {
        when {
            length == 0 -> if (Skin.revista) QuizIntroRevista(domain, partner?.name, theirRun, onBack, onDomain,
                    filters = if (domain == 0) ({ GameFilters(fYears, fPlats) { y, p -> setFilters(y, p) } }) else null) { start(it) }
                else QuizIntro(domain, accent, partner?.name, theirRun, onBack) { start(it) }
            taste == null -> {
                val q = qs.getOrNull(idx)
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                    if (Skin.revista) {
                        // Revista: «Volver», «Pregunta 2 de 7» y un filete que se va llenando de rojo
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RevistaBack { if (idx > 0) { idx--; answers = answers.dropLast(1) } else length = 0 }
                            Spacer(Modifier.weight(1f))
                            Text("PREGUNTA ${idx + 1} DE ${qs.size}", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        }
                        val prog by animateFloatAsState((idx + 1f) / qs.size.coerceAtLeast(1), Motion.smooth(), label = "prog")
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Charcoal)) {
                            Box(Modifier.fillMaxWidth(prog).fillMaxHeight().background(ColGeneral))
                        }
                    } else Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BackRound { if (idx > 0) { idx--; answers = answers.dropLast(1) } else length = 0 }
                        Spacer(Modifier.width(12.dp))
                        val prog by animateFloatAsState((idx + 1f) / qs.size.coerceAtLeast(1), Motion.smooth(), label = "prog")
                        Box(Modifier.weight(1f).height(8.dp).clip(pillShape()).background(Sand)) {
                            Box(Modifier.fillMaxWidth(prog).fillMaxHeight().clip(pillShape()).background(accent))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("${idx + 1}/${qs.size}", color = Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (q != null) AnimatedContent(
                        idx, transitionSpec = {
                            val fwd = targetState > initialState
                            (slideInHorizontally(Motion.bouncy()) { if (fwd) it / 3 else -it / 3 } + fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith
                                (slideOutHorizontally { if (fwd) -it / 4 else it / 4 } + fadeOut())
                        }, label = "pregunta", modifier = Modifier.weight(1f)
                    ) { i ->
                        val qq = qs[i]
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 34.dp, bottom = 16.dp)) {
                            if (Skin.revista) {
                                Text(quizWhat(domain).uppercase(), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                                FitTitle(qq.q, 38.sp, 41.sp, letterSpacing = (-0.8).sp)
                            } else Text(qq.q, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp)
                            Spacer(Modifier.height(26.dp))
                            qq.answers.forEachIndexed { ai, (txt, eff) ->
                                if (Skin.revista) Row(
                                    // Revista: cada respuesta, una línea con su letra capitular en rojo
                                    Modifier.fillMaxWidth().rise(ai).press {
                                        val all = answers + eff
                                        answers = all
                                        if (i + 1 >= qs.size) finish(all) else idx = i + 1
                                    }.drawBehind { drawLine(Charcoal, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 2f) }
                                        .padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(('A' + ai).toString(), color = ColGeneral, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.width(40.dp))
                                    Text(txt, fontFamily = Display, fontSize = 21.sp, lineHeight = 26.sp, modifier = Modifier.weight(1f))
                                } else Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp).rise(ai).press {
                                        val all = answers + eff
                                        answers = all
                                        if (i + 1 >= qs.size) finish(all) else idx = i + 1
                                    }.clip(shp(20.dp)).background(Crudo).padding(horizontal = 18.dp, vertical = 17.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(30.dp).clip(roundShape()).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                        Text(('A' + ai).toString(), color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Text(txt, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            if (Skin.revista) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(Charcoal))
                                Text("ME DA IGUAL, SIGUIENTE\u00A0→", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                                    modifier = Modifier.press { val all = answers + JSONObject(); answers = all; if (i + 1 >= qs.size) finish(all) else idx = i + 1 }.padding(vertical = 14.dp))
                            } else TextButton({ val all = answers + JSONObject(); answers = all; if (i + 1 >= qs.size) finish(all) else idx = i + 1 }) {
                                Text("Me da igual, siguiente", color = Muted)
                            }
                        }
                    }
                }
            }
            else -> if (Skin.revista) QuizResultsRevista(
                domain, Quiz.summary(taste!!, domain) + filterSummary(fYears, fPlats), results, error, added,
                partner?.name, theirRun?.let { r -> results?.let { quizMatches(it, r.items) } },
                onOpen = { open = it },
                onAdd = { it -> added = added + "${it.tv}${it.id}"; onAdd(it) },
                onAgain = { start(length) }, onBack = onBack,
                filters = if (domain == 0) ({ GameFilters(fYears, fPlats) { y, p -> setFilters(y, p) } }) else null
            ) else QuizResults(
                domain, accent, Quiz.summary(taste!!, domain), results, error, added,
                partner?.name, theirRun?.let { r -> results?.let { quizMatches(it, r.items) } },
                onOpen = { open = it },
                onAdd = { it -> added = added + "${it.tv}${it.id}"; onAdd(it) },
                onAgain = { start(length) }, onBack = onBack
            )
        }
    }
    open?.let { it ->
        QuizItemSheet(it, accent, "${it.tv}${it.id}" in added, store.rawgKey, onAdd = { added = added + "${it.tv}${it.id}"; onAdd(it) }) { open = null }
    }
}

@Composable
private fun QuizIntro(domain: Int, accent: Color, partnerName: String?, theirs: QuizRun?, onBack: () -> Unit, onStart: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        BackRound(onBack = onBack)
        Spacer(Modifier.weight(0.6f))
        Icon(when (domain) { 0 -> Icons.Rounded.SportsEsports; 1 -> Icons.Rounded.AutoStories; else -> Icons.Rounded.Movie }, null, tint = accent, modifier = Modifier.size(52.dp).rise(0))
        Spacer(Modifier.height(14.dp))
        Text(when (domain) { 0 -> "¿A qué juego ahora?"; 1 -> "¿Qué leo ahora?"; else -> "¿Qué vemos hoy?" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 43.sp, modifier = Modifier.rise(1))
        Spacer(Modifier.height(10.dp))
        Text(
            "Unas pocas preguntas (de más de 300 distintas, nunca las mismas) y te propongo un montón de " +
                when (domain) { 0 -> "juegos"; 1 -> "libros"; else -> "películas y series" } + " a tu medida.",
            color = Muted, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.rise(2)
        )
        if (partnerName != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                if (theirs != null) "$partnerName hizo este test el ${fmtDay(theirs.time / 86_400_000L)}. Haz el tuyo: si os sale lo mismo, aparecerá arriba de los resultados."
                else "Cuando lo hagas, a $partnerName le llegará un aviso; si hace el suyo y coincidís en algo, os saldrá a los dos.",
                fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth().clip(shp(14.dp)).background(accent.copy(alpha = 0.12f)).padding(12.dp).rise(3)
            )
        }
        Spacer(Modifier.weight(1f))
        Text("¿Cuántas preguntas?", fontWeight = FontWeight.Bold, modifier = Modifier.rise(3))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.rise(4)) {
            listOf(5 to "Rápido", 7 to "Normal", 10 to "A fondo").forEach { (n, label) ->
                Column(
                    Modifier.weight(1f).press { onStart(n) }.clip(shp(22.dp)).background(if (n == 7) accent else Crudo).padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("$n", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = if (n == 7) Color.White else Charcoal)
                    Text(label, fontSize = 13.sp, color = if (n == 7) Color.White.copy(alpha = 0.85f) else Muted)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val GAME_PLATS = listOf("pc" to "PC", "playstation" to "PlayStation", "xbox" to "Xbox", "nintendo" to "Nintendo", "mobile" to "Móvil")
private const val YEAR_MIN = 1980

/** Filtros de juegos: entre qué años salió y en qué plataformas (ninguna marcada: todas). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameFilters(years: IntRange?, plats: Set<String>, onChange: (IntRange?, Set<String>) -> Unit) {
    val now = java.time.LocalDate.now().year
    var range by remember(years) { mutableStateOf((years?.first ?: YEAR_MIN).toFloat()..(years?.last ?: now).toFloat()) }
    val any = range.start.toInt() <= YEAR_MIN && range.endInclusive.toInt() >= now
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("AÑOS", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            Text(if (any) "CUALQUIERA" else "${range.start.toInt()} — ${range.endInclusive.toInt()}", color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        }
        YearRange(range, YEAR_MIN.toFloat(), now.toFloat(), { range = it }) {
            val a = range.start.toInt(); val b = range.endInclusive.toInt()
            onChange(if (a <= YEAR_MIN && b >= now) null else a..b, plats)
        }
        Text("PLATAFORMAS", fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(top = 4.dp))
        FlowRow(Modifier.padding(top = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val all = plats.isEmpty()
            Text("TODAS", color = if (all) ColGeneral else Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                modifier = Modifier.press { if (!all) onChange(years, emptySet()) }.padding(vertical = 6.dp))
            GAME_PLATS.forEach { (id, name) ->
                val on = id in plats
                Text(name.uppercase(), color = if (on) ColGeneral else Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                    textDecoration = if (on) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                    modifier = Modifier.press { onChange(years, if (on) plats - id else plats + id) }.padding(vertical = 6.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Charcoal))
    }
}

/**
 * Deslizador de años de la revista: una línea fina, el tramo elegido en tinta y dos tiradores cuadrados del acento.
 * Se arrastra el tirador más cercano al dedo; al soltar se avisa con [onDone].
 */
@Composable
private fun YearRange(
    value: ClosedFloatingPointRange<Float>, min: Float, max: Float,
    onValue: (ClosedFloatingPointRange<Float>) -> Unit, onDone: () -> Unit
) {
    var width by remember { mutableStateOf(1f) }
    val cur by rememberUpdatedState(value)
    // Los gestos se crean una vez (pointerInput no se rehace): así llaman siempre a las funciones de ahora,
    // no a las de la primera vez (que escribirían en un estado viejo y con las plataformas de entonces)
    val onValueNow by rememberUpdatedState(onValue)
    val onDoneNow by rememberUpdatedState(onDone)
    val accent = ColGeneral
    val ink = Charcoal
    val line = Line
    fun toValue(x: Float) = (min + (x / width).coerceIn(0f, 1f) * (max - min)).let { kotlin.math.round(it) }
    Box(
        Modifier.fillMaxWidth().height(40.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .semantics { contentDescription = "Años" }
            .pointerInput(min, max) {
                var which = 0
                detectDragGestures(
                    onDragStart = { o ->
                        val v = toValue(o.x)
                        which = if (kotlin.math.abs(v - cur.start) <= kotlin.math.abs(v - cur.endInclusive)) 0 else 1
                    },
                    onDragEnd = { onDoneNow() }, onDragCancel = { onDoneNow() }
                ) { ch, _ ->
                    val v = toValue(ch.position.x)
                    onValueNow(if (which == 0) v.coerceAtMost(cur.endInclusive)..cur.endInclusive else cur.start..v.coerceAtLeast(cur.start))
                }
            }
            .pointerInput(min, max) {
                // Un toque suelto: el tirador más cercano salta ahí
                detectTapGestures { o ->
                    val v = toValue(o.x)
                    onValueNow(if (kotlin.math.abs(v - cur.start) <= kotlin.math.abs(v - cur.endInclusive)) v.coerceAtMost(cur.endInclusive)..cur.endInclusive else cur.start..v.coerceAtLeast(cur.start))
                    onDoneNow()
                }
            }
            .drawBehind {
                val y = size.height / 2
                val k = 7.dp.toPx()
                fun xOf(v: Float) = k + (v - min) / (max - min).coerceAtLeast(1f) * (size.width - 2 * k)
                drawLine(line, androidx.compose.ui.geometry.Offset(k, y), androidx.compose.ui.geometry.Offset(size.width - k, y), 2.dp.toPx())
                val a = xOf(cur.start); val b = xOf(cur.endInclusive)
                drawLine(ink, androidx.compose.ui.geometry.Offset(a, y), androidx.compose.ui.geometry.Offset(b, y), 3.dp.toPx())
                for (x in listOf(a, b)) drawRect(accent, androidx.compose.ui.geometry.Offset(x - k, y - k), androidx.compose.ui.geometry.Size(2 * k, 2 * k))
            }
    )
}

/** Revista: portadilla con el titular enorme, la entradilla y el número de preguntas en tres columnas. */
@Composable
private fun QuizIntroRevista(domain: Int, partnerName: String?, theirs: QuizRun?, onBack: () -> Unit, onDomain: ((Int) -> Unit)?,
    filters: (@Composable () -> Unit)? = null, onStart: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp).verticalScroll(rememberScrollState())) {
        RevistaBack(onBack)
        Box(Modifier.fillMaxWidth().height(3.dp).background(Charcoal))
        // Qué descubrir (como las pestañas de Descubrir en el PC)
        if (onDomain != null) PestanasRevista(listOf("Juegos", "Libros", "Pelis y series"), domain.coerceIn(0, 2)) { if (it != domain) onDomain(it) }
        Spacer(Modifier.height(14.dp))
        Text("DESCUBRIR · ${quizWhat(domain).uppercase()}", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.rise(0))
        FitTitle(when (domain) { 0 -> "¿A qué juego ahora?"; 1 -> "¿Qué leo ahora?"; else -> "¿Qué vemos hoy?" },
            60.sp, 60.sp, letterSpacing = (-1.8).sp, modifier = Modifier.fillMaxWidth().rise(1))
        Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(1.dp).background(Charcoal))
        Text(
            "Unas pocas preguntas, de más de trescientas y nunca las mismas, y una lista de " +
                when (domain) { 0 -> "juegos"; 1 -> "libros"; else -> "películas y series" } + " hecha a tu medida.",
            fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 21.sp, lineHeight = 28.sp, color = Muted, modifier = Modifier.rise(2)
        )
        if (partnerName != null) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.height(IntrinsicSize.Min).rise(3)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(ColGeneral))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (theirs != null) "$partnerName hizo este test el ${fmtDay(theirs.time / 86_400_000L)}. Haz el tuyo: si os sale lo mismo, aparecerá arriba de los resultados."
                    else "Cuando lo hagas, a $partnerName le llegará un aviso; si hace el suyo y coincidís en algo, os saldrá a los dos.",
                    fontSize = 15.sp, lineHeight = 21.sp
                )
            }
        }
        Spacer(Modifier.height(36.dp))
        // En el móvil, las opciones van abajo (a mano del pulgar); en una tableta, justo debajo (si no, queda un hueco enorme)
        if (LocalConfiguration.current.screenWidthDp < 600) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(12.dp))
        if (filters != null) { filters(); Spacer(Modifier.height(22.dp)) }
        Text("¿CUÁNTAS PREGUNTAS?", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).rise(4)) {
            listOf(5 to "Rápido", 7 to "Normal", 10 to "A fondo").forEachIndexed { i, (n, label) ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Charcoal))
                Column(Modifier.weight(1f).press { onStart(n) }.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$n", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 58.sp, color = if (n == 7) ColGeneral else Charcoal)
                    Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, color = if (n == 7) ColGeneral else Muted)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
        Spacer(Modifier.height(24.dp))
    }
}

/** Revista: los resultados como un índice (número, título, datos y el % que encaja), sin portadas. */
@Composable
private fun QuizResultsRevista(
    domain: Int, summary: List<String>, results: List<QuizItem>?, error: String, added: Set<String>,
    partnerName: String?, both: List<QuizItem>?,
    onOpen: (QuizItem) -> Unit, onAdd: (QuizItem) -> Unit, onAgain: () -> Unit, onBack: () -> Unit,
    filters: (@Composable () -> Unit)? = null
) {
    val italic = androidx.compose.ui.text.font.FontStyle.Italic
    var showFilters by remember { mutableStateOf(false) }
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RevistaBack(onBack); Spacer(Modifier.weight(1f))
                Text("OTRA RONDA ↻", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.press(onClick = onAgain).padding(vertical = 10.dp))
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(Charcoal))
            Spacer(Modifier.height(12.dp))
            Text((if (results == null) "BUSCANDO…" else "${results.size} RESULTADOS · ${quizWhat(domain).uppercase()}"), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Para ti", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 60.sp, lineHeight = 60.sp, letterSpacing = (-1.8).sp)
            if (summary.isNotEmpty()) Text(summary.joinToString(" · "), fontFamily = Display, fontStyle = italic, fontSize = 18.sp, lineHeight = 24.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
            if (filters != null) {
                Text(if (showFilters) "OCULTAR FILTROS ↑" else "FILTRAR POR AÑO Y PLATAFORMA ↓", color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                    modifier = Modifier.press { showFilters = !showFilters }.padding(vertical = 10.dp))
                if (showFilters) filters()
            }
            Spacer(Modifier.height(14.dp))
            // Lo que os ha salido a los dos
            if (partnerName != null && both != null && results != null) {
                Column(Modifier.fillMaxWidth().border(2.dp, Charcoal).padding(14.dp)) {
                    Text((if (both.isEmpty()) "Sin coincidencias con $partnerName" else "Coincidís con $partnerName").uppercase(), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    if (both.isEmpty()) Text("Nada de lo que te sale estaba en su último test. Prueba otra ronda.", fontFamily = Display, fontStyle = italic, fontSize = 16.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                    both.take(8).forEach { b ->
                        Text("${b.title}\u00A0→", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.fillMaxWidth().press { onOpen(b) }.padding(vertical = 5.dp))
                    }
                    if (both.size > 8) Text("Y ${both.size - 8} MÁS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(14.dp))
            }
            when {
                results == null -> Row(Modifier.fillMaxWidth().padding(40.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = ColGeneral) }
                error.isNotBlank() -> Text(error, color = ColGeneral, fontFamily = Display, fontSize = 18.sp, modifier = Modifier.padding(vertical = 20.dp))
                results.isEmpty() -> Text("No ha salido nada con esa mezcla. Prueba otra ronda.", fontFamily = Display, fontStyle = italic, fontSize = 18.sp, color = Muted, modifier = Modifier.padding(vertical = 20.dp))
            }
        }
        listItemsIndexed(results ?: emptyList(), key = { _: Int, it: QuizItem -> "${it.tv}${it.id}" }) { i: Int, it: QuizItem ->
            val done = "${it.tv}${it.id}" in added
            Column(Modifier.fillMaxWidth().drawBehind { drawLine(Charcoal.copy(alpha = 0.25f), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 2f) }.press { onOpen(it) }.padding(vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("%02d".format(i + 1), color = ColGeneral, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.width(52.dp))
                    Column(Modifier.weight(1f)) {
                        FitTitle(it.title, 24.sp, 27.sp)
                        val sub = listOfNotNull(it.year.ifBlank { null }, if (domain == 2) (if (it.tv) "Serie" else "Película") else it.creator.ifBlank { null }).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        Text(if (done) "EN TU LISTA ✓" else "A MI LISTA +", color = if (done) Muted else ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                            modifier = Modifier.press { if (!done) onAdd(it) }.padding(top = 8.dp, bottom = 2.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${it.match}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 34.sp)
                        Text("% PARA TI", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizResults(
    domain: Int, accent: Color, summary: List<String>, results: List<QuizItem>?, error: String, added: Set<String>,
    partnerName: String?, both: List<QuizItem>?,
    onOpen: (QuizItem) -> Unit, onAdd: (QuizItem) -> Unit, onAgain: () -> Unit, onBack: () -> Unit
) {
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                BigHeader("Para ti", if (results == null) "Buscando…" else "${results.size} resultados") {
                    RoundIcon(Icons.Rounded.Refresh, "Otra ronda", onClick = onAgain)
                    BackRound(onBack = onBack)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    summary.forEach { InfoChip(it) }
                }
                Spacer(Modifier.height(12.dp))
                // Lo que os ha salido a los dos
                if (partnerName != null && both != null && results != null) {
                    Column(Modifier.fillMaxWidth().clip(shp(16.dp)).background(accent.copy(alpha = 0.12f)).padding(14.dp)) {
                        Text(if (both.isEmpty()) "Esta vez no coincidís" else "Coincidís con $partnerName", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        if (both.isEmpty()) Text("Nada de lo que te sale estaba en el último test de $partnerName. Prueba otra ronda.", color = Muted, fontSize = 13.sp)
                        both.take(8).forEach { b ->
                            Row(Modifier.fillMaxWidth().press { onOpen(b) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("♥", color = accent, fontSize = 16.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(b.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(b.year, color = Muted, fontSize = 12.sp)
                            }
                        }
                        if (both.size > 8) Text("y ${both.size - 8} más", color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                when {
                    results == null -> Row(Modifier.fillMaxWidth().padding(40.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = accent) }
                    error.isNotBlank() -> Text(error, color = ColGeneral, modifier = Modifier.padding(vertical = 20.dp))
                    results.isEmpty() -> Text("No ha salido nada con esa mezcla. Prueba otra ronda.", color = Muted, modifier = Modifier.padding(vertical = 20.dp))
                }
            }
        }
        itemsIndexed(results ?: emptyList(), key = { _, it -> "${it.tv}${it.id}" }) { i, it ->
            Column(Modifier.rise(i, i < 12)) {
                CoverCard(
                    it.cover, it.title, listOfNotNull(it.year.ifBlank { null }, if (domain == 2) (if (it.tv) "Serie" else "Película") else it.creator.ifBlank { null }).joinToString(" · "),
                    null,
                    badge = {
                        Text("${it.match}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier.clip(pillShape()).background(accent).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                ) { onOpen(it) }
                Spacer(Modifier.height(6.dp))
                val done = "${it.tv}${it.id}" in added
                Row(
                    Modifier.fillMaxWidth().press { if (!done) onAdd(it) }.clip(pillShape()).background(if (done) Sand else Crudo).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (done) Icons.Rounded.Check else Icons.Rounded.Add, null, tint = if (done) Muted else Charcoal, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (done) "En tu lista" else "A mi lista", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (done) Muted else Charcoal)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuizItemSheet(it: QuizItem, accent: Color, added: Boolean, rawgKey: String, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Juegos: el resumen y la nota de Metacritic se piden a RAWG al abrirlo
    var summary by remember(it.id) { mutableStateOf(it.overview.takeIf { _ -> it.domain != 0 } ?: "") }
    var meta by remember(it.id) { mutableStateOf(if (it.domain == 0) (it.rating * 10).toInt() else 0) }
    var loading by remember(it.id) { mutableStateOf(it.domain == 0) }
    var translated by remember(it.id) { mutableStateOf(false) }
    var translating by remember(it.id) { mutableStateOf(false) }
    var trError by remember(it.id) { mutableStateOf("") }
    if (it.domain == 0) LaunchedEffect(it.id) {
        try {
            val (d, m) = Quiz.gameDetail(it.id, rawgKey)
            summary = d
            if (m > 0) meta = m
        } catch (e: Exception) { summary = "" }
        loading = false
    }
    ModalBottomSheet(onDismiss, containerColor = Crudo, shape = shp(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Row {
                if (!Skin.revista) {
                    AsyncImage(it.cover, it.title, Modifier.width(110.dp).aspectRatio(2f / 3f).clip(shp(16.dp)).background(Sand), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(it.title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 25.sp)
                    if (it.domain == 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Los colores de Metacritic: verde desde 75, amarillo desde 50, rojo por debajo
                            val mc = when { meta >= 75 -> Color(0xFF66CC33); meta >= 50 -> Color(0xFFFFCC33); meta > 0 -> Color(0xFFFF0000); else -> Sand }
                            Box(Modifier.size(46.dp).clip(shp(8.dp)).background(mc), contentAlignment = Alignment.Center) {
                                Text(if (meta > 0) "$meta" else "–", color = if (meta in 50..74) Color.Black else Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(if (meta > 0) "METACRITIC" else "SIN NOTA EN METACRITIC", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        }
                    }
                    if (it.original.isNotBlank() && normName(it.original) != normName(it.title)) Text(it.original, color = Muted, fontSize = 13.sp)
                    Text(listOfNotNull(it.year.ifBlank { null }, it.creator.ifBlank { null }).joinToString(" · "), color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip("${it.match}% para ti")
                        if (it.rating > 0 && it.domain != 0) InfoChip(when (it.domain) {
                            1 -> "★ " + String.format(java.util.Locale("es", "ES"), "%.1f", it.rating)
                            else -> "TMDB " + String.format(java.util.Locale("es", "ES"), "%.1f", it.rating)
                        })
                        if (it.domain == 2 && !it.tv) RtChips(kindOf(Mode.kinds.firstOrNull { k -> k.isMovie }?.id ?: 0), it.id, it.title)
                    }
                }
            }
            if (it.domain == 0) {
                Spacer(Modifier.height(14.dp))
                Text("RESUMEN", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                when {
                    loading -> Text("Cargando el resumen…", color = Muted, fontSize = 14.sp)
                    summary.isBlank() -> Text("RAWG no tiene resumen de este juego.", color = Muted, fontSize = 14.sp)
                    else -> {
                        Text(summary, fontSize = 15.sp, lineHeight = 22.sp)
                        if (it.overview.isNotBlank()) Text(it.overview, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                        if (!translated) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (translating) "Traduciendo… (la primera vez se descarga el traductor del inglés, unos 30 MB)" else "TRADUCIR AL ESPAÑOL\u00A0→",
                                color = if (translating) Muted else accent, fontSize = if (translating) 13.sp else 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                                modifier = Modifier.press {
                                    if (translating) return@press
                                    translating = true; trError = ""
                                    scope.launch {
                                        try {
                                            val src = summary
                                            // Por párrafos: el traductor del móvil va mejor con textos cortos
                                            summary = withContext(Dispatchers.IO) { src.split(Regex("\\n+")).filter { p -> p.isNotBlank() }.joinToString("\n\n") { p -> Diccionario.translate(p.trim(), "en") } }
                                            translated = true
                                        } catch (e: Exception) { trError = e.message ?: "No se ha podido traducir." }
                                        translating = false
                                    }
                                }.padding(vertical = 6.dp)
                            )
                            if (trError.isNotBlank()) Text(trError, color = ColGeneral, fontSize = 13.sp)
                        }
                    }
                }
            } else if (it.overview.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(it.overview, fontSize = 15.sp, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(18.dp))
            BigAction(if (added) Icons.Rounded.Check else Icons.Rounded.PlaylistAdd, if (added) "Ya está en tu lista" else "Añadir a mi lista", Modifier.fillMaxWidth(), enabled = !added) { onAdd() }
        }
    }
}
