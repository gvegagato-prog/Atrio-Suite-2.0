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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
 * Descubrir con preguntas: de un banco de 330 preguntas (pelis y series) o 320 (libros) se hacen solo unas pocas
 * (5, 7 o 10), variadas y sin repetir las de las últimas veces. Cada respuesta suma o resta géneros, temas, épocas,
 * duración, idioma, popularidad y nota mínima; al final se buscan muchos resultados (TMDB para pelis y series,
 * Open Library para libros) y se ordenan por lo que encajan contigo.
 */

/** Algo descubierto, listo para añadir a tu lista. */
class QuizItem(
    val domain: Int, val tv: Boolean, val id: Long, val title: String, val original: String, val year: String,
    val cover: String, val creator: String, val overview: String = "", val rating: Double = 0.0, val match: Int = 0
)

/** Una pregunta del banco. */
class QuizQ(val id: String, val q: String, val axis: String, val forType: String, val answers: List<Pair<String, JSONObject>>)

/** Lo que van diciendo tus respuestas. */
class Taste {
    val weights = HashMap<String, Double>()   // géneros (pelis) o temas (libros)
    val kws = LinkedHashMap<String, Int>()
    val votes = HashMap<String, HashMap<String, Int>>() // era, len, type, lang, pop, fic
    var rate = 0.0

    fun add(e: JSONObject) {
        for (k in e.keys()) when (k) {
            "g", "s" -> e.getJSONObject(k).let { o -> for (g in o.keys()) weights[g] = (weights[g] ?: 0.0) + o.getDouble(g) }
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
}

object Quiz {
    private fun bank(ctx: Context, domain: Int): List<QuizQ> {
        val a = JSONArray(ctx.assets.open(if (domain == 1) "descubrir/libros.json" else "descubrir/pantalla.json").bufferedReader().use { it.readText() })
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
        if (domain != 1) fresh.firstOrNull { it.axis == "format" }?.let { out += it }
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
        else {
            when (t.pick("type")) { "movie" -> out += "Películas"; "tv" -> out += "Series" }
            out += t.liked().take(3).mapNotNull { G_NAMES[it] }
        }
        t.pick("era")?.let { ERA_NAMES[it] }?.let { out += it }
        when (t.pick("pop")) { "hidden" -> out += "Joyas ocultas"; "mainstream" -> out += "Éxitos"; "cult" -> out += "De culto" }
        when (t.pick("len")) { "short" -> out += "Cortas"; "long" -> out += "Largas" }
        when (t.pick("lang")) { "es", "spa" -> out += "En español"; "ja" -> out += "Japón"; "ko" -> out += "Corea"; "foreign" -> out += "Fuera de Hollywood"; "fr" -> out += "Francia" }
        if (t.rate >= 7.5) out += "Solo lo mejor"
        return out.distinct()
    }

    suspend fun screen(t: Taste, key: String, mine: Set<String>): List<QuizItem> = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw IllegalStateException("Para descubrir pelis y series hace falta tu clave gratuita de TMDB (Perfil → Ajustes → Servicios).")
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
fun QuizScreen(domain: Int, store: Store, mine: Set<String>, onAdd: (QuizItem) -> Unit, onBack: () -> Unit) {
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
    val accent = if (domain == 1) Color(0xFF8B5CF6) else Color(0xFFE85A2E)

    fun finish(all: List<JSONObject>) {
        val t = Taste().also { tt -> all.forEach { tt.add(it) } }
        taste = t
        error = ""
        results = null
        scope.launch {
            try { results = if (domain == 1) Quiz.books(t, mineN) else Quiz.screen(t, store.tmdbKey, mineN) }
            catch (e: Exception) { error = e.message ?: "No se ha podido buscar."; results = emptyList() }
        }
    }
    fun start(n: Int) { length = n; qs = Quiz.pick(ctx, domain, n); idx = 0; answers = emptyList(); results = null; taste = null }

    Box(Modifier.fillMaxSize()) {
        when {
            length == 0 -> QuizIntro(domain, accent, onBack) { start(it) }
            taste == null -> {
                val q = qs.getOrNull(idx)
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            Text(qq.q, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp)
                            Spacer(Modifier.height(26.dp))
                            qq.answers.forEachIndexed { ai, (txt, eff) ->
                                Row(
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
                            TextButton({ val all = answers + JSONObject(); answers = all; if (i + 1 >= qs.size) finish(all) else idx = i + 1 }) {
                                Text("Me da igual, siguiente", color = Muted)
                            }
                        }
                    }
                }
            }
            else -> QuizResults(
                domain, accent, Quiz.summary(taste!!, domain), results, error, added,
                onOpen = { open = it },
                onAdd = { it -> added = added + "${it.tv}${it.id}"; onAdd(it) },
                onAgain = { start(length) }, onBack = onBack
            )
        }
    }
    open?.let { it ->
        QuizItemSheet(it, accent, "${it.tv}${it.id}" in added, onAdd = { added = added + "${it.tv}${it.id}"; onAdd(it) }) { open = null }
    }
}

@Composable
private fun QuizIntro(domain: Int, accent: Color, onBack: () -> Unit, onStart: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        BackRound(onBack = onBack)
        Spacer(Modifier.weight(0.6f))
        Icon(if (domain == 1) Icons.Rounded.AutoStories else Icons.Rounded.Movie, null, tint = accent, modifier = Modifier.size(52.dp).rise(0))
        Spacer(Modifier.height(14.dp))
        Text(if (domain == 1) "¿Qué leo ahora?" else "¿Qué vemos hoy?", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 43.sp, modifier = Modifier.rise(1))
        Spacer(Modifier.height(10.dp))
        Text(
            if (domain == 1) "Unas pocas preguntas (de más de 300 distintas, nunca las mismas) y te propongo un montón de libros a tu medida."
            else "Unas pocas preguntas (de más de 300 distintas, nunca las mismas) y te propongo un montón de películas y series a tu medida.",
            color = Muted, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.rise(2)
        )
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

@Composable
private fun QuizResults(
    domain: Int, accent: Color, summary: List<String>, results: List<QuizItem>?, error: String, added: Set<String>,
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
                    it.cover, it.title, listOfNotNull(it.year.ifBlank { null }, if (domain != 1) (if (it.tv) "Serie" else "Película") else it.creator.ifBlank { null }).joinToString(" · "),
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
private fun QuizItemSheet(it: QuizItem, accent: Color, added: Boolean, onAdd: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismiss, containerColor = Crudo, shape = shp(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Row {
                if (!Skin.revista) {
                    AsyncImage(it.cover, it.title, Modifier.width(110.dp).aspectRatio(2f / 3f).clip(shp(16.dp)).background(Sand), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(it.title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 25.sp)
                    if (it.original.isNotBlank() && normName(it.original) != normName(it.title)) Text(it.original, color = Muted, fontSize = 13.sp)
                    Text(listOfNotNull(it.year.ifBlank { null }, it.creator.ifBlank { null }).joinToString(" · "), color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip("${it.match}% para ti")
                        if (it.rating > 0) InfoChip(if (it.domain == 1) "★ " + String.format(java.util.Locale("es", "ES"), "%.1f", it.rating) else "TMDB " + String.format(java.util.Locale("es", "ES"), "%.1f", it.rating))
                        if (it.domain == 2 && !it.tv) RtChips(kindOf(Mode.kinds.firstOrNull { k -> k.isMovie }?.id ?: 0), it.id, it.title)
                    }
                }
            }
            if (it.overview.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(it.overview, fontSize = 15.sp, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(18.dp))
            BigAction(if (added) Icons.Rounded.Check else Icons.Rounded.PlaylistAdd, if (added) "Ya está en tu lista" else "Añadir a mi lista", Modifier.fillMaxWidth(), enabled = !added) { onAdd() }
        }
    }
}
