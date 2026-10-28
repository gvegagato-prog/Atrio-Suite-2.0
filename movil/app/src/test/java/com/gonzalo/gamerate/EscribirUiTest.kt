package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Escribir en pantalla (sin emulador): la biblioteca de obras, el manuscrito, el editor con su panel, las fichas, el tablón,
 * la línea temporal y las cifras, con una novela de ejemplo. Capturas con -Proborazzi.test.record=true en build/outputs/roborazzi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
open class EscribirUiTest {
    open val prefijo: String get() = ""
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun limpia() {
        Escritos.reiniciaParaPruebas()
        File(ctx.filesDir, "escritos").deleteRecursively()
    }

    private fun shoot(n: String) {
        rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$prefijo$n.png")
    }

    /** Una novela de ejemplo con capítulos, escenas, fichas, notas, hitos y días escritos. */
    private fun siembra(e: Escritos): Doc {
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "La torre del cuervo"); put("autor", "Gonzalo"); put("genero", "Fantasía oscura"); put("meta", 80000); put("diaria", 600); put("color", "#B0102F")
            put("sinopsis", "Aldara sube a la torre donde nadie ha vuelto a ser la misma persona. Lo que encuentra arriba no es un monstruo, sino un espejo.") }
        val c1 = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "La subida"); put("parte", "Parte I · El valle") }
        val c2 = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "El cuervo") }
        val c3 = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Lo que dice el espejo"); put("parte", "Parte II · La torre") }
        val prosa = "Aldara subió la torre sin mirar atrás. El viento del valle le arrancaba el capuchón y le llevaba las palabras de la hechicera, aquellas que había repetido tantas veces que ya no sabía si las recordaba o si las inventaba cada vez.\n\n" +
            "—No hay nada arriba —dijo el Cuervo, posado en la barandilla—. Lo sabes.\n\n" +
            "*Lo sé*, pensó ella, y siguió subiendo. Cada escalón era una pequeña mentira que se negaba a creer.\n\n***\n\n" +
            "A medianoche encontró la primera puerta. Estaba abierta, y detrás había **una habitación igual que la suya**."
        val e1 = e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c1.id); put("titulo", "Al pie de la torre"); put("estado", 2); put("texto", prosa); put("resumen", "Aldara llega al valle y decide subir."); put("cuando", "Día 1") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c1.id); put("titulo", "La primera puerta"); put("estado", 1); put("texto", "La habitación olía a su infancia. Aldara tocó la colcha y la colcha le devolvió el gesto.\n\n> Todo lo que subes, lo bajas.\n\nNo era una amenaza. Era una instrucción."); put("resumen", "Encuentra una habitación idéntica a la suya."); put("cuando", "Día 1, medianoche") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c2.id); put("titulo", "Un pacto en la barandilla"); put("estado", 0); put("texto", "El Cuervo no mentía, pero tampoco decía toda la verdad. Esa era su forma de ser honesto."); put("resumen", "El Cuervo ofrece un trato."); put("cuando", "Día 2") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c3.id); put("titulo", "El espejo"); put("estado", 0); put("texto", ""); put("resumen", "El clímax: Aldara se ve a sí misma.") }
        e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "Aldara"); put("alias", "la hechicera, Ald"); put("resumen", "Joven aprendiz que huye de su pasado."); put("color", "#B0102F")
            put("campos", org.json.JSONArray().put(org.json.JSONObject().put("k", "Edad").put("v", "23")).put(org.json.JSONObject().put("k", "Teme").put("v", "Parecerse a su maestra"))) }
        e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "El Cuervo"); put("resumen", "Guardián de la torre. Dice la verdad a medias."); put("color", "#4A6FA5") }
        e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "lugar"); put("nombre", "El valle"); put("resumen", "Tierra de nieblas al pie de la torre."); put("color", "#5B8C5A") }
        e.nuevo(Escritos.T_NOTA, o.id) { put("tipo", "idea"); put("titulo", "¿Y si el espejo miente?"); put("texto", "Que lo que Aldara ve no sea ella, sino lo que la torre quiere que sea."); put("color", "#C9A227"); put("fija", 1) }
        e.nuevo(Escritos.T_NOTA, o.id) { put("tipo", "plantilla"); put("titulo", "Ficha de escena"); put("texto", "Objetivo:\nConflicto:\nQué cambia:\n"); put("color", "#7B5EA7") }
        e.nuevo(Escritos.T_NOTA, o.id) { put("tipo", "investigacion"); put("titulo", "Torres de vigía medievales"); put("texto", "Escaleras de caracol hacia la derecha: el defensor tenía libre el brazo de la espada."); put("color", "#3E8E9E") }
        e.nuevo(Escritos.T_HITO, o.id) { put("titulo", "Aldara llega al valle"); put("cuando", "Día 1"); put("eid", e1.id); put("nota", "Sale al amanecer.") }
        e.nuevo(Escritos.T_HITO, o.id) { put("titulo", "La primera puerta"); put("cuando", "Día 1, medianoche") }
        // Palabras de los últimos días
        val hoy = Escritos.hoy()
        listOf(0 to 340, 1 to 720, 2 to 0, 3 to 510, 4 to 880, 5 to 430, 6 to 650, 8 to 300, 9 to 910, 11 to 520).forEach { (k, n) ->
            if (n > 0) e.pon(org.json.JSONObject().put("id", "l_${o.id}_${hoy - k}").put("t", Escritos.T_LOG).put("pid", o.id).put("dia", hoy - k).put("dev", org.json.JSONObject().put("movil", n)))
        }
        return o
    }

    @Test fun recorrido() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val e = Escritos.get(ctx)
        Skin.load(ctx); Skin.shell = 0; Mode.dark = false; Mode.themeMode = 1
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { EscribirScreen() } } }
        shoot("e01_vacio")
        siembra(e)
        rule.waitForIdle()
        shoot("e02_biblioteca")
        rule.onAllNodesWithText("La torre del cuervo")[0].performClick(); shoot("e03_manuscrito")
        rule.onAllNodesWithText("Esquema")[0].performClick(); shoot("e03b_esquema")
        rule.onAllNodesWithText("Esquema")[0].performClick()
        rule.onAllNodesWithText("Fichas")[0].performClick(); shoot("e04_fichas")
        rule.onAllNodesWithText("Notas")[0].performClick(); shoot("e05_notas")
        rule.onAllNodesWithText("Línea")[0].performClick(); shoot("e06_linea")
        rule.onAllNodesWithText("Cifras")[0].performClick(); shoot("e07_cifras")
        rule.onAllNodesWithText("Manuscrito")[0].performClick()
        rule.onAllNodesWithText("Al pie de la torre")[0].performClick(); shoot("e08_editor")
        rule.onNodeWithContentDescription("Panel de la escena").performClick(); shoot("e09_panel_escena")
        rule.onAllNodesWithText("Fichas")[0].performClick(); shoot("e10_panel_fichas")
        rule.onAllNodesWithText("Notas")[0].performClick(); shoot("e11_panel_notas")
        assertTrue(e.obras().size == 1)
    }

    /** Desde la portada de la revista (la interfaz que usas): «Escribir» en el menú de secciones lleva a tus obras. */
    @Test fun desdeLaRevista() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val e = Escritos.get(ctx)
        siembra(e)
        val store = Store(ctx)
        store.myName = "Gonzalo"
        Skin.load(ctx); Skin.applyShell(2, ctx)
        Mode.dark = false; Mode.themeMode = 1
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { App(store, 0L, false, "") {} } } }
        rule.waitForIdle()
        shoot("e20_revista_portada")
        rule.onAllNodesWithText("Escribir")[0].performClick()
        rule.waitForIdle()
        assertTrue("las obras salen al entrar en Escribir", rule.onAllNodesWithText("La torre del cuervo").fetchSemanticsNodes().isNotEmpty())
        shoot("e21_revista_escribir")
        rule.onAllNodesWithText("La torre del cuervo")[0].performClick()
        rule.onAllNodesWithText("Al pie de la torre")[0].performClick()
        rule.waitForIdle()
        assertTrue("el editor se abre y el dock se esconde", EscribirHub.enEditor)
        shoot("e22_revista_editor")
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        assertTrue("Atrás cierra el editor", !EscribirHub.enEditor)
    }
}
