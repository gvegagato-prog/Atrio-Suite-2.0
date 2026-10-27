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
class PortadaTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun mismaAlturaEnTodas() {
        Mode.dark = false
        val s = Store(ctx)
        s.savePlays(listOf(
            Play(1, "Hades", "", "", 1, emptyList(), creator = "Supergiant", updated = System.currentTimeMillis()),
            Play(2, "Una historia larguísima con un título que ocupa varias líneas en la portada", "", "", 1, emptyList(), creator = "Alguien", updated = System.currentTimeMillis() - 1000)), 0)
        Skin.load(ctx); Skin.applyShell(2, ctx)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { App(s, 0L, false, "") {} }
            }
        }
        rule.waitForIdle()
        val h0 = rule.onNodeWithTag("portada").fetchSemanticsNode().size.height
        rule.onNodeWithTag("portada").performTouchInput { swipeLeft(startX = width * 0.9f, endX = width * 0.1f, durationMillis = 250) }
        rule.waitForIdle()
        val h1 = rule.onNodeWithTag("portada").fetchSemanticsNode().size.height
        assertEquals("el marco no cambia de tamaño al pasar de portada", h0, h1)
    }
}
