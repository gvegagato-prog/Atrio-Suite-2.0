package com.gonzalo.gamerate

import org.junit.Assert.assertEquals
import org.junit.Test

/** El brillo del lector, en la escala de la barra de Android (curva HLG de AOSP): el mínimo es el mínimo de verdad. */
class BrilloTest {
    @Test
    fun curva() {
        assertEquals(BRILLO_MINIMO, brilloLineal(0f), 0f)      // del todo a la izquierda: lo más bajo de la pantalla (sin apagarla)
        assertEquals(0.25f / 12f, brilloLineal(0.25f), 1e-4f)  // un cuarto de barra ≈ 2 % de luz (lo que antes era el mínimo)
        assertEquals(1f / 12f, brilloLineal(0.5f), 1e-4f)
        assertEquals(1f, brilloLineal(1f), 1e-3f)
        assertEquals(BRILLO_MINIMO, brilloLineal(-1f), 0f)
    }

    /** De luz real a la barra y vuelta: el brillo que tiene el móvil se ve en la barra en su sitio. */
    @Test
    fun vuelta() {
        for (x in listOf(0.15f, 0.25f, 0.4f, 0.5f, 0.6f, 0.75f, 0.9f, 1f)) assertEquals("barra $x", x, brilloBarra(brilloLineal(x)), 2e-3f)
        assertEquals(0f, brilloBarra(0f), 0f)
        assertEquals(1f, brilloBarra(1f), 1e-3f)
    }
}
