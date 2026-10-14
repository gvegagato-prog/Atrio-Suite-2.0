package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

/*
 * Notas de Rotten Tomatoes (rottentomatoes.com) para películas y series: la de la crítica (Tomatometer) y la del
 * público (Popcornmeter). No hay API pública: se busca el título original en su buscador (cada resultado trae el
 * año y el Tomatometer), se elige el que coincide por título y año, y en su página se leen las dos notas del JSON
 * que la propia página lleva dentro. Se guardan una semana en el móvil.
 */

/** Notas de Rotten Tomatoes (−1 = no hay). */
class RtScore(val tomato: Int, val popcorn: Int, val url: String, val certified: Boolean = false) {
    val fresh get() = tomato >= 60
}

object Rt {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    private const val WEEK = 7L * 24 * 3600 * 1000
    private val mem = HashMap<String, RtScore?>()
    private val gate = Semaphore(2)

    private fun key(kind: Kind, id: Long) = (if (kind.isTv) "t:" else "m:") + id

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("rt", Context.MODE_PRIVATE)

    /** Lo guardado (aunque esté viejo), sin ir a internet. */
    fun cached(ctx: Context, kind: Kind, id: Long): RtScore? {
        val k = key(kind, id)
        synchronized(mem) { if (mem.containsKey(k)) return mem[k] }
        val o = prefs(ctx).getString(k, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        if (o.optBoolean("none")) return null
        return RtScore(o.optInt("tm", -1), o.optInt("pc", -1), o.optString("u"), o.optBoolean("cf"))
    }

    private fun fresh(ctx: Context, kind: Kind, id: Long): Boolean {
        val o = prefs(ctx).getString(key(kind, id), null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        return System.currentTimeMillis() - o.optLong("at") < WEEK
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.8")
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        c.instanceFollowRedirects = true
        if (c.responseCode !in 200..299) throw IllegalStateException("Rotten Tomatoes: ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace("&", "and").replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Título original y año: de TMDB (películas, si hay clave) o de TVmaze (series). */
    private fun original(kind: Kind, id: Long, name: String, tmdbKey: String): Pair<String, String> {
        return try {
            if (kind.isTv) {
                val o = JSONObject(mget("https://api.tvmaze.com/shows/$id"))
                o.optString("name").ifBlank { name } to o.optString("premiered").take(4)
            } else if (tmdbKey.isNotBlank()) {
                val k = tmdbKey.trim()
                val token = k.startsWith("eyJ")
                val url = "https://api.themoviedb.org/3/movie/$id?language=en-US" + if (token) "" else "&api_key=${menc(k)}"
                val o = JSONObject(mget(url, if (token) k else ""))
                o.optString("title").ifBlank { o.optString("original_title") }.ifBlank { name } to o.optString("release_date").take(4)
            } else name to ""
        } catch (e: Exception) { name to "" }
    }

    /** Busca en Rotten Tomatoes y devuelve la dirección de la ficha que coincide. */
    fun findUrl(title: String, year: String, tv: Boolean): String? {
        val html = get("https://www.rottentomatoes.com/search?search=" + URLEncoder.encode(title, "UTF-8"))
        val want = if (tv) "tvSeries" else "movie"
        val sections = html.split("<search-page-result").drop(1)
        val sec = sections.firstOrNull { Regex("type=\"$want\"").containsMatchIn(it.substringBefore('>')) } ?: return null
        class Row(val url: String, val name: String, val year: Int?)
        val rows = Regex("<search-page-media-row([^>]*)>(.*?)</search-page-media-row>", RegexOption.DOT_MATCHES_ALL).findAll(sec).mapNotNull { m ->
            val attrs = m.groupValues[1]; val body = m.groupValues[2]
            val url = Regex("href=\"(https://www\\.rottentomatoes\\.com/(?:m|tv)/[^\"]+)\"").find(body)?.groupValues?.get(1) ?: return@mapNotNull null
            val nm = Regex("data-qa=\"info-name\"[^>]*>\\s*([^<]+?)\\s*</a>").find(body)?.groupValues?.get(1)
                ?: Regex("alt=\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
            fun attr(n: String) = Regex("$n=\"(\\d{4})\"").find(attrs)?.groupValues?.get(1)?.toIntOrNull()
            Row(url, android.text.Html.fromHtml(nm, 0).toString(), attr(if (tv) "start-year" else "release-year") ?: attr("release-year"))
        }.toList()
        if (rows.isEmpty()) return null
        val t = norm(title)
        val y = year.toIntOrNull()
        return (rows.firstOrNull { norm(it.name) == t && (y == null || it.year == null || kotlin.math.abs(it.year - y) <= 1) }
            ?: rows.firstOrNull { norm(it.name) == t }
            ?: rows.firstOrNull { y != null && it.year == y && (norm(it.name).startsWith(t) || t.startsWith(norm(it.name))) })?.url
    }

    /** Notas de la ficha (las dos van en un JSON dentro de la página). */
    fun scoresAt(url: String): RtScore? {
        val html = get(url)
        fun score(block: String): Int? = Regex("\"$block\":\\{[^}]*?\"score\":\"(\\d+)\"").find(html)?.groupValues?.get(1)?.toIntOrNull()
        val tm = score("criticsScore") ?: -1
        val pc = score("audienceScore") ?: -1
        if (tm < 0 && pc < 0) return null
        val cert = Regex("\"criticsScore\":\\{[^}]*?\"certified\":true").containsMatchIn(html)
        return RtScore(tm, pc, url, cert)
    }

    /** Notas de una película o serie (de la memoria, del móvil o de internet). */
    suspend fun scores(ctx: Context, kind: Kind, id: Long, name: String, tmdbKey: String): RtScore? {
        val k = key(kind, id)
        if (fresh(ctx, kind, id)) return cached(ctx, kind, id)
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                val r = try {
                    val (t, y) = original(kind, id, name, tmdbKey)
                    val url = findUrl(t, y, kind.isTv) ?: (if (normName(t) != normName(name)) findUrl(name, y, kind.isTv) else null)
                    url?.let { scoresAt(it) }
                } catch (e: Exception) {
                    // Sin conexión: se usa lo que hubiera y se vuelve a intentar otro día
                    return@withContext cached(ctx, kind, id)
                }
                val o = if (r == null) JSONObject().put("none", true) else JSONObject().put("tm", r.tomato).put("pc", r.popcorn).put("u", r.url).put("cf", r.certified)
                prefs(ctx).edit().putString(k, o.put("at", System.currentTimeMillis()).toString()).apply()
                synchronized(mem) { mem[k] = r }
                r
            }
        }
    }
}

/** Las notas de Rotten Tomatoes de un título, cargadas al verlo. */
@Composable
fun rememberRt(kind: Kind, id: Long, name: String): RtScore? {
    val ctx = LocalContext.current
    val v by produceState(Rt.cached(ctx, kind, id), kind.id, id) {
        value = Rt.scores(ctx, kind, id, name, Store(ctx).tmdbKey)
    }
    return v
}

/** Tomate (fresco: rojo con hojas; podrido: mancha verde). */
@Composable
fun TomatoIcon(fresh: Boolean, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        if (fresh) {
            drawCircle(Color(0xFFFA320A), w * 0.42f, Offset(w * 0.5f, w * 0.56f))
            val leaf = Path().apply {
                moveTo(w * 0.5f, w * 0.18f); lineTo(w * 0.28f, w * 0.06f); lineTo(w * 0.42f, w * 0.24f)
                lineTo(w * 0.5f, w * 0.0f); lineTo(w * 0.58f, w * 0.24f); lineTo(w * 0.72f, w * 0.06f); close()
            }
            drawPath(leaf, Color(0xFF00912D))
        } else {
            val c = Color(0xFF0AC855)
            drawCircle(c, w * 0.32f, Offset(w * 0.5f, w * 0.52f))
            for (i in 0 until 6) {
                val a = Math.toRadians(i * 60.0 + 15)
                drawCircle(c, w * 0.12f, Offset(w * 0.5f + w * 0.38f * Math.cos(a).toFloat(), w * 0.52f + w * 0.38f * Math.sin(a).toFloat()))
            }
        }
    }
}

/** Cubo de palomitas. */
@Composable
fun PopcornIcon(size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        listOf(0.3f to 0.25f, 0.5f to 0.18f, 0.7f to 0.25f, 0.4f to 0.32f, 0.6f to 0.32f).forEach { (x, y) ->
            drawCircle(Color(0xFFFFE9B0), w * 0.14f, Offset(w * x, w * y))
        }
        val bucket = Path().apply { moveTo(w * 0.18f, w * 0.38f); lineTo(w * 0.82f, w * 0.38f); lineTo(w * 0.7f, w); lineTo(w * 0.3f, w); close() }
        drawPath(bucket, Color(0xFFFA320A))
        drawRect(Color.White, Offset(w * 0.44f, w * 0.38f), Size(w * 0.12f, w * 0.62f))
    }
}

/** Insignia en la portada: tomate y porcentaje de la crítica. */
@Composable
fun BoxScope.RtBadgeFor(kind: Kind, id: Long, name: String) {
    val r = rememberRt(kind, id, name) ?: return
    if (r.tomato < 0) return
    Row(Modifier.clip(pillShape()).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TomatoIcon(r.fresh, 13.dp)
        Spacer(Modifier.width(4.dp))
        Text("${r.tomato}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Las dos notas en la cabecera de la ficha. */
@Composable
fun RtChips(kind: Kind, id: Long, name: String) {
    val r = rememberRt(kind, id, name) ?: return
    if (r.tomato >= 0) RtPill({ TomatoIcon(r.fresh, 15.dp) }, "${r.tomato}%", if (r.certified) "Crítica · certificado" else "Crítica")
    if (r.popcorn >= 0) RtPill({ PopcornIcon(15.dp) }, "${r.popcorn}%", "Público")
}

@Composable
private fun RtPill(icon: @Composable () -> Unit, value: String, label: String) {
    Row(Modifier.clip(pillShape()).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(5.dp))
        Text(value, color = Charcoal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}
