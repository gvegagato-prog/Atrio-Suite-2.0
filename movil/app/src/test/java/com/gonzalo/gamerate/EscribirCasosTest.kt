package com.gonzalo.gamerate

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Tipografía automática y sinónimos con los casos de herramientas/casos (los mismos que prueba el PC). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EscribirCasosTest {
    private fun raiz(): File {
        var dir = File("").absoluteFile
        while (!File(dir, "herramientas/casos").exists()) dir = dir.parentFile ?: error("no encuentro herramientas/casos")
        return dir
    }

    @Test fun tipografia() {
        val a = JSONObject(File(raiz(), "herramientas/casos/tipografia.json").readText()).getJSONArray("casos")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            val r = Tipografia.aplica(c.getString("texto"), c.getInt("cursor"), c.getString("ch")[0])
            if (c.isNull("esperado")) assertNull(c.getString("nombre"), r)
            else {
                assertEquals(c.getString("nombre"), c.getString("esperado"), r?.first)
                assertEquals(c.getString("nombre") + " (cursor)", c.getInt("cursorEsperado"), r?.second)
            }
        }
    }

    @Test fun laTipografiaSeDeshaceSola() {
        // «--» se vuelve «—», y deshacer vuelve a los dos guiones (no borra todo lo escrito)
        val h = HistorialTexto(TextFieldValue("Hola", TextRange(4)))
        h.apunta(TextFieldValue("Hola -", TextRange(6)), 1000)
        h.apunta(TextFieldValue("Hola --", TextRange(7)), 1100)
        h.corte()
        h.apunta(TextFieldValue("Hola —", TextRange(6)), 1100)
        h.corte()
        assertEquals("Hola --", h.deshace()?.text)
        assertEquals("Hola", h.deshace()?.text)
    }

    @Test fun sinonimos() {
        val n = Sinonimos.cargaTexto(String(File(raiz(), "movil/app/src/main/assets/sinonimos/th_es_v2.dat").readBytes(), Charsets.ISO_8859_1))
        assertTrue("el tesauro tiene más de 20.000 palabras ($n)", n > 20_000)
        val a = JSONObject(File(raiz(), "herramientas/casos/sinonimos.json").readText()).getJSONArray("casos")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            val esperado = c.getJSONArray("grupos").let { g -> (0 until g.length()).map { j -> g.getJSONArray(j).let { x -> (0 until x.length()).map { x.getString(it) } } } }
            val r = Sinonimos.busca(c.getString("palabra"))
            assertEquals(c.getString("palabra"), esperado, r.take(esperado.size))
            if (esperado.isEmpty()) assertTrue(r.isEmpty())
        }
        assertEquals("miró", Sinonimos.palabraEn("Ella miró la casa.", 7, 7)?.let { "Ella miró la casa.".substring(it.first, it.last + 1) })
        assertEquals("casa", Sinonimos.palabraEn("Ella miró la casa.", 13, 17)?.let { "Ella miró la casa.".substring(it.first, it.last + 1) })
        assertNull(Sinonimos.palabraEn("Ella  miró", 5, 5))
    }
}
