package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La portada: el marco mide lo mismo en todas las páginas (si no, al pasar de una a otra salta). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
open class PortadaTest {
    open val prefijo = ""
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun mismaAlturaEnTodas() {
        Mode.dark = false
        val s = Store(ctx)
        s.savePlays(listOf(
            Play(1, "Deadlock", "", "", 1, emptyList(), creator = "Valve", updated = System.currentTimeMillis()),
            Play(2, "Coiling Dragon Libro 10", "", "", 1, emptyList(), creator = "I Eat Tomatoes", updated = System.currentTimeMillis() - 1000)), 0)
        Skin.load(ctx); Skin.applyShell(2, ctx)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { App(s, 0L, false, "") {} }
            }
        }
        rule.waitForIdle()
        foto("1")
        val h0 = rule.onNodeWithTag("portada").fetchSemanticsNode().size.height
        rule.onNodeWithTag("portada").performTouchInput { swipeLeft(startX = width * 0.9f, endX = width * 0.1f, durationMillis = 250) }
        rule.waitForIdle()
        foto("2")
        val h1 = rule.onNodeWithTag("portada").fetchSemanticsNode().size.height
        assertEquals("el marco no cambia de tamaño al pasar de portada", h0, h1)
        // Y ninguna deja hueco: en vertical, el texto de las dos acaba justo abajo del todo (la imagen de la de titular corto
        // crece); en horizontal, foto y texto van centrados
        if (ctx.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_PORTRAIT) return
        val marco = rule.onNodeWithTag("portada").fetchSemanticsNode().boundsInRoot
        for (t in listOf("Apuntar\u00A0→")) {
            val fin = rule.onAllNodesWithText(t).fetchSemanticsNodes().filter { it.boundsInRoot.center.x in marco.left..marco.right }.maxOf { it.boundsInRoot.bottom }
            assertTrue("«$t» acaba al pie de la portada ($fin de ${marco.bottom})", marco.bottom - fin < 4f)
        }
    }

    private fun foto(n: String) {
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/${prefijo}portada_igual_$n.png")
    }
}

@Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class PortadaOppoHorizontalTest : PortadaTest() { override val prefijo get() = "oppo_h_" }
