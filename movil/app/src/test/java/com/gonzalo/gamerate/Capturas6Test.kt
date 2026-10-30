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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.junit.Assert.assertTrue
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

/**
 * Capturas de la interfaz en el ordenador (sin emulador), con datos de ejemplo. Para revisar el diseño:
 *   gradle :app:testDebugUnitTest --tests '*Capturas6Test*' -Proborazzi.test.record=true
 * Las imágenes quedan en app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
open class Capturas6Test {
    /** Delante del nombre de cada captura (las de la tableta llevan el suyo). */
    open val prefijo: String get() = ""
    @get:Rule val rule = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** Portada de mentira: degradado con el título. */
    private fun cover(name: String, a: Int, b: Int): String {
        val f = File(ctx.cacheDir, "c_${name.hashCode()}.png")
        if (f.exists()) return f.absolutePath
        val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 300f, 450f, a, b, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, 300f, 450f, p)
        val t = Paint().apply { color = 0x55FFFFFF; textSize = 120f; isAntiAlias = true; isFakeBoldText = true }
        c.drawText(name.take(1), 30f, 160f, t)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    /** Imagen de cuadrícula numerada (para comprobar qué parte de la foto se ve en el encuadre y en la ficha). */
    private fun grid(): String {
        val f = File(ctx.cacheDir, "grid.png")
        if (f.exists()) return f.absolutePath
        val bmp = Bitmap.createBitmap(1000, 1250, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFE6E6E6.toInt())
        val line = Paint().apply { color = 0xFF000000.toInt(); strokeWidth = 3f }
        val txt = Paint().apply { textSize = 48f; isAntiAlias = true; isFakeBoldText = true }
        for (i in 0 until 10) for (j in 0 until 13) {
            c.drawLine(i * 100f, 0f, i * 100f, 1250f, line); c.drawLine(0f, j * 100f, 1000f, j * 100f, line)
            txt.color = if ((i + j) % 2 == 1) 0xFFC80000.toInt() else 0xFF0000C8.toInt()
            c.drawText("$i${'A' + j}", i * 100f + 8f, j * 100f + 56f, txt)
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    private fun seed(): Store {
        val s = Store(ctx)
        val d = LocalDate.now().toEpochDay()
        s.myName = "Gonzalo"
        s.save(listOf(
            Game(101, "Clair Obscur: Expedition 33", cover("Clair", 0xFF8E2DE2.toInt(), 0xFF4A00E0.toInt()), 100, 100, 100, 100, 100, creator = "Sandfall Interactive", end = d - 20, fav = true),
            Game(102, "Hades II", cover("Hades", 0xFFE96443.toInt(), 0xFF904E95.toInt()), 91, creator = "Supergiant", end = d - 60),
            Game(103, "Hollow Knight: Silksong", cover("Silk", 0xFF134E5E.toInt(), 0xFF71B280.toInt()), 88, creator = "Team Cherry", end = d - 3),
            Game(104, "Balatro", cover("Balatro", 0xFFDA4453.toInt(), 0xFF89216B.toInt()), 85, creator = "LocalThunk", end = d - 100),
        ), 0)
        s.savePlays(listOf(
            Play(201, "Deadlock", cover("Dead", 0xFFF7971E.toInt(), 0xFF7F3800.toInt()), "", 1, emptyList(), creator = "Valve", minutes = 3120, updated = System.currentTimeMillis()),
            Play(202, "Elden Ring Nightreign", cover("Elden", 0xFFC9D6FF.toInt(), 0xFF6A5B3B.toInt()), "", 1, emptyList(), creator = "FromSoftware", minutes = 840, ttbMain = 2400, updated = System.currentTimeMillis() - 3600_000),
            Play(203, "Death Stranding 2", cover("DS2", 0xFF00B4DB.toInt(), 0xFF0083B0.toInt()), "", 0, emptyList(), creator = "Kojima Productions"),
            Play(204, "Kingdom Come: Deliverance II", cover("KCD", 0xFF636363.toInt(), 0xFFA2AB58.toInt()), "", 0, emptyList()),
        ), 0)
        s.savePlays(listOf(
            Play(301, "El nombre del viento", cover("Viento", 0xFF603813.toInt(), 0xFFB29F94.toInt()), "", 1, emptyList(), chapter = 34, pages = 880, creator = "Patrick Rothfuss", updated = System.currentTimeMillis() - 7200_000),
            Play(302, "Proyecto Hail Mary", cover("Hail", 0xFF0F2027.toInt(), 0xFF2C5364.toInt()), "", 0, emptyList(), creator = "Andy Weir"),
        ), 1)
        s.save(listOf(Game(401, "Dune", cover("Dune", 0xFFF2994A.toInt(), 0xFFF2C94C.toInt()), 89, creator = "Frank Herbert", end = d - 30)), 1)
        return s
    }

    private fun shoot(name: String) {
        rule.waitForIdle()
        Thread.sleep(400)
        rule.waitForIdle()
        // Pantalla entera con el renderizado por hardware (las capas de Compose no se dibujan por software)
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$prefijo$name.png")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() = this.onNode(androidx.compose.ui.test.isRoot())

    @Test
    fun interfaces() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val store = seed()
        Skin.load(ctx)
        Mode.dark = false
        Mode.themeMode = 1
        Skin.applyShell(2, ctx)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    App(store, 0L, false, "") {}
                }
            }
        }
        shoot("r1_portada")
        // En portada se desliza con el dedo: de Deadlock a la siguiente (y vuelta)
        // Con todas las páginas compuestas a la vez, se mira cuál está dentro del marco (no cuántas hay)
        fun enMarco(t: String): Boolean {
            val marco = rule.onNodeWithTag("portada").fetchSemanticsNode().boundsInRoot
            return rule.onAllNodesWithText(t).fetchSemanticsNodes().any { it.boundsInRoot.center.x in marco.left..marco.right && it.boundsInRoot.center.y in marco.top..marco.bottom }
        }
        assertTrue("al principio no se ve la siguiente", !enMarco("Elden Ring Nightreign"))
        rule.onRoot().performTouchInput { swipeLeft(startX = width * 0.8f, endX = width * 0.1f, durationMillis = 250) }
        rule.waitForIdle()
        assertTrue("al deslizar, la portada pasa a la siguiente", enMarco("Elden Ring Nightreign"))
        shoot("r1b_portada_deslizada")
        rule.onRoot().performTouchInput { swipeRight(startX = width * 0.1f, endX = width * 0.9f, durationMillis = 250) }
        rule.waitForIdle()
        rule.onRoot().performTouchInput { swipeUp() }; rule.onRoot().performTouchInput { swipeUp() }; shoot("r2_portada_abajo")
        // Las secciones están arriba del todo, antes de la portada: se vuelve arriba como haría uno
        repeat(3) { rule.onRoot().performTouchInput { swipeDown() } }; rule.waitForIdle()
        rule.onAllNodesWithText("Videojuegos", ignoreCase = true)[0].performClick(); shoot("r3_biblioteca")
        rule.onAllNodesWithText("Pasados", ignoreCase = true)[0].performClick(); shoot("r3b_biblioteca_pasados")
        // «Portadas en las listas» (Perfil): cada título con su portada, como las sagas
        rule.runOnUiThread { Skin.listCovers = true }; rule.waitForIdle(); shoot("r3c_biblioteca_portadas")
        rule.runOnUiThread { Skin.listCovers = false }; rule.waitForIdle()
        rule.onAllNodesWithText("Hades II", ignoreCase = true)[0].performClick(); shoot("r4_ficha_valorado")
        // Modo edición: el diseño de la página
        rule.onAllNodesWithText("EDITAR", ignoreCase = true)[0].performClick(); shoot("r8_diseno")
        // En «Editar»: cambiar la portada y el fondo de la ficha, directamente
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("Cambiar portada", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("Cambiar fondo", ignoreCase = true)[0].performClick(); rule.waitForIdle(); shoot("r8z_cambiar_fondo")
        org.junit.Assert.assertTrue("se abre el selector de fondos", rule.onAllNodesWithText("Elige el fondo", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("← VOLVER", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        org.junit.Assert.assertTrue("vuelve a la página de la ficha", rule.onAllNodesWithText("EDITAR", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("EDITAR", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("⇔", ignoreCase = true)[0].performTouchInput { swipeRight() }; shoot("r8b_linea_movida")
        rule.onAllNodesWithText("Ninguna todavía.", ignoreCase = true)[0].performClick(); shoot("r8c_elegir")
        // Elegir «Opinión» para la casilla de las guías
        rule.onAllNodesWithText("Opinión", ignoreCase = true)[0].performClick(); shoot("r8d_elegida")
        // (Los diálogos con campos de texto no se quedan quietos en Robolectric: el texto libre se mete directamente)
        rule.runOnUiThread {
            MagLayouts.save(ctx, "v:0:102", listOf(
                MagRowSpec(listOf(MagCell("texto", "Mi veredicto", "El mejor roguelike que he jugado. Cada partida cuenta una historia distinta y la música no se olvida."), MagCell("nota")), 0.62f),
                MagRowSpec(listOf(MagCell("horas"), MagCell("enlaces")), 0.4f),
                MagRowSpec(listOf(MagCell("datos")))
            ))
        }
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithText("Hades II", ignoreCase = true)[0].performClick(); shoot("r8e_texto_libre")
        rule.onAllNodesWithText("EDITAR", ignoreCase = true)[0].performClick()
        rule.onAllNodesWithText("LISTO", ignoreCase = true)[0].performClick(); shoot("r8f_resultado")
        // Imagen encuadrada (ampliada y movida) en una fila de alto fijo, y el editor de encuadre
        rule.runOnUiThread {
            MagLayouts.save(ctx, "v:0:102", listOf(
                MagRowSpec(listOf(MagCell("imagen", image = cover("Hades", 0xFFE96443.toInt(), 0xFF904E95.toInt()), zoom = 1.8f, ox = -0.2f, oy = 0.1f), MagCell("nota")), 0.55f, 300f),
                MagRowSpec(listOf(MagCell("texto", "Mi veredicto", "El mejor roguelike que he jugado.")))
            ))
        }
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithText("Hades II", ignoreCase = true)[0].performClick(); shoot("r9_imagen")
        rule.onAllNodesWithText("EDITAR", ignoreCase = true)[0].performClick(); shoot("r9a_diseno_imagen")
        // Eliminar de la app (por si lo añadiste por error): pide confirmación
        rule.onAllNodesWithText("ELIMINAR DE LA APP", ignoreCase = true)[0].performClick(); shoot("r9h_eliminar")
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("¿Eliminar «Hades II»?", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("Cancelar", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        // Cambiar la imagen: pestañas Portada · Fondo · Logo y, la última, Archivo (de tus archivos o la galería)
        rule.onNodeWithContentDescription("Casilla Imagen").performClick()
        rule.onAllNodesWithText("Cambiar la imagen", ignoreCase = true)[0].performClick(); shoot("r9f_imagen_pestanas")
        rule.onAllNodesWithText("ARCHIVO", ignoreCase = true)[0].performClick(); shoot("r9g_imagen_archivo")
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("ELEGIR DE MIS ARCHIVOS\u00A0→", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("Cerrar", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        rule.onNodeWithContentDescription("Casilla Imagen").performClick()
        rule.onAllNodesWithText("Encuadrar la imagen", ignoreCase = true)[0].performClick(); shoot("r9b_encuadre")
        rule.onAllNodesWithText("+ AMPLIAR", ignoreCase = true)[0].performClick(); rule.onAllNodesWithText("+ AMPLIAR", ignoreCase = true)[0].performClick()
        rule.onNodeWithContentDescription("Marco de la imagen").performTouchInput { swipeRight() }; shoot("r9c_encuadre_cambiado")
        rule.onAllNodesWithText("GUARDAR", ignoreCase = true)[0].performClick()
        rule.onNodeWithContentDescription("Alto de la fila 2").performTouchInput { swipeDown() }; shoot("r9d_alto")
        rule.onAllNodesWithText("LISTO", ignoreCase = true)[0].performClick(); shoot("r9e_resultado")
        rule.onRoot().performTouchInput { swipeUp() }; shoot("r4b_ficha_valorado_2")
        rule.onRoot().performTouchInput { swipeUp() }; shoot("r4c_ficha_valorado_3")
        // Fila de alto automático (foto + nota): lo que se ve al encuadrar es lo que sale en la página
        rule.runOnUiThread {
            MagLayouts.save(ctx, "v:0:102", listOf(
                MagRowSpec(listOf(MagCell("imagen", image = cover("Hades", 0xFFE96443.toInt(), 0xFF904E95.toInt()), shape = "llenar"), MagCell("nota")), 0.45f),
                MagRowSpec(listOf(MagCell("texto", "Mi veredicto", "El mejor roguelike que he jugado.")))
            ))
        }
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithText("Hades II", ignoreCase = true)[0].performClick(); shoot("r9i_auto_antes")
        rule.onAllNodesWithText("EDITAR", ignoreCase = true)[0].performClick()
        rule.onNodeWithContentDescription("Casilla Imagen").performClick()
        rule.onAllNodesWithText("Encuadrar la imagen", ignoreCase = true)[0].performClick(); shoot("r9j_auto_encuadre")
        rule.onAllNodesWithText("+ AMPLIAR", ignoreCase = true)[0].performClick(); rule.onAllNodesWithText("+ AMPLIAR", ignoreCase = true)[0].performClick()
        rule.onAllNodesWithText("GUARDAR", ignoreCase = true)[0].performClick()
        rule.onAllNodesWithText("LISTO", ignoreCase = true)[0].performClick(); shoot("r9k_auto_resultado")
        // Con una imagen de cuadrícula: lo que se ve en el marco tiene que ser lo mismo que sale en la casilla
        rule.runOnUiThread {
            MagLayouts.save(ctx, "v:0:102", listOf(
                MagRowSpec(listOf(MagCell("imagen", image = grid(), shape = "llenar"), MagCell("nota")), 0.45f),
                MagRowSpec(listOf(MagCell("texto", "Mi veredicto", "El mejor roguelike que he jugado.")))
            ))
        }
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithText("Hades II", ignoreCase = true)[0].performClick(); shoot("r9l_grid_antes")
        rule.onAllNodesWithText("EDITAR", ignoreCase = true)[0].performClick()
        rule.onNodeWithContentDescription("Casilla Imagen").performClick()
        rule.onAllNodesWithText("Encuadrar la imagen", ignoreCase = true)[0].performClick(); shoot("r9m_grid_encuadre0")
        rule.onAllNodesWithText("+ AMPLIAR", ignoreCase = true)[0].performClick()
        rule.onNodeWithContentDescription("Marco de la imagen").performTouchInput { this.swipe(center, androidx.compose.ui.geometry.Offset(center.x, center.y + 400f)) }; shoot("r9n_grid_encuadre1")
        rule.onAllNodesWithText("GUARDAR", ignoreCase = true)[0].performClick()
        rule.onAllNodesWithText("LISTO", ignoreCase = true)[0].performClick(); shoot("r9o_grid_resultado")
        // Casilla de una versión anterior (sin forma guardada) con la foto desplazada muy abajo: no puede quedar un hueco arriba
        rule.runOnUiThread {
            MagLayouts.save(ctx, "v:0:102", listOf(
                MagRowSpec(listOf(MagCell("nota"), MagCell("imagen", image = grid(), shape = "llenar", zoom = 1f, ox = 0.3f, oy = 1.0f)), 0.5f),
                MagRowSpec(listOf(MagCell("texto", "Mi veredicto", "El mejor roguelike que he jugado.")))
            ))
        }
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithText("Hades II", ignoreCase = true)[0].performClick(); shoot("r9p_antigua_lectura")
        rule.onAllNodesWithText("EDITAR", ignoreCase = true)[0].performClick(); shoot("r9q_antigua_edicion")
        rule.onAllNodesWithText("LISTO", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.onAllNodesWithText("Jugando", ignoreCase = true)[0].performClick()
        rule.onAllNodesWithText("Deadlock", ignoreCase = true)[0].performClick(); shoot("r5_ficha_jugando")
        rule.onRoot().performTouchInput { swipeUp() }; shoot("r5b_ficha_jugando_2")
        rule.onRoot().performTouchInput { swipeUp() }; shoot("r5c_ficha_jugando_3")
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        repeat(3) { rule.onRoot().performTouchInput { swipeDown() } }; rule.waitForIdle()
        rule.onAllNodesWithText("Diario", ignoreCase = true)[0].performClick(); shoot("r6_diario")
        org.junit.Assert.assertTrue("el Diario se abre desde la portada", rule.onAllNodesWithText("CALENDARIO", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        rule.runOnUiThread { Mode.dark = true; Mode.themeMode = 2 }
        rule.onRoot().performTouchInput { swipeDown() }; rule.onRoot().performTouchInput { swipeDown() }; rule.onRoot().performTouchInput { swipeDown() }
        shoot("r7_noche")
    }

    /** Libro con subrayados (la casilla nueva de la ficha) y el test de juegos con la pareja. */
    @Test
    fun subrayadosYTest() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val store = seed()
        Skin.load(ctx)
        Mode.dark = false
        Mode.themeMode = 1
        Skin.applyShell(2, ctx)
        val now = System.currentTimeMillis()
        BookFiles.put(ctx, 301, org.json.JSONObject().put("uri", "content://prueba/dune.epub").put("name", "dune.epub").put("fraction", 0.42)
            .put("notes", org.json.JSONArray()
                .put(org.json.JSONObject().put("value", "epubcfi(/6/8!/4/2/1:0)").put("text", "El miedo es el asesino de la mente.").put("note", "La letanía: me la sé de memoria.").put("label", "Capítulo 1").put("created", now - 86_400_000L * 3).put("index", 3))
                .put(org.json.JSONObject().put("value", "epubcfi(/6/20!/4/2/1:0)").put("text", "Una persona necesita nuevas experiencias. Sacuden algo muy dentro, permitiéndole crecer.").put("note", "").put("label", "Capítulo 9").put("created", now).put("index", 9))))
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    App(store, 0L, false, "") {}
                }
            }
        }
        rule.onAllNodesWithText("El nombre del viento", ignoreCase = true)[0].let { n -> runCatching { n.performScrollTo() }; n.performClick() }; shoot("s1_libro")
        rule.onAllNodesWithText("SUBRAYADOS Y NOTAS", ignoreCase = true)[0].performScrollTo(); shoot("s2_libro_subrayados")
    }

    @Test
    fun testJuegosPareja() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        val store = seed()
        Skin.load(ctx)
        Mode.dark = false
        Mode.themeMode = 1
        Skin.applyShell(2, ctx)
        val theirs = QuizRun(System.currentTimeMillis() - 86_400_000L, listOf(QuizItem(0, false, 3328, "The Witcher 3", "The Witcher 3", "2015", "", "Rol (RPG)")))
        val partner = Partner("Laura", 0L, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), 0, 0, quizzes = mapOf(0 to theirs))
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    QuizScreen(0, store, emptySet(), partner, onAdd = {}, onBack = {})
                }
            }
        }
        shoot("t1_test_juegos")
        rule.onAllNodesWithText("5", ignoreCase = true)[0].performClick(); shoot("t2_pregunta_juegos")
        // Coincidencias: la cuenta del aviso a la pareja
        val mine = listOf(QuizItem(0, false, 3328, "The Witcher 3", "The Witcher 3", "2015", "", ""), QuizItem(0, false, 1, "Otro", "Otro", "2020", "", ""))
        org.junit.Assert.assertEquals(listOf("The Witcher 3"), quizMatches(mine, theirs.items).map { it.title })
        val back = quizRunsFromJson(org.json.JSONObject().put("0", quizRunToJson(theirs)))
        org.junit.Assert.assertEquals("The Witcher 3", back[0]?.items?.first()?.title)
    }

    /** Buscar un libro que no aparece: la primera fila lo añade tal cual lo has escrito. */
    @Test
    fun anadirTalCual() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Skin.load(ctx)
        Mode.dark = true
        Mode.themeMode = 2
        Skin.applyShell(2, ctx)
        var manualName by mutableStateOf<String?>(null)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    val n = manualName
                    if (n != null) CustomAddScreen(BOOKS, true, initialName = n, onPhoto = { _, _ -> }, onCreate = { _, _, _, _ -> }) {}
                    else SearchScreen(
                        needsKey = false, key = "", owned = { null },
                        search = { listOf(Hit(1, "나 혼자만 레벨 업 1 (novel)", "추공 · 2018 · 322 págs."), Hit(2, "SOLO LEVELING 02", "Dubu · 2021")) },
                        onPick = {}, onBack = {}, title = "Libros", onManual = { manualName = it }
                    )
                }
            }
        }
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Solo Leveling")
        rule.waitForIdle(); Thread.sleep(900); shoot("y1_buscar_tal_cual")
        rule.onAllNodesWithText("Añadir «Solo Leveling» tal cual", ignoreCase = true)[0].performClick()
        org.junit.Assert.assertEquals("Solo Leveling", manualName)
        shoot("y2_a_mano_con_nombre")
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("Solo Leveling", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
    }

    /** Elegir portada y fondo: pestañas Portadas · Fondos · Archivo; y añadir a mano un juego con portada y fondo. */
    @Test
    fun imagenesPropias() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Skin.load(ctx)
        Mode.dark = false
        Mode.themeMode = 1
        Skin.applyShell(2, ctx)
        var heroTab by mutableStateOf(false)
        var manual by mutableStateOf(false)
        val picked = mutableListOf<String>()
        val g = Game(4242, "Control Resonant", cover("Control", 0xFF8E2DE2.toInt(), 0xFF4A00E0.toInt()), hero = "")
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    if (manual) CustomAddScreen(GAMES, true, onPhoto = { f, _ -> picked += if (f) "archivos" else "galeria" }, onCreate = { _, _, _, _ -> }) {}
                    else ArtScreen(
                        g, heroTab,
                        load = { List(6) { i -> cover("C$i", 0xFF000000.toInt() + i * 0x202020, 0xFF336699.toInt()).let { it to it } } },
                        onPick = {}, onSkip = {}, onBack = {},
                        hasHero = true, onTab = { heroTab = it }, onLocal = { h, f -> picked += "$h/$f" }
                    )
                }
            }
        }
        shoot("x1_portadas")
        rule.onAllNodesWithText("FONDOS", ignoreCase = true)[0].performClick(); shoot("x2_fondos")
        rule.onAllNodesWithText("ARCHIVO", ignoreCase = true)[0].performClick(); shoot("x3_archivo")
        rule.onAllNodesWithText("Elegir de mis archivos", ignoreCase = true)[0].performClick()
        rule.onAllNodesWithText("La portada", ignoreCase = true)[0].performClick()
        rule.onAllNodesWithText("Desde la galería", ignoreCase = true)[0].performClick()
        org.junit.Assert.assertEquals(listOf("true/true", "false/false"), picked)
        picked.clear()
        manual = true; rule.waitForIdle(); shoot("x4_a_mano")
        rule.onAllNodesWithText("Foto de fondo (opcional)", ignoreCase = true)[0].performClick(); shoot("x5_de_donde")
        rule.onAllNodesWithText("De mis archivos", ignoreCase = true)[0].performClick()
        org.junit.Assert.assertEquals(listOf("archivos"), picked)
    }

    /** Buscar en todo desde la portada: sin tildes, por categorías, y también en lo subrayado de los libros. */
    @Test
    fun buscador() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val store = seed()
        // Un subrayado en «Dune» (libro 201 de las pruebas, o el que sea de los libros)
        val libro = store.load(BOOKS.id).first()
        BookFiles.putQuiet(ctx, libro.id, org.json.JSONObject().put("uri", "file:///x.epub").put("notes", org.json.JSONArray().put(org.json.JSONObject().put("value", "epubcfi(/6/4!/4/2/1:0)").put("text", "El miedo mata la mente").put("note", "").put("index", 1))))
        Skin.load(ctx)
        Mode.dark = false
        Mode.themeMode = 1
        Skin.applyShell(2, ctx)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    App(store, 0L, false, "") {}
                }
            }
        }
        rule.waitForIdle()
        rule.onAllNodesWithText("Buscar", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") shoot("b1_buscar")
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("hades")
        rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") shoot("b2_buscar_hades")
        assertTrue("encuentra «Hades II» sin mayúsculas", rule.onAllNodesWithText("Hades II", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextReplacement("MIEDO MENTE")
        rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") shoot("b3_buscar_cita")
        assertTrue("y en lo subrayado", rule.onAllNodesWithText("«El miedo mata la mente»", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
    }

    /** Apariencia (se abre desde MainActivity, fuera de App): claro u oscuro y el acento. */
    @Test
    fun apariencia() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Skin.load(ctx)
        Mode.dark = false
        Mode.themeMode = 1
        Skin.applyShell(2, ctx)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    StyleScreen(Mode.themeMode, { Mode.themeMode = it; Mode.dark = it == 2 }, {}) {}
                }
            }
        }
        shoot("a1_apariencia")
        rule.onAllNodesWithText("AZUL", substring = true, ignoreCase = true)[0].performClick(); shoot("a2_acento_azul")
        rule.onAllNodesWithText("Oscuro", ignoreCase = true)[0].performClick(); shoot("a3_oscuro")
    }

    /** Todas las pantallas con el estilo revista (para revisar que cuadran). */
    @Test
    fun pestanas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val store = seed()
        Skin.load(ctx)
        Mode.dark = false
        Mode.themeMode = 1
        Skin.applyShell(2, ctx)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    App(store, 0L, false, "") {}
                }
            }
        }
        fun go(t: String) { val n = rule.onAllNodesWithText(t, ignoreCase = true)[0]; runCatching { n.performScrollTo() }; n.performClick(); rule.waitForIdle() }
        fun back() { androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle() }
        fun home() { repeat(4) { if (rule.onAllNodesWithText("EN PORTADA", ignoreCase = true).fetchSemanticsNodes().isEmpty()) back() } }
        // Una foto vertical que llena su casilla, al lado de la nota
        rule.runOnUiThread {
            MagLayouts.save(ctx, "v:0:102", listOf(
                MagRowSpec(listOf(MagCell("imagen", image = cover("Hades", 0xFFE96443.toInt(), 0xFF904E95.toInt()), shape = "llenar"), MagCell("nota"))),
                MagRowSpec(listOf(MagCell("imagen", image = cover("Hades", 0xFFE96443.toInt(), 0xFF904E95.toInt()), shape = "original", fit = true)))
            ))
        }
        shoot("p00a_portada"); go("Videojuegos"); go("Pasados"); go("Hades II"); shoot("p00_foto_llena")
        rule.onRoot().performTouchInput { swipeUp() }; shoot("p00b_foto_original")
        home()
        go("Videojuegos"); go("DESCUBRIR\u00A0→"); shoot("p01_descubrir")
        rule.onRoot().performTouchInput { swipeUp() }; shoot("p01b_descubrir_filtros")
        go("5"); shoot("p02_pregunta"); back(); back()
        go("Libros"); go("DESCUBRIR\u00A0→"); shoot("p03_descubrir_libro"); home()
        go("Diario"); shoot("p04_diario"); home()
        go("Cifras"); shoot("p05_estadisticas"); home()
        go("Ajustes"); shoot("p06_perfil")
        rule.onRoot().performTouchInput { swipeUp() }; shoot("p06b_perfil")
        go("Servicios y copias"); shoot("p07_ajustes")
        rule.onRoot().performTouchInput { swipeUp() }; shoot("p07b_ajustes"); home()
        go("Videojuegos"); shoot("p08_biblioteca")
        go("+ AÑADIR"); shoot("p09_buscar"); back()
        go("Deadlock"); go("Apuntar\u00A0→"); shoot("p10_apunte"); back(); home()
        go("Ajustes"); go("Apariencia"); shoot("p11_apariencia"); back(); home()
        go("Tu PC"); shoot("p12_pc"); home()
        go("Cifras"); go("VER TU ${java.time.LocalDate.now().year}, NÚMERO ESPECIAL\u00A0→"); shoot("p15_especial")
        // El número especial es un diálogo: tiene su propia raíz
        val dlg = { rule.onAllNodes(androidx.compose.ui.test.isRoot()).let { it[it.fetchSemanticsNodes().size - 1] } }
        dlg().performTouchInput { swipeUp() }; shoot("p15b_especial")
        dlg().performTouchInput { swipeUp() }; shoot("p15c_especial"); back(); home()
        go("Libros"); go("DESCUBRIR\u00A0→"); go("5")
        repeat(5) { i -> rule.waitForIdle(); Thread.sleep(300); rule.onAllNodesWithText(if (i % 2 == 0) "A" else "B")[0].performClick() }
        rule.waitUntil(60_000) { rule.onAllNodesWithText("A MI LISTA +", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("No ha salido nada", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shoot("p13_resultados")
        if (rule.onAllNodesWithText("% PARA TI", ignoreCase = true).fetchSemanticsNodes().isNotEmpty()) { rule.onAllNodesWithText("% PARA TI", ignoreCase = true)[0].performClick(); shoot("p14_ficha_resultado") }
    }
}
