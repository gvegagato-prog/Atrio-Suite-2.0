package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** La guía de la novela: la lógica con los casos de herramientas/casos/guia.json (los mismos que el PC) y el recorrido en pantalla. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class EscribirGuiaTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun limpia() {
        Escritos.reiniciaParaPruebas()
        File(ctx.filesDir, "escritos").deleteRecursively()
    }

    private fun raiz(): File {
        var dir = File("").absoluteFile
        while (!File(dir, "herramientas/casos/guia.json").exists()) dir = dir.parentFile ?: error("no encuentro herramientas/casos")
        return dir
    }

    @Test fun casosCompartidosConElPc() {
        val j = JSONObject(File(raiz(), "herramientas/casos/guia.json").readText())
        assertEquals("los pasos", (0 until j.getJSONArray("pasos").length()).map { j.getJSONArray("pasos").getInt(it) }, GuiaNovela.PASOS.map { it.id })
        assertEquals("los títulos", (0 until j.getJSONArray("titulos").length()).map { j.getJSONArray("titulos").getString(it) }, GuiaNovela.PASOS.map { it.titulo })
        for (p in GuiaNovela.PASOS) assertTrue("el paso ${p.id} tiene todos sus textos", p.que.isNotBlank() && p.como.size >= 3 && p.accion.isNotBlank() && p.hecho.isNotBlank())
        val esq = j.getJSONArray("esquema")
        for (i in 0 until esq.length()) { val c = esq.getJSONObject(i); assertEquals("esquema: ${c.getString("nombre")}", c.getInt("esperado"), GuiaNovela.seccionesEsquema(c.getString("texto"))) }
        assertEquals(0, GuiaNovela.seccionesEsquema(GuiaNovela.PLANTILLA_ESQUEMA))
        val casos = j.getJSONArray("casos")
        for (i in 0 until casos.length()) {
            val c = casos.getJSONObject(i); val d = c.getJSONObject("datos")
            val est = GuiaNovela.estados(DatosGuia(d.getJSONObject("obra"), d.optInt("personajes"), d.optInt("resumenes"), d.optInt("dias"), d.optInt("palabras"), d.getLong("ahora")))
            val esperado = c.getJSONArray("esperado").let { a -> (0 until a.length()).map { a.getString(it) } }
            assertEquals(c.getString("nombre"), esperado, est.filter { it.estado != "pendiente" }.map { "${it.id}:${it.estado}" })
            assertEquals(c.getString("nombre") + " (siguiente)", if (c.isNull("siguiente")) null else c.getInt("siguiente"), GuiaNovela.siguiente(est))
            if (c.has("diasReposo")) assertEquals(c.getString("nombre") + " (días)", c.getInt("diasReposo"), est[6].dias)
        }
    }

    private fun pon(cambia: () -> Unit) { rule.waitForIdle(); cambia(); rule.waitForIdle() }

    @Test fun recorridoEnPantalla() {
        val e = Escritos.get(ctx)
        Skin.load(ctx); Skin.shell = 0; Mode.dark = false; Mode.themeMode = 1
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { EscribirScreen() } } }
        rule.waitForIdle()
        // «Nueva obra»: «Guiarme paso a paso» viene marcada y al crearla se abre la pestaña Guía
        rule.onNodeWithContentDescription("Nueva obra").performClick(); rule.waitForIdle()
        assertTrue("viene marcada", rule.onAllNodesWithText("Guiarme paso a paso ✓").fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Mi primera novela"); rule.waitForIdle()
        rule.onAllNodesWithText("CREAR")[0].performScrollTo().performClick(); rule.waitForIdle()
        val obra = e.obras().single()
        assertEquals(1, obra.i("gOn"))
        assertTrue("se abre la guía", rule.onAllNodesWithText("0 de 11 pasos").fetchSemanticsNodes().isNotEmpty())
        assertTrue("con el paso 1 abierto", rule.onAllNodesWithText("ESCRIBIR MI FRASE").fetchSemanticsNodes().isNotEmpty())
        shoot("guia1_nueva")
        // Paso 1: la frase
        rule.onAllNodesWithText("ESCRIBIR MI FRASE")[0].performClick(); rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Una aprendiz sube a una torre para salvar a su hermana."); rule.waitForIdle()
        rule.onAllNodesWithText("GUARDAR")[0].performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(e.get(obra.id)!!.s("idea").startsWith("Una aprendiz"))
        assertTrue("1 de 11 y su frase", rule.onAllNodesWithText("1 de 11 pasos").fetchSemanticsNodes().isNotEmpty() && rule.onAllNodesWithText("Una aprendiz sube a una torre para salvar a su hermana.").fetchSemanticsNodes().isNotEmpty())
        shoot("guia2_frase")
        // Paso 3: los tres actos
        rule.onAllNodesWithText("Un esquema ligero")[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("CREAR LOS TRES ACTOS")[0].performScrollTo().performClick(); rule.waitForIdle()
        // (el esquema es una nota, fuera del manuscrito: los capítulos no se tocan)
        val esq = e.notas(obra.id).firstOrNull { it.s("titulo") == GuiaNovela.TITULO_ESQUEMA }
        assertTrue("la nota del esquema con sus tres apartados", esq != null && esq.s("texto").contains("Planteamiento:") && esq.s("texto").contains("Nudo:") && esq.s("texto").contains("Desenlace:"))
        assertEquals(listOf("Capítulo 1"), e.capitulos(obra.id).map { it.s("titulo") })
        rule.onNodeWithContentDescription("Cerrar").performClick(); rule.waitForIdle()
        // Y el manuscrito enseña el paso que toca (ahora el 2)
        rule.onAllNodesWithText("Manuscrito")[0].performClick(); rule.waitForIdle()
        assertTrue("la tarjeta del manuscrito", rule.onAllNodesWithText("GUÍA · PASO 2 DE 11").fetchSemanticsNodes().isNotEmpty())
        shoot("guia3_tarjeta")
        // Paso 4 a mano
        rule.onAllNodesWithText("Guía")[0].performScrollTo().performClick(); rule.waitForIdle()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Promesa, progreso y recompensa")); rule.onAllNodesWithText("Promesa, progreso y recompensa")[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("YA LO TENGO")[0].performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(1, e.get(obra.id)!!.i("g4"))
        // Reposo
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Dejarlo reposar")); rule.onAllNodesWithText("Dejarlo reposar")[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("EMPEZAR EL REPOSO")[0].performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(e.get(obra.id)!!.n("g7t") > 0)
        assertTrue("cuenta días", rule.onAllNodesWithText("REPOSO: 0 DÍAS").fetchSemanticsNodes().isNotEmpty())
        // Desactivar
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Desactivar")); rule.onAllNodesWithText("Desactivar")[0].performClick(); rule.waitForIdle()
        assertEquals(0, e.get(obra.id)!!.i("gOn"))
    }

    private fun shoot(n: String) {
        rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$n.png")
    }
}
