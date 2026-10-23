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

    @Test
    fun formasDeLaPalabra() {
        val c = Diccionario.candidates("acordarme")
        assertTrue(c.toString(), "acordar" in c)
        assertTrue(Diccionario.candidates("dándole").toString(), "dando" in Diccionario.candidates("dándole"))
        assertTrue("decir" in Diccionario.candidates("decírselo"))
        assertTrue("casa" in Diccionario.candidates("casas"))
        assertEquals("hidalgo", Diccionario.candidates("Hidalgo")[0])
    }

    @Test
    fun formasSeReconocen() {
        assertEquals("falda", Diccionario.lemmaDe("Forma del plural de falda."))
        assertEquals("amigo", Diccionario.lemmaDe("Forma del femenino plural de amigo."))
        assertEquals("amigar", Diccionario.lemmaDe("Segunda persona del singular (tú) del presente de indicativo de amigar."))
        assertEquals("bello", Diccionario.lemmaDe("Superlativo de bello."))
        assertEquals(null, Diccionario.lemmaDe("Prenda de vestir que cae desde la cintura."))
        assertEquals(null, Diccionario.lemmaDe("Forma de gobierno en la que manda uno solo."))
        assertEquals(null, Diccionario.lemmaDe("Forma de gobierno de Venecia."))
        assertEquals(null, Diccionario.lemmaDe("Primera parte de Fausto."))
        assertEquals("correr", Diccionario.lemmaDe("Primera persona del singular (yo) del pretérito imperfecto de indicativo de correr."))
        assertEquals("ojo", Diccionario.lemmaDe("Forma del plural de ojo."))
        assertEquals("poner", Diccionario.lemmaDe("Tercera persona del singular (él, ella, ello; usted) del pretérito perfecto simple de indicativo de poner."))
        assertEquals("dar", Diccionario.lemmaDe("Gerundio de dar."))
        assertEquals("gato", Diccionario.lemmaDe("Diminutivo de gato."))
    }

    @Test
    fun soloFormasSeDevuelvenSiNoHayOtraCosa() {
        val ex = "\n== Español ==\n=== Forma sustantiva femenina ===\n1\nForma del plural de falda.\n"
        assertEquals(listOf("Forma del plural de falda."), Diccionario.parse(ex))
    }
}
