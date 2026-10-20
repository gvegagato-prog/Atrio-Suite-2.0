package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Por dónde vas en un libro: todos los dispositivos se ponen en lo más lejos que has llegado en cualquiera. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PosicionTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun remoto(id: Long, lejos: Double, updated: Long, extra: JSONObject = JSONObject()) =
        JSONObject().put("lectura", JSONArray().put(JSONObject().put("id", id).put("updated", updated)
            .put("s", JSONObject(extra.toString()).put("cfi", "cfi-$lejos").put("fraction", lejos).put("bestCfi", "cfi-$lejos").put("bestFrac", lejos))))

    private fun local(id: Long, actual: Double, lejos: Double) =
        BookFiles.put(ctx, id, JSONObject().put("cfi", "cfi-$actual").put("fraction", actual).put("bestCfi", "cfi-$lejos").put("bestFrac", lejos))

    @Test fun todosALoMasLejos() {
        val s = Store(ctx)
        val t = System.currentTimeMillis()
        // El dispositivo del trabajo se quedó en la 10 (el 10 %)
        local(7, 0.10, 0.10)
        // En el personal llegaste a la 120 (el 40 %): al juntar las cuentas, el del trabajo se pone ahí
        s.mergeRemote(remoto(7, 0.40, t - 60_000).toString())
        assertEquals(0.40, BookFiles.progress(ctx, 7).toDouble(), 0.0001)
        assertEquals("cfi-0.4", BookFiles.state(ctx, 7).getString("cfi"))
        // Lo del dispositivo de trabajo que cambia después (ajustes, hora más nueva) no echa atrás la posición
        BookFiles.put(ctx, 7, BookFiles.state(ctx, 7).put("settings", JSONObject().put("pdfFlow", "strip")))
        s.mergeRemote(remoto(7, 0.40, t - 60_000).toString())
        assertEquals(0.40, BookFiles.progress(ctx, 7).toDouble(), 0.0001)
        // Un dispositivo que solo abrió el libro por el principio (aunque lo haga más tarde) no echa atrás a nadie
        s.mergeRemote(remoto(7, 0.05, t + 500_000).toString())
        assertEquals(0.40, BookFiles.progress(ctx, 7).toDouble(), 0.0001)
        assertEquals(0.40, JSONObject(s.syncData()).getJSONArray("lectura").getJSONObject(0).getJSONObject("s").getDouble("bestFrac"), 0.0001)
        // Si luego en otro llegas más lejos (la 250: el 83 %), todos pasan a esa
        s.mergeRemote(remoto(7, 0.83, t + 600_000).toString())
        assertEquals(0.83, BookFiles.progress(ctx, 7).toDouble(), 0.0001)
        assertEquals("y el ajuste del libro se queda", "strip", BookFiles.state(ctx, 7).getJSONObject("settings").getString("pdfFlow"))
    }

    @Test fun irAUnSubrayadoAtrasNoCambiaLoMasLejos() {
        val s = Store(ctx)
        // Llegaste al 60 %, vas a ver un subrayado del 10 %: aquí «vas» por el 10 %, pero lo más lejos sigue en el 60 %
        local(8, 0.10, 0.60)
        s.mergeRemote(remoto(8, 0.30, System.currentTimeMillis()).toString())
        assertEquals("aquí sigues donde estás (el subrayado)", 0.10, BookFiles.progress(ctx, 8).toDouble(), 0.0001)
        assertEquals("y lo más lejos, en el 60 %", 0.60, BookFiles.state(ctx, 8).getDouble("bestFrac"), 0.0001)
    }
}
