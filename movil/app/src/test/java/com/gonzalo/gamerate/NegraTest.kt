package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** La lista negra de juegos y el mínimo de 5 minutos seguidos para que un día cuente. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NegraTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val g get() = ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE)

    @Test fun listaNegraBorraSuFichaYViaja() {
        g.edit().clear().commit()
        val s = Store(ctx)
        s.save(listOf(Game(1, "Wallpaper Engine", "", 50), Game(2, "Hades II", "", 91)), 0)
        s.savePlays(listOf(Play(3, "wallpaper  engine", "", "", 1, emptyList()), Play(4, "Deadlock", "", "", 1, emptyList())), 0)
        s.addNegra("Wallpaper Engine")
        assertEquals(listOf("Hades II"), s.load(0).map { it.name })
        assertEquals(listOf("Deadlock"), s.loadPlays(0).map { it.name })
        assertTrue(Negra.vetado("WALLPAPER ENGINE")); assertFalse(Negra.vetado("Hades II"))
        val data = s.syncData()
        assertEquals(1, JSONObject(data).getJSONArray("negra").length())
        // Otro dispositivo que aún tenía la ficha: al recibir la lista negra, se va
        g.edit().clear().commit()
        s.save(listOf(Game(1, "Wallpaper Engine", "", 50)), 0)
        Negra.poner(emptyList())
        s.mergeRemote(data)
        assertTrue(Negra.vetado("Wallpaper Engine"))
        assertEquals("Wallpaper se fue; llega lo demás", listOf("Hades II"), s.load(0).map { it.name })
        // Quitarla de la lista deja que vuelva a salir
        s.removeNegra(s.loadNegra().first())
        assertFalse(Negra.vetado("Wallpaper Engine"))
    }

    @Test fun elDiarioNoLaEnsena() {
        Negra.poner(listOf(Vetado(1, "Wallpaper Engine")))
        val zona = ZoneId.of("Europe/Madrid")
        val hoy = LocalDate.of(2026, 10, 26)
        val ms = { h: Int -> hoy.atTime(h, 0).atZone(zona).toInstant().toEpochMilli() }
        val dias = diasDelDiario(FuentesDiario(
            listOf(Triple(GAMES, listOf(Game(1, "Wallpaper Engine", "", 50, end = hoy.toEpochDay())), emptyList())),
            jugado = mapOf(hoy.toEpochDay() to mapOf(JuegoPc(0, "Wallpaper Engine") to 3_600_000L, JuegoPc(0, "Hades II") to 3_600_000L)),
            capturas = listOf(Shot("s", "pc", 1, 1, "image/png", 1, ms(10), "Wallpaper Engine", 0))
        ), zona)
        assertEquals(listOf("Hades II"), dias.flatMap { it.toques }.map { it.nombre })
        Negra.poner(emptyList())
    }

    @Test fun cincoMinutosSeguidos() {
        val d = "2026-10-26"
        val st = { total: Long, mejor: Long? -> JSONObject().put("days", JSONObject().put(d, total)).apply { if (mejor != null) put("best", JSONObject().put(d, mejor)) } }
        assertFalse("40 min en ratitos", ReadStats.cuenta(st(40 * 60_000L, 2 * 60_000L), d))
        assertTrue(ReadStats.cuenta(st(6 * 60_000L, 5 * 60_000L), d))
        assertFalse("un día antiguo sin racha: vale el total, y 4 min no llega", ReadStats.cuenta(st(4 * 60_000L, null), d))
        assertTrue(ReadStats.cuenta(st(8 * 60_000L, null), d))
        // Al juntar dos dispositivos, la racha es la mejor (no la suma)
        val a = JSONObject().put("dev", JSONObject().put("a", JSONObject().put("total", 180_000L).put("days", JSONObject().put(d, 180_000L)).put("best", JSONObject().put(d, 180_000L))))
        val b = JSONObject().put("dev", JSONObject().put("b", JSONObject().put("total", 200_000L).put("days", JSONObject().put(d, 200_000L)).put("best", JSONObject().put(d, 200_000L))))
        val m = ReadStats.merge(a, b, "a", "b")
        assertEquals(380_000L, m.getJSONObject("days").getLong(d))
        assertEquals(200_000L, m.getJSONObject("best").getLong(d))
        assertFalse("3 + 3,3 min en dos dispositivos no son 5 seguidos", ReadStats.cuenta(m, d))
        // Lo que se enseña en el diario
        val hoy = LocalDate.now()
        BookFiles.putQuiet(ctx, 70, JSONObject().put("stats", st(30 * 60_000L, 2 * 60_000L).let { JSONObject().put("days", JSONObject().put(hoy.toString(), 30 * 60_000L)).put("best", JSONObject().put(hoy.toString(), 2 * 60_000L)) }))
        BookFiles.putQuiet(ctx, 71, JSONObject().put("stats", JSONObject().put("days", JSONObject().put(hoy.toString(), 9 * 60_000L)).put("best", JSONObject().put(hoy.toString(), 9 * 60_000L))))
        val l = BookFiles.leidoPorDia(ctx, soloSeguidos = true)[hoy.toEpochDay()].orEmpty()
        assertEquals(setOf(71L), l.keys)
        assertEquals("sin el filtro salen los dos", setOf(70L, 71L), BookFiles.leidoPorDia(ctx)[hoy.toEpochDay()]!!.keys)
    }

    @Test fun partidasDelPcDeMenosDeCincoMinutosNoCuentan() {
        val zona = ZoneId.of("Europe/Madrid")
        val d = LocalDate.of(2026, 10, 24)
        val ms = { h: Int, m: Int -> d.atTime(h, m).atZone(zona).toInstant().toEpochMilli() }
        val pc = JSONObject().put("games", JSONObject().put("steam:1", JSONObject().put("n", "Hades II")))
            .put("s", JSONArray()
                .put(JSONArray().put("steam:1").put(ms(10, 0)).put(ms(10, 4)))
                .put(JSONArray().put("steam:1").put(ms(12, 0)).put(ms(12, 5)))
                .put(JSONArray().put("steam:1").put(ms(15, 0)).put(ms(15, 20))))
        val m = HorasPc.deSesiones(listOf(pc), zona)
        assertEquals("solo las de 5 y 20 min", 25 * 60_000L, m[d.toEpochDay()]?.values?.sum())
    }
}
