package com.gonzalo.gamerate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Las ediciones de la revista: cada una con su cabecera, su papel y sus letras; elegir una en Apariencia; que viaje con la cuenta. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
open class EdicionesTest {
    open val prefijo: String get() = ""
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun cover(name: String, a: Int, b: Int): String {
        val f = File(ctx.cacheDir, "e_${name.hashCode()}.png")
        if (f.exists()) return f.absolutePath
        val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, 300f, 450f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 450f, a, b, Shader.TileMode.CLAMP) })
        c.drawText(name.take(1), 30f, 160f, Paint().apply { color = 0x55FFFFFF; textSize = 120f; isAntiAlias = true; isFakeBoldText = true })
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    private fun seed(): Store {
        val s = Store(ctx)
        val d = LocalDate.now().toEpochDay()
        s.myName = "Gonzalo"
        s.save(listOf(
            Game(101, "Clair Obscur: Expedition 33", cover("Clair", 0xFF8E2DE2.toInt(), 0xFF4A00E0.toInt()), 97, creator = "Sandfall Interactive", end = d - 20),
            Game(102, "Hades II", cover("Hades", 0xFFE96443.toInt(), 0xFF904E95.toInt()), 91, creator = "Supergiant", end = d - 60),
            Game(103, "Hollow Knight: Silksong", cover("Silk", 0xFF134E5E.toInt(), 0xFF71B280.toInt()), 88, creator = "Team Cherry", end = d - 3),
        ), 0)
        s.savePlays(listOf(
            Play(201, "Deadlock", cover("Dead", 0xFFF7971E.toInt(), 0xFF7F3800.toInt()), "", 1, emptyList(), creator = "Valve", minutes = 3120, updated = System.currentTimeMillis()),
            Play(202, "Elden Ring Nightreign", cover("Elden", 0xFFC9D6FF.toInt(), 0xFF6A5B3B.toInt()), "", 1, emptyList(), creator = "FromSoftware", minutes = 840, updated = System.currentTimeMillis() - 3600_000),
        ), 0)
        s.savePlays(listOf(Play(301, "El nombre del viento", cover("Viento", 0xFF603813.toInt(), 0xFFB29F94.toInt()), "", 1, emptyList(), chapter = 34, pages = 880, creator = "Patrick Rothfuss", updated = System.currentTimeMillis() - 7200_000)), 1)
        return s
    }

    private fun shoot(name: String) {
        rule.waitForIdle(); Thread.sleep(300); rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/${prefijo}ed_$name.png")
    }

    @Test
    fun portadas() {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val store = seed()
        Skin.load(ctx); Skin.shell = 2
        Mode.themeMode = 1; Mode.dark = false
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { App(store, 0L, false, "") {} }
            }
        }
        for (oscuro in listOf(false, true)) for (e in EDICIONES) {
            Mode.dark = oscuro
            Ediciones.aplicar(e, ctx)
            rule.waitForIdle()
            assertTrue("${e.id}: su cabecera «${e.cabecera}»", rule.onAllNodesWithText(e.cabecera).fetchSemanticsNodes().isNotEmpty())
            assertEquals("${e.id}: su papel", e.paleta.id, Skin.palette.id)
            assertEquals("${e.id}: su letra de títulos", e.titulos, Display)
            shoot("${if (oscuro) "n" else "c"}_${e.id}")
        }
        // Una ficha con la de música (titulares en mayúsculas) y la de fanzine (fotos en blanco y negro)
        Mode.dark = false
        for (id in listOf("musica", "cultural")) {
            Ediciones.aplicar(Ediciones.porId(id), ctx)
            rule.waitForIdle()
            rule.onAllNodesWithText(if (id == "musica") "HOLLOW KNIGHT: SILKSONG" else "Hollow Knight: Silksong")[0].performScrollTo().performClick()
            shoot("ficha_$id")
            androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        }
        Ediciones.aplicar(EDICIONES.first(), ctx)
    }

    @Test
    fun elegirEnApariencia() {
        Skin.load(ctx); Skin.shell = 2; Mode.themeMode = 1; Mode.dark = false
        Store(ctx).myName = "Gonzalo"
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) { StyleScreen(1, {}) {} }
            }
        }
        shoot("apariencia")
        rule.onAllNodesWithText("MODA")[0].performScrollTo().performClick(); rule.waitForIdle()
        assertEquals("moda", Skin.edicion)
        assertEquals("el acento vuelve a ser el de la edición", "edicion", Skin.accentId)
        assertEquals("se guarda", "moda", ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE).getString("skin_edicion", ""))
        // y viaja con la cuenta
        assertTrue(Store(ctx).ajustesJson().getJSONObject("skin").optString("skin_edicion") == "moda")
        shoot("apariencia_moda")
        // un acento encima de la edición
        rule.onAllNodesWithText("FUCSIA")[0].performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(0xFFE6007E, Skin.tones.general)
        rule.onAllNodesWithText("SALVIA")[0].performScrollTo(); shoot("apariencia_acentos")
        Ediciones.aplicar(EDICIONES.first(), ctx)
        assertEquals("con «De la edición», el acento de Atrio", PALETTES.first { it.id == "revista" }.light.general, Skin.tones.general)
    }
}
