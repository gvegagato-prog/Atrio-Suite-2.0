package com.gonzalo.gamerate

import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** El objetivo de lectura del día: el aviso (solo si no lo has cumplido, con el libro por el que vas) y la portada. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class LecturaAvisoTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun libro(id: Long, min: Long, read: Long, frac: Double, dia: LocalDate = LocalDate.now()) =
        BookFiles.putQuiet(ctx, id, JSONObject().put("uri", "file:///x/$id.epub").put("fraction", frac).put("read", read)
            .put("stats", JSONObject().put("days", JSONObject().put(dia.toString(), min * 60_000L))))

    @Test fun avisaSoloSiNoLoHasCumplido() {
        val st = Store(ctx)
        st.savePlays(listOf(Play(5, "Dune", "", "", 1, emptyList()), Play(6, "Fundación", "", "", 1, emptyList())), BOOKS.id)
        libro(5, 10, 1000, 0.34)
        libro(6, 4, 500, 0.5)
        assertFalse("sin objetivo, nada", LecturaAviso.avisa(ctx))
        st.metaLectura = 30
        assertEquals(14L, LecturaAviso.hoy(ctx) / 60_000L)
        assertEquals(5L, LecturaAviso.ultimo(ctx)?.first)
        assertTrue(LecturaAviso.avisa(ctx))
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val n = Shadows.shadowOf(nm).allNotifications.last()
        val txt = n.extras.getCharSequence("android.title").toString() + " · " + n.extras.getCharSequence("android.text")
        assertTrue(txt, txt.contains("14 de 30") && txt.contains("Dune") && txt.contains("34 %"))
        // El atajo del icono: «Seguir leyendo» el último abierto (Dune)
        AtajoLectura.pon(ctx)
        val sc = ctx.getSystemService(android.content.pm.ShortcutManager::class.java).dynamicShortcuts
        assertEquals(1, sc.size)
        assertTrue(sc[0].longLabel.toString(), sc[0].longLabel.toString().contains("Seguir leyendo «Dune» · 34 %"))
        assertEquals(5L, sc[0].intent?.getLongExtra("leer", 0L))
        // Cumplido: ya no avisa
        st.metaLectura = 10
        assertFalse(LecturaAviso.avisa(ctx))
        // Programar: con objetivo y hora, queda la tarea; sin hora, se quita
        st.metaLectura = 30; st.avisoLectura = 21
        LecturaAviso.programa(ctx)
        st.avisoLectura = -1
        LecturaAviso.programa(ctx)
    }

    @Test fun loQueQueda() {
        val hoy = LocalDate.of(2026, 10, 25)
        // Un 25 % en 2 h: quedan 6 h; estas dos semanas, 3 h 30 min (15 min al día): se acaba en 24 días
        val st = JSONObject().put("fraction", 0.25).put("stats", JSONObject().put("total", 120 * 60_000L)
            .put("days", JSONObject().put(hoy.toString(), 90 * 60_000L).put(hoy.minusDays(3).toString(), 120 * 60_000L).put(hoy.minusDays(30).toString(), 999 * 60_000L)))
        val (min, fin) = quedaPorLeer(st, hoy)!!
        assertEquals(360L, min)
        assertEquals(hoy.plusDays(24), fin)
        assertEquals("el 18 de noviembre", fmtFin(hoy.plusDays(24), hoy))
        assertEquals("mañana", fmtFin(hoy.plusDays(1), hoy))
        // Sin leerlo estas semanas: lo que queda, sin fecha; recién empezado o casi acabado: nada
        assertEquals(null, quedaPorLeer(JSONObject(st.toString()).apply { getJSONObject("stats").put("days", JSONObject()) }, hoy)?.second)
        assertEquals(null, quedaPorLeer(JSONObject(st.toString()).put("fraction", 0.01), hoy))
        assertEquals(null, quedaPorLeer(JSONObject(st.toString()).put("fraction", 0.995), hoy))
    }

    @Test fun portadaConLaSemana() {
        Mode.dark = false
        Skin.load(ctx); Skin.applyShell(2, ctx)
        Store(ctx).metaLectura = 20
        val hoy = LocalDate.now()
        val dias = JSONObject()
        listOf(0L to 12L, 1L to 35L, 2L to 20L, 4L to 50L, 5L to 6L).forEach { (d, m) -> dias.put(hoy.minusDays(d).toString(), m * 60_000L) }
        BookFiles.putQuiet(ctx, 9, JSONObject().put("stats", JSONObject().put("days", dias)))
        val e = ShellEntry(BOOKS, Play(9, "Dune", "", "", 1, emptyList()), null)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(22.dp)) { RevistaLectura(listOf(e)) }
                }
            }
        }
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasText("12 DE 20 MIN HOY", substring = true)).fetchSemanticsNodes().isNotEmpty())
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/lectura_portada.png")
    }
}
