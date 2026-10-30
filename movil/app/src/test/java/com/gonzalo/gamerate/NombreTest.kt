package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Corregir el nombre de una ficha («c8oiling» → «Coiling Dragon»): en lo valorado y en lo que llevas entre manos. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class NombreTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun shoot(n: String) {
        rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/nombre_$n.png")
    }

    @Test fun valorado() {
        Mode.dark = false
        Skin.load(ctx); Skin.applyShell(2, ctx)
        var guardado: Game? = null
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { TextoAireado {
                    EditScreen(Game(77, "c8oiling", "", 85, creator = "I Eat Tomatoes"), false, BOOKS, null, null, {}, { guardado = it }, {}, { _, _ -> }, { _, _ -> }, null, editing = true) {}
                } }
            }
        }
        rule.onNode(hasText("c8oiling", ignoreCase = true) and hasSetTextAction()).performTextReplacement("Coiling Dragon")
        shoot("1_valorado")
        rule.onAllNodesWithText("Guardar", ignoreCase = true)[0].performScrollTo().performClick(); rule.waitForIdle()
        assertEquals("Coiling Dragon", guardado?.name)
        assertEquals("la misma ficha", 77L, guardado?.id)
        assertEquals("lo demás no se toca", 85, guardado?.general)
    }

    @Test fun valoradoSinNombre() {
        Skin.load(ctx); Skin.applyShell(2, ctx)
        var guardado: Game? = null
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { TextoAireado {
                    EditScreen(Game(77, "c8oiling", "", 85), false, BOOKS, null, null, {}, { guardado = it }, {}, { _, _ -> }, { _, _ -> }, null, editing = true) {}
                } }
            }
        }
        rule.onNode(hasText("c8oiling", ignoreCase = true) and hasSetTextAction()).performTextReplacement("   ")
        rule.onAllNodesWithText("Guardar", ignoreCase = true)[0].performScrollTo().performClick(); rule.waitForIdle()
        assertEquals("vacío: se queda el que tenía", "c8oiling", guardado?.name)
    }

    @Test fun enCurso() {
        Mode.dark = false
        Skin.load(ctx); Skin.applyShell(2, ctx)
        val cambios = ArrayList<Play>()
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { TextoAireado {
                    PlayDetailScreen(BOOKS, null, Play(78, "c8oiling", "", "", 1, emptyList(), start = today()), startEditing = true,
                        myReact = null, onReact = {}, onChange = { cambios += it }, onNote = { _, _ -> }, onArt = { _, _ -> }, onFinish = {},
                        onRecommend = null, onDelete = {}) {}
                } }
            }
        }
        val campo = rule.onNode(hasText("c8oiling", ignoreCase = true) and hasSetTextAction())
        campo.performTextReplacement("")
        assertEquals("vacío no se guarda", 0, cambios.count { it.name.isBlank() })
        rule.onNode(hasSetTextAction() and hasText("Nombre", ignoreCase = true)).performTextReplacement("Coiling Dragon")
        shoot("2_en_curso")
        assertEquals("Coiling Dragon", cambios.last().name)
        assertEquals(78L, cambios.last().id)
        assertEquals(1, cambios.last().status)
    }
}
