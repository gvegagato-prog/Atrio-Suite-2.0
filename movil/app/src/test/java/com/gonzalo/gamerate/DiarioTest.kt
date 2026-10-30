package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * El Diario: cada día con lo que escribiste y lo que hiciste con cada cosa (terminado, empezado, leído, jugado en el PC,
 * apuntes, subrayados, capturas); el resumen del mes, la racha, «tal día como hoy», lo que viaja con la cuenta y la
 * pantalla (días, calendario, un día abierto y el editor).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DiarioTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val zona = ZoneId.of("Europe/Madrid")

    private fun shoot(n: String) {
        rule.waitForIdle()
        if (System.getProperty("roborazzi.test.record") == "true") com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/diario_$n.png")
    }

    private fun ms(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zona).toInstant().toEpochMilli()

    @Test fun leidoPorDia() {
        val hoy = LocalDate.now()
        BookFiles.putQuiet(ctx, 7, JSONObject().put("stats", JSONObject().put("days", JSONObject().put(hoy.toString(), 25 * 60_000L).put(hoy.minusDays(1).toString(), 5_000L))))
        BookFiles.putQuiet(ctx, 8, JSONObject().put("stats", JSONObject().put("days", JSONObject().put(hoy.toString(), 10 * 60_000L))))
        val m = BookFiles.leidoPorDia(ctx)
        assertEquals(25 * 60_000L, m[hoy.toEpochDay()]?.get(7L))
        assertEquals(10 * 60_000L, m[hoy.toEpochDay()]?.get(8L))
        assertEquals(5_000L, m[hoy.minusDays(1).toEpochDay()]?.get(7L))
    }

    @Test fun tiempoLeyendoDelAno() {
        val hoy = LocalDate.of(2026, 10, 25)
        val d = { y: Int, m: Int, dd: Int -> LocalDate.of(y, m, dd).toEpochDay() }
        val leido = mapOf(
            d(2026, 10, 20) to mapOf(1L to 30 * 60_000L), d(2026, 10, 21) to mapOf(1L to 20 * 60_000L, 2L to 10 * 60_000L),
            d(2026, 10, 22) to mapOf(2L to 50 * 60_000L), d(2026, 3, 1) to mapOf(3L to 60 * 60_000L),
            d(2026, 3, 2) to mapOf(3L to 20_000L),   // (menos de un minuto: no cuenta como día)
            d(2025, 12, 31) to mapOf(1L to 90 * 60_000L)
        )
        val t = tiempoLeyendo(leido, 2026, hoy)
        assertEquals((30 + 20 + 10 + 50 + 60) * 60_000L + 20_000L, t.total)
        assertEquals(4, t.dias)
        assertEquals("tres días seguidos en octubre", 3, t.racha)
        assertEquals(12, t.meses.size)
        assertEquals(listOf(3L, 2L, 1L), t.libros.map { it.first })
        val todo = tiempoLeyendo(leido, null, hoy)
        assertEquals(5, todo.dias)
        assertEquals("el último mes de los 12 es el de hoy", 12, todo.meses.size)
    }

    /** Lo de cada día sale de lo que ya guarda la app, junto por ficha. */
    @Test fun dias() {
        val hoy = LocalDate.of(2026, 10, 26)
        val d = { n: Long -> hoy.minusDays(n) }
        val juegos = listOf(Game(1, "Hades II", "", 91, review = "Un final redondo. Lo mejor de Supergiant.", start = d(20).toEpochDay(), end = d(1).toEpochDay()))
        val jugando = listOf(Play(2, "Deadlock", "", "", 1, emptyList(), start = d(3).toEpochDay(),
            memos = listOf(Memo(ms(d(1), 22, 10), "El nuevo héroe está roto"), Memo(ms(d(1), 23), "borrado", del = true))))
        val libros = listOf(Game(7, "Dune", "", 88, end = d(30).toEpochDay()))
        val leyendo = listOf(Play(8, "Fundación", "", "", 1, listOf(Entry(ms(d(0), 9), 80, 70, 75, 70, 80, "Me está gustando")), start = d(6).toEpochDay()))
        val dejado = listOf(Play(9, "Ulises", "", "", 2, emptyList(), start = d(40).toEpochDay(), end = d(1).toEpochDay()))
        val notas = listOf(
            Nota(ms(d(1), 21), d(1).toEpochDay(), "Por fin terminé Hades II.", sobre = listOf(refDe(0, 1))),
            Nota(ms(d(1), 8), d(1).toEpochDay(), "Mañana tranquila."),
            Nota(ms(d(2), 20), d(2).toEpochDay(), "Leyendo en el tren.", sobre = listOf(refDe(1, 8)))
        )
        val f = FuentesDiario(
            listOf(Triple(GAMES, juegos, jugando), Triple(BOOKS, libros, leyendo + dejado)), notas,
            leido = mapOf(d(1).toEpochDay() to mapOf(8L to 45 * 60_000L, 7L to 30_000L), d(0).toEpochDay() to mapOf(8L to 20 * 60_000L)),
            jugado = mapOf(d(1).toEpochDay() to mapOf(JuegoPc(1, "Hades II") to 2 * 3_600_000L + 10 * 60_000L, JuegoPc(0, "Hollow Knight") to 50 * 60_000L)),
            citas = mapOf(8L to listOf(BookNote("epubcfi(/6/4)", "La violencia es el último refugio del incompetente", "", "Cap. 1", ms(d(1), 22, 30), 0))),
            capturas = listOf(Shot("s1", "pc", 1920, 1080, "image/png", 1, ms(d(1), 20), "Hades II", 1))
        )
        val dias = diasDelDiario(f, zona)
        assertEquals("del más nuevo al más viejo", listOf(0L, 1L, 2L, 3L, 6L, 20L, 30L, 40L).map { hoy.minusDays(it).toEpochDay() }, dias.map { it.dia })
        val ayer = dias.first { it.dia == d(1).toEpochDay() }
        // Lo escrito, en el orden en que lo escribiste
        assertEquals(listOf("Mañana tranquila.", "Por fin terminé Hades II."), ayer.notas.map { it.texto })
        // Lo terminado va primero; una ficha por cosa, con todo lo de ese día
        val hades = ayer.toques.first()
        assertEquals("Hades II", hades.nombre)
        assertTrue(hades.terminado); assertEquals(91, hades.nota)
        assertEquals(130 * 60_000L, hades.jugadoMs)
        assertEquals(1, hades.capturas.size)
        assertEquals("Lo terminaste · 2 h 10 min jugando · una captura", hades.resumen())
        val fundacion = ayer.toques.first { it.id == 8L }
        assertEquals(45 * 60_000L, fundacion.leidoMs)
        assertEquals(1, fundacion.citas.size)
        assertTrue("Dune: 30 s leídos no cuentan", ayer.toques.none { it.id == 7L })
        val deadlock = ayer.toques.first { it.id == 2L }
        assertEquals("el apunte borrado no sale", listOf("El nuevo héroe está roto"), deadlock.apuntes.map { it.x })
        assertTrue("lo dejado", ayer.toques.first { it.id == 9L }.abandonado)
        // Un juego del PC que no está en tu colección: con su nombre, sin ficha
        val hk = ayer.toques.first { it.nombre == "Hollow Knight" }
        assertEquals(0L, hk.id)
        // Hoy: un apunte con nota y lo leído de Fundación, en la misma ficha
        val deHoy = dias.first()
        assertEquals(1, deHoy.toques.size)
        assertEquals(1, deHoy.toques[0].valoraciones.size)
        assertEquals(20 * 60_000L, deHoy.toques[0].leidoMs)
        // Resumen de octubre
        val oct = resumenDiario(dias, LocalDate.of(2026, 10, 1).toEpochDay(), LocalDate.of(2026, 10, 31).toEpochDay())
        assertEquals(1, oct.terminados)
        assertEquals(3, oct.entradas)
        assertEquals("1 terminado · 1 h 5 min leyendo · 3 h jugando · 3 entradas", oct.texto())
        // Solo Libros: lo escrito sobre libros (no lo que no habla de ninguna ficha)
        val soloLibros = diasDelDiario(FuentesDiario(listOf(Triple(BOOKS, libros, leyendo + dejado)), notas, soloDe = setOf(BOOKS.id)), zona)
        assertEquals(listOf("Leyendo en el tren."), soloLibros.flatMap { it.notas }.map { it.texto })
        assertTrue("sin juegos", soloLibros.flatMap { it.toques }.none { it.kind.isGame })
    }

    /** Las partidas del PC (las que sube Atrio): por día, partiendo las que pasan de medianoche; lo olvidado no cuenta. */
    @Test fun horasDelPc() {
        val d = LocalDate.of(2026, 10, 24)
        val pc = JSONObject()
            .put("games", JSONObject().put("steam:1145360", JSONObject().put("n", "Hades II").put("sg", 5555)).put("lutris:hk", JSONObject().put("n", "Hollow Knight")))
            .put("s", JSONArray()
                .put(JSONArray().put("steam:1145360").put(ms(d, 23, 30)).put(ms(d.plusDays(1), 1, 0)))
                .put(JSONArray().put("lutris:hk").put(ms(d, 18)).put(ms(d, 18, 40)))
                .put(JSONArray().put("lutris:hk").put(ms(d, 10)).put(ms(d, 11))))
            .put("forgot", JSONObject().put("lutris:hk", ms(d, 12)))
        val m = HorasPc.deSesiones(listOf(pc), zona)
        val hades = JuegoPc(5555, "Hades II")
        assertEquals(30 * 60_000L, m[d.toEpochDay()]?.get(hades))
        assertEquals(60 * 60_000L, m[d.plusDays(1).toEpochDay()]?.get(hades))
        assertEquals("la de las 10 se olvidó; la de las 18 no", 40 * 60_000L, m[d.toEpochDay()]?.get(JuegoPc(0, "Hollow Knight")))
        // Y en el diario, Hades II va a su ficha por el id de SteamGridDB
        val dias = diasDelDiario(FuentesDiario(listOf(Triple(GAMES, listOf(Game(5555, "Hades II", "", 90)), emptyList())), jugado = m), zona)
        assertEquals(5555L, dias.first { it.dia == d.toEpochDay() }.toques.first { it.jugadoMs == 30 * 60_000L }.id)
    }

    @Test fun rachaYTalDia() {
        val hoy = LocalDate.of(2026, 10, 26).toEpochDay()
        assertEquals(3, rachaDiario(listOf(hoy, hoy - 1, hoy - 2, hoy - 4), hoy))
        assertEquals("hoy aún nada: cuenta desde ayer", 2, rachaDiario(listOf(hoy - 1, hoy - 2), hoy))
        assertEquals(0, rachaDiario(listOf(hoy - 2), hoy))
        val dias = listOf(LocalDate.of(2025, 10, 26), LocalDate.of(2023, 10, 26), LocalDate.of(2025, 10, 25)).map { Dia(it.toEpochDay(), listOf(Nota(1, it.toEpochDay(), "x")), emptyList()) }
        assertEquals(listOf(1, 3), talDiaComoHoy(dias, LocalDate.of(2026, 10, 26)).map { it.first })
        assertEquals("Una frase larga que…", extracto("Una frase larga que sigue y sigue", 22))
    }

    /** Lo escrito viaja con la cuenta: a otro dispositivo llega, lo borrado se borra y tu pareja solo ve lo que compartes. */
    @Test fun viajaConLaCuenta() {
        val g = ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE)
        g.edit().clear().commit()
        val s = Store(ctx)
        val hoy = LocalDate.now().toEpochDay()
        val a = Nota(1000L, hoy, "Solo para mí", updated = 1000L)
        val b = Nota(2000L, hoy, "Para los dos", sobre = listOf(refDe(1, 8)), foto = "file:///data/covers/foto_1.jpg", comparte = true, updated = 2000L)
        s.saveDiario(listOf(a, b))
        assertEquals(listOf(a, b), s.loadDiario())
        val data = s.syncData()
        assertTrue(JSONObject(data).getJSONArray("diario").length() == 2)
        // Otro dispositivo sin nada
        g.edit().clear().commit()
        s.mergeRemote(data)
        assertEquals(listOf("Solo para mí", "Para los dos"), s.loadDiario().map { it.texto })
        // Cambiar una aquí y borrar otra: la de la cuenta (más vieja) no la revive
        s.saveDiario(listOf(b.copy(texto = "Para los dos (editado)", updated = System.currentTimeMillis())))
        s.mergeRemote(data)
        assertEquals(listOf("Para los dos (editado)"), s.loadDiario().map { it.texto })
        // Tu pareja: solo lo compartido, y sin tus fotos (están en tu móvil)
        s.saveDiario(listOf(a, b))
        s.partnerRaw = JSONObject().put("name", "Laura").put("updated", 1L).put("data", s.syncData()).toString()
        val p = Couple.cached(s)
        assertNotNull(p)
        assertEquals(listOf("Para los dos"), p!!.diario.map { it.texto })
        assertEquals("", p.diario[0].foto)
        // Una copia antigua (sin diario) no borra el tuyo
        s.importSync(JSONObject(data).apply { remove("diario") }.toString())
        assertEquals(2, s.loadDiario().size)
    }

    @Test fun pantalla() {
        Mode.dark = false
        Skin.load(ctx); Skin.applyShell(2, ctx)
        val hoy = LocalDate.now()
        val d = { n: Long -> hoy.minusDays(n) }
        val z = ZoneId.systemDefault()
        val t = { dd: LocalDate, h: Int -> dd.atTime(h, 0).atZone(z).toInstant().toEpochMilli() }
        val juegos = listOf(Game(1, "Hades II", "", 91, review = "Un final redondo: la música, los jefes y una historia que se cierra como tiene que cerrarse.", start = d(20).toEpochDay(), end = d(1).toEpochDay()))
        val jugando = listOf(Play(2, "Deadlock", "", "", 1, emptyList(), start = d(9).toEpochDay(), memos = listOf(Memo(t(d(1), 22), "El nuevo héroe está roto, hay que probarlo con Laura."))))
        val leyendo = listOf(Play(8, "Fundación", "", "", 1, emptyList(), start = d(6).toEpochDay(), creator = "Isaac Asimov"))
        val notas = listOf(
            Nota(t(d(1), 23), d(1).toEpochDay(), "Por fin terminé Hades II. Me ha dejado con ganas de volver a empezarlo con otra arma.", sobre = listOf(refDe(0, 1))),
            Nota(t(d(0), 9), d(0).toEpochDay(), "Café y un capítulo de Fundación antes de trabajar.", sobre = listOf(refDe(1, 8)))
        )
        val f = FuentesDiario(
            listOf(Triple(GAMES, juegos, jugando), Triple(BOOKS, emptyList(), leyendo)), notas,
            leido = mapOf(d(0).toEpochDay() to mapOf(8L to 25 * 60_000L), d(1).toEpochDay() to mapOf(8L to 70 * 60_000L), d(2).toEpochDay() to mapOf(8L to 40 * 60_000L)),
            jugado = mapOf(d(1).toEpochDay() to mapOf(JuegoPc(1, "Hades II") to 150 * 60_000L), d(2).toEpochDay() to mapOf(JuegoPc(2, "Deadlock") to 95 * 60_000L)),
            citas = mapOf(8L to listOf(BookNote("epubcfi(/6/4)", "La violencia es el último refugio del incompetente.", "Salvor Hardin", "Parte I", t(d(1), 21), 0)))
        )
        val dias = diasDelDiario(f)
        var escrito: Pair<Long, Nota?>? = null
        var abierto: Toque? = null
        var apunte: Play? = null
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    DiarioScreen(dias, listOf(GAMES to jugando[0], BOOKS to leyendo[0]), listOf(GAMES, BOOKS), null, {}, "Laura", false, {},
                        nombreDe = { r -> mapOf(refDe(0, 1) to "Hades II", refDe(1, 8) to "Fundación")[r] },
                        acc = DiarioAcciones(abrir = { abierto = it }, escribir = { dd, n -> escrito = dd to n }, apuntar = { _, p -> apunte = p }, aviso = {}))
                }
            }
        }
        shoot("1_dias")
        assertTrue("hoy arriba", rule.onAllNodesWithText("Café y un capítulo", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("lo terminado, con su resumen", rule.onAllNodesWithText("Lo terminaste · 2 h 30 min jugando", ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithText("¿Qué tal hoy?", substring = true).fetchSemanticsNodes().let { assertTrue("hay hueco para escribir", it.isEmpty()) }
        rule.onAllNodesWithText("Añadir algo más", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        assertEquals(hoy.toEpochDay(), escrito?.first)
        rule.onAllNodesWithText("Café y un capítulo", substring = true)[0].performClick(); rule.waitForIdle()
        assertEquals("tocar lo escrito lo abre para cambiarlo", notas[1], escrito?.second)
        rule.onAllNodesWithText("+ Apunte", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        assertEquals(2L, apunte?.id)
        rule.onAllNodesWithText("Hades II", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        assertEquals(1L, abierto?.id)
        // El calendario y un día abierto
        rule.onAllNodesWithText("CALENDARIO", ignoreCase = true)[0].performClick(); shoot("2_calendario")
        val ayer = d(1)
        if (ayer.month == hoy.month) {
            rule.onAllNodesWithText("${ayer.dayOfMonth}", ignoreCase = true)[0].performClick(); shoot("3_dia")
            assertTrue("el día abierto", rule.onAllNodesWithText("Por fin terminé", substring = true).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test fun editor() {
        Mode.dark = false
        Skin.load(ctx); Skin.applyShell(2, ctx)
        val hoy = today()
        var guardada: Nota? = null
        var cerrado = false
        val props = listOf(RefFicha(refDe(0, 2), GAMES, "Deadlock", ""), RefFicha(refDe(1, 8), BOOKS, "Fundación", ""))
        rule.setContent {
            MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    EscribirDiario(hoy, null, listOf(refDe(1, 8)), props, props + RefFicha(refDe(0, 1), GAMES, "Hades II", ""), "Laura", elegirFoto = {},
                        onGuardar = { guardada = it }, onBorrar = {}, onCerrar = { cerrado = true })
                }
            }
        }
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Hoy he leído en el parque.")
        rule.onAllNodesWithText("Deadlock", ignoreCase = true)[0].performClick()
        shoot("4_escribir")
        rule.onAllNodesWithText("También Laura".uppercase())[0].performClick()
        rule.onAllNodesWithText("LISTO", ignoreCase = true)[0].performClick(); rule.waitForIdle()
        assertTrue(cerrado)
        val n = guardada!!
        assertEquals("Hoy he leído en el parque.", n.texto)
        assertEquals(hoy, n.dia)
        assertEquals(setOf(refDe(1, 8), refDe(0, 2)), n.sobre.toSet())
        assertTrue(n.comparte)
        assertFalse(n.foto.isNotBlank())
    }
}
