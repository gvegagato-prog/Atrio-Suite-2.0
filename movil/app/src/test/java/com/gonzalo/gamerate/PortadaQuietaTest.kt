package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Al pasar de una portada a otra con el dedo, el marco no cambia de alto ni un fotograma (antes «tintineaba»: cada
 * página nueva del carrusel salía un fotograma con el titular sin ajustar y todas las imágenes cambiaban de alto).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PortadaQuietaTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun elMarcoNoSaltaAlDeslizar() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val s = Store(ctx)
        // Titulares de largo distinto, con palabras largas (las que obligan a encoger la letra)
        s.savePlays(listOf(
            Play(201, "Cyberpunk 2077", "", "", 1, emptyList(), creator = "CD Projekt Red", minutes = 728, updated = System.currentTimeMillis()),
            Play(202, "Kingdom Come: Deliverance II", "", "", 1, emptyList(), creator = "Warhorse", updated = System.currentTimeMillis() - 1000),
            Play(203, "Hellblade: Senua's Sacrifice", "", "", 1, emptyList(), creator = "Ninja Theory", updated = System.currentTimeMillis() - 2000),
            Play(204, "Supercalifragilisticoespialidoso", "", "", 1, emptyList(), updated = System.currentTimeMillis() - 3000),
        ), 0)
        Skin.load(ctx); Mode.dark = true; Mode.themeMode = 2; Skin.applyShell(2, ctx)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { App(s, 0L, false, "") {} }
            }
        }
        rule.waitForIdle()
        // Dónde está (en vertical) cada titular que se ve dentro del marco: al deslizar en horizontal, no debe moverse
        val marco = { rule.onNodeWithTag("portada").fetchSemanticsNode().boundsInRoot }
        val titulos = listOf("Cyberpunk 2077", "Kingdom Come: Deliverance II", "Hellblade: Senua's Sacrifice", "Supercalifragilisticoespialidoso")
        val posiciones = mutableMapOf<String, MutableSet<Int>>()
        val altos = mutableSetOf<Int>()
        fun mira() {
            val m = marco(); altos += Math.round(m.height)
            for (t in titulos) for (n in rule.onAllNodesWithText(t, ignoreCase = true, useUnmergedTree = true).fetchSemanticsNodes()) {
                val r = n.boundsInRoot
                if (r.width > 0 && r.left >= m.left - 1 && r.right <= m.right + 1) posiciones.getOrPut(t) { mutableSetOf() } += Math.round(r.top)
            }
        }
        mira()
        rule.mainClock.autoAdvance = false
        // Cuatro pasadas lentas con el dedo (una vuelta entera), fotograma a fotograma
        repeat(4) {
            rule.onNodeWithTag("portada").performTouchInput { down(center.copy(x = width * 0.9f)) }
            repeat(30) {
                rule.onNodeWithTag("portada").performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(-width * 0.025f, 0f)) }
                rule.mainClock.advanceTimeByFrame(); mira()
            }
            rule.onNodeWithTag("portada").performTouchInput { up() }
            repeat(45) { rule.mainClock.advanceTimeByFrame(); mira() }
        }
        rule.mainClock.autoAdvance = true
        assertTrue("el marco cambia de alto al deslizar: $altos", altos.size == 1)
        for ((t, ys) in posiciones) assertTrue("«$t» salta de altura al deslizar: $ys", ys.size == 1)
        assertTrue("se han visto las cuatro portadas: ${posiciones.keys}", posiciones.size == 4)
    }
}
