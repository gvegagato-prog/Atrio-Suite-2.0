package com.gonzalo.gamerate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate


/** Cambiar el estado de una ficha (En lista → Leyendo) desde la revista: se queda guardado y en su sitio. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class EstadoTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun deEnListaALeyendo() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val s = Store(ctx)
        s.savePlays(listOf(Play(302, "Proyecto Hail Mary", "", "", 0, emptyList(), creator = "Andy Weir")), 1)
        Skin.load(ctx); Mode.dark = false; Mode.themeMode = 1; Skin.applyShell(2, ctx)
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { App(s, 0L, false, "") {} } } }
        rule.waitForIdle()
        rule.onAllNodesWithText("Libros", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("En lista", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Proyecto Hail Mary", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/estado_1_ficha.png")
        rule.onAllNodesWithText("EDITAR", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/estado_2_editar.png")
        rule.onAllNodesWithText("LISTO", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        // Desde la página, sin pasar por «Editar»: la casilla Estado lo cambia
        rule.onAllNodesWithText("LEYENDO", ignoreCase = true)[0].performScrollTo().performClick(); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/estado_3_tras.png")
        org.junit.Assert.assertTrue("sigue en la ficha", rule.onAllNodesWithText("Proyecto Hail Mary", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        org.junit.Assert.assertEquals(1, Store(ctx).loadPlays(1).single().status)
        org.junit.Assert.assertTrue(Store(ctx).loadPlays(0).isEmpty())
        // Y de vuelta en la biblioteca, está en «Leyendo»
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithText("Leyendo", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/estado_4_biblioteca.png")
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("Proyecto Hail Mary", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        // Y vuelve a la lista igual de fácil
        rule.onAllNodesWithText("Proyecto Hail Mary", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("EN LISTA", ignoreCase = true)[0].performScrollTo().performClick(); rule.waitForIdle()
        org.junit.Assert.assertEquals(0, Store(ctx).loadPlays(1).single().status)
    }

    @Test
    fun desdeDatosYNota() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val s = Store(ctx)
        s.savePlays(listOf(Play(303, "Klara y el Sol", "", "", 0, emptyList(), creator = "Kazuo Ishiguro")), 1)
        Skin.load(ctx); Mode.dark = false; Mode.themeMode = 1; Skin.applyShell(2, ctx)
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { App(s, 0L, false, "") {} } } }
        rule.waitForIdle()
        rule.onAllNodesWithText("Libros", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("En lista", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Klara y el Sol", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("EDITAR", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("DATOS Y NOTA", substring = true)[0].performClick(); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/estado_5_datos.png")
        val n = rule.onAllNodesWithText("Leyendo", ignoreCase = true).fetchSemanticsNodes().size
        println("NODOS: $n")
        rule.onAllNodesWithText("Leyendo", ignoreCase = true)[n - 1].performScrollTo().performClick(); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/estado_6_datos_tras.png")
        println("ESTADO2: " + Store(ctx).loadPlays(1).map { it.name to it.status })
        org.junit.Assert.assertEquals(1, Store(ctx).loadPlays(1).single().status)
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        println("ESTADO3: " + Store(ctx).loadPlays(1).map { it.name to it.status })
        org.junit.Assert.assertEquals(1, Store(ctx).loadPlays(1).single().status)
    }
}
