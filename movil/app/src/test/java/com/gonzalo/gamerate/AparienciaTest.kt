package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Apariencia se abre (siempre, no solo al hacer capturas) y «A tu manera» funciona: colores y letras. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AparienciaTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun abre(antes: () -> Unit = {}) {
        Skin.load(ctx); Skin.applyShell(2, ctx); Mode.dark = false
        antes()
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { StyleScreen(1, {}, {}) {} }
            }
        }
        rule.waitForIdle()
    }

    @Test fun seAbreSinLetraElegida() {
        abre { Skin.letraTitulos = ""; Skin.letraTexto = "" }
        rule.onNodeWithText("A tu manera", ignoreCase = true, useUnmergedTree = true).assertExists()
        assertTrue(rule.onAllNodesWithText("La de la edición", useUnmergedTree = true).fetchSemanticsNodes().size == 2)
    }

    @Test fun seAbreConLetraYColoresElegidos() {
        try {
            abre {
                Skin.letraTitulos = DISPLAY_FONTS.first().name; Skin.letraTexto = BODY_FONTS.first().name
                Skin.fondoPropio = 0xFF204060; Skin.tintaPropia = 0xFFFFD0A0; Skin.acentoPropio = 0xFF00AA55
            }
            rule.onNodeWithText("#204060", useUnmergedTree = true).assertExists()
            rule.onNodeWithText(DISPLAY_FONTS.first().name, useUnmergedTree = true).assertExists()
        } finally {
            Skin.letraTitulos = ""; Skin.letraTexto = ""; Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L
        }
    }

    @Test fun selectoresAbiertos() {
        Mode.dark = false
        var color = 0L; var letra = ""
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    androidx.compose.foundation.layout.Column {
                        SelectorColor("Fondo", color, Paper, { color = it }) {}
                        SelectorLetra("Letra de los títulos", letra, DISPLAY_FONTS.map { it.name }) { letra = it }
                    }
                }
            }
        }
        rule.onNodeWithText("Fondo").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Código (#RRGGBB)").assertExists()
        rule.onNodeWithText("Letra de los títulos").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText(DISPLAY_FONTS[1].name)[0].performClick(); rule.waitForIdle()
        assertTrue(letra == DISPLAY_FONTS[1].name)
    }
}
