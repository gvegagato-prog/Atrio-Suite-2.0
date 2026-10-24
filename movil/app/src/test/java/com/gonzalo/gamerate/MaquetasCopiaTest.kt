package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Copiar la página de un libro de la saga a los demás: casillas, fotos y encuadre, con deshacer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaquetasCopiaTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val mp get() = ctx.getSharedPreferences("maqueta", Context.MODE_PRIVATE)

    @Test fun copiarYDeshacer() {
        val foto = MagCell("imagen", image = "file:///data/covers/foto_1.jpg", zoom = 1.6f, ox = 0.1f, oy = -0.2f, shape = "vertical")
        MagLayouts.save(ctx, "v:1:10", listOf(MagRowSpec(listOf(foto, MagCell("nota")), 0.4f, 220f)))
        MagLayouts.save(ctx, "p:1:10", listOf(MagRowSpec(listOf(MagCell("guias")))))
        val propia = listOf(MagRowSpec(listOf(MagCell("texto", title = "Mía", text = "No me la toques"))))
        MagLayouts.save(ctx, "v:1:12", propia)
        val vOrigen = mp.getString("v:1:10", null)!!; val pOrigen = mp.getString("p:1:10", null)!!
        val viejo12 = mp.getString("v:1:12", null)!!

        // A los sin página: el 12 ya tenía, no se toca; el 11 recibe las dos
        assertEquals(1, MagLayouts.copiar(ctx, 1, 10, listOf(10, 11, 12), soloSinDiseno = true, undo = "saga:1"))
        assertEquals(vOrigen, mp.getString("v:1:11", null)); assertEquals(pOrigen, mp.getString("p:1:11", null))
        assertEquals(viejo12, mp.getString("v:1:12", null))
        assertTrue("el encuadre y la foto viajan tal cual", mp.getString("v:1:11", "")!!.contains("foto_1.jpg") && mp.getString("v:1:11", "")!!.contains("\"z\":1.6"))
        assertTrue(MagLayouts.hayCopia(ctx, "saga:1"))

        // A todos: el 12 también cambia, y se puede devolver
        assertEquals(2, MagLayouts.copiar(ctx, 1, 10, listOf(10, 11, 12), soloSinDiseno = false, undo = "saga:1"))
        assertEquals(vOrigen, mp.getString("v:1:12", null))
        MagLayouts.deshacerCopia(ctx, "saga:1")
        assertEquals("el 12 recupera la suya", viejo12, mp.getString("v:1:12", null))
        assertEquals("el origen no cambia", vOrigen, mp.getString("v:1:10", null))
        assertTrue(!MagLayouts.hayCopia(ctx, "saga:1"))

        // Viaja con la cuenta
        val data = Store(ctx).syncData()
        assertTrue(data.contains("v:1:11") && data.contains("p:1:11"))
    }

    @Test fun origenSinPaginaYSoloUnaDeLasDos() {
        assertEquals(-1, MagLayouts.copiar(ctx, 1, 50, listOf(50, 51), false, "saga:2"))
        MagLayouts.save(ctx, "v:1:50", listOf(MagRowSpec(listOf(MagCell("nota")))))
        assertEquals(1, MagLayouts.copiar(ctx, 1, 50, listOf(50, 51), false, "saga:2"))
        assertEquals("con solo una, vale para las dos", mp.getString("v:1:50", null), mp.getString("p:1:51", null))
        MagLayouts.deshacerCopia(ctx, "saga:2")
        assertNull(mp.getString("p:1:51", null)); assertNull(mp.getString("v:1:51", null))
    }
}
