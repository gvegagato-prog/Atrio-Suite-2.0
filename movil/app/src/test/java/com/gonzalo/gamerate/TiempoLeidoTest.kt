package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El tiempo leído de un libro se suma entre dispositivos: el de uno nunca pisa al del otro. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TiempoLeidoTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val min = 60_000L

    private fun cubo(total: Long, vararg dias: Pair<String, Long>) = JSONObject().put("total", total).put("days", JSONObject().apply { dias.forEach { put(it.first, it.second) } })
    private fun stats(vararg d: Pair<String, JSONObject>) = ReadStats.derived(JSONObject().apply { d.forEach { put(it.first, it.second) } })

    /** Lo que llega de «otro dispositivo» con su tiempo. */
    private fun remoto(id: Long, st: JSONObject, updated: Long) = JSONObject().put("lectura", JSONArray().put(JSONObject().put("id", id).put("updated", updated)
        .put("s", JSONObject().put("stats", st).put("cfi", "c").put("fraction", 0.5))))

    @Test fun sumaDeDosDispositivos() {
        val s = Store(ctx)
        val yo = s.deviceId
        val t = System.currentTimeMillis()
        // Aquí leíste 10 min
        BookFiles.put(ctx, 7, JSONObject().put("cfi", "c").put("fraction", 0.5).put("stats", stats(yo to cubo(10 * min, "2026-10-19" to 10 * min))))
        assertEquals(10 * min, ReadStats.total(BookFiles.state(ctx, 7).optJSONObject("stats")))
        // En la tablet, 20 min: al juntar, 30 en los dos
        val tablet = stats("tablet" to cubo(20 * min, "2026-10-19" to 15 * min, "2026-10-20" to 5 * min))
        s.mergeRemote(remoto(7, tablet, t + 1000).toString())
        var st = BookFiles.state(ctx, 7).getJSONObject("stats")
        assertEquals("10 + 20", 30 * min, st.getLong("total"))
        assertEquals("por días también", 25 * min, st.getJSONObject("days").getLong("2026-10-19"))
        // Sigues leyendo aquí (+5, como lo manda el lector: solo su cubo, sin lo de la tablet): 35
        val lector = stats(yo to cubo(15 * min, "2026-10-19" to 15 * min))
        BookFiles.put(ctx, 7, BookFiles.state(ctx, 7).put("stats", ReadStats.merge(BookFiles.state(ctx, 7).getJSONObject("stats"), lector, yo, yo)))
        assertEquals("35: lo de la tablet no se pierde", 35 * min, BookFiles.state(ctx, 7).getJSONObject("stats").getLong("total"))
        // Llega otra vez lo mismo de la tablet (y una copia vieja de la mía): no suma de más
        s.mergeRemote(remoto(7, tablet, t + 2000).toString())
        s.mergeRemote(remoto(7, stats(yo to cubo(10 * min)), t + 3000).toString())
        assertEquals(35 * min, BookFiles.state(ctx, 7).getJSONObject("stats").getLong("total"))
        // Lo que viaja a la cuenta lleva los dos
        st = JSONObject(s.syncData()).getJSONArray("lectura").getJSONObject(0).getJSONObject("s").getJSONObject("stats")
        assertEquals(35 * min, st.getLong("total"))
        assertEquals(setOf(yo, "tablet"), st.getJSONObject("dev").keys().asSequence().toSet())
        // La tablet sigue leyendo (25) y lo manda: aquí pasan a 40
        s.mergeRemote(remoto(7, stats("tablet" to cubo(25 * min, "2026-10-19" to 15 * min, "2026-10-20" to 10 * min)), t + 4000).toString())
        assertEquals(40 * min, BookFiles.state(ctx, 7).getJSONObject("stats").getLong("total"))
    }

    @Test fun tiempoAntiguoSinRepartir() {
        val s = Store(ctx)
        val yo = s.deviceId
        // Lo de antes (un solo total): pasa a ser de este dispositivo y se suma con el de la tablet
        BookFiles.put(ctx, 8, JSONObject().put("cfi", "c").put("fraction", 0.1).put("stats", JSONObject().put("total", 12 * min).put("days", JSONObject().put("2026-10-18", 12 * min))))
        s.mergeRemote(remoto(8, stats("tablet" to cubo(8 * min, "2026-10-19" to 8 * min)), System.currentTimeMillis() + 1000).toString())
        assertEquals(20 * min, BookFiles.state(ctx, 8).getJSONObject("stats").getLong("total"))
        assertTrue(BookFiles.state(ctx, 8).getJSONObject("stats").getJSONObject("dev").has(yo))
    }

    @Test fun fusionEsIdempotenteYConmutativa() {
        val a = stats("A" to cubo(10 * min, "d1" to 10 * min))
        val b = stats("B" to cubo(5 * min, "d1" to 2 * min, "d2" to 3 * min), "A" to cubo(4 * min, "d1" to 4 * min))
        val ab = ReadStats.merge(a, b, "x", "x"); val ba = ReadStats.merge(b, a, "x", "x")
        assertEquals(15 * min, ab.getLong("total"))   // A: lo más alto (10), B: 5
        assertEquals(ab.getLong("total"), ba.getLong("total"))
        assertEquals(ab.getLong("total"), ReadStats.merge(ab, b, "x", "x").getLong("total"))
        assertEquals(12 * min, ab.getJSONObject("days").getLong("d1"))
    }
}
