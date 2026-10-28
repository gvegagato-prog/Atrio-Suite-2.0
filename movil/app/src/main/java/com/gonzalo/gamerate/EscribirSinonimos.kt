package com.gonzalo.gamerate

import android.content.Context

/**
 * Sinónimos del español para el editor de Escribir: el tesauro libre de RLA-ES (assets/sinonimos/th_es_v2.dat, LGPL 2.1,
 * sin cambios; ver LEEME.txt). Se busca la palabra, o su singular / masculino, y lo encontrado se adapta (plural,
 * femenino, mayúscula) a la palabra escrita. Igual que escritorio/src/main/sinonimos.js (casos en herramientas/casos).
 */
object Sinonimos {
    @Volatile private var lineas: List<String>? = null
    @Volatile private var indice: HashMap<String, Int>? = null

    /** Lo lee de la app la primera vez (unos 3 MB; mejor fuera del hilo principal). */
    fun carga(ctx: Context) {
        if (indice != null) return
        synchronized(this) {
            if (indice != null) return
            runCatching { ctx.assets.open("sinonimos/th_es_v2.dat").use { cargaTexto(String(it.readBytes(), Charsets.ISO_8859_1)) } }
        }
    }

    /** Lee el tesauro de un texto. Devuelve cuántas palabras tiene. */
    fun cargaTexto(t: String): Int {
        val l = t.split('\n')
        val idx = HashMap<String, Int>(32_000)
        var i = 1
        while (i < l.size) {
            val bar = l[i].lastIndexOf('|')
            val n = if (bar > 0) l[i].substring(bar + 1).trim().toIntOrNull() ?: 0 else 0
            if (n <= 0) { i++; continue }
            idx.putIfAbsent(l[i].substring(0, bar).lowercase(), i)
            i += n + 1
        }
        lineas = l; indice = idx
        return idx.size
    }

    private fun grupos(i: Int): List<List<String>> {
        val l = lineas ?: return emptyList()
        val n = l[i].substring(l[i].lastIndexOf('|') + 1).trim().toIntOrNull() ?: 0
        return (1..n).mapNotNull { k -> l.getOrNull(i + k)?.split('|')?.drop(1)?.map { it.trim() }?.filter { it.isNotEmpty() } }
    }

    private val SIN_TILDE = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u')
    private fun mayus(s: String) = s.replaceFirstChar { it.uppercaseChar() }
    private fun plural(s: String): String = when {
        s.isEmpty() -> s
        s.last() in "aeiouáéó" -> s + "s"
        s.endsWith("z") -> s.dropLast(1) + "ces"
        s.length >= 2 && s[s.length - 2] in SIN_TILDE && s.last() in "ns" -> s.dropLast(2) + SIN_TILDE[s[s.length - 2]] + s.last() + "es"
        s.endsWith("s") -> s
        else -> s + "es"
    }
    private fun femenino(s: String) = if (s.endsWith("o")) s.dropLast(1) + "a" else s

    /** Los sinónimos de una palabra, en grupos (cada grupo, un sentido), ya adaptados a cómo está escrita. */
    fun busca(palabra: String): List<List<String>> {
        val idx = indice ?: return emptyList()
        val w = palabra.trim()
        val p = w.lowercase()
        if (p.isEmpty()) return emptyList()
        val cands = ArrayList<Triple<String, Boolean, Boolean>>()
        cands += Triple(p, false, false)
        if (p.endsWith("ces")) cands += Triple(p.dropLast(3) + "z", true, false)
        if (p.endsWith("iones")) cands += Triple(p.dropLast(5) + "ión", true, false)
        if (p.endsWith("es")) cands += Triple(p.dropLast(2), true, false)
        if (p.endsWith("s")) cands += Triple(p.dropLast(1), true, false)
        if (p.endsWith("as")) cands += Triple(p.dropLast(2) + "o", true, true)
        if (p.endsWith("a")) cands += Triple(p.dropLast(1) + "o", false, true)
        for ((c, pl, fe) in cands) {
            val i = idx[c] ?: continue
            val visto = hashSetOf(p, c)
            val out = ArrayList<List<String>>()
            for (g in grupos(i)) {
                // (buscando por el singular o el masculino, los sentidos de adverbio no encajan)
                if ((pl || fe) && g.any { it.endsWith("mente") }) continue
                val r = ArrayList<String>()
                for (s0 in g) {
                    var s = s0
                    if (' ' !in s) { if (fe) s = femenino(s); if (pl) s = plural(s) }
                    if (w.first().isUpperCase()) s = mayus(s)
                    if (visto.add(s.lowercase())) r += s
                }
                if (r.isNotEmpty()) out += r
            }
            return out
        }
        return emptyList()
    }

    /** La palabra en la que está el cursor (o lo elegido): su principio y su fin en el texto, o null. */
    fun palabraEn(texto: String, desde: Int, hasta: Int): IntRange? {
        val a0 = minOf(desde, hasta).coerceIn(0, texto.length); val b0 = maxOf(desde, hasta).coerceIn(0, texto.length)
        fun letra(c: Char) = c.isLetter() || c == '\'' || c == '’'
        if (a0 != b0) {
            val sel = texto.substring(a0, b0).trim()
            return if (sel.isNotEmpty() && sel.all { letra(it) || it == ' ' || it == '-' }) { val ini = texto.indexOf(sel, a0); ini until ini + sel.length } else null
        }
        var a = a0; var b = a0
        while (a > 0 && letra(texto[a - 1])) a--
        while (b < texto.length && letra(texto[b])) b++
        return if (b > a) a until b else null
    }
}
