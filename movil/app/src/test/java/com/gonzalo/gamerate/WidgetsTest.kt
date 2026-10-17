package com.gonzalo.gamerate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Los widgets dibujados con datos de ejemplo, en claro y en oscuro y a varios tamaños, para revisar el diseño:
 *   gradle :app:testDebugUnitTest --tests '*WidgetsTest*'
 * Quedan en app/build/outputs/widgets/. Con ATRIO_ARTE=<carpeta con portadas de Steam> usa portadas de verdad;
 * con WPREV=<carpeta>, deja ahí las vistas previas del selector de widgets.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val out = File("build/outputs/widgets").apply { mkdirs() }
    private val arte = System.getenv("ATRIO_ARTE")?.let { File(it) }?.takeIf { it.isDirectory }
        ?.listFiles()?.filter { it.name.endsWith("_library_600x900.jpg") }?.sortedBy { it.name }.orEmpty()
    private val heroes = System.getenv("ATRIO_ARTE")?.let { File(it) }?.takeIf { it.isDirectory }
        ?.listFiles()?.filter { it.name.endsWith("_library_hero.jpg") }?.sortedBy { it.name }.orEmpty()
    private var n = 0

    /** Portada de mentira (degradado y una letra) o, si hay, una de verdad. */
    private fun cover(name: String, a: Int, b: Int): String {
        arte.getOrNull(n++)?.let { return "file://${it.absolutePath}" }
        val f = File(ctx.cacheDir, "w_${name.hashCode()}.png")
        if (!f.exists()) {
            val bmp = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawRect(0f, 0f, 300f, 450f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 450f, a, b, Shader.TileMode.CLAMP) })
            c.drawText(name.take(1), 30f, 160f, Paint().apply { color = 0x55FFFFFF; textSize = 120f; isAntiAlias = true; isFakeBoldText = true })
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return "file://${f.absolutePath}"
    }

    private fun seed() {
        val s = Store(ctx)
        val hoy = LocalDate.now()
        val d = hoy.toEpochDay()
        val haceUnAno = hoy.minusYears(1).toEpochDay()
        val haceDos = hoy.minusYears(2).toEpochDay()
        val mes = hoy.withDayOfMonth(1).toEpochDay()
        s.save(listOf(
            Game(101, "Clair Obscur: Expedition 33", cover("Clair", 0xFF8E2DE2.toInt(), 0xFF4A00E0.toInt()), 94, creator = "Sandfall Interactive",
                start = mes, end = minOf(d, mes + 3), hero = heroes.getOrNull(0)?.let { "file://${it.absolutePath}" } ?: ""),
            Game(102, "Hades II", cover("Hades", 0xFFE96443.toInt(), 0xFF904E95.toInt()), 91, creator = "Supergiant", end = haceUnAno),
            Game(103, "Hollow Knight: Silksong", cover("Silk", 0xFF134E5E.toInt(), 0xFF71B280.toInt()), 88, creator = "Team Cherry", end = minOf(d, mes + 9)),
            Game(104, "Balatro", cover("Balatro", 0xFFDA4453.toInt(), 0xFF89216B.toInt()), 85, creator = "LocalThunk", end = minOf(d, mes + 9)),
            Game(105, "The Witcher 3: Wild Hunt", cover("Witcher", 0xFF3A3A3A.toInt(), 0xFF8B0000.toInt()), 97, creator = "CD Projekt Red", end = haceDos)
        ), 0)
        s.savePlays(listOf(
            Play(201, "Deadlock", cover("Dead", 0xFFF7971E.toInt(), 0xFF7F3800.toInt()), heroes.getOrNull(1)?.let { "file://${it.absolutePath}" } ?: "", 1, emptyList(),
                creator = "Valve", minutes = 3120, start = mes + 1, updated = System.currentTimeMillis(),
                segs = listOf(Seg("a", System.currentTimeMillis() - 86_400_000, System.currentTimeMillis(), 600, "atrio"))),
            Play(202, "Elden Ring Nightreign", cover("Elden", 0xFFC9D6FF.toInt(), 0xFF6A5B3B.toInt()), "", 1, emptyList(), creator = "FromSoftware",
                minutes = 840, ttbMain = 2400, start = mes + 5, updated = System.currentTimeMillis() - 3600_000)
        ), 0)
        s.savePlays(listOf(
            Play(301, "El nombre del viento", cover("Viento", 0xFF603813.toInt(), 0xFFB29F94.toInt()), "", 1, emptyList(), chapter = 34, pages = 880,
                creator = "Patrick Rothfuss", start = mes + 2, updated = System.currentTimeMillis() - 7200_000),
            Play(303, "Klara y el Sol", cover("Klara", 0xFF3E5151.toInt(), 0xFFDECBA4.toInt()), "", 0, emptyList(), creator = "Kazuo Ishiguro"),
            Play(302, "Proyecto Hail Mary", cover("Hail", 0xFF0F2027.toInt(), 0xFF2C5364.toInt()), "", 1, emptyList(), creator = "Andy Weir",
                updated = System.currentTimeMillis() - 86_400_000)
        ), 1)
        s.save(listOf(Game(401, "Dune", cover("Dune", 0xFFF2994A.toInt(), 0xFFF2C94C.toInt()), 89, creator = "Frank Herbert", end = minOf(d, mes + 14))), 1)
        s.setGoal(0, hoy.year, 24); s.setGoal(1, hoy.year, 12)
        s.myName = "Gonzalo"
        // El libro que se está leyendo tiene su archivo, lo leído y dos subrayados
        BookFiles.put(ctx, 301, JSONObject().put("uri", "content://prueba/viento.epub").put("name", "viento.epub").put("fraction", 0.43)
            .put("read", System.currentTimeMillis())
            .put("stats", JSONObject().put("days", JSONObject().apply {
                // Lectura de los últimos días (para «Tu actividad»): racha de 6 días
                for (k in 0..5) put(hoy.minusDays(k.toLong()).toString(), (10 + k * 17) * 60_000L)
                for (k in listOf(9, 12, 20, 33, 40)) put(hoy.minusDays(k.toLong()).toString(), 45 * 60_000L)
            }))
            .put("notes", JSONArray()
                .put(JSONObject().put("value", "epubcfi(/6/4)").put("text", "Hay tres cosas que todo hombre sabio teme: el mar en una tormenta, una noche sin luna y la ira de un hombre amable.").put("created", 1L).put("index", 1))
                .put(JSONObject().put("value", "epubcfi(/6/8)").put("text", "Las palabras son pálidas sombras de nombres olvidados.").put("created", 2L).put("index", 2))))
    }

    private fun guardar(nombre: String, b: Bitmap) = File(out, "$nombre.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test
    fun todos() = runBlocking {
        seed()
        val tamanos = mapOf(
            WTipo.LEYENDO to listOf(480f to 220f, 330f to 165f, 165f to 165f, 165f to 330f),
            WTipo.ESTANTE to listOf(480f to 220f, 330f to 165f, 330f to 120f),
            WTipo.CALENDARIO to listOf(480f to 460f, 330f to 340f, 330f to 250f),
            WTipo.PORTADA to listOf(480f to 340f, 330f to 250f, 165f to 165f),
            WTipo.ANO to listOf(165f to 165f, 330f to 165f),
            WTipo.CITA to listOf(480f to 220f, 330f to 165f, 330f to 250f),
            WTipo.EFEMERIDE to listOf(330f to 165f, 165f to 165f),
            WTipo.MARCA to listOf(330f to 80f, 330f to 165f, 165f to 60f),
            WTipo.MARCA_PAPEL to listOf(330f to 165f, 330f to 80f, 165f to 165f, 480f to 220f),
            WTipo.SIGUIENTE to listOf(480f to 220f, 330f to 165f, 165f to 165f),
            WTipo.ACTIVIDAD to listOf(480f to 220f, 330f to 165f, 165f to 165f)
        )
        val prev = System.getenv("WPREV")?.let { File(it).apply { mkdirs() } }
        for ((t, ls) in tamanos) for ((w, h) in ls) {
            val p = Widgets.componer(ctx, t, 1, w, h)
            assertTrue("$t sin zonas", p.zonas.isNotEmpty())
            for (noche in listOf(false, true)) guardar("${t.name.lowercase()}_${w.toInt()}x${h.toInt()}_${if (noche) "oscuro" else "claro"}", p.bitmap(ctx, w, h, noche, 3f))
            if (prev != null && (w to h) == ls.first { it.first < 400f }) File(prev, "w_prev_${t.name.lowercase().replace("ano", "ano")}.png").outputStream().use {
                p.bitmap(ctx, w, h, false, 1.6f).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        // La racha: 6 días seguidos de lectura
        assertEquals(6, Widgets.racha(Widgets.minutosPorDia(WDatos(ctx))))
        // Las esquinas quedan recortadas (transparentes) y el centro, pintado
        val b = Widgets.componer(ctx, WTipo.LEYENDO, 1, 330f, 165f).bitmap(ctx, 330f, 165f, false, 2f)
        assertEquals(0, Color.alpha(b.getPixel(0, 0)))
        assertEquals(255, Color.alpha(b.getPixel(b.width / 2, b.height / 2)))
    }

    @Test
    fun vacios() = runBlocking {
        // Sin nada guardado: cada widget dice qué hacer, sin romperse
        for (t in WTipo.entries) {
            val (w, h) = Widgets.porDefecto(t)
            val p = Widgets.componer(ctx, t, 2, w, h)
            guardar("vacio_${t.name.lowercase()}", p.bitmap(ctx, w, h, false, 2f))
            assertTrue(p.zonas.isNotEmpty())
        }
    }

    @Test
    fun seMontanYSeTocan() = runBlocking {
        seed()
        // El widget de verdad (RemoteViews) se infla, con sus zonas para tocar
        for (t in WTipo.entries) {
            val (w, h) = Widgets.porDefecto(t)
            val rv = Widgets.vistas(ctx, t, 3, w, h)
            val v = rv.apply(ctx, FrameLayout(ctx))
            val zonas = v.findViewById<FrameLayout>(R.id.w_zonas)
            assertTrue("$t sin zonas", zonas.childCount > 0)
        }
        // El calendario: la rejilla tiene las semanas del mes, y se cambia de categoría y de mes
        Widgets.accion(ctx, 3, "cat")
        Widgets.accion(ctx, 3, "mes-")
        val p = Widgets.componer(ctx, WTipo.CALENDARIO, 3, 330f, 340f)
        guardar("calendario_juegos_mes_anterior", p.bitmap(ctx, 330f, 340f, false, 2f))
        val rejilla = p.zonas.first { it.celdas != null }.celdas!!
        assertTrue(rejilla.size in 4..6 && rejilla.all { it.size == 7 })
        Widgets.accion(ctx, 3, "hoy")
        // Leyendo: el libro con archivo se abre en el lector
        val pi = Widgets.componer(ctx, WTipo.LEYENDO, 4, 330f, 165f).zonas.first().toque!!
        val i = org.robolectric.Shadows.shadowOf(pi).savedIntent
        assertEquals(ReaderActivity::class.java.name, i.component?.className)
        assertEquals(301L, i.getLongExtra("id", 0L))
    }
}
