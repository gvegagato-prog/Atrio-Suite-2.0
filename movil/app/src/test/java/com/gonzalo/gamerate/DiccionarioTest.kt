package com.gonzalo.gamerate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La lectura de las acepciones del Wikcionario (texto plano de la API, con la estructura que tiene). */
class DiccionarioTest {
    private val casa = """
== Español ==
=== Etimología 1 ===
Del latín casa.
==== Sustantivo femenino ====
casa ¦ plural: casas
1
Edificio para habitar.
2 Arquitectura
Edificio de una o pocas plantas, por oposición al piso.
3 Conjunto de personas que viven juntas.
Sinónimos: hogar, morada.
4 Juegos
Casilla del tablero.
5 Quinta acepción que ya no se coge.
== Aragonés ==
==== Sustantivo femenino ====
1
Edificio (en aragonés).
""".trimIndent()

    @Test
    fun acepcionesDelEspanol() {
        val d = Diccionario.parse(casa)
        assertEquals(4, d.size)
        assertEquals("Edificio para habitar.", d[0])
        assertEquals("(Arquitectura) Edificio de una o pocas plantas, por oposición al piso.", d[1])
        assertEquals("Conjunto de personas que viven juntas.", d[2])
        assertEquals("(Juegos) Casilla del tablero.", d[3])
    }

    @Test
    fun otraLengua() {
        val d = Diccionario.parse(casa, "Aragonés")
        assertEquals(listOf("Edificio (en aragonés)."), d)
    }

    @Test
    fun sinSeccion() {
        assertTrue(Diccionario.parse("nada aquí").isEmpty())
    }
}
