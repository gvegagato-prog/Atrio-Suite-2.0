package com.gonzalo.gamerate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/** El ISBN del código de barras (o escrito a mano) busca la obra en Open Library. */
class IsbnTest {
    @Test fun validos() {
        assertEquals("9780441013593", Isbn.limpia("978-0-441-01359-3"))   // Dune
        assertEquals("0441013597", Isbn.limpia("0-441-01359-7"))
        assertEquals("080442957X", Isbn.limpia("0-8044-2957-x"))         // con X de control
        assertNull(Isbn.limpia("9780441013594"))                          // control mal
        assertNull(Isbn.limpia("4006381333931"))                          // un EAN de otra cosa (no 978/979)
        assertNull(Isbn.limpia("Dune"))
        assertNull(Isbn.limpia("1984"))                                   // un título de números no es un ISBN
    }

    /** De verdad contra Open Library (si hay conexión): el ISBN de Dune da Dune. */
    @Test fun openLibrary() = runBlocking {
        val r = try { OpenLib.porIsbn("9780441013593") } catch (e: Exception) { null }
        assumeTrue("sin conexión con Open Library", r != null)
        assertEquals(893414L, r!!.first().id)
    }
}
