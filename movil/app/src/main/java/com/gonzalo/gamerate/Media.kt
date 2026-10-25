package com.gonzalo.gamerate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

internal fun mget(url: String, bearer: String = ""): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.setRequestProperty("User-Agent", "Atrio/4.4 (app personal; Android)")
    c.setRequestProperty("Accept", "application/json")
    if (bearer.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $bearer")
    c.connectTimeout = 15000
    c.readTimeout = 20000
    try {
        val code = c.responseCode
        if (code == 401) throw IllegalArgumentException("la clave de TMDB no es válida (revísala en Ajustes)")
        if (code == 429) throw IllegalStateException("demasiadas búsquedas seguidas; espera un momento")
        if (code !in 200..299) throw IllegalStateException("el servidor respondió $code")
        return c.inputStream.bufferedReader().readText()
    } finally {
        c.disconnect()
    }
}

internal fun menc(s: String) = URLEncoder.encode(s.trim(), "UTF-8")

/**
 * Películas con TMDB (themoviedb.org). Necesita tu clave gratuita: vale la "Clave de la API" (v3)
 * o el "Token de acceso de lectura" (empieza por eyJ).
 */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p"

    /** La clave v3 va en la dirección; el token de lectura, en la cabecera. */
    private fun get(path: String, key: String): JSONObject {
        val k = key.trim()
        if (k.isEmpty()) throw IllegalArgumentException("añade tu clave de TMDB en Ajustes → Servicios")
        val token = k.startsWith("eyJ")
        val sep = if ('?' in path) '&' else '?'
        val url = API + path + if (token) "" else "${sep}api_key=${menc(k)}"
        return JSONObject(mget(url, if (token) k else ""))
    }

    suspend fun search(q: String, key: String): List<Hit> = withContext(Dispatchers.IO) {
        val a = get("/search/movie?query=${menc(q)}&language=es-ES&include_adult=false", key).optJSONArray("results") ?: JSONArray()
        (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull { o ->
            val id = o.optLong("id", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val title = o.optString("title").ifBlank { o.optString("original_title") }
            val orig = o.optString("original_title")
            val year = o.optString("release_date").take(4)
            val poster = o.optString("poster_path").takeIf { it.startsWith("/") }
            Hit(
                id, title,
                listOf(year, if (orig.isNotBlank() && normName(orig) != normName(title)) orig else "").filter { it.isNotBlank() }.joinToString(" · "),
                if (poster != null) "$IMG/w500$poster" else ""
            )
        }
    }

    /** Pósters (hero = false) o fondos (hero = true) de una película: en español, inglés y sin texto. */
    suspend fun images(id: Long, key: String, hero: Boolean): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val o = get("/movie/$id/images?include_image_language=es,en,null", key)
        val a = o.optJSONArray(if (hero) "backdrops" else "posters") ?: JSONArray()
        (0 until a.length()).map { a.getJSONObject(it) }
            // Primero los de España, luego los demás (TMDB ya los da ordenados por votos).
            .sortedBy { if (it.optString("iso_639_1") == "es") 0 else 1 }
            .mapNotNull { it.optString("file_path").takeIf { p -> p.startsWith("/") } }
            .take(30)
            .map { p -> if (hero) "$IMG/w780$p" to "$IMG/w1280$p" else "$IMG/w342$p" to "$IMG/w500$p" }
    }

    /** Dónde verla en España (TMDB con los datos de JustWatch): [path] es «/movie/ID» o «/tv/ID». */
    fun proveedores(path: String, key: String): Donde? = proveedoresDe(get("$path/watch/providers", key))

    /** Lo de España en la respuesta de «watch/providers». */
    fun proveedoresDe(o: JSONObject): Donde {
        val r = o.optJSONObject("results")?.optJSONObject("ES") ?: return Donde("", emptyList(), emptyList(), emptyList(), emptyList())
        fun lista(k: String) = r.optJSONArray(k)?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.sortedBy { it.optInt("display_priority", 99) }
                .map { Plat(it.optString("provider_name"), it.optString("logo_path").takeIf { p -> p.startsWith("/") }?.let { p -> "$IMG/w92$p" } ?: "") }
                .filter { it.nombre.isNotBlank() }.distinctBy { it.nombre }
        } ?: emptyList()
        return Donde(r.optString("link"), lista("flatrate"), lista("rent"), lista("buy"), lista("free") + lista("ads"))
    }

    /** La primera peli que sale al buscar su título (para las añadidas a mano). */
    fun primeraPorNombre(q: String, key: String): Long? =
        get("/search/movie?query=${menc(q)}&language=es-ES&include_adult=false", key).optJSONArray("results")?.optJSONObject(0)?.optLong("id")?.takeIf { it > 0 }

    /** La serie de TMDB que corresponde a un código de IMDb (las series de Atrio son de TVmaze). */
    fun tvPorImdb(imdb: String, key: String): Long? =
        get("/find/${menc(imdb)}?external_source=imdb_id", key).optJSONArray("tv_results")?.optJSONObject(0)?.optLong("id")?.takeIf { it > 0 }

    /** Director o directores de la película. */
    suspend fun director(id: Long, key: String): String? = withContext(Dispatchers.IO) {
        val crew = get("/movie/$id/credits", key).optJSONArray("crew") ?: return@withContext null
        (0 until crew.length()).map { crew.getJSONObject(it) }
            .filter { it.optString("job") == "Director" }
            .map { it.optString("name") }.filter { it.isNotBlank() }.distinct().take(2)
            .joinToString(", ").ifBlank { null }
    }
}

/** Series con TVmaze (tvmaze.com): no necesita clave. Encuentra también títulos en español. */
object TvMaze {
    private const val API = "https://api.tvmaze.com"

    suspend fun search(q: String): List<Hit> = withContext(Dispatchers.IO) {
        val a = JSONArray(mget("$API/search/shows?q=${menc(q)}"))
        (0 until a.length()).mapNotNull { a.getJSONObject(it).optJSONObject("show") }.mapNotNull { s ->
            val id = s.optLong("id", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val net = (s.optJSONObject("network") ?: s.optJSONObject("webChannel"))?.optString("name") ?: ""
            val year = s.optString("premiered").takeIf { it != "null" }?.take(4) ?: ""
            Hit(
                id, s.optString("name"), listOf(year, net).filter { it.isNotBlank() }.joinToString(" · "),
                s.optJSONObject("image")?.optString("original") ?: ""
            )
        }
    }

    /** Pósters (hero = false) o fondos (hero = true) de una serie. */
    suspend fun images(id: Long, hero: Boolean): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val a = JSONArray(mget("$API/shows/$id/images"))
        (0 until a.length()).map { a.getJSONObject(it) }
            .filter { it.optString("type") == if (hero) "background" else "poster" }
            .sortedByDescending { it.optBoolean("main") }
            .mapNotNull { o ->
                val r = o.optJSONObject("resolutions") ?: return@mapNotNull null
                val full = r.optJSONObject("original")?.optString("url") ?: return@mapNotNull null
                (r.optJSONObject("medium")?.optString("url") ?: full) to full
            }
    }

    /** El próximo episodio de una serie (fecha, temporada×episodio y título), o null si no hay anunciado. */
    suspend fun proximo(id: Long): Proximo? = withContext(Dispatchers.IO) {
        proximoDe(org.json.JSONObject(mget("$API/shows/$id?embed=nextepisode")))
    }

    fun proximoDe(o: org.json.JSONObject): Proximo? {
        val e = o.optJSONObject("_embedded")?.optJSONObject("nextepisode") ?: return null
        val d = e.optString("airdate").takeIf { it.length == 10 }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val n = e.optInt("number", 0)
        val num = if (n > 0) "${e.optInt("season")}×${n.toString().padStart(2, '0')}" else "${e.optInt("season")}×esp."
        return Proximo(d, num, e.optString("name").takeIf { it != "null" }.orEmpty())
    }

    /** Creadores de la serie. */
    suspend fun creators(id: Long): String? = withContext(Dispatchers.IO) {
        val a = JSONArray(mget("$API/shows/$id/crew"))
        (0 until a.length()).map { a.getJSONObject(it) }
            .filter { it.optString("type") == "Creator" }
            .mapNotNull { it.optJSONObject("person")?.optString("name") }.filter { it.isNotBlank() }.distinct().take(3)
            .joinToString(", ").ifBlank { null }
    }

    /**
     * Episodios ya emitidos, para ir marcándolos: "1×01 · Winter Is Coming".
     * Si la serie se añadió a mano (sin id de TVmaze), se busca por su nombre.
     */
    suspend fun episodes(id: Long, name: String): List<String> = withContext(Dispatchers.IO) {
        val sid = if (isWebId(id)) id else search(name).firstOrNull()?.id
            ?: throw IllegalArgumentException("no encuentro «$name» en TVmaze")
        val a = JSONArray(mget("$API/shows/$sid/episodes"))
        val today = LocalDate.now().toString()
        (0 until a.length()).map { a.getJSONObject(it) }
            .filter { val d = it.optString("airdate"); d.length == 10 && d <= today }
            .map { e ->
                val n = e.optInt("number", 0)
                val num = if (n > 0) "${e.optInt("season")}×${n.toString().padStart(2, '0')}" else "${e.optInt("season")}×esp."
                "$num · ${e.optString("name")}"
            }
    }
}

/** Una plataforma (Netflix, Prime Video…) con su logo. */
class Plat(val nombre: String, val logo: String)

/** Dónde ver algo en España: en suscripción, alquiler, compra o gratis; y, en series, dónde se emite. */
class Donde(val link: String, val suscripcion: List<Plat>, val alquiler: List<Plat>, val compra: List<Plat>, val gratis: List<Plat>, val emision: String = "") {
    val vacio: Boolean get() = suscripcion.isEmpty() && alquiler.isEmpty() && compra.isEmpty() && gratis.isEmpty()
}

/** Dónde ver una peli o una serie (se guarda unas horas en memoria para no preguntar cada vez). */
object DondeVer {
    private val cache = HashMap<String, Pair<Long, Donde?>>()
    fun guardado(kind: Kind, id: Long): Donde? = cache["${kind.id}:$id"]?.takeIf { System.currentTimeMillis() - it.first < 6 * 3_600_000L }?.second

    suspend fun de(kind: Kind, id: Long, name: String, key: String): Donde? = withContext(Dispatchers.IO) {
        val ck = "${kind.id}:$id"
        cache[ck]?.let { (t, d) -> if (System.currentTimeMillis() - t < 6 * 3_600_000L) return@withContext d }
        val r = runCatching { busca(kind, id, name, key.trim()) }.getOrNull()
        cache[ck] = System.currentTimeMillis() to r
        r
    }

    private fun busca(kind: Kind, id: Long, name: String, key: String): Donde? {
        if (kind.isMovie) {
            if (key.isEmpty()) return null
            val mid = if (isWebId(id)) id else Tmdb.primeraPorNombre(name, key) ?: return null
            return Tmdb.proveedores("/movie/$mid", key)
        }
        // Series (de TVmaze): dónde se emite y, con TMDB, en qué plataformas está
        val sid = if (isWebId(id)) id else runCatching { kotlinx.coroutines.runBlocking { TvMaze.search(name) }.firstOrNull()?.id }.getOrNull() ?: return null
        val show = org.json.JSONObject(mget("https://api.tvmaze.com/shows/$sid"))
        val emision = (show.optJSONObject("webChannel") ?: show.optJSONObject("network"))?.optString("name").orEmpty()
        val vacio = Donde("", emptyList(), emptyList(), emptyList(), emptyList(), emision)
        if (key.isEmpty()) return vacio
        val imdb = show.optJSONObject("externals")?.optString("imdb")?.takeIf { it.startsWith("tt") } ?: return vacio
        val tv = Tmdb.tvPorImdb(imdb, key) ?: return vacio
        val d = Tmdb.proveedores("/tv/$tv", key) ?: return vacio
        return Donde(d.link, d.suscripcion, d.alquiler, d.compra, d.gratis, emision)
    }
}

/** El próximo episodio de una serie. */
class Proximo(val dia: LocalDate, val num: String, val titulo: String)

/** Los próximos episodios de tus series (se guardan unas horas para no preguntar a TVmaze cada vez). */
object Proximos {
    private val cache = HashMap<Long, Pair<Long, Proximo?>>()
    suspend fun de(id: Long): Proximo? {
        cache[id]?.let { (t, p) -> if (System.currentTimeMillis() - t < 6 * 3_600_000L) return p }
        val p = runCatching { TvMaze.proximo(id) }.getOrElse { return cache[id]?.second }
        cache[id] = System.currentTimeMillis() to p
        return p
    }
}
