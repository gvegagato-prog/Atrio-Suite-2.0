package com.gonzalo.gamerate

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Elegir una imagen de tu nube: se abre en la carpeta de la ficha, se busca por nombre en todo el kDrive y se usa la elegida. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class NubePickerTest {
    // Los efectos (LaunchedEffect, rememberCoroutineScope) en un despachador de pruebas: lo que vuelve de la red de
    // mentira se reanuda en el hilo de la prueba, como en el móvil (en el principal). Con el de por defecto se reanudaba
    // a veces en el hilo de la red, y el diálogo se quedaba en «Abriendo…» sin repintarse (fallaba de vez en cuando).
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @get:Rule val rule = createComposeRule(kotlinx.coroutines.test.StandardTestDispatcher())
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var k: FakeKDrive

    @Before fun up() {
        k = FakeKDrive(); KDrive.api = k.url; KDrive.share = k.url
        KDrive.save(ctx, "tok", "777")
        fun png(c: Int) = java.io.ByteArrayOutputStream().also { Bitmap.createBitmap(30, 45, Bitmap.Config.ARGB_8888).apply { eraseColor(c) }.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        // Mi carpeta/Atrio/Libros/Dune/Portadas/portada.png y Mi carpeta/Atrio/Juegos/Hades/dragon_fondo.png
        k.files[10] = FakeKDrive.F(10, "Atrio", true, 2); k.files[11] = FakeKDrive.F(11, "Libros", true, 10)
        k.files[12] = FakeKDrive.F(12, "Dune", true, 11); k.files[13] = FakeKDrive.F(13, "Portadas", true, 12)
        k.files[14] = FakeKDrive.F(14, "portada.png", false, 13, png(0xFFAA3300.toInt()))
        k.files[16] = FakeKDrive.F(16, "Juegos", true, 10); k.files[17] = FakeKDrive.F(17, "Hades", true, 16)
        k.files[18] = FakeKDrive.F(18, "dragon_fondo.png", false, 17, png(0xFF2244AA.toInt()))
    }
    @After fun down() { k.stop() }

    @Test fun buscaYCarpetaDeLaFicha() {
        val r = KDrive.busca(ctx, "dragon")
        assertEquals(listOf(18L), r.map { it.id })
        assertEquals("Mi carpeta/Atrio/Juegos/Hades", r.single().path)
        assertEquals(12L to "Mi carpeta/Atrio/Libros/Dune", KDrive.carpetaDeFicha(ctx, "Dune"))
        assertEquals("sin carpeta: null", null, KDrive.carpetaDeFicha(ctx, "Otra cosa"))
    }

    @Test fun elegirDeTuNube() {
        var elegida = ""
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { NubePicker("Dune", onPick = { elegida = it }) {} }
            }
        }
        // Abre en la carpeta de la ficha y enseña las portadas de dentro
        espera("la carpeta de la ficha") { rule.onAllNodesWithText("portada.png", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("PORTADAS", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        shoot("1_carpeta")
        // Buscar en todo tu kDrive
        rule.onAllNodesWithText("Buscar imágenes en tu nube", ignoreCase = true)[0].let { rule.onNode(hasSetTextAction()).performTextReplacement("dragon") }
        rule.onNodeWithContentDescription("Buscar").performClick()
        espera("los resultados de buscar") { rule.onAllNodesWithText("Atrio/Juegos/Hades", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        shoot("2_buscar")
        rule.onAllNodesWithText("Atrio/Juegos/Hades", ignoreCase = true)[0].performClick()
        espera("bajar la elegida") { elegida.isNotEmpty() }
        assertTrue("la elegida queda como foto de este móvil: $elegida", Photos.isLocal(elegida) && Photos.here(ctx, elegida))
    }

    /**
     * Espera a que se cumpla [c] (hasta 90 s: con todas las pruebas a la vez, la red de mentira va lenta). El diálogo va en su propia ventana: en Robolectric solo se vuelve a maquetar
     * si avanza el reloj de la app, y los efectos solo corren si avanza el de Compose, así que aquí se hacen avanzar los dos
     * (y se deja trabajar a la red de mentira). Si no llega, dice en qué paso y qué había en pantalla.
     */
    private fun espera(paso: String, c: () -> Boolean) {
        val fin = System.currentTimeMillis() + 90_000
        while (System.currentTimeMillis() < fin) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            rule.mainClock.advanceTimeBy(50)
            rule.waitForIdle()
            if (runCatching(c).getOrDefault(false)) return
            Thread.sleep(30)
        }
        // Lo que hay en pantalla (también en el diálogo, que va en su propia ventana), para saber dónde se quedó
        val textos = mutableListOf<String>()
        fun lee(n: androidx.compose.ui.semantics.SemanticsNode) {
            val k = androidx.compose.ui.semantics.SemanticsProperties.Text
            if (n.config.contains(k)) textos += n.config[k].joinToString { it.text }
            n.children.forEach { lee(it) }
        }
        runCatching { rule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().forEach { lee(it) } }
        throw AssertionError("no ha llegado a tiempo: $paso (en pantalla: ${textos.joinToString(" | ").take(600)})")
    }

    private fun shoot(n: String) {
        rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/nubepicker_$n.png")
    }
}
