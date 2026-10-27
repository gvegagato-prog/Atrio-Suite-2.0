package com.gonzalo.gamerate

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El formato al escribir: pintar el Markdown ligero, los botones del formato y deshacer/rehacer. */
class EscribirTextoTest {
    private fun v(t: String, a: Int = t.length, b: Int = a) = TextFieldValue(t, TextRange(a, b))

    @Test fun envuelve() {
        assertEquals(v("hola **mundo** ya", 7, 12), EdicionTexto.envuelve(v("hola mundo ya", 5, 10), "**"))
        // Quitarla: dentro y alrededor
        assertEquals("hola mundo ya", EdicionTexto.envuelve(v("hola **mundo** ya", 7, 12), "**").text)
        assertEquals("hola mundo ya", EdicionTexto.envuelve(v("hola **mundo** ya", 5, 14), "**").text)
        // Sin selección: el par y el cursor en medio
        val vacio = EdicionTexto.envuelve(v("a  b", 2), "**")
        assertEquals("a **** b", vacio.text); assertEquals(4, vacio.selection.start)
        // La cursiva no confunde la negrita con su marca
        assertEquals("*a* **b**", EdicionTexto.envuelve(v("*a* b", 4, 5), "**").text)
        assertEquals("*hola*", EdicionTexto.envuelve(v("hola", 0, 4), "*").text)
        assertEquals("negrita y cursiva a la vez", "***hola***", EdicionTexto.envuelve(v("**hola**", 2, 6), "*").text)
        assertEquals("y se quita solo la cursiva", "**hola**", EdicionTexto.envuelve(v("***hola***", 3, 7), "*").text)
    }

    @Test fun lineas() {
        assertEquals("# Capítulo\ntexto", EdicionTexto.prefijoLinea(v("Capítulo\ntexto", 3), "# ").text)
        assertEquals("Capítulo\ntexto", EdicionTexto.prefijoLinea(v("# Capítulo\ntexto", 5), "# ").text)
        // Cambiar un título por otro y una cita por un título
        assertEquals("## Capítulo", EdicionTexto.prefijoLinea(v("# Capítulo", 4), "## ").text)
        assertEquals("# dicho", EdicionTexto.prefijoLinea(v("> dicho", 3), "# ").text)
        // Varias líneas elegidas
        assertEquals("> uno\n> dos\ntres", EdicionTexto.prefijoLinea(v("uno\ndos\ntres", 1, 6), "> ").text)
        // Línea del medio
        assertEquals("uno\n> dos\ntres", EdicionTexto.prefijoLinea(v("uno\ndos\ntres", 5), "> ").text)
    }

    @Test fun saltoYComillas() {
        assertEquals("fin\n\n***\n\nOtra", EdicionTexto.salto(v("fin\nOtra", 4)).text)
        assertEquals("***\n\nOtra", EdicionTexto.salto(v("Otra", 0)).text)
        assertEquals("«dicho»", EdicionTexto.comillas(v("dicho", 0, 5)).text)
        assertEquals(1, EdicionTexto.comillas(v("", 0)).selection.start)
        assertEquals("hola — adiós", EdicionTexto.inserta(v("hola  adiós", 5), "—").text)
    }

    @Test fun pintaElFormato() {
        val a = AspectoTexto(Color.Black, Color.Gray, Color.Red, FontFamily.Serif, 18f)
        val f = FormatoMarkdown(a, Regex("\\bAldara\\b"), Regex("torre", RegexOption.IGNORE_CASE))
        val t = "# Título\nAldara subió la **torre** y *miró*.\n***\n> dicho"
        val s = f.estilo(t)
        assertEquals(t, s.text)
        fun en(i: Int) = s.spanStyles.filter { i >= it.start && i < it.end }.map { it.item }
        assertTrue("el título, en grande y negrita", en(3).any { it.fontWeight == FontWeight.Bold && it.fontSize.value > 18f })
        assertTrue("la negrita", en(t.indexOf("orre")).any { it.fontWeight == FontWeight.Bold })
        assertTrue("la cursiva", en(t.indexOf("miró") + 1).any { it.fontStyle == FontStyle.Italic })
        assertTrue("el signo del formato, apagado", en(t.indexOf("**torre")).any { it.color == Color.Gray })
        assertTrue("la ficha, subrayada", en(t.indexOf("Aldara")).any { it.textDecoration != null })
        assertTrue("lo buscado, resaltado", en(t.indexOf("torre") + 1).any { it.background != Color.Unspecified })
        assertEquals("la cita, en cursiva", true, en(t.indexOf("dicho")).any { it.fontStyle == FontStyle.Italic })
        assertTrue("el salto de escena, centrado", s.paragraphStyles.isNotEmpty())
    }

    @Test fun deshacerYRehacer() {
        val h = HistorialTexto(v(""))
        h.apunta(v("Hola"), 0, 900); h.apunta(v("Hola m"), 100, 900); h.apunta(v("Hola mu"), 200, 900)   // muy seguidos: un solo paso
        h.apunta(v("Hola mundo, esto sigue después de una pausa"), 5_000, 900)
        assertEquals("Hola mu", h.deshace()!!.text)
        assertEquals("", h.deshace()!!.text)
        assertNull(h.deshace())
        assertEquals("Hola mu", h.rehace()!!.text)
        h.apunta(v("otra cosa"), 20_000, 900)
        assertFalse("escribir borra lo que se podía rehacer", h.puedeRehacer)
    }
}
