package com.gonzalo.gamerate

/** Una parte de la comparación de dos textos: igual en los dos, o distinta (lo de cada lado). */
sealed class Parte {
    class Igual(val parrafos: List<String>) : Parte()
    class Cambio(val aqui: List<String>, val otro: List<String>) : Parte()
}

/** Comparar dos versiones de un texto párrafo a párrafo, y mezclarlas eligiendo de cada diferencia. */
object EscribirDiff {
    fun parrafos(t: String): List<String> = t.replace("\r\n", "\n").split(Regex("\n\\s*\n|\n")).map { it.trim() }.filter { it.isNotEmpty() }

    /** Las diferencias entre [aqui] y [otro] (subsucesión común más larga de párrafos). */
    fun compara(aqui: String, otro: String): List<Parte> {
        val a = parrafos(aqui); val b = parrafos(otro)
        val n = a.size; val m = b.size
        val l = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) l[i][j] = if (a[i] == b[j]) l[i + 1][j + 1] + 1 else maxOf(l[i + 1][j], l[i][j + 1])
        val out = ArrayList<Parte>()
        var i = 0; var j = 0
        val igual = ArrayList<String>(); val xa = ArrayList<String>(); val xb = ArrayList<String>()
        fun cierraIgual() { if (igual.isNotEmpty()) { out += Parte.Igual(ArrayList(igual)); igual.clear() } }
        fun cierraCambio() { if (xa.isNotEmpty() || xb.isNotEmpty()) { out += Parte.Cambio(ArrayList(xa), ArrayList(xb)); xa.clear(); xb.clear() } }
        while (i < n || j < m) {
            when {
                i < n && j < m && a[i] == b[j] -> { cierraCambio(); igual += a[i]; i++; j++ }
                j < m && (i == n || l[i][j + 1] >= l[i + 1][j]) -> { cierraIgual(); xb += b[j]; j++ }
                else -> { cierraIgual(); xa += a[i]; i++ }
            }
        }
        cierraIgual(); cierraCambio()
        return out
    }

    /** El texto mezclado: lo igual, y de cada diferencia lo de [aqui] o lo de [otro] según [usaOtro] (por orden de diferencia). */
    fun mezcla(partes: List<Parte>, usaOtro: List<Boolean>): String {
        var k = 0
        val ps = ArrayList<String>()
        for (p in partes) when (p) {
            is Parte.Igual -> ps += p.parrafos
            is Parte.Cambio -> { ps += if (usaOtro.getOrElse(k) { false }) p.otro else p.aqui; k++ }
        }
        return ps.joinToString("\n\n")
    }
}
