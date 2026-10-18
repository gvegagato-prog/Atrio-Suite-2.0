package com.gonzalo.gamerate

import org.junit.Assert.assertEquals
import org.junit.Test

/** El brillo del lector, en la escala de la barra de Android (curva HLG de AOSP): el mínimo es el mínimo de verdad. */
class BrilloTest {
    @Test
    fun curva() {
        assertEquals(0f, brilloLineal(0f), 0f)                 // del todo a la izquierda: lo más bajo de la pantalla
        assertEquals(0.25f / 12f, brilloLineal(0.25f), 1e-4f)  // un cuarto de barra ≈ 2 % de luz (lo que antes era el mínimo)
        assertEquals(1f / 12f, brilloLineal(0.5f), 1e-4f)
        assertEquals(1f, brilloLineal(1f), 1e-3f)
        assertEquals(0f, brilloLineal(-1f), 0f)
    }
}
