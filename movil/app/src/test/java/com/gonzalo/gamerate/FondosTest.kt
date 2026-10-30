package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Fondos de color para toda la app (granate y compañía): se guardan, mandan sobre el tema, se leen bien y se ven. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class FondosTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** Luminancia relativa WCAG (con el gamma de sRGB). */
    private fun luz(c: Color): Float {
        fun l(v: Float) = if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * l(c.red) + 0.7152f * l(c.green) + 0.0722f * l(c.blue)
    }
    private fun contraste(a: Color, b: Color): Float { val x = luz(a) + 0.05f; val y = luz(b) + 0.05f; return maxOf(x, y) / minOf(x, y) }

    @Test fun seGuardaYMandaSobreElTema() {
        Skin.load(ctx); Skin.applyShell(2, ctx)
        Skin.fondoId = "granate"; Skin.save(ctx)
        Skin.fondoId = ""
        Skin.load(ctx)
        assertEquals("granate", Skin.fondoId)
        Mode.dark = false
        assertEquals("el papel es el granate aunque el tema sea claro", Color(0xFF5A1424), Skin.tones.cPaper)
        assertEquals("el acento es el del fondo (oro)", Color(0xFFF0C26A), Skin.tones.cGeneral())
        // Un acento que eliges tú manda sobre el del fondo
        Skin.accentId = "turquesa"
        assertFalse(Skin.tones.cGeneral() == Color(0xFFF0C26A))
        Skin.accentId = "edicion"
        // En lo de tu pareja no hay fondo de color
        Mode.viewP = true
        assertTrue(Skin.fondoActual == null)
        Mode.viewP = false
        // Viaja con tus ajustes
        assertTrue("skin_fondo" in AJUSTES_SKIN)
        Skin.fondoId = ""; Skin.save(ctx)
    }

    @Test fun seLeenBien() {
        for (f in FONDOS) {
            val c = f.tones
            assertTrue("${f.name}: tinta sobre fondo ≥ 7", contraste(c.cInk, c.cPaper) >= 7f)
            assertTrue("${f.name}: texto secundario ≥ 4,5", contraste(c.cMuted, c.cPaper) >= 4.5f)
            assertTrue("${f.name}: acento sobre fondo ≥ 4,5", contraste(Color(c.general), c.cPaper) >= 4.5f)
            assertTrue("${f.name}: tinta sobre tarjetas ≥ 6", contraste(c.cInk, c.cCrudo) >= 6f)
            assertEquals("${f.name}: oscuro si el fondo lo es", f.oscuro, c.darkBg)
        }
    }

    @Test fun capturas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val s = Store(ctx)
        val d = LocalDate.now().toEpochDay()
        s.save(listOf(Game(1, "Hades II", "", 91, creator = "Supergiant", end = d - 3), Game(2, "Balatro", "", 85, creator = "LocalThunk", end = d - 20)), 0)
        s.savePlays(listOf(Play(3, "Deadlock", "", "", 1, emptyList(), creator = "Valve", minutes = 3120, updated = System.currentTimeMillis())), 0)
        s.savePlays(listOf(Play(4, "El nombre del viento", "", "", 1, emptyList(), creator = "Patrick Rothfuss", chapter = 12, updated = System.currentTimeMillis() - 3_600_000)), 1)
        Skin.load(ctx); Skin.applyShell(2, ctx)
        val fondo = androidx.compose.runtime.mutableStateOf("granate")
        rule.setContent {
            Skin.fondoId = fondo.value
            Mode.dark = Skin.fondoActual?.oscuro ?: true
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { App(s, 0L, false, "") {} }
            }
        }
        fun shoot(n: String) { rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle(); com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/fondo_$n.png") }
        shoot("granate_1_inicio")
        rule.onAllNodesWithText("Diario", ignoreCase = true)[0].performClick(); shoot("granate_2_diario")
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        for (f in listOf("papelreal", "mostaza", "bosque", "berenjena")) { fondo.value = f; shoot("${f}_1_inicio") }
        Skin.fondoId = ""; Skin.save(ctx)
    }
}
