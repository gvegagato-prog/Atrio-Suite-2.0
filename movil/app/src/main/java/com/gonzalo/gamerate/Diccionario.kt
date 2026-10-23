package com.gonzalo.gamerate

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/*
 * Para el lector: qué significa una palabra (Wikcionario en español) y traducirla al español si el libro está en
 * otro idioma (traductor de Google en el propio móvil, ML Kit: funciona sin conexión una vez bajado el idioma).
 */
object Diccionario {
    private const val UA = "Atrio/6 (lector de libros personal; https://github.com/gvegagato-prog/Atrio-Suite-2.0)"

    private fun extract(title: String): String? {
        val u = "https://es.wiktionary.org/w/api.php?action=query&prop=extracts&explaintext=1&format=json&redirects=1&titles=" +
            URLEncoder.encode(title, "UTF-8")
        var c = URL(u).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA)
        c.connectTimeout = 12000; c.readTimeout = 12000
        // Si Wikcionario pide ir más despacio (429), se espera un momento y se reintenta una vez
        if (c.responseCode == 429) {
            Thread.sleep(((c.getHeaderField("Retry-After")?.toLongOrNull() ?: 2L).coerceIn(1L, 4L)) * 1000)
            c = URL(u).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", UA)
            c.connectTimeout = 12000; c.readTimeout = 12000
        }
        if (c.responseCode !in 200..299) error("Wikcionario respondió ${c.responseCode}")
        val pages = JSONObject(c.inputStream.bufferedReader().readText()).getJSONObject("query").getJSONObject("pages")
        val p = pages.getJSONObject(pages.keys().next())
        return if (p.has("missing")) null else p.optString("extract").ifBlank { null }
    }

    /** Las acepciones de la sección del idioma (por defecto, «Español»): las líneas que empiezan por número. */
    fun parse(ex: String, langName: String = "Español"): List<String> {
        val all = parseAll(ex, langName)
        val real = all.filter { lemmaDe(it) == null }
        return (real.ifEmpty { all }).take(4)
    }

    private fun parseAll(ex: String, langName: String): List<String> {
        val lines = ex.lines().map { it.replace(' ', ' ').replace("¦", "").trim() }
        val head = Regex("^(=+)\\s*(.+?)\\s*=+$")
        var i = lines.indexOfFirst { head.matchEntire(it)?.let { m -> m.groupValues[1].length == 2 && m.groupValues[2].startsWith(langName, true) } == true }
        if (i < 0) i = lines.indexOfFirst { head.matchEntire(it)?.groupValues?.get(1)?.length == 2 }   // la primera lengua que haya
        if (i < 0) return emptyList()
        val out = ArrayList<String>()
        var j = i + 1
        val skip = Regex("^(Sinónimos?|Antónimos?|Hiperónimos?|Hipónimos?|Derivados?|Relacionados?|Véase|Ejemplos?|Uso|Ámbito|Variantes?|Información|Traducciones|Locuciones|Refranes|Parónimos?|Compuestos?|Abreviaturas?)\\b.*")
        while (j < lines.size && out.size < 14) {
            val l = lines[j]
            val m = head.matchEntire(l)
            if (m != null && m.groupValues[1].length == 2) break           // empieza otra lengua
            val num = Regex("^(\\d+)\\s*(.*)$").matchEntire(l)
            if (num != null && m == null) {
                var rest = num.groupValues[2].trim()
                // A veces el número va solo (o con una etiqueta de ámbito) y la definición en la línea siguiente
                if (rest.length < 25 && j + 1 < lines.size) {
                    val next = lines[j + 1]
                    if (next.isNotBlank() && head.matchEntire(next) == null && !Regex("^\\d+\\b.*").matches(next) && !skip.matches(next)) {
                        rest = if (rest.isBlank()) next else "($rest) $next"
                        j++
                    }
                }
                if (rest.length >= 3 && !skip.matches(rest)) out += rest
            }
            j++
        }
        return out
    }

    private val FORMA = Regex(
        "^(?:Forma|Plural|Singular|Femenino|Masculino|Superlativo|Diminutivo|Aumentativo|Despectivo|Participio|Gerundio|Infinitivo|Imperativo|Primera|Segunda|Tercera)\\b.{0,110}?\\bde(?:l)?\\s+(?:verbo\\s+|la\\s+|lo\\s+)?([\\p{L}]+)\\.?$"
    )
    private val VERBAL = Regex("^(?:Primera|Segunda|Tercera|Participio|Gerundio|Infinitivo|Imperativo|Forma verbal)\\b.*")

    /** Si la acepción solo dice «forma del plural de falda», la palabra de la que es forma (falda); si no, null. */
    fun lemmaDe(def: String): String? = FORMA.matchEntire(def.trim().replace(Regex("\\s*\\(.*?\\)\\s*$"), ""))?.groupValues?.get(1)?.takeIf { it.length > 1 }

    private fun plain(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /**
     * Las palabras que se prueban, por orden: tal cual, en minúscula, sin pronombres pegados (acordarme → acordar,
     * dándole → dando, decírselo → decir) y en singular (casas → casa).
     */
    fun candidates(word: String): List<String> {
        val w = word.trim()
        val low = w.lowercase(Locale("es"))
        val out = linkedSetOf(low, w)
        // Pronombres pegados al infinitivo, al gerundio o al imperativo
        val cl = Regex("^(.+?)((?:me|te|se|nos|os|lo|la|le|los|las|les){1,2})$").matchEntire(low)
        if (cl != null) {
            val stem = cl.groupValues[1]
            // Sin la tilde que le pone el pronombre (dándole → dando); «oír», «reír»… la conservan
            if (Regex(".*(ar|er|ir|ando|iendo|yendo)$").matches(plain(stem))) { out += plain(stem); out += stem }
        }
        if (low.length > 4 && low.endsWith("es")) out += low.dropLast(2)
        if (low.length > 3 && low.endsWith("s")) out += low.dropLast(1)
        return out.toList()
    }

    /**
     * Las acepciones de una palabra y la palabra con la que se han encontrado. Si la palabra es solo una forma de otra
     * (faldas = «plural de falda», corría = «forma de correr»), se va a la palabra de verdad y se enseña su significado.
     */
    fun define(word: String, langName: String = "Español", prof: Int = 0): Pair<String, List<String>> {
        for (t in candidates(word).take(5)) {
            val ex = extract(t) ?: continue
            val all = parseAll(ex, langName)
            if (all.isEmpty()) continue
            val real = all.filter { lemmaDe(it) == null }
            if (real.isNotEmpty()) return t to real.take(4)
            if (prof < 2) {
                // Primero las formas de sustantivo o adjetivo; las verbales, al final
                val lemas = all.sortedBy { if (VERBAL.matches(it)) 1 else 0 }.mapNotNull { lemmaDe(it) }.distinct().filter { !it.equals(t, true) }.take(2)
                val sacados = lemas.mapNotNull { l -> runCatching { define(l, langName, prof + 1) }.getOrNull()?.takeIf { it.second.isNotEmpty() } }
                if (sacados.isNotEmpty()) {
                    val defs = if (sacados.size == 1) sacados[0].second else sacados.flatMap { (l, d) -> d.take(3).map { "($l) $it" } }
                    return sacados[0].first to defs
                }
            }
            return t to all.take(4)
        }
        return word to emptyList()
    }

    /** El idioma de un texto (código BCP-47, «und» si no se sabe). */
    fun identify(text: String): String = try {
        Tasks.await(LanguageIdentification.getClient().identifyLanguage(text), 8, TimeUnit.SECONDS) ?: "und"
    } catch (e: Exception) { "und" }

    fun langName(code: String): String =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale("es", "ES")).replaceFirstChar { it.uppercase() }

    /** Traduce al español (baja el idioma la primera vez). */
    fun translate(text: String, from: String): String {
        val src = TranslateLanguage.fromLanguageTag(from) ?: error("Ese idioma no se puede traducir aquí.")
        val tr = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(TranslateLanguage.SPANISH).build())
        try {
            Tasks.await(tr.downloadModelIfNeeded(DownloadConditions.Builder().build()), 180, TimeUnit.SECONDS)
            return Tasks.await(tr.translate(text), 30, TimeUnit.SECONDS)
        } finally { tr.close() }
    }
}
