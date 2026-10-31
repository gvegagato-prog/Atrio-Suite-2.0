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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Las pantallas de compartir: marcar terminado/compartido en un capítulo, y leer y anotar lo que te comparten. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CompartirUiTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun shoot(n: String) {
        rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/compartir_$n.png")
    }

    private fun tema(c: @androidx.compose.runtime.Composable () -> Unit) {
        Skin.load(ctx); Skin.applyShell(2, ctx); Mode.dark = false
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { c() } } }
    }

    @Test fun marcarTerminadoEnElCapitulo() {
        val e = Escritos(File(ctx.cacheDir, "cu_${System.nanoTime()}"))
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Guadaña azabache") }
        val c = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "El juramento") }
        e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", c.id); put("texto", "Hola.") }
        tema { CapituloScreen(e, c.id, onBack = {}, abrir = {}, verArbol = {}) }
        shoot("1_capitulo")
        rule.onAllNodesWithText("Marcar como terminado", ignoreCase = true)[0].performClick()
        rule.waitForIdle()
        assertTrue("se guarda terminado", e.get(c.id)!!.b("hecho"))
        assertTrue(rule.onAllNodesWithText("✓ Terminado", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        // (sin pareja enlazada, se explica cómo compartir)
        assertTrue(rule.onAllNodesWithText("Para compartirlo", substring = true).fetchSemanticsNodes().isNotEmpty())
        shoot("2_terminado")
    }

    @Test fun leerYAnotarLoQueTeComparten() {
        Pareja.reiniciaParaPruebas()
        val pe = Pareja.escritos(ctx)
        val o = JSONObject().put("id", "p_1").put("t", "p").put("u", 1).put("titulo", "Guadaña azabache").put("sinopsis", "Una corte, un juramento y una guadaña.")
        val c = JSONObject().put("id", "c_1").put("t", "c").put("pid", "p_1").put("u", 1).put("orden", 1000.0).put("titulo", "El juramento").put("comp", 1).put("hecho", 1)
        val s = JSONObject().put("id", "e_1").put("t", "e").put("pid", "p_1").put("cid", "c_1").put("u", 1).put("titulo", "Escena 1")
            .put("texto", "-Fausto, Lord Fausto- dijo, dejando un claro silencio.\n\nNo entiendo qué tiene que ver eso conmigo, señor.")
        pe.ponDeOtro(listOf(o, c, s))
        tema { LecturaPareja(onBack = {}) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("El juramento").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("Terminado", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        shoot("3_te_comparte")
        rule.onAllNodesWithText("El juramento")[0].performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Toca un párrafo para anotarlo", substring = true).fetchSemanticsNodes().isNotEmpty())
        shoot("4_leer")
        rule.onAllNodesWithText("No entiendo qué tiene que ver", substring = true)[0].performClick()
        rule.waitForIdle()
        assertTrue("se abre la nota con la cita del párrafo", rule.onAllNodesWithText("«No entiendo qué tiene que ver eso conmigo, señor.»").fetchSemanticsNodes().isNotEmpty())
        shoot("5_anotar")
    }
}
