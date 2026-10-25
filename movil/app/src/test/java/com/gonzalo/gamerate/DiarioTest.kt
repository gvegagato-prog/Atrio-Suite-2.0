package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** El Diario: todas las categorías juntas, los días que leíste (con los minutos de cada libro) y el resumen del mes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DiarioTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun shoot(n: String) {
        rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/diario_$n.png")
    }

    @Test fun leidoPorDia() {
        val hoy = LocalDate.now()
        BookFiles.putQuiet(ctx, 7, JSONObject().put("stats", JSONObject().put("days", JSONObject().put(hoy.toString(), 25 * 60_000L).put(hoy.minusDays(1).toString(), 5_000L))))
        BookFiles.putQuiet(ctx, 8, JSONObject().put("stats", JSONObject().put("days", JSONObject().put(hoy.toString(), 10 * 60_000L))))
        val m = BookFiles.leidoPorDia(ctx)
        assertEquals(25 * 60_000L, m[hoy.toEpochDay()]?.get(7L))
        assertEquals(10 * 60_000L, m[hoy.toEpochDay()]?.get(8L))
        assertEquals(5_000L, m[hoy.minusDays(1).toEpochDay()]?.get(7L))
    }

    @Test fun tiempoLeyendoDelAno() {
        val hoy = LocalDate.of(2026, 10, 25)
        val d = { y: Int, m: Int, dd: Int -> LocalDate.of(y, m, dd).toEpochDay() }
        val leido = mapOf(
            d(2026, 10, 20) to mapOf(1L to 30 * 60_000L), d(2026, 10, 21) to mapOf(1L to 20 * 60_000L, 2L to 10 * 60_000L),
            d(2026, 10, 22) to mapOf(2L to 50 * 60_000L), d(2026, 3, 1) to mapOf(3L to 60 * 60_000L),
            d(2026, 3, 2) to mapOf(3L to 20_000L),   // (menos de un minuto: no cuenta como día)
            d(2025, 12, 31) to mapOf(1L to 90 * 60_000L)
        )
        val t = tiempoLeyendo(leido, 2026, hoy)
        assertEquals((30 + 20 + 10 + 50 + 60) * 60_000L + 20_000L, t.total)
        assertEquals(4, t.dias)
        assertEquals("tres días seguidos en octubre", 3, t.racha)
        assertEquals(12, t.meses.size)
        // (el 3: 60 min y 20 s; el 2: 60 min; el 1: 50 min)
        assertEquals(listOf(3L, 2L, 1L), t.libros.map { it.first })
        val todo = tiempoLeyendo(leido, null, hoy)
        assertEquals(5, todo.dias)
        assertEquals("el último mes de los 12 es el de hoy", 12, todo.meses.size)
    }

    @Test fun pantalla() {
        Mode.dark = false
        Skin.load(ctx); Skin.applyShell(2, ctx)
        val hoy = LocalDate.now()
        val d = { n: Long -> hoy.minusDays(n).toEpochDay() }
        val juegos = listOf(Game(1, "Hades II", "", 91, start = d(20), end = d(3)))
        val libros = listOf(Game(7, "Dune", "", 88, start = d(12), end = d(1), creator = "Frank Herbert"))
        val leyendo = listOf(Play(8, "Fundación", "", "", 1, emptyList(), start = d(6)))
        val items = calItems(juegos, emptyList(), GAMES.id) + calItems(libros, leyendo, BOOKS.id)
        val leido = mapOf(d(0) to mapOf(8L to 40 * 60_000L), d(1) to mapOf(7L to 70 * 60_000L, 8L to 12 * 60_000L), d(2) to mapOf(7L to 3 * 60_000L))
        var abierto: CalItem? = null
        var libro = -1L
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    CalendarScreen(BOOKS, emptyList(), emptyList(), null, {}, heading = "Diario", items = items, leido = leido,
                        nombreLibro = { id -> if (id == 7L) "Dune" else "Fundación" }, onOpenLeido = { libro = it }) { abierto = it }
                }
            }
        }
        shoot("1_mes")
        val ayer = hoy.minusDays(1)
        if (ayer.month == hoy.month) {
            assertTrue(rule.onAllNodesWithText("leyendo", substring = true).fetchSemanticsNodes().isNotEmpty())
            rule.onAllNodesWithText("${ayer.dayOfMonth}")[0].performClick()
            shoot("2_dia")
            assertTrue("el día enseña lo leído", rule.onAllNodesWithText("LEÍSTE", substring = true).fetchSemanticsNodes().isNotEmpty())
            // (el último «Fundación» es el de lo leído, abajo del todo en la hoja del día)
            val nodos = rule.onAllNodesWithText("Fundación")
            nodos[nodos.fetchSemanticsNodes().size - 1].performClick(); rule.waitForIdle()
            assertEquals("tocar un libro leído lo abre", 8L, libro)
        }
    }
}
