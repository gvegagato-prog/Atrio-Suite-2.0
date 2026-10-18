package com.gonzalo.gamerate

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cambiar notas en masa: elegir de varias categorías, repasar uno tras otro, guardar, saltar y volver atrás. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class NotasEnMasaTest {
    @get:Rule val rule = createComposeRule()

    private fun shoot(n: String) {
        rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") rule.onAllNodes(androidx.compose.ui.test.isRoot())[0].captureRoboImage("build/outputs/roborazzi/masa_$n.png")
    }

    @Test
    fun repasar() {
        Mode.dark = false
        var books by mutableStateOf(listOf(
            Game(1, "Dune", "", 80, creator = "Frank Herbert"),
            Game(2, "Fundación", "", 60, creator = "Isaac Asimov"),
            Game(3, "Hyperion", "", 87, 70, 90, 85, 88, creator = "Dan Simmons")
        ))
        var games by mutableStateOf(listOf(Game(10, "Hades II", "", 91, creator = "Supergiant")))
        var backs = 0
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    MassScoreScreen(listOf(GAMES, BOOKS), { k -> if (k.isBook) books else games },
                        onSave = { k, g -> if (k.isBook) books = books.map { if (it.id == g.id) g else it } else games = games.map { if (it.id == g.id) g else it } }
                    ) { backs++ }
                }
            }
        }
        fun tap(t: String) { rule.onAllNodesWithText(t)[0].performClick(); rule.waitForIdle() }
        shoot("1_elegir")
        // Libros: «los de estrellas» elige Dune (80) y Fundación (60), no Hyperion (87)
        tap("Libros"); tap("LOS DE ESTRELLAS (20, 40…)")
        assertTrue(rule.onAllNodesWithText("2 elegidos").fetchSemanticsNodes().isNotEmpty())
        // Y un juego, de otra categoría
        tap("Videojuegos"); tap("Hades II")
        assertTrue(rule.onAllNodesWithText("3 elegidos").fetchSemanticsNodes().isNotEmpty())
        shoot("2_elegidos")
        tap("REPASAR →")
        assertTrue(rule.onAllNodesWithText("1 de 3").fetchSemanticsNodes().isNotEmpty())
        shoot("3_repasar")
        // El primero: otra nota general escrita a mano, y guardar
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("95"); rule.waitForIdle()
        tap("GUARDAR →")
        assertTrue(rule.onAllNodesWithText("2 de 3").fetchSemanticsNodes().isNotEmpty())
        // Volver al anterior: ya con su nota nueva
        tap("← ANTERIOR")
        assertTrue(rule.onAllNodesWithText("Tenía un 95").fetchSemanticsNodes().isNotEmpty())
        tap("GUARDAR →")   // sin cambios: no cuenta
        tap("SALTAR")
        assertTrue(rule.onAllNodesWithText("3 de 3").fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("70"); rule.waitForIdle()
        tap("GUARDAR Y TERMINAR")
        shoot("4_hecho")
        assertTrue(rule.onAllNodesWithText("Has cambiado 2 notas de 3.").fetchSemanticsNodes().isNotEmpty())
        val all = (books + games).associate { it.name to it.general }
        // Lo elegido va en el orden en que se eligió: Dune, Fundación (saltado), Hades II
        assertEquals(95, all["Dune"]); assertEquals(60, all["Fundación"]); assertEquals(70, all["Hades II"]); assertEquals(87, all["Hyperion"])
        tap("VOLVER →")
        assertEquals(1, backs)
    }
}
