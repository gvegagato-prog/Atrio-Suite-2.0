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

/** La maqueta de cada ficha (casillas, fotos) viaja con la cuenta entre tus dispositivos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MaquetasSyncTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val mp get() = ctx.getSharedPreferences("maqueta", Context.MODE_PRIVATE)
    private val g get() = ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE)

    @Test fun guardarViajaYRestablecerBorra() {
        val s = Store(ctx)
        MagLayouts.save(ctx, "p:1:42", listOf(MagRowSpec(listOf(MagCell("imagen", image = "file:///x/covers/foto_1.jpg")))))
        val j = mp.getString("p:1:42", null)!!
        MagLayouts.saveTemplate(ctx, "tpl:v:0", listOf(MagRowSpec(listOf(MagCell("nota")))))
        val plantilla = mp.getString("tpl:v:0", null)!!
        val data = s.syncData()
        assertTrue("viaja la maqueta de la ficha", data.contains("p:1:42"))

        // «Otro dispositivo»: sin nada, recibe lo de la cuenta
        mp.edit().clear().commit(); g.edit().clear().commit()
        s.mergeRemote(data)
        assertEquals(j, mp.getString("p:1:42", null))
        assertEquals(plantilla, mp.getString("tpl:v:0", null))

        // Una maqueta más nueva en la cuenta pisa la local; la más vieja no
        val nueva = "[{\"c\":[{\"t\":\"t\",\"h\":\"hola\"}]}]"
        val otro = org.json.JSONObject(data)
        val arr = otro.getJSONArray("maquetas")
        for (i in 0 until arr.length()) arr.getJSONObject(i).takeIf { it.getString("k") == "p:1:42" }?.put("j", nueva)?.put("updated", System.currentTimeMillis() + 60_000)
        s.mergeRemote(otro.toString())
        assertEquals(nueva, mp.getString("p:1:42", null))
        val vieja = org.json.JSONObject(otro.toString())
        val va = vieja.getJSONArray("maquetas")
        for (i in 0 until va.length()) va.getJSONObject(i).put("j", "VIEJA").put("updated", 1L)
        s.mergeRemote(vieja.toString())
        assertEquals("lo viejo no pisa lo nuevo", nueva, mp.getString("p:1:42", null))

        // Restablecer aquí la quita también de la cuenta
        MagLayouts.reset(ctx, "p:1:42", "tpl:p:1")
        assertTrue("ya no viaja", !s.syncData().contains("\"k\":\"p:1:42\""))
        assertNull(mp.getString("p:1:42", null))
    }
}
