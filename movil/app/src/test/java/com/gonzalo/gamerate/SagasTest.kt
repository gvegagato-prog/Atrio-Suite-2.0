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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Sagas: libros juntos bajo una portada, estén donde estén (leídos con su nota, en curso o en lista). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
open class SagasTest {
    open val prefijo: String get() = ""
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun cover(name: String, a: Int, b: Int): String {
        val f = File(ctx.cacheDir, "s_${name.hashCode()}.png")
        if (!f.exists()) {
            val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawRect(0f, 0f, 300f, 450f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 450f, a, b, Shader.TileMode.CLAMP) })
            c.drawText(name.take(1), 30f, 160f, Paint().apply { color = 0x55FFFFFF; textSize = 120f; isAntiAlias = true; isFakeBoldText = true })
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return f.absolutePath
    }

    /**
     * Pulsa el botón [texto] del diálogo abierto por su acción de accesibilidad, sin esperar a que Compose quede quieto.
     * En Robolectric un campo de texto de ancho flexible dentro de un diálogo con el ancho de la plataforma hace que la
     * ventana se mida dos veces con anchos distintos en cada pasada, y eso no se acaba nunca: cualquier espera
     * (performClick, waitForIdle) se agota a los 60 s según lo cargada que esté la máquina (en CI fallaba siempre).
     */
    private fun pulsaEnDialogo(texto: String) {
        val wmg = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        fun vistas() = (wmg.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(wmg) as List<android.view.View>).toList()
        fun raices(v: android.view.View): List<androidx.compose.ui.node.RootForTest> = when (v) {
            is androidx.compose.ui.node.RootForTest -> listOf(v)
            is android.view.ViewGroup -> (0 until v.childCount).flatMap { raices(v.getChildAt(it)) }
            else -> emptyList()
        }
        fun busca(n: androidx.compose.ui.semantics.SemanticsNode): androidx.compose.ui.semantics.SemanticsNode? =
            if (n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any { it.text == texto } == true) n
            else n.children.firstNotNullOfOrNull { busca(it) }
        // El diálogo es la última ventana (se le deja colocarse: unos fotogramas de la ventana, no de Compose)
        var nodo: androidx.compose.ui.semantics.SemanticsNode? = null
        for (i in 0 until 50) {
            nodo = vistas().asReversed().flatMap { raices(it) }.firstNotNullOfOrNull { busca(it.semanticsOwner.unmergedRootSemanticsNode) }
            if (nodo != null) break
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        }
        if (nodo == null) error("no está «$texto» en ningún diálogo")
        var n: androidx.compose.ui.semantics.SemanticsNode? = nodo
        while (n != null && n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick) == null) n = n.parent
        val accion = n?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.action ?: error("«$texto» no se puede pulsar")
        accion()
    }

    private fun seed(): Store {
        val s = Store(ctx)
        val d = java.time.LocalDate.now().toEpochDay()
        // La trilogía de Rothfuss, repartida: el 1 leído, el 2 a medias y el 3 en la lista (títulos al estilo de Goodreads)
        s.save(listOf(
            Game(501, "El nombre del viento (Crónica del asesino de reyes, #1)", cover("Viento", 0xFF603813.toInt(), 0xFFB29F94.toInt()), 92, creator = "Patrick Rothfuss", end = d - 200),
            Game(601, "Dune", cover("Dune", 0xFFF2994A.toInt(), 0xFFF2C94C.toInt()), 89, creator = "Frank Herbert", end = d - 30),
            Game(603, "Hijos de Dune", cover("Hijos", 0xFF7F3800.toInt(), 0xFFF7971E.toInt()), 78, creator = "Frank Herbert", end = d - 10)
        ), 1)
        s.savePlays(listOf(
            Play(502, "El temor de un hombre sabio (Crónica del asesino de reyes, #2)", cover("Temor", 0xFF0F2027.toInt(), 0xFF2C5364.toInt()), "", 1, emptyList(), chapter = 41, creator = "Patrick Rothfuss", updated = System.currentTimeMillis()),
            Play(503, "Las puertas de piedra (Crónica del asesino de reyes, #3)", cover("Puertas", 0xFF3E5151.toInt(), 0xFFDECBA4.toInt()), "", 0, emptyList(), creator = "Patrick Rothfuss"),
            Play(602, "El mesías de Dune", cover("Mesias", 0xFFC9D6FF.toInt(), 0xFF6A5B3B.toInt()), "", 0, emptyList(), creator = "Frank Herbert")
        ), 1)
        return s
    }

    private fun shoot(n: String) {
        rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/${prefijo}saga_$n.png")
    }

    @Test fun modelo() {
        assertEquals("Crónica del asesino de reyes" to 2f, seriesOf("El temor de un hombre sabio (Crónica del asesino de reyes, #2)"))
        assertEquals(null, seriesOf("Dune"))
        val s = seed()
        val sug = sagaSuggestions(emptyList(), s.loadPlays(1), s.load(1))
        // Por el título, en su orden; y por autor, los de Herbert
        assertEquals(listOf(501L, 502L, 503L), sug.first { it.name == "Crónica del asesino de reyes" }.ids)
        assertTrue(sug.any { it.why == "De Frank Herbert" && it.ids.toSet() == setOf(601L, 602L, 603L) })
        // Los libros de una saga, estén donde estén
        val saga = Saga(1, "Crónica", listOf(501, 502, 503, 999), updated = 1)
        val b = sagaBooks(saga, s.loadPlays(1), s.load(1))
        assertEquals(3, b.size)
        assertTrue(b[0].read && b[0].score == 92 && b[1].reading && !b[2].read)
        assertEquals(502L, sagaNext(b)?.id)
        assertEquals("3 libros · 1 leído · leyendo · media 92", sagaLine(b))
        // Se guarda, viaja con la cuenta y se mezcla con otro dispositivo (la versión más reciente gana)
        s.saveSagas(listOf(saga))
        assertEquals(saga, s.loadSagas().single())
        val remoto = org.json.JSONObject(s.syncData())
        assertEquals(1, remoto.getJSONArray("sagas").length())
        remoto.put("sagas", org.json.JSONArray().put(sagaToJson(saga.copy(name = "Crónica del asesino de reyes", updated = 5))).put(sagaToJson(Saga(2, "Dune", listOf(601, 602), updated = 5))))
        s.mergeRemote(remoto.toString())
        assertEquals(listOf("Crónica del asesino de reyes", "Dune"), s.loadSagas().map { it.name })
        // Borrada en este móvil: no vuelve al mezclar con otro que aún la tenía
        s.saveSagas(s.loadSagas().filter { it.id != 2L })
        s.mergeRemote(remoto.toString())
        assertEquals(listOf(1L), s.loadSagas().map { it.id })
        // En la biblioteca, los de una saga van juntos: en Leídos, Dune (2 leídos de 3) en una sola entrada, sin nota
        // de saga porque falta uno; con todos leídos, la media
        val dune = Saga(2, "Dune", listOf(601, 602, 603), updated = 1)
        val leidos = s.load(1).map { Cell(it.id, it.name, it.cover, "", null, it) }
        val g = groupSagas(leidos, listOf(saga, dune), s.loadPlays(1), s.load(1), BOOKS)
        assertEquals(listOf("El nombre del viento (Crónica del asesino de reyes, #1)", "Dune"), g.map { it.name })
        assertEquals(dune, g[1].saga)
        assertEquals(null, g[1].sagaScore)
        assertTrue(g[1].sub, g[1].sub.startsWith("Saga · 2 libros de 3"))
        val todos = groupSagas(leidos, listOf(Saga(3, "Dune", listOf(601, 603))), s.loadPlays(1), s.load(1), BOOKS)
        assertEquals(listOf(84), todos.mapNotNull { it.sagaScore })
        // Sin otro de la saga en ese apartado, el libro se queda suelto
        assertEquals(3, groupSagas(leidos, listOf(Saga(4, "X", listOf(601, 502))), s.loadPlays(1), s.load(1), BOOKS).size)

        // Lo que no son libros: por el principio del título y en el orden de sus números
        assertEquals(normName("Dark Souls"), titleStem("Dark Souls III: The Ringed City"))
        assertEquals(3f, titleNumber("Dark Souls III: The Ringed City"))
        assertEquals(2f, titleNumber("Mass Effect 2"))
        assertEquals(1f, titleNumber("Mass Effect: Andromeda"))
        val juegos = sagaSuggestions(emptyList(), listOf(Play(13, "Mass Effect 3", "", "", 0, emptyList())), listOf(Game(12, "Mass Effect 2", "", 90), Game(11, "Mass Effect", "", 88), Game(20, "Hades", "", 95)), GAMES)
        assertEquals(1, juegos.size)
        assertEquals("Mass Effect", juegos[0].name)
        assertEquals(listOf(11L, 12L, 13L), juegos[0].ids)
        assertEquals("2 juegos · 1 pasado", sagaLine(sagaBooks(Saga(3, "ME", listOf(11, 13), kind = 0), listOf(Play(13, "Mass Effect 3", "", "", 0, emptyList())), listOf(Game(11, "Mass Effect", "", 88))), GAMES).substringBefore(" · media"))
    }

    /** Una saga de juegos: su pestaña, la sugerencia por el título, su página y el buscador de portadas. */
    @Test fun juegos() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val s = seed()
        val d = java.time.LocalDate.now().toEpochDay()
        s.save(listOf(
            Game(701, "Mass Effect", cover("ME1", 0xFF0B1D3A.toInt(), 0xFF5B86E5.toInt()), 88, creator = "BioWare", end = d - 300),
            Game(702, "Mass Effect 2", cover("ME2", 0xFF141E30.toInt(), 0xFFF7971E.toInt()), 95, creator = "BioWare", end = d - 100)
        ), 0)
        s.savePlays(listOf(Play(703, "Mass Effect 3", cover("ME3", 0xFF232526.toInt(), 0xFFB21F1F.toInt()), "", 0, emptyList(), creator = "BioWare")), 0)
        // Una saga de juegos (crearla pasa por el mismo diálogo que la de libros, que ya se prueba en «recorrido»)
        s.saveSagas(listOf(Saga(77, "Mass Effect", listOf(701, 702, 703), kind = 0, updated = System.currentTimeMillis())))
        Skin.load(ctx); Mode.dark = false; Mode.themeMode = 1; Skin.applyShell(2, ctx)
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { App(s, 0L, false, "") {} } } }
        rule.waitForIdle()
        rule.onAllNodesWithText("Juegos")[0].performScrollTo().performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Sagas")[0].performClick(); rule.waitForIdle()
        shoot("8_juegos")
        assertTrue(rule.onAllNodesWithText("3 JUEGOS · 2 PASADOS · MEDIA 92", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("Mass Effect")[0].performClick(); rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("EN ORDEN").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Empezar a jugar", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("2/3").fetchSemanticsNodes().isNotEmpty())
        shoot("9_saga_juegos")
        // La portada: la automática, las de sus juegos, buscarla, de la galería o de un archivo
        rule.onAllNodesWithText("EDITAR")[0].performClick(); rule.waitForIdle()
        shoot("10_portada")
        for (t in listOf("AUTOMÁTICA", "BUSCAR", "GALERÍA", "ARCHIVO")) assertTrue(t, rule.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("LISTO")[0].performClick(); rule.waitForIdle()
        // En la ficha del juego: su saga y su número
        rule.onAllNodesWithText("Mass Effect 2").onLast().performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Saga · Mass Effect", substring = true).fetchSemanticsNodes().isNotEmpty())
        // Cada saga es de su categoría: un libro con el mismo id no está en ella
        assertEquals(77L, SagaHub.of(702, 0)?.id)
        assertEquals(null, SagaHub.of(702, 1))
    }

    @Test fun recorrido() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val s = seed()
        Skin.load(ctx); Mode.dark = false; Mode.themeMode = 1; Skin.applyShell(2, ctx)
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { App(s, 0L, false, "") {} } } }
        rule.waitForIdle()
        rule.onAllNodesWithText("Libros").onLast().performScrollTo().performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Sagas")[0].performClick(); rule.waitForIdle()
        shoot("1_pestana")
        // Crear la de Rothfuss desde la sugerencia (ya viene con su nombre y sus libros en orden)
        rule.onAllNodesWithText("Crónica del asesino de reyes")[0].performScrollTo()
        // El diálogo y el paso a la saga, fotograma a fotograma: con un campo de texto dentro, Robolectric vuelve a medir
        // el diálogo sin parar (su anchura oscila: ver pulsaEnDialogo) y Compose no queda nunca quieto con el reloj en marcha
        rule.mainClock.autoAdvance = false
        rule.onAllNodesWithText("Crónica del asesino de reyes")[0].performClick()
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/${prefijo}saga_2_crear.png")
        pulsaEnDialogo("Crear")
        repeat(60) { rule.mainClock.advanceTimeByFrame() }
        // Ya sin el diálogo, otra vez el reloj automático: lo que llega después (portadas, la saga guardada) se termina de pintar
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/${prefijo}saga_3_saga.png")
        assertEquals(listOf(501L, 502L, 503L), Store(ctx).loadSagas().single().items)
        assertTrue(rule.onAllNodesWithText("AHORA MISMO").fetchSemanticsNodes().isNotEmpty())
        rule.onRoot().performTouchInput { swipeUp() }; shoot("4_saga_abajo")
        // Un libro leído, desde la saga: su ficha; y «volver» lleva a la saga
        rule.onAllNodesWithText("El nombre del viento").onLast().performScrollTo().performClick(); rule.waitForIdle()
        shoot("5_libro")
        assertTrue(rule.onAllNodesWithText("Saga · Crónica del asesino de reyes", substring = true).fetchSemanticsNodes().isNotEmpty())
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        assertTrue("vuelve a la saga", rule.onAllNodesWithText("EN ORDEN DE LECTURA").fetchSemanticsNodes().isNotEmpty())
        // Editar: subir el tercero al segundo puesto
        rule.onAllNodesWithText("EDITAR")[0].performClick(); rule.waitForIdle()
        shoot("6_editar")
        rule.onAllNodesWithText("↑")[2].performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(listOf(501L, 503L, 502L), Store(ctx).loadSagas().single().items)
        rule.onAllNodesWithText("LISTO")[0].performClick(); rule.waitForIdle()
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        shoot("7_lista")
    }

    /** El buscador de portadas de una saga: empieza en la web de su categoría y, sin clave de SteamGridDB, dice dónde ponerla. */
    @Test fun buscador() {
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) {
            androidx.compose.foundation.layout.Box(Modifier.padding(24.dp)) { SagaCoverSearchBody("Mass Effect", GAMES, "", "", {}) }
        } } }
        rule.waitForIdle()
        shoot("11_buscador")
        assertTrue(rule.onAllNodesWithText("Añade tu clave de SteamGridDB", substring = true).fetchSemanticsNodes().isNotEmpty())
        for (t in listOf("STEAMGRIDDB", "OPEN LIBRARY", "TMDB", "TVMAZE")) assertTrue(t, rule.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty())
        assertEquals(CoverSource.LIBROS, coverSourceFor(BOOKS))
        assertEquals(CoverSource.SGDB, coverSourceFor(GAMES))
        assertEquals(CoverSource.PELIS, coverSourceFor(customKind(5, "Pelis", listOf("Guion"), "Viendo", 0, SRC_MOVIE)))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() = this.onNode(androidx.compose.ui.test.isRoot())
}

@Config(sdk = [34], qualifiers = "w1260dp-h840dp-land-xhdpi")
class SagasOppoHorizontalTest : SagasTest() { override val prefijo get() = "oppo_h_" }
