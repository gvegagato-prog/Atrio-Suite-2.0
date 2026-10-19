package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tus ajustes viajan con la cuenta; la maqueta de cada ficha y el brillo del lector se quedan en cada dispositivo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AjustesSyncTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun ajustesSiMaquetaNo() {
        val s = Store(ctx)
        // En el móvil: portadas en las listas, oscuro, letra del lector al 120 % con brillo 0.3, y una maqueta
        Skin.load(ctx); Skin.listCovers = true; Skin.save(ctx)
        s.themeMode = 2
        ctx.getSharedPreferences("lector", Context.MODE_PRIVATE).edit().putString("ajustes", JSONObject().put("theme", "negro").put("size", 120).put("brightness", 0.3).toString()).commit()
        ajustesCambiados(ctx)
        ctx.getSharedPreferences("maqueta", Context.MODE_PRIVATE).edit().putString("v:0:1", "[{\"c\":[]}]").commit()
        BookFiles.put(ctx, 7, JSONObject().put("fraction", 0.5).put("settings", JSONObject().put("size", 120).put("brightness", 0.3).put("pdfFlow", "strip")))
        val data = s.syncData()
        assertTrue("la maqueta no viaja", !data.contains("v:0:1"))
        assertTrue("el brillo no viaja", !data.contains("\"brightness\""))
        assertTrue("la letra no viaja", !data.contains("\"size\":120"))

        // La tablet: otra cosa, con su brillo y su maqueta; llega lo del móvil (más nuevo)
        val g = ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE)
        g.edit().putBoolean("skin_listcovers", false).putInt("theme31", 1).putLong("ajustesAt", 1L).commit()
        ctx.getSharedPreferences("lector", Context.MODE_PRIVATE).edit().putString("ajustes", JSONObject().put("theme", "papel").put("brightness", 0.8).put("size", 90).toString()).commit()
        ctx.getSharedPreferences("maqueta", Context.MODE_PRIVATE).edit().putString("v:0:1", "TABLETA").commit()
        BookFiles.putQuiet(ctx, 7, JSONObject().put("settings", JSONObject().put("brightness", 0.8).put("size", 90)).put("syncAt", 1L))
        s.mergeRemote(data)
        assertEquals(true, g.getBoolean("skin_listcovers", false))
        assertEquals(2, g.getInt("theme31", 0))
        val l = JSONObject(ctx.getSharedPreferences("lector", Context.MODE_PRIVATE).getString("ajustes", "{}")!!)
        assertEquals("negro", l.getString("theme")); assertEquals("la letra de la tablet se queda", 90, l.getInt("size"))
        assertEquals("el brillo de la tablet se queda", 0.8, l.getDouble("brightness"), 0.0)
        assertEquals("y en el libro, también", 0.8, BookFiles.state(ctx, 7).getJSONObject("settings").getDouble("brightness"), 0.0)
        assertEquals("y su letra", 90, BookFiles.state(ctx, 7).getJSONObject("settings").getInt("size"))
        assertEquals("lo demás del libro sí viaja (este, hacia abajo)", "strip", BookFiles.state(ctx, 7).getJSONObject("settings").getString("pdfFlow"))
        assertEquals("la maqueta de la tablet se queda", "TABLETA", ctx.getSharedPreferences("maqueta", Context.MODE_PRIVATE).getString("v:0:1", ""))
        // Un dispositivo nuevo que entra con tu cuenta (se queda con lo de la cuenta): también por dónde vas y los ajustes
        ctx.getSharedPreferences("lector", Context.MODE_PRIVATE).edit().clear().commit()
        g.edit().putLong("ajustesAt", 0L).putBoolean("skin_listcovers", false).commit()
        s.importSync(data)
        assertEquals("por dónde vas", 0.5, BookFiles.progress(ctx, 7).toDouble(), 0.001)
        assertEquals("y tus ajustes", true, g.getBoolean("skin_listcovers", false))
        // Lo viejo no pisa lo nuevo
        g.edit().putLong("ajustesAt", System.currentTimeMillis() + 60_000).putBoolean("skin_listcovers", false).commit()
        s.mergeRemote(data)
        assertEquals(false, g.getBoolean("skin_listcovers", true))
    }
}
