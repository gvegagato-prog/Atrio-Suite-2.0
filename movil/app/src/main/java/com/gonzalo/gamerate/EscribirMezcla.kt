package com.gonzalo.gamerate

import org.json.JSONArray
import org.json.JSONObject

/** El resultado de juntar dos versiones de un texto: el texto y cuántos trozos chocaban (se cambió lo mismo en los dos). */
class Mezcla(val texto: String, val choques: Int)

/** El resultado de juntar dos versiones de un documento: el documento (sin «u» ni «pu»), cuántos choques y en qué campos. */
class MezclaDoc(val o: JSONObject, val choques: Int, val chocan: Set<String>)

/**
 * Juntar dos versiones que salen de la misma (la «base»): lo que solo cambió en una se queda, y solo cuando las dos cambian
 * lo mismo (el mismo campo, el mismo párrafo) manda la preferida. Igual que mezclaTexto/mezclaDocs de escritos.js en el PC
 * (los dos se prueban con herramientas/casos/mezcla.json).
 */
object EscribirMezcla {
    /** Campos de texto largo: se juntan por párrafos. */
    val TEXTOS = setOf("texto", "resumen", "sinopsis", "nota")
    private val FUERA = setOf("id", "t", "u", "pu")

    /** Más de tantos párrafos distintos entre dos versiones (ya sin lo igual del principio y del final) se toma como reescrito. */
    const val LIMITE = 1500

    /**
     * Índices emparejados de lo común a [a] y [b], en orden (lo igual al principio y al final se aparta antes). Es el
     * algoritmo de Myers: la memoria crece con lo que cambia, no con lo largo que sea el texto.
     */
    fun comunes(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        var ini = 0
        while (ini < a.size && ini < b.size && a[ini] == b[ini]) ini++
        var fa = a.size; var fb = b.size
        while (fa > ini && fb > ini && a[fa - 1] == b[fb - 1]) { fa--; fb-- }
        val out = ArrayList<Pair<Int, Int>>()
        for (i in 0 until ini) out += i to i
        out += myers(a.subList(ini, fa), b.subList(ini, fb)).map { (i, j) -> (ini + i) to (ini + j) }
        for (k in 0 until a.size - fa) out += (fa + k) to (fb + k)
        return out
    }

    private fun myers(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val n = a.size; val m = b.size
        if (n == 0 || m == 0) return emptyList()
        val max = n + m
        val off = max + 1
        val v = IntArray(2 * max + 3)
        val traza = ArrayList<IntArray>()
        for (d in 0..minOf(max, LIMITE)) {
            traza += v.copyOfRange(off - d - 1, off + d + 2)
            var k = -d
            while (k <= d) {
                var x = if (k == -d || (k != d && v[off + k - 1] < v[off + k + 1])) v[off + k + 1] else v[off + k - 1] + 1
                var y = x - k
                while (x < n && y < m && a[x] == b[y]) { x++; y++ }
                v[off + k] = x
                if (x >= n && y >= m) return atras(traza, n, m)
                k += 2
            }
        }
        return emptyList()
    }

    private fun atras(traza: List<IntArray>, n: Int, m: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var x = n; var y = m
        for (d in traza.size - 1 downTo 0) {
            val v = traza[d]
            fun at(k: Int) = v[k + d + 1]
            val k = x - y
            val pk = if (k == -d || (k != d && at(k - 1) < at(k + 1))) k + 1 else k - 1
            val px = at(pk); val py = px - pk
            while (x > px && y > py && x > 0 && y > 0) { out += (x - 1) to (y - 1); x--; y-- }
            if (d > 0) { x = px; y = py }
        }
        return out.asReversed()
    }

    /**
     * Mezcla a tres bandas por líneas (cada párrafo es una línea). Si los dos añaden párrafos en el mismo sitio, se quedan
     * los dos. Si cambian el mismo párrafo (un choque), de ese trozo se queda el de [prefiereNuestro], o los dos seguidos si
     * [ambos] (así no se pierde nada en una nota; en una escena, lo que no se queda se guarda aparte como borrador).
     */
    fun texto(base: String, nuestro: String, suyo: String, prefiereNuestro: Boolean, ambos: Boolean = false): Mezcla {
        if (nuestro == suyo) return Mezcla(nuestro, 0)
        if (nuestro == base) return Mezcla(suyo, 0)
        if (suyo == base) return Mezcla(nuestro, 0)
        val (partes, choques) = junta3(base.split('\n'), nuestro.split('\n'), suyo.split('\n'), prefiereNuestro, lineas = true, ambos = ambos)
        return Mezcla(partes.joinToString("\n"), choques)
    }

    private val PIEZA = Regex("\\s+|[\\p{L}\\p{N}]+|[^\\s\\p{L}\\p{N}]")
    private fun piezas(s: String): List<String> = PIEZA.findAll(s).map { it.value }.toList()

    /** Un mismo párrafo cambiado en los dos: se junta por palabras si los cambios no se tocan (si no, null). */
    private fun porPalabras(a: String, o: String, t: String): String? {
        if (o == t || o == a) return t
        if (t == a) return o
        val (p, ch) = junta3(piezas(a), piezas(o), piezas(t), true, lineas = false, ambos = false)
        return if (ch == 0) p.joinToString("") else null
    }

    /**
     * La mezcla a tres bandas de dos listas (párrafos, o palabras dentro de un párrafo si no es [lineas]): se sigue lo común a
     * las tres y, entre medias, se mira quién cambió qué.
     */
    private fun junta3(a: List<String>, o: List<String>, t: List<String>, pref: Boolean, lineas: Boolean, ambos: Boolean): Pair<List<String>, Int> {
        val mo = HashMap<Int, Int>().apply { comunes(a, o).forEach { put(it.first, it.second) } }
        val mt = HashMap<Int, Int>().apply { comunes(a, t).forEach { put(it.first, it.second) } }
        val out = ArrayList<String>()
        var choques = 0
        var pa = -1; var po = -1; var pt = -1
        fun empieza(x: List<String>, c: List<String>) = c.isNotEmpty() && x.size > c.size && x.subList(0, c.size) == c
        fun acaba(x: List<String>, c: List<String>) = c.isNotEmpty() && x.size > c.size && x.subList(x.size - c.size, x.size) == c
        fun trozo(ia: Int, io: Int, it: Int) {
            val ca = a.subList(pa + 1, ia); val co = o.subList(po + 1, io); val ct = t.subList(pt + 1, it)
            val (pri, seg) = if (pref) co to ct else ct to co
            when {
                co == ca -> out += ct
                ct == ca -> out += co
                co == ct -> out += co
                // Los dos añaden en el mismo sitio: párrafos nuevos se quedan los dos; palabras, no (chocan)
                ca.isEmpty() && lineas -> { out += pri; out += seg }
                // Uno solo añadió detrás (o delante) de lo que había y el otro lo cambió: lo cambiado y lo añadido
                empieza(ct, ca) -> { out += co; out += ct.subList(ca.size, ct.size) }
                acaba(ct, ca) -> { out += ct.subList(0, ct.size - ca.size); out += co }
                empieza(co, ca) -> { out += ct; out += co.subList(ca.size, co.size) }
                acaba(co, ca) -> { out += co.subList(0, co.size - ca.size); out += ct }
                // Los mismos párrafos cambiados en los dos: se juntan por palabras, párrafo a párrafo
                lineas && ca.size == co.size && ca.size == ct.size -> for (j in ca.indices) {
                    val p = porPalabras(ca[j], co[j], ct[j])
                    when {
                        p != null -> out += p
                        ambos -> { out += pri[j]; if (seg[j] != pri[j]) out += seg[j] }
                        else -> { choques++; out += pri[j] }
                    }
                }
                ambos -> { out += pri; out += seg.filter { it !in pri } }
                else -> { choques++; out += pri }
            }
        }
        for (i in a.indices) {
            val io = mo[i] ?: continue
            val it = mt[i] ?: continue
            if (io <= po || it <= pt) continue
            trozo(i, io, it)
            out += a[i]
            pa = i; po = io; pt = it
        }
        trozo(a.size, o.size, t.size)
        return out to choques
    }

    /** Texto canónico de un valor (claves en orden; 1000 y 1000.0 son lo mismo) para comparar. */
    fun canon(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> v.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canon(v.opt(it)) }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.opt(it)) }
        is Number -> v.toDouble().let { d -> if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString() else d.toString() }
        is Boolean -> v.toString()
        else -> JSONObject.quote(v.toString())
    }

    /** El contenido de un documento, sin sus horas («u», «pu»), en texto canónico. */
    fun contenido(o: JSONObject): String = canon(JSONObject(o.toString()).apply { remove("u"); remove("pu") })

    /** Junta dos versiones de un documento a partir de [base] (null si no se tiene: entonces cada campo distinto choca). */
    fun docs(base: JSONObject?, nuestro: JSONObject, suyo: JSONObject, prefiereNuestro: Boolean): MezclaDoc {
        val out = JSONObject()
        var choques = 0
        val chocan = HashSet<String>()
        val tipo = nuestro.optString("t", suyo.optString("t"))
        val claves = (nuestro.keys().asSequence() + suyo.keys().asSequence() + (base?.keys()?.asSequence() ?: emptySequence())).toSortedSet()
        for (k in claves) {
            if (k in FUERA || (tipo == Escritos.T_ESCENA && k == "palabras")) continue
            val o = nuestro.opt(k); val t = suyo.opt(k); val a = base?.opt(k)
            val co = canon(o); val ct = canon(t); val ca = if (base == null) null else canon(a)
            val v: Any? = when {
                co == ct -> o
                ca != null && co == ca -> t
                ca != null && ct == ca -> o
                k in TEXTOS && (o is String || o == null) && (t is String || t == null) -> {
                    // En una escena, el texto que choca se guarda aparte (borrador); en lo demás se quedan los dos trozos
                    val m = texto(if (a is String) a else "", o as? String ?: "", t as? String ?: "", prefiereNuestro, ambos = !(tipo == Escritos.T_ESCENA && k == "texto"))
                    if (m.choques > 0) { choques += m.choques; chocan += k }
                    m.texto
                }
                else -> { choques++; chocan += k; if (prefiereNuestro) o else t }
            }
            if (v != null && v != JSONObject.NULL) out.put(k, v)
        }
        out.put("id", nuestro.opt("id") ?: suyo.opt("id")); out.put("t", tipo)
        if (tipo == Escritos.T_ESCENA) out.put("palabras", Escritos.palabras(out.optString("texto")))
        return MezclaDoc(out, choques, chocan)
    }
}
