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
        val c = URL(u).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA)
        c.connectTimeout = 12000; c.readTimeout = 12000
        if (c.responseCode !in 200..299) error("Wikcionario respondió ${c.responseCode}")
        val pages = JSONObject(c.inputStream.bufferedReader().readText()).getJSONObject("query").getJSONObject("pages")
        val p = pages.getJSONObject(pages.keys().next())
        return if (p.has("missing")) null else p.optString("extract").ifBlank { null }
    }

    /** Las acepciones de la sección del idioma (por defecto, «Español»): las líneas que empiezan por número. */
    fun parse(ex: String, langName: String = "Español"): List<String> {
        val lines = ex.lines().map { it.replace(' ', ' ').replace("¦", "").trim() }
        val head = Regex("^(=+)\\s*(.+?)\\s*=+$")
        var i = lines.indexOfFirst { head.matchEntire(it)?.let { m -> m.groupValues[1].length == 2 && m.groupValues[2].startsWith(langName, true) } == true }
        if (i < 0) i = lines.indexOfFirst { head.matchEntire(it)?.groupValues?.get(1)?.length == 2 }   // la primera lengua que haya
        if (i < 0) return emptyList()
        val out = ArrayList<String>()
        var j = i + 1
        val skip = Regex("^(Sinónimos?|Antónimos?|Hiperónimos?|Hipónimos?|Derivados?|Relacionados?|Véase|Ejemplos?|Uso|Ámbito|Variantes?|Información|Traducciones|Locuciones|Refranes|Parónimos?|Compuestos?|Abreviaturas?)\\b.*")
        while (j < lines.size && out.size < 4) {
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

    /** Las acepciones de una palabra (prueba también en minúscula). */
    fun define(word: String, langName: String = "Español"): List<String> {
        val w = word.trim()
        for (t in listOf(w.lowercase(Locale("es")), w).distinct()) {
            val ex = extract(t) ?: continue
            val d = parse(ex, langName)
            if (d.isNotEmpty()) return d
        }
        return emptyList()
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
