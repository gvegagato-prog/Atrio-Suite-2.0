package com.gonzalo.gamerate

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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
class Capturas6Test {
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

    private fun seed(): Store {
        val s = Store(ctx)
        val d = LocalDate.now().toEpochDay()
        s.myName = "Gonzalo"
        s.save(listOf(
            Game(101, "Clair Obscur: Expedition 33", cover("Clair", 0xFF8E2DE2.toInt(), 0xFF4A00E0.toInt()), 94, 96, 92, 98, 90, creator = "Sandfall Interactive", end = d - 20, fav = true),
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
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() = this.onNode(androidx.compose.ui.test.isRoot())

    @Test
    fun recorrido() {
        // Solo al pedir capturas (-Proborazzi.test.record=true): en la integración continua no se hace
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        val store = seed()
        Skin.load(ctx)
        Mode.dark = true
        Mode.themeMode = 2
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    App(store, 0L, false, "") {}
                }
            }
        }
        shoot("01_inicio")
        rule.onNodeWithContentDescription("Juegos").performClick(); shoot("02_juegos")
        rule.onAllNodesWithText("Deadlock")[0].performClick(); shoot("02b_ficha_jugando")
        androidx.test.espresso.Espresso.pressBack()
        rule.onAllNodesWithText("Pasados")[0].performClick(); shoot("03_juegos_valorados")
        rule.onAllNodesWithText("Hades II")[0].performClick(); shoot("03b_ficha_valorado")
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithContentDescription("Libros").performClick(); shoot("04_libros")
        rule.onAllNodesWithText("Descubrir")[0].performClick(); shoot("08_descubrir_intro")
        rule.onAllNodesWithText("5")[0].performClick(); shoot("08b_pregunta")
        repeat(5) { i ->
            rule.waitForIdle(); Thread.sleep(300)
            rule.onAllNodesWithText(if (i % 2 == 0) "A" else "B")[0].performClick()
        }
        rule.waitUntil(60_000) { rule.onAllNodesWithText("A mi lista").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("No ha salido nada", substring = true).fetchSemanticsNodes().isNotEmpty() }
        shoot("08c_resultados")
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithContentDescription("Cine").performClick(); shoot("05_cine")
        rule.onNodeWithContentDescription("Diario").performClick(); shoot("06_diario")
        rule.onNodeWithContentDescription("Inicio").performClick()
        rule.onNodeWithContentDescription("Perfil y ajustes").performClick(); shoot("07_perfil")
    }
}
