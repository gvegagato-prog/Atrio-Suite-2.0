package com.gonzalo.gamerate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Lo encuadrado se ve en la casilla igual que en el editor: la foto pegada a un borde no deja hueco.
 * (Antes la casilla recortaba la foto y DESPUÉS la movía, y al pegarla arriba quedaba una franja vacía.)
 * Foto vertical (800×1000) con una franja ROJA arriba y AZUL abajo, en una casilla apaisada (320×200 dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EncuadreTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun foto(): String {
        val f = File(ctx.cacheDir, "franjas.png")
        val bmp = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF808080.toInt())
        c.drawRect(0f, 0f, 800f, 100f, Paint().apply { color = 0xFFFF0000.toInt() })
        c.drawRect(0f, 900f, 800f, 1000f, Paint().apply { color = 0xFF0000FF.toInt() })
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    /** Color de la casilla en (x, y) en fracciones, con la foto colocada como [c]. */
    private fun pinta(c: MagCell, puntos: List<Pair<Float, Float>>): List<Int> {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        rule.setContent {
            Box(Modifier.size(320.dp, 200.dp).background(Color.White).clipToBounds().testTag("caja")) {
                FramedImage(c, Modifier.matchParentSize())
            }
        }
        rule.waitUntil(15_000) { MagImages.ratios[c.image] != null }
        rule.waitForIdle()
        // Captura de la pantalla con el renderizado de verdad (captureToImage no funciona en Robolectric)
        val r = rule.onNodeWithTag("caja").fetchSemanticsNode().boundsInWindow
        val png = File(ctx.cacheDir, "encuadre_${System.nanoTime()}.png")
        val antes = System.getProperty("roborazzi.test.record")
        System.setProperty("roborazzi.test.record", "true")
        try { com.github.takahirom.roborazzi.captureScreenRoboImage(png.absolutePath) } finally {
            if (antes == null) System.clearProperty("roborazzi.test.record") else System.setProperty("roborazzi.test.record", antes)
        }
        val b = android.graphics.BitmapFactory.decodeFile(png.absolutePath)
        // (un píxel hacia dentro de cada borde)
        return puntos.map { (x, y) ->
            val px = (r.left + 1 + x * (r.width - 3)).toInt()
            val py = (r.top + 1 + y * (r.height - 3)).toInt()
            b.getPixel(px, py) and 0xFFFFFF
        }
    }

    // Casilla 1,6 y foto 0,8: la foto mide 320×400 y sobran 100 dp por arriba y 100 por abajo (oy ±0,5 de la casilla)

    @Test fun pegadaArribaNoDejaHueco() {
        val f = foto()
        val (arriba, abajo) = pinta(MagCell("imagen", image = f, zoom = 1f, oy = 0.5f, asp = 1.6f), listOf(0.5f to 0f, 0.5f to 1f))
        assertEquals("arriba tiene que verse la franja roja de la foto, no un hueco", 0xFF0000, arriba)
        assertEquals(0x808080, abajo)
    }

    @Test fun pegadaAbajoNoDejaHueco() {
        val f = foto()
        val (arriba, abajo) = pinta(MagCell("imagen", image = f, zoom = 1f, oy = -0.5f, asp = 1.6f), listOf(0.5f to 0f, 0.5f to 1f))
        assertEquals(0x808080, arriba)
        assertEquals("abajo tiene que verse la franja azul de la foto, no un hueco", 0x0000FF, abajo)
    }

    @Test fun desplazadaDeMasSeQuedaEnElBorde() {
        // Una casilla de una versión anterior, con la foto movida mucho más allá del borde: se queda pegada, sin hueco
        val f = foto()
        val (arriba, abajo) = pinta(MagCell("imagen", image = f, zoom = 1f, oy = 2f, asp = 1.6f), listOf(0.5f to 0f, 0.5f to 1f))
        assertEquals(0xFF0000, arriba)
        assertEquals(0x808080, abajo)
    }

    @Test fun centradaNoEnseñaLasFranjas() {
        val f = foto()
        val (arriba, abajo) = pinta(MagCell("imagen", image = f, zoom = 1f, asp = 1.6f), listOf(0.5f to 0f, 0.5f to 1f))
        assertEquals(0x808080, arriba)
        assertEquals(0x808080, abajo)
    }

    private fun raiz(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "herramientas/casos/maqueta.json").exists()) dir = dir.parentFile
        return dir ?: error("no encuentro herramientas/casos")
    }

    /** Los mismos casos que el PC (herramientas/casos/maqueta.json): dónde se dibuja una imagen encuadrada. */
    @Test fun casosCompartidosConElPc() {
        val casos = org.json.JSONObject(File(raiz(), "herramientas/casos/maqueta.json").readText()).getJSONArray("lugar")
        for (i in 0 until casos.length()) {
            val c = casos.getJSONObject(i)
            val m = c.getJSONObject("c"); val e = c.getJSONObject("esperado"); val caja = c.getJSONArray("caja")
            val ratio = c.getDouble("ratio").toFloat()
            MagImages.ratios.remove("caso")
            if (ratio > 0f) MagImages.ratios["caso"] = ratio
            val celda = MagCell("imagen", image = "caso", fit = m.getBoolean("fit"), zoom = m.getDouble("zoom").toFloat(), ox = m.getDouble("ox").toFloat(), oy = m.getDouble("oy").toFloat())
            val (z, tx, ty) = magPlace(celda, caja.getDouble(0).toFloat(), caja.getDouble(1).toFloat())
            assertEquals("${c.getString("nombre")}: zoom", e.getDouble("z"), z.toDouble(), 1e-3)
            assertEquals("${c.getString("nombre")}: tx", e.getDouble("tx"), tx.toDouble(), 1e-3)
            assertEquals("${c.getString("nombre")}: ty", e.getDouble("ty"), ty.toDouble(), 1e-3)
        }
    }

    /** Los mismos casos que el PC: lo de España en la respuesta de «watch/providers» de TMDB. */
    @Test fun proveedoresIgualQueElPc() {
        val casos = org.json.JSONObject(File(raiz(), "herramientas/casos/maqueta.json").readText()).getJSONArray("proveedores")
        for (i in 0 until casos.length()) {
            val c = casos.getJSONObject(i); val e = c.getJSONObject("esperado")
            val d = Tmdb.proveedoresDe(c.getJSONObject("entrada"))
            fun plats(l: List<Plat>) = l.map { it.nombre + "|" + it.logo }
            fun esperadas(k: String) = (0 until e.getJSONArray(k).length()).map { e.getJSONArray(k).getJSONObject(it) }.map { it.getString("nombre") + "|" + it.getString("logo") }
            assertEquals("${c.getString("nombre")}: link", e.getString("link"), d.link)
            assertEquals("${c.getString("nombre")}: suscripción", esperadas("suscripcion"), plats(d.suscripcion))
            assertEquals("${c.getString("nombre")}: alquiler", esperadas("alquiler"), plats(d.alquiler))
            assertEquals("${c.getString("nombre")}: compra", esperadas("compra"), plats(d.compra))
            assertEquals("${c.getString("nombre")}: gratis", esperadas("gratis"), plats(d.gratis))
        }
    }
}
