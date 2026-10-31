package com.gonzalo.gamerate

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

/** Apariencia → A tu manera: el fondo, el texto y el acento a mano (y lo que sale de ellos). */
class ColoresPropiosTest {
    private val base = Tones(paper = 0xFFF3EEE4, crudo = 0xFFFAF6EE, ink = 0xFF15120F, general = 0xFFC8372D,
        gfx = 0xFF000001, story = 0xFF000002, music = 0xFF000003, gameplay = 0xFF000004, gold = 0xFF000005, line = 0xFF123456)

    @Test fun sinNadaQuedaIgual() = assertEquals(base, Skin.conPropios(base, 0L, 0L, 0L))

    @Test fun fondoOscuroPoneTextoClaro() {
        val t = Skin.conPropios(base, 0xFF204060, 0L, 0L)
        assertEquals(0xFF204060, t.paper)
        assertEquals(0xFFF1EBE0, t.ink)
        // las líneas salen de mezclar fondo y texto (no la línea de la paleta)
        assertEquals(0L, t.line)
        assertEquals(base.general, t.general)
    }

    @Test fun fondoClaroPoneTextoOscuro() = assertEquals(0xFF15120F, Skin.conPropios(base, 0xFFFFF0C0, 0L, 0L).ink)

    @Test fun textoYAcentoAMano() {
        val t = Skin.conPropios(base, 0L, 0xFF884422, 0xFF00AA55)
        assertEquals(base.paper, t.paper)
        assertEquals(0xFF884422, t.ink)
        assertEquals(0xFF00AA55, t.general)
        assertEquals(0xFF884422.toInt(), t.cInk.toArgb())
    }
}
