package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
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

/** Duelos de libros: empezar, contestar, ver las notas, aplicarlas (sin empates) y deshacerlas. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DuelosPantallaTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun shoot(n: String) {
        rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") rule.onAllNodes(androidx.compose.ui.test.isRoot())[0].captureRoboImage("build/outputs/roborazzi/duelos_$n.png")
    }

    @Test
    fun elFlujoCompleto() {
        Mode.dark = false
        val nombres = listOf("Dune", "Fundación", "Hyperion", "Neuromante", "Solaris", "Ubik", "Contacto", "Cándido", "Drácula", "Rebelión", "Los Pilares", "Ilíada", "Odisea", "Walden")
        val notasIni = listOf(100, 100, 100, 100, 100, 90, 90, 90, 90, 80, 80, 80, 60, 60)
        var books by mutableStateOf(nombres.mapIndexed { i, n -> Game(i + 1L, n, "", notasIni[i], notasIni[i], notasIni[i], notasIni[i], notasIni[i], creator = "Autor $i") } + Game(99, "Con notas sueltas", "", 80, 70, 90, 50, 60))
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    DuelosScreen(books, onSave = { l -> val m = l.associateBy { it.id }; books = books.map { m[it.id] ?: it } }, onBack = {})
                }
            }
        }
        fun tap(t: String) { rule.onAllNodesWithText(t)[0].performClick(); rule.waitForIdle() }
        shoot("1_intro")
        rule.onAllNodesWithText("14 libros").assertCountEquals1()
        tap("EMPEZAR\u00A0→")
        shoot("2_duelo")
        repeat(9) {
            val n = nombres.first { rule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
            tap(n)
        }
        shoot("3_duelo9")
        tap("VER NOTAS\u00A0→")
        shoot("4_notas")
        tap("APLICAR\u00A0→")
        tap("Aplicar")
        val nuevas = books.filter { it.id != 99L }.map { it.general }
        println("NOTAS NUEVAS: $nuevas")
        assertEquals("sin empates", nuevas.size, nuevas.toSet().size)
        assertTrue("ya no son redondas", nuevas.count { it % 10 == 0 } < nuevas.size)
        assertEquals("las cinco notas iguales como al importar", true, books.filter { it.id != 99L }.all { listOf(it.gfx, it.story, it.music, it.gameplay).all { s -> s == it.general } })
        assertEquals("el libro con notas sueltas no se toca", listOf(80, 70, 90, 50, 60), books.first { it.id == 99L }.scores())
        // Deshacer devuelve las de antes
        shoot("5_hecho")
        tap("DESHACER EL ÚLTIMO REPARTO")
        assertEquals(notasIni, books.filter { it.id != 99L }.map { it.general })
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals1() { assertEquals(1, fetchSemanticsNodes().size) }
}
