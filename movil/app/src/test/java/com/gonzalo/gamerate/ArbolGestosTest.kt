package com.gonzalo.gamerate

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * El árbol de Escribir en el móvil: la ficha sigue al dedo mientras la arrastras (también si algo cambia en Escribir a
 * mitad, como al sincronizar) y, con el dedo encima de una ficha, se apaga lo que no está unido a ella.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ArbolGestosTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun limpia() {
        Escritos.reiniciaParaPruebas()
        File(ctx.filesDir, "escritos").deleteRecursively()
    }

    // (se dibuja la ventana a mano: la captura de Compose espera un redibujado que, con un dedo puesto, no llega en Robolectric)
    private fun foto(): Bitmap {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(android.graphics.Canvas(b)) }
        val n = rule.onNodeWithContentDescription("Árbol de relaciones").fetchSemanticsNode().boundsInWindow
        return Bitmap.createBitmap(b, n.left.toInt(), n.top.toInt(), n.width.toInt(), n.height.toInt())
    }
    /** Cuántos píxeles cambian en un cuadrado de lado 2·r alrededor de [c]. */
    private fun cambios(a: Bitmap, b: Bitmap, c: Offset, r: Int = 40): Int {
        var n = 0
        for (x in max(0, c.x.toInt() - r) until min(a.width, c.x.toInt() + r)) for (y in max(0, c.y.toInt() - r) until min(a.height, c.y.toInt() + r)) {
            val p = a.getPixel(x, y); val q = b.getPixel(x, y)
            if (abs((p shr 16 and 255) - (q shr 16 and 255)) + abs((p shr 8 and 255) - (q shr 8 and 255)) + abs((p and 255) - (q and 255)) > 30) n++
        }
        return n
    }

    @Test fun laFichaSigueAlDedoYApagaLoQueNoEstaUnido() {
        Mode.dark = false
        val e = Escritos.get(ctx)
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Prueba") }
        val a = e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "Aldara"); put("color", "#B0102F"); put("ax", -200); put("ay", 0) }
        val b = e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "Cuervo"); put("color", "#4A6FA5"); put("ax", 200); put("ay", 0) }
        e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "Solo"); put("color", "#5B8C5A"); put("ax", 0); put("ay", 150) }
        e.nuevo(Escritos.T_REL, o.id) { put("a", a.id); put("b", b.id); put("titulo", "rivales") }
        var ver by mutableIntStateOf(0)
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { ArbolObra(e, o, ver, AbreEscribir(), null) }
            }
        }
        rule.waitForIdle()
        // Dónde queda cada ficha en la pantalla (lo mismo que hace el árbol al entrar: que se vea todo)
        val nodo = rule.onNodeWithContentDescription("Árbol de relaciones").fetchSemanticsNode()
        val w = nodo.size.width.toFloat(); val h = nodo.size.height.toFloat()
        val dens = ctx.resources.displayMetrics.density
        val minX = -200f - 110; val maxX = 200f + 110; val minY = 0f - 70; val maxY = 150f + 70
        val k = min(1.4f, max(0.3f, min(w / dens / (maxX - minX), h / dens / (maxY - minY))))
        val vx = w / 2f - (minX + maxX) / 2 * dens * k; val vy = h / 2f - (minY + maxY) / 2 * dens * k
        fun enPantalla(x: Float, y: Float) = Offset(vx + x * dens * k, vy + y * dens * k)
        val pA = enPantalla(-200f, 0f); val pSolo = enPantalla(0f, 150f)

        // Algo cambia en Escribir (como una sincronización): antes, el gesto se quedaba con el mapa de posiciones viejo
        ver++; rule.waitForIdle()
        val antes = foto()
        // Dedo sobre Aldara: lo que no está unido a ella («Solo») se apaga
        rule.onNodeWithContentDescription("Árbol de relaciones").performTouchInput { down(pA) }
        rule.waitForIdle()
        val pulsada = foto()
        assertTrue("con el dedo encima de Aldara, «Solo» (que no está unido) se apaga", cambios(antes, pulsada, pSolo) > 50)
        // Arrastrar sin soltar: la ficha ya se ha movido (no espera a soltarla)
        rule.onNodeWithContentDescription("Árbol de relaciones").performTouchInput { moveBy(Offset(0f, 30f)); moveBy(Offset(0f, 120f)); moveBy(Offset(0f, 120f)) }
        rule.waitForIdle()
        val moviendo = foto()
        assertTrue("al arrastrar, Aldara ya no está donde estaba", cambios(pulsada, moviendo, pA) > 200)
        // (la ficha se coge al pasar el margen del toque, el primer paso de 30 px: desde ahí sigue lo que arrastras, sin saltar hacia el dedo)
        assertTrue("y está donde la lleva el dedo", cambios(pulsada, moviendo, pA + Offset(0f, 240f), 80) > 200)
        // A mitad del arrastre cambia otra cosa de Escribir (una sincronización, un guardado): el arrastre no se corta
        rule.runOnUiThread { e.edita(b.id) { put("resumen", "cambiado desde otro dispositivo") }; ver++ }
        rule.waitForIdle()
        // Justo en ese fotograma (antes de mover más el dedo), la ficha no vuelve a su sitio guardado: se quedaba un fotograma
        // en la posición de antes de arrastrarla y daba un parpadeo
        val trasCambio = foto()
        assertTrue("al cambiar algo a mitad, la ficha no salta a su sitio de antes: ${cambios(moviendo, trasCambio, pA + Offset(0f, 240f), 80)}", cambios(moviendo, trasCambio, pA + Offset(0f, 240f), 80) < 60)
        rule.onNodeWithContentDescription("Árbol de relaciones").performTouchInput { moveBy(Offset(120f, 0f)); moveBy(Offset(120f, 0f)) }
        rule.waitForIdle()
        val siguiendo = foto()
        assertTrue("tras el cambio, la ficha sigue al dedo", cambios(moviendo, siguiendo, pA + Offset(240f, 240f), 80) > 200)
        rule.onNodeWithContentDescription("Árbol de relaciones").performTouchInput { up() }
        rule.waitForIdle()
        // Al soltar se guarda donde quedó y «Solo» deja de estar apagado
        val guardada = e.get(a.id)!!
        assertTrue("se guarda donde quedó: ${guardada.i("ax")}, ${guardada.i("ay")}", guardada.i("ay") > 50 && guardada.i("ax") > -150)
    }

    /** «Unir dos fichas»: pulsar el botón y luego dos fichas funciona; también tras arrastrar, tras un cambio de Escribir y tras un toque cancelado. */
    @Test fun unirDosFichasSigueFuncionando() {
        Mode.dark = false
        val e = Escritos.get(ctx)
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Prueba") }
        val a = e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "Aldara"); put("color", "#B0102F"); put("ax", -150); put("ay", 0) }
        val b = e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "Cuervo"); put("color", "#4A6FA5"); put("ax", 150); put("ay", 0) }
        var ver by mutableIntStateOf(0)
        rule.setContent { MaterialTheme(colorScheme = appColors(), typography = AppTypography) { Surface(Modifier.fillMaxSize(), color = Paper) { ArbolObra(e, o, ver, AbreEscribir(), null) } } }
        rule.waitForIdle()
        val nodo = rule.onNodeWithContentDescription("Árbol de relaciones").fetchSemanticsNode()
        val w = nodo.size.width.toFloat(); val h = nodo.size.height.toFloat(); val dens = ctx.resources.displayMetrics.density
        val k = min(1.4f, max(0.3f, min(w / dens / 360f, h / dens / 160f)))
        fun en(x: Float, y: Float) = Offset(w / 2f + x * dens * k, h / 2f + y * dens * k)
        val arbol = { rule.onNodeWithContentDescription("Árbol de relaciones") }
        fun toca(p: Offset) { arbol().performTouchInput { down(p); up() }; rule.waitForIdle() }
        // Un arrastre, un cambio de Escribir y un toque que el sistema cancela (como un gesto de borde de pantalla)
        arbol().performTouchInput { down(en(-150f, 0f)); moveBy(Offset(0f, 60f)); moveBy(Offset(0f, 60f)) }
        rule.runOnUiThread { e.edita(b.id) { put("resumen", "x") }; ver++ }
        rule.waitForIdle()
        arbol().performTouchInput { up() }
        rule.waitForIdle()
        arbol().performTouchInput { down(en(0f, 120f)); cancel() }
        rule.waitForIdle()
        rule.onAllNodesWithText("Unir dos fichas", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("Pulsa la primera ficha", substring = true, ignoreCase = true)[0].assertIsDisplayed()
        toca(en(-150f, 60f / dens / k))  // Aldara, que se movió 60 px al arrastrarla (los primeros 60 son el margen del toque)
        rule.onAllNodesWithText("Ahora pulsa la ficha con la que unir a Aldara", substring = true, ignoreCase = true)[0].assertIsDisplayed()
        toca(en(150f, 0f))
        rule.onAllNodesWithText("Pulsa la primera ficha", substring = true, ignoreCase = true).assertCountEquals(0)
    }
}
