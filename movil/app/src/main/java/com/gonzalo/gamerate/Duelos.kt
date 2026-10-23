package com.gonzalo.gamerate

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Ordenar tus libros por duelos: «¿cuál te gustó más?». Pensado para lo importado de Bookmory o Goodreads, donde la
 * nota viene de estrellas (todo 100, 90, 80…). Cada duelo es una pregunta de dos libros; con las respuestas se calcula
 * una fuerza para cada libro (modelo de Bradley-Terry, que no depende del orden en que respondiste) y de ahí un orden
 * sin empates. El orden se convierte en notas con un baremo que tú ajustas.
 *
 * Para no cansar, los duelos se eligen de uno en uno donde más enseñan: libros que aún no se han comparado y que están
 * cerca en el orden. La nota que ya tenían sirve de punto de partida, así que un 100 nunca se compara con un 40.
 */
object Duelos {
    /** Un duelo contestado: res = 1 gana [a], -1 gana [b], 0 «me gustan igual». */
    data class Duelo(val a: Long, val b: Long, val res: Int)

    /** Cuántos duelos tiene cada libro, de media, para dar el orden por bueno. */
    const val OBJETIVO = 4

    private fun sig(x: Double) = 1.0 / (1.0 + exp(-x))

    /** Punto de partida de un libro: su nota de estrellas (0–100) pasada a la escala de las fuerzas. */
    fun inicial(nota: Int) = (nota - 70) / 14.0

    /**
     * La fuerza de cada libro: lo que más encaja con los duelos contestados, sin alejarse mucho de donde partía
     * (así lo poco comparado se queda cerca de su nota de estrellas).
     */
    fun fuerzas(notas: Map<Long, Int>, duelos: List<Duelo>): Map<Long, Double> {
        val th0 = notas.mapValues { inicial(it.value) }
        val th = HashMap(th0)
        val por = HashMap<Long, MutableList<Duelo>>()
        for (d in duelos) {
            if (d.a !in th || d.b !in th) continue
            por.getOrPut(d.a) { ArrayList() }.add(d); por.getOrPut(d.b) { ArrayList() }.add(d)
        }
        val inv = 1.0 / (1.0 * 1.0)
        repeat(60) {
            for (id in notas.keys) {
                val ds = por[id] ?: continue
                var g = -(th[id]!! - th0[id]!!) * inv
                var h = inv
                for (d in ds) {
                    val mio = if (d.a == id) 1 else -1
                    val otro = if (d.a == id) d.b else d.a
                    val s = when { d.res == 0 -> 0.5; d.res == mio -> 1.0; else -> 0.0 }
                    val p = sig(th[id]!! - th[otro]!!)
                    g += s - p; h += p * (1 - p)
                }
                th[id] = th[id]!! + (g / h).coerceIn(-1.5, 1.5)
            }
        }
        return th
    }

    /** El orden, del mejor al peor; a igual fuerza, el de mejor nota de estrellas y luego el nombre. */
    fun orden(notas: Map<Long, Int>, duelos: List<Duelo>, nombres: Map<Long, String> = emptyMap()): List<Long> {
        val f = fuerzas(notas, duelos)
        return notas.keys.sortedWith(compareByDescending<Long> { f[it] ?: 0.0 }.thenByDescending { notas[it] ?: 0 }.thenBy { nombres[it].orEmpty().lowercase() }.thenBy { it })
    }

    private fun clave(a: Long, b: Long) = if (a < b) a to b else b to a

    /**
     * El siguiente duelo: entre los libros vecinos en el orden actual, el que más enseña (parejas que van parejas,
     * con libros poco comparados). [saltados] son parejas que dijiste que no sabías contestar; [ultimo] son los dos
     * del duelo anterior, para no repetir el mismo libro seguido. null si ya no queda nada útil que preguntar.
     */
    fun siguiente(notas: Map<Long, Int>, duelos: List<Duelo>, saltados: Set<Pair<Long, Long>> = emptySet(), ultimo: Pair<Long, Long>? = null): Pair<Long, Long>? {
        if (notas.size < 2) return null
        val f = fuerzas(notas, duelos)
        val n = HashMap<Long, Int>().also { m -> duelos.forEach { d -> m[d.a] = (m[d.a] ?: 0) + 1; m[d.b] = (m[d.b] ?: 0) + 1 } }
        val hechos = duelos.map { clave(it.a, it.b) }.toSet() + saltados.map { clave(it.first, it.second) }
        val ord = notas.keys.sortedWith(compareByDescending<Long> { f[it] ?: 0.0 }.thenByDescending { notas[it] ?: 0 }.thenBy { it })
        var mejor: Pair<Long, Long>? = null
        var vm = -1.0
        for (i in ord.indices) {
            for (k in 1..6) {
                val j = i + k
                if (j >= ord.size) break
                val a = ord[i]; val b = ord[j]
                if (clave(a, b) in hechos) continue
                val p = sig((f[a] ?: 0.0) - (f[b] ?: 0.0))
                val incierto = 4 * p * (1 - p)
                val ua = 1.0 / (1 + (n[a] ?: 0)).toDouble().pow(1.2)
                val ub = 1.0 / (1 + (n[b] ?: 0)).toDouble().pow(1.2)
                var v = incierto * (ua + ub) / (1 + 0.15 * (k - 1))
                // Para no ver el mismo libro dos veces seguidas
                if (ultimo != null && (a == ultimo.first || a == ultimo.second || b == ultimo.first || b == ultimo.second)) v *= 0.35
                if (v > vm) { vm = v; mejor = a to b }
            }
        }
        return mejor
    }

    /** Cuánto está hecho (0–1): todos los libros con [OBJETIVO] duelos (los que más cuentan son los primeros de cada libro). */
    fun avance(notas: Map<Long, Int>, duelos: List<Duelo>): Float {
        if (notas.isEmpty()) return 1f
        val n = HashMap<Long, Int>().also { m -> duelos.forEach { d -> m[d.a] = (m[d.a] ?: 0) + 1; m[d.b] = (m[d.b] ?: 0) + 1 } }
        return notas.keys.sumOf { (n[it] ?: 0).coerceAtMost(OBJETIVO).toDouble() }.div(notas.size * OBJETIVO).toFloat().coerceIn(0f, 1f)
    }

    /** Cuántos duelos faltan, más o menos, para llegar al objetivo. */
    fun faltan(notas: Map<Long, Int>, duelos: List<Duelo>): Int = ((1f - avance(notas, duelos)) * notas.size * OBJETIVO / 2f).roundToInt()

    /**
     * Cómo se reparten las notas: la mejor [tope], la peor [suelo] y la [exigencia] (1 = reparto parejo; más: pocas
     * notas altas; menos: muchas).
     */
    data class Baremo(val tope: Int = 97, val suelo: Int = 40, val exigencia: Double = 1.25) {
        companion object {
            val EXIGENTE = Baremo(95, 30, 1.7)
            val EQUILIBRADO = Baremo(97, 40, 1.25)
            val GENEROSO = Baremo(99, 50, 0.85)
        }
    }

    /**
     * Las notas por orden (del mejor al peor). Si caben entre el suelo y el tope, no se repite ninguna; si hay más
     * libros que notas, se repiten lo mínimo (de menos a más arriba, nunca al revés).
     */
    fun notas(orden: List<Long>, b: Baremo): Map<Long, Int> {
        val n = orden.size
        if (n == 0) return emptyMap()
        val tope = b.tope.coerceIn(2, 100); val suelo = b.suelo.coerceIn(1, tope - 1)
        val caben = n <= tope - suelo + 1
        val out = LinkedHashMap<Long, Int>()
        var prev = Int.MAX_VALUE
        orden.forEachIndexed { i, id ->
            val p = if (n == 1) 0.0 else i / (n - 1).toDouble()
            val ideal = (suelo + (tope - suelo) * (1 - p).pow(b.exigencia)).roundToInt()
            val bajo = if (caben) suelo + (n - 1 - i) else suelo
            val alto = minOf(if (caben) tope - i else tope, if (caben && prev != Int.MAX_VALUE) prev - 1 else prev)
            val s = ideal.coerceIn(bajo, maxOf(bajo, alto))
            out[id] = s
            prev = s
        }
        return out
    }
}
