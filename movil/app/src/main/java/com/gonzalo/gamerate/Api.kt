package com.gonzalo.gamerate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Resultado de búsqueda genérico (juego o libro). */
class Hit(val id: Long, val name: String, val sub: String, val cover: String = "", val pages: Int = 0, val creator: String = "")

object Sgdb {
    private const val BASE = "https://www.steamgriddb.com/api/v2"

    private fun get(path: String, key: String): JSONArray {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $key")
        c.connectTimeout = 15000
        c.readTimeout = 15000
        if (c.responseCode !in 200..299) error("SteamGridDB respondió ${c.responseCode}. Revisa tu API key.")
        val text = c.inputStream.bufferedReader().readText()
        return JSONObject(text).getJSONArray("data")
    }

    private fun pairs(a: JSONArray): List<Pair<String, String>> =
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("thumb") to o.getString("url") } }

    /** Busca juegos. Si SteamGridDB da la fecha de salida, se enseña el año. */
    suspend fun search(q: String, key: String): List<Hit> = withContext(Dispatchers.IO) {
        val term = URLEncoder.encode(q.trim(), "UTF-8").replace("+", "%20")
        val a = get("/search/autocomplete/$term", key)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val rel = o.optLong("release_date", 0L)
            val year = if (rel > 0L) java.time.Instant.ofEpochSecond(rel).atZone(java.time.ZoneOffset.UTC).year.toString() else ""
            Hit(o.getLong("id"), o.getString("name"), year)
        }
    }

    suspend fun covers(id: Long, key: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        pairs(get("/grids/game/$id?dimensions=600x900", key))
    }

    suspend fun heroes(id: Long, key: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        pairs(get("/heroes/game/$id", key))
    }

    suspend fun logos(id: Long, key: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        pairs(get("/logos/game/$id", key))
    }
}

/** ISBN-10 o ISBN-13 (978/979) válidos, con su dígito de control; lo demás no es un ISBN. */
object Isbn {
    fun limpia(raw: String): String? {
        val t = raw.trim().replace("-", "").replace(" ", "").uppercase()
        return when {
            t.length == 13 && t.all { it.isDigit() } && (t.startsWith("978") || t.startsWith("979")) ->
                t.takeIf { (t.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()) % 10 == 0 }
            t.length == 10 && t.take(9).all { it.isDigit() } && (t[9].isDigit() || t[9] == 'X') ->
                t.takeIf { t.mapIndexed { i, c -> (if (c == 'X') 10 else c - '0') * (10 - i) }.sum() % 11 == 0 }
            else -> null
        }
    }
}

object OpenLib {
    private const val COVERS = "https://covers.openlibrary.org/b/id"

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "GameRate/1.0")
        c.connectTimeout = 15000
        c.readTimeout = 15000
        if (c.responseCode !in 200..299) error("Open Library respondió ${c.responseCode}.")
        return c.inputStream.bufferedReader().readText()
    }

    suspend fun search(q: String): List<Hit> = buscaEn("q=" + URLEncoder.encode(q.trim(), "UTF-8"))

    /** La obra de un ISBN (el código de barras de la contracubierta, o escrito a mano). */
    suspend fun porIsbn(isbn: String): List<Hit> = buscaEn("isbn=$isbn")

    private suspend fun buscaEn(filtro: String): List<Hit> = withContext(Dispatchers.IO) {
        val url = "https://openlibrary.org/search.json?$filtro" +
            "&fields=key,title,author_name,first_publish_year,cover_i,number_of_pages_median&limit=20"
        val docs = JSONObject(fetch(url)).getJSONArray("docs")
        (0 until docs.length()).mapNotNull { i ->
            val o = docs.getJSONObject(i)
            val id = o.optString("key").filter { it.isDigit() }.toLongOrNull()
            if (id == null) {
                null
            } else {
                val author = o.optJSONArray("author_name")?.optString(0) ?: ""
                val year = if (o.has("first_publish_year")) o.optInt("first_publish_year").toString() else ""
                val ci = o.optLong("cover_i", 0L)
                val pages = o.optInt("number_of_pages_median", 0)
                Hit(id, o.optString("title"),
                    listOf(author, year, if (pages > 0) "$pages págs." else "").filter { it.isNotBlank() }.joinToString(" · "),
                    if (ci > 0) "$COVERS/$ci-L.jpg" else "", pages, author)
            }
        }
    }

    /** Portadas de las obras que salen al buscar ese título (para libros importados sin portada). */
    suspend fun coversByTitle(name: String): List<Pair<String, String>> =
        search(Goodreads.cleanTitle(name)).mapNotNull { h -> h.cover.takeIf { it.isNotBlank() } }.distinct()
            .map { it.replace("-L.jpg", "-M.jpg") to it }

    /**
     * Portadas para lo que escribas en el buscador de portadas: todas las ediciones de la obra
     * que mejor encaja y, detrás, las de las demás obras que salen con ese título.
     */
    suspend fun coversFor(q: String): List<Pair<String, String>> {
        val hits = search(q)
        val first = hits.firstOrNull()?.let { h -> try { covers(h.id) } catch (e: Exception) { emptyList() } } ?: emptyList()
        val rest = hits.mapNotNull { h -> h.cover.takeIf { it.isNotBlank() } }.map { it.replace("-L.jpg", "-M.jpg") to it }
        return (first + rest).distinctBy { it.second }
    }

    /** Pares (miniatura, url completa) de las portadas de una obra. */
    suspend fun covers(id: Long): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        // Libros importados que Open Library no encontró: no tienen portadas allí.
        if (id >= Goodreads.FALLBACK_ID) return@withContext emptyList()
        val arr = JSONObject(fetch("https://openlibrary.org/works/OL${id}W.json")).optJSONArray("covers")
        if (arr == null) {
            emptyList()
        } else {
            (0 until arr.length()).map { arr.optLong(it) }.filter { it > 0 }.take(18)
                .map { "$COVERS/$it-M.jpg" to "$COVERS/$it-L.jpg" }
        }
    }
}

/**
 * Pasa un título al inglés para buscar portadas. Primero busca el libro en Wikidata (da el título
 * original de verdad: "Amanecer en la cosecha" → "Sunrise on the Reaping"); si no está, lo traduce
 * con MyMemory (traducción automática, que puede equivocarse).
 */
object TitleEn {
    class Result(val title: String, val source: String)

    /** Tipos de Wikidata que son libros u obras escritas (novela, obra literaria, saga…). */
    private val BOOKISH = setOf("Q7725634", "Q8261", "Q47461344", "Q571", "Q1667921", "Q277759", "Q49084", "Q112983")

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Atrio/4.4 (app personal; Android)")
        c.connectTimeout = 15000
        c.readTimeout = 15000
        try {
            if (c.responseCode !in 200..299) error("El servidor respondió ${c.responseCode}.")
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }

    private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8")

    /** Wikidata a veces rechaza peticiones seguidas: se reintenta una vez. */
    private fun wikidata(q: String): String? = wikidataOnce(q) ?: run { Thread.sleep(1200); wikidataOnce(q) }

    private fun wikidataOnce(q: String): String? = try {
        val hits = get("https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${enc(q)}&language=es&uselang=es&type=item&limit=7&format=json")
            .optJSONArray("search") ?: JSONArray()
        val ids = (0 until hits.length()).map { hits.getJSONObject(it).optString("id") }.filter { it.isNotBlank() }
        if (ids.isEmpty()) null else {
            val ents = get("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=${ids.joinToString("|")}&props=labels|claims&languages=en&format=json")
                .optJSONObject("entities") ?: JSONObject()
            ids.firstNotNullOfOrNull { id ->
                val e = ents.optJSONObject(id) ?: return@firstNotNullOfOrNull null
                val p31 = e.optJSONObject("claims")?.optJSONArray("P31") ?: JSONArray()
                val types = (0 until p31.length()).mapNotNull {
                    p31.optJSONObject(it)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")
                }
                if (types.none { it in BOOKISH }) null
                else e.optJSONObject("labels")?.optJSONObject("en")?.optString("value")?.takeIf { it.isNotBlank() }
            }
        }
    } catch (e: Exception) { null }

    private fun mymemory(q: String): String? = try {
        val o = get("https://api.mymemory.translated.net/get?q=${enc(q)}&langpair=es%7Cen")
        o.optJSONObject("responseData")?.optString("translatedText")
            ?.trim()?.trim('"', '“', '”', '«', '»', '\'', '!', '¡', '.', ' ')
            ?.takeIf { it.isNotBlank() && !it.contains("MYMEMORY WARNING", true) && !it.equals(q.trim(), true) }
    } catch (e: Exception) { null }

    suspend fun translate(q: String): Result? = withContext(Dispatchers.IO) {
        val t = Goodreads.cleanTitle(q)
        wikidata(t)?.let { return@withContext Result(it, "título original según Wikidata") }
        mymemory(t)?.let { return@withContext Result(it, "traducción automática, puede no ser el título real") }
        null
    }
}
