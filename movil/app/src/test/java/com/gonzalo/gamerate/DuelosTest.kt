package com.gonzalo.gamerate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import com.gonzalo.gamerate.Duelos.Duelo

class DuelosTest {
    /** 120 libros con una calidad «verdadera» y estrellas redondas; ella contesta casi siempre bien (con algún despiste). */
    private fun simula(n: Int, despiste: Double, tope: Int): Triple<Map<Long, Int>, List<Duelo>, Map<Long, Double>> = run {
        val r = Random(7)
        val real = (1..n).associate { it.toLong() to r.nextDouble() * 100 }
        // Estrellas como en Bookmory: calidad → 20..100, con mucha gente apelotonada arriba
        val notas = real.mapValues { (_, q) -> ((q / 100.0).pow(0.5) * 5).coerceIn(1.0, 5.0).let { (it * 2).toInt() / 2.0 } .let { (it * 20).toInt() } }
        var duelos = emptyList<Duelo>()
        var ultimo: Pair<Long, Long>? = null
        repeat(tope) {
            val (a, b) = Duelos.siguiente(notas, duelos, emptySet(), ultimo) ?: return@repeat
            val gana = if ((real[a]!! > real[b]!!) xor (r.nextDouble() < despiste)) 1 else -1
            duelos = duelos + Duelo(a, b, gana); ultimo = a to b
        }
        Triple(notas, duelos, real)
    }
    private fun Double.pow(e: Double) = Math.pow(this, e)

    private fun spearman(real: Map<Long, Double>, orden: List<Long>): Double {
        val rr = real.keys.sortedByDescending { real[it] }.withIndex().associate { it.value to it.index }
        val n = orden.size
        val d2 = orden.withIndex().sumOf { (i, id) -> ((rr[id]!! - i).toDouble()).let { it * it } }
        return 1 - 6 * d2 / (n.toDouble() * (n * n - 1))
    }

    @Test fun ordenaConPocosDuelos() {
        val n = 120
        val (notas, duelos, real) = simula(n, 0.05, n * Duelos.OBJETIVO / 2)
        val orden = Duelos.orden(notas, duelos)
        val rho = spearman(real, orden)
        // Lo que importa: libros con la misma nota de estrellas, ¿los pone en su orden verdadero?
        val pos = orden.withIndex().associate { it.value to it.index }
        var ok = 0; var tot = 0
        for (x in notas.keys) for (y in notas.keys) if (x < y && notas[x] == notas[y]) { tot++; if ((real[x]!! > real[y]!!) == (pos[x]!! < pos[y]!!)) ok++ }
        val acierto = ok.toDouble() / tot
        println("DUELOS ${duelos.size} para $n libros: orden global ${"%.3f".format(rho)}, parejas con la misma nota bien ordenadas ${"%.1f".format(acierto * 100)} % de $tot (las estrellas solas: 50 %)")
        assertTrue("dentro de cada nota acierta bastante: $acierto", acierto >= 0.78)
        assertTrue("y el orden global es bueno: $rho", rho >= 0.97)
        assertTrue("avance al objetivo", Duelos.avance(notas, duelos) >= 0.9f)
    }

    @Test fun noPreguntaDosVecesLoMismoNiSaltados() {
        val notas = (1L..6L).associateWith { 80 }
        var duelos = emptyList<Duelo>()
        val vistos = HashSet<Pair<Long, Long>>()
        var ultimo: Pair<Long, Long>? = null
        repeat(15) {
            val p = Duelos.siguiente(notas, duelos, emptySet(), ultimo) ?: return@repeat
            assertTrue("repetido $p", vistos.add(minOf(p.first, p.second) to maxOf(p.first, p.second)))
            duelos = duelos + Duelo(p.first, p.second, 1); ultimo = p
        }
        val saltado = setOf(1L to 2L)
        val p = Duelos.siguiente(mapOf(1L to 80, 2L to 80), emptyList(), saltado)
        assertEquals(null, p)
    }

    @Test fun elIgualNoMueve() {
        val notas = mapOf(1L to 100, 2L to 100)
        val f = Duelos.fuerzas(notas, listOf(Duelo(1, 2, 0), Duelo(1, 2, 0)))
        assertEquals(f[1]!!, f[2]!!, 1e-6)
    }

    @Test fun ganarSube() {
        val notas = mapOf(1L to 90, 2L to 90, 3L to 90)
        val o = Duelos.orden(notas, listOf(Duelo(3, 1, 1), Duelo(3, 2, 1), Duelo(2, 1, 1)))
        assertEquals(listOf(3L, 2L, 1L), o)
    }

    @Test fun baremoSinEmpates() {
        val orden = (1L..40L).toList()
        for (b in listOf(Duelos.Baremo.EXIGENTE, Duelos.Baremo.EQUILIBRADO, Duelos.Baremo.GENEROSO)) {
            val nt = Duelos.notas(orden, b)
            val v = orden.map { nt[it]!! }
            assertEquals("sin empates con $b: $v", 40, v.toSet().size)
            assertTrue("decrece", v.zipWithNext().all { (x, y) -> x > y })
            assertEquals(b.tope.coerceAtMost(100), v.first().coerceAtMost(b.tope))
            assertTrue("dentro de [suelo,tope]", v.all { it in b.suelo..b.tope })
        }
    }

    @Test fun baremoConMasLibrosQueNotas() {
        val orden = (1L..200L).toList()
        val nt = Duelos.notas(orden, Duelos.Baremo(95, 40, 1.2))
        val v = orden.map { nt[it]!! }
        assertTrue("nunca sube", v.zipWithNext().all { (x, y) -> x >= y })
        assertTrue(v.all { it in 40..95 })
        assertTrue("no más de 5 con la misma nota: ${v.groupBy { it }.maxOf { it.value.size }}", v.groupBy { it }.maxOf { it.value.size } <= 6)
    }

    @Test fun exigenciaBajaLaMedia() {
        val orden = (1L..60L).toList()
        val ex = Duelos.notas(orden, Duelos.Baremo.EXIGENTE).values.average()
        val ge = Duelos.notas(orden, Duelos.Baremo.GENEROSO).values.average()
        assertTrue("$ex < $ge", ex < ge - 10)
    }
}
