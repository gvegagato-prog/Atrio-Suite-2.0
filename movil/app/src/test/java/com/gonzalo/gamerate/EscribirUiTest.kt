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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
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
        EscribirHub.listaManuscrito = false
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
        // Una rama de la línea y uniones del árbol
        val h0 = e.hitos(o.id).first()
        val r = e.nuevo(Escritos.T_RAMA, o.id) { put("titulo", "El pasado del Cuervo"); put("color", "#4A6FA5"); put("desde", h0.orden) }
        e.nuevo(Escritos.T_HITO, o.id) { put("titulo", "El Cuervo fue un hombre"); put("cuando", "Hace cien años"); put("rama", r.id); put("orden", h0.orden + 500) }
        val fs = e.fichas(o.id).associateBy { it.s("nombre") }
        e.nuevo(Escritos.T_REL, o.id) { put("a", fs.getValue("Aldara").id); put("b", fs.getValue("El Cuervo").id); put("titulo", "pacto"); put("texto", "Él la guía a cambio de algo que ella aún no sabe."); put("flecha", 0) }
        e.nuevo(Escritos.T_REL, o.id) { put("a", fs.getValue("El Cuervo").id); put("b", fs.getValue("El valle").id); put("titulo", "hogar"); put("flecha", 1) }
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
        rule.onAllNodesWithText("Parte I · El valle")[0].performClick(); shoot("e03c_parte")
        rule.onAllNodesWithText("La subida")[0].performClick(); shoot("e03d_capitulo")
        rule.onAllNodesWithText("LEERLO ENTERO")[0].performClick(); shoot("e03e_leer")
        rule.onNodeWithContentDescription("Cerrar").performClick(); rule.waitForIdle()
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle(); androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithText("Lista", ignoreCase = true)[0].performClick()
        rule.onAllNodesWithText("Ver resúmenes")[0].performClick(); shoot("e03b_esquema")
        rule.onAllNodesWithText("Ver el texto")[0].performClick()
        rule.onAllNodesWithText("Fichas")[0].performClick(); shoot("e04_fichas")
        rule.onAllNodesWithText("Aldara")[0].performClick(); shoot("e04b_ficha_lectura")
        rule.onNodeWithContentDescription("Cerrar").performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Árbol")[0].performClick(); shoot("e04c_arbol")
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

    /** Partes de verdad, sus páginas, el modo lectura de una ficha con sus uniones y la vista previa en el editor. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun partesLecturaYVistaPrevia() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val e = Escritos.get(ctx)
        val o = siembra(e)
        Skin.load(ctx); Skin.shell = 0; Mode.dark = false; Mode.themeMode = 1
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { EscribirScreen() } } }
        rule.waitForIdle()
        rule.onAllNodesWithText("La torre del cuervo")[0].performClick(); rule.waitForIdle()
        // Las partes de texto pasan a ser partes de verdad, y el capítulo sin parte se queda suelto
        assertTrue(e.partes(o.id).map { it.s("titulo") }.toSet() == setOf("Parte I · El valle", "Parte II · La torre"))
        assertTrue(e.capitulos(o.id).map { it.s("titulo") } == listOf("La subida", "El cuervo", "Lo que dice el espejo"))
        rule.onAllNodesWithText("Parte I · El valle")[0].performClick(); rule.waitForIdle()
        assertTrue("la página de la parte", rule.onAllNodesWithText("NUEVO CAPÍTULO").fetchSemanticsNodes().isNotEmpty() && rule.onAllNodesWithText("La subida").fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("La subida")[0].performClick(); rule.waitForIdle()
        assertTrue("la página del capítulo", rule.onAllNodesWithText("Al pie de la torre").fetchSemanticsNodes().isNotEmpty() && rule.onAllNodesWithText("LEERLO ENTERO").fetchSemanticsNodes().isNotEmpty())
        // Una nota creada en el capítulo queda vinculada a él
        rule.onAllNodesWithText("NOTA")[0].performScrollTo().performClick(); rule.waitForIdle()
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("Ojo con el viento"); rule.waitForIdle()
        rule.onAllNodesWithText("GUARDAR")[0].performScrollTo().performClick(); rule.waitForIdle()
        val c1 = e.capitulos(o.id)[0]
        assertTrue("vinculada", e.notas(o.id).single { it.s("titulo") == "Ojo con el viento" }.lista("vin") == listOf(c1.id))
        // Escena → editor; con el cursor en «Aldara», su vista previa; «Abrir» la enseña en modo lectura con sus uniones
        rule.onAllNodesWithText("Al pie de la torre")[0].performClick(); rule.waitForIdle()
        val s = e.escenas(c1.id)[0]
        val i = s.s("texto").indexOf("Aldara") + 2
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[1].performTextInputSelection(androidx.compose.ui.text.TextRange(i)); rule.waitForIdle()
        assertTrue("la vista previa", rule.onAllNodesWithText("Personaje · Joven aprendiz que huye de su pasado.").fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("ABRIR")[0].performClick(); rule.waitForIdle()
        assertTrue("modo lectura con sus uniones", rule.onAllNodesWithText("EDITAR").fetchSemanticsNodes().isNotEmpty() && rule.onAllNodesWithText("PACTO").fetchSemanticsNodes().isNotEmpty())
        // La palabra de la unión abre la unión
        rule.onAllNodesWithText("PACTO")[0].performClick(); rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Él la guía a cambio de algo que ella aún no sabe.").fetchSemanticsNodes().isNotEmpty())
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
        rule.waitForIdle()
        shoot("e21b_revista_obra")
        rule.onAllNodesWithText("Lista", ignoreCase = true)[0].performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Al pie de la torre")[0].performClick()
        rule.waitForIdle()
        assertTrue("el editor se abre y el dock se esconde", EscribirHub.enEditor)
        shoot("e22_revista_editor")
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        assertTrue("Atrás cierra el editor", !EscribirHub.enEditor)
    }

    /** Borrar con «Deshacer» y recuperar desde la papelera, en la app de verdad. */
    @Test fun papeleraConDeshacer() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val e = Escritos.get(ctx)
        val o = siembra(e)
        val store = Store(ctx)
        store.myName = "Gonzalo"
        Skin.load(ctx); Skin.applyShell(2, ctx)
        Mode.dark = false; Mode.themeMode = 1
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { App(store, 0L, false, "") {} } } }
        rule.waitForIdle()
        rule.onAllNodesWithText("Escribir")[0].performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("La torre del cuervo")[0].performClick()
        rule.waitForIdle()
        val s = e.escenasDe(o.id)[1]
        rule.runOnIdle { aLaPapelera(e, s) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("Deshacer", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("el aviso dice qué se ha ido a la papelera", rule.onAllNodesWithText("Escena «La primera puerta» en la papelera").fetchSemanticsNodes().isNotEmpty())
        shoot("e23_deshacer")
        rule.onAllNodesWithText("Deshacer", ignoreCase = true)[0].performClick()
        rule.waitForIdle()
        assertTrue("«Deshacer» la trae de vuelta", e.get(s.id) != null)
        // Un capítulo a la papelera y recuperarlo desde allí
        val c = e.capitulos(o.id)[1]
        rule.runOnIdle { aLaPapelera(e, c) }
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        rule.onAllNodesWithText("PAPELERA · 1")[0].performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("El cuervo").fetchSemanticsNodes().isNotEmpty())
        shoot("e24_papelera")
        rule.onAllNodesWithText("Recuperar", ignoreCase = true)[0].performClick()
        rule.waitForIdle()
        assertTrue("vuelve con su escena", e.capitulos(o.id).size == 3 && e.escenasDe(o.id).size == 4)
        assertTrue(rule.onAllNodesWithText("La papelera está vacía.").fetchSemanticsNodes().isNotEmpty())
    }

    /** El editor: tipografía automática al teclear, atajos de un teclado físico y sinónimos. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun editorTipografiaAtajosYSinonimos() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val e = Escritos.get(ctx)
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Pruebas") }
        val c = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s = e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("titulo", "Escena"); put("texto", "") }
        Skin.load(ctx); Skin.shell = 0; Mode.dark = false; Mode.themeMode = 1
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { EscribirEditor(s.id, onBack = {}, abrirEscena = {}) } } }
        rule.waitForIdle()
        val editor = rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[1]
        editor.performClick()
        "- Hola --dijo-- \"casa\"...".forEach { ch -> editor.performTextInput(ch.toString()) }
        rule.waitForIdle()
        assertTrue("tipografía: ${editor.fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText)}",
            rule.onAllNodesWithText("—Hola —dijo— «casa»…").fetchSemanticsNodes().isNotEmpty())
        // Deshacer quita solo el último cambio de la tipografía (… vuelve a ser ...)
        editor.performKeyInput { withKeyDown(androidx.compose.ui.input.key.Key.CtrlLeft) { pressKey(androidx.compose.ui.input.key.Key.Z) } }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("—Hola —dijo— «casa»...").fetchSemanticsNodes().isNotEmpty())
        editor.performKeyInput { withKeyDown(androidx.compose.ui.input.key.Key.CtrlLeft) { pressKey(androidx.compose.ui.input.key.Key.Y) } }
        rule.waitForIdle()
        assertTrue("Ctrl+Y lo rehace", rule.onAllNodesWithText("—Hola —dijo— «casa»…").fetchSemanticsNodes().isNotEmpty())
        // Sinónimos de la palabra del cursor
        editor.performTextInputSelection(androidx.compose.ui.text.TextRange(15))
        Thread.sleep(300); rule.waitForIdle()
        rule.onNodeWithContentDescription("Sinónimos").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("vivienda").fetchSemanticsNodes().isNotEmpty() }
        shoot("e25_sinonimos")
        rule.onAllNodesWithText("vivienda")[0].performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("—Hola —dijo— «vivienda»…").fetchSemanticsNodes().isNotEmpty())
        // Ctrl+B pone negrita a lo elegido
        editor.performTextInputSelection(androidx.compose.ui.text.TextRange(1, 5))
        editor.performKeyInput { withKeyDown(androidx.compose.ui.input.key.Key.CtrlLeft) { pressKey(androidx.compose.ui.input.key.Key.B) } }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("—**Hola** —dijo— «vivienda»…").fetchSemanticsNodes().isNotEmpty())
        rule.waitUntil(3000) { e.get(s.id)?.s("texto") == "—**Hola** —dijo— «vivienda»…" }
    }

    /** La portada de la revista: «Escribiendo» con la obra y «Seguir con…», que abre la última escena; y el filtro por etiquetas. */
    @Test fun portadaYEtiquetas() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val e = Escritos.get(ctx)
        val o = siembra(e)
        val escenas = e.escenasDe(o.id)
        e.edita(escenas[0].id) { put("etq", org.json.JSONArray().put("flashback")) }
        e.edita(escenas[2].id) { put("etq", org.json.JSONArray().put("flashback").put("Cuervo")) }
        e.escribe(escenas[1].id, escenas[1].s("texto") + "\n\nY la colcha le contó un secreto.")
        val store = Store(ctx)
        store.myName = "Gonzalo"
        Skin.load(ctx); Skin.applyShell(2, ctx)
        Mode.dark = false; Mode.themeMode = 1
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { App(store, 0L, false, "") {} } } }
        rule.waitForIdle()
        rule.onAllNodesWithText("Seguir con «La primera puerta»", substring = true)[0].performScrollTo()
        shoot("e26_portada_escribiendo")
        rule.onAllNodesWithText("Seguir con «La primera puerta»", substring = true)[0].performClick()
        rule.waitForIdle()
        shoot("e26b_tras_seguir")
        // (en una máquina cargada el editor tarda unos fotogramas en componerse tras la transición: se espera, no se supone)
        runCatching { rule.waitUntil(8000) { EscribirHub.enEditor && rule.onAllNodesWithText("Y la colcha le contó un secreto.", substring = true).fetchSemanticsNodes().isNotEmpty() } }
        assertTrue("abre el editor de esa escena", EscribirHub.enEditor && rule.onAllNodesWithText("Y la colcha le contó un secreto.", substring = true).fetchSemanticsNodes().isNotEmpty())
        // (del editor se vuelve a su capítulo y de ahí a su parte, como en el PC; luego a la obra)
        repeat(5) { if (rule.onAllNodesWithText("Manuscrito", ignoreCase = true).fetchSemanticsNodes().isEmpty()) { androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle() } }
        // En la obra, en lista: filtrar por etiqueta
        rule.onAllNodesWithText("Lista", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("#flashback")[0].performClick(); rule.waitForIdle()
        assertTrue("solo las escenas con esa etiqueta", rule.onAllNodesWithText("Al pie de la torre").fetchSemanticsNodes().isNotEmpty() && rule.onAllNodesWithText("La primera puerta").fetchSemanticsNodes().isEmpty())
        shoot("e27_filtro_etiqueta")
        rule.onAllNodesWithText("Todas")[0].performClick(); rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("La primera puerta").fetchSemanticsNodes().isNotEmpty())
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        assertTrue("la biblioteca: «Sigue escribiendo»", rule.onAllNodesWithText("SIGUE ESCRIBIENDO").fetchSemanticsNodes().isNotEmpty())
        shoot("e28_sigue_escribiendo")
    }
}
