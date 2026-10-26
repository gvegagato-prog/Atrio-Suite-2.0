package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Leer unos minutos mueve el libro solo a «Leyendo». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoLeyendoTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val hoy = LocalDate.of(2026, 10, 27)
    /** [ms] leídos hoy en total y [seguido] la racha más larga (null: no se guardó, como en días de versiones antiguas). */
    private fun estado(ms: Long, seguido: Long? = null) = JSONObject().put("stats", JSONObject().put("days", JSONObject().put(hoy.toString(), ms))
        .apply { if (seguido != null) put("best", JSONObject().put(hoy.toString(), seguido)) })

    @Test fun mueveSoloTrasVariosMinutos() {
        val s = Store(ctx)
        s.savePlays(listOf(Play(1, "Dune", "", "", 0, emptyList()), Play(2, "Ulises", "", "", 2, emptyList(), start = 100, end = 200)), BOOKS.id)
        assertFalse("4 min: aún no", BookFiles.pasaALeyendo(ctx, 1, estado(4 * 60_000L, 4 * 60_000L), hoy))
        assertFalse("40 min en ratitos de 2: tampoco (no son 5 seguidos)", BookFiles.pasaALeyendo(ctx, 1, estado(40 * 60_000L, 2 * 60_000L), hoy))
        assertEquals(0, s.loadPlays(BOOKS.id).first { it.id == 1L }.status)
        assertTrue("5 seguidos", BookFiles.pasaALeyendo(ctx, 1, estado(5 * 60_000L, 5 * 60_000L), hoy))
        val d = s.loadPlays(BOOKS.id).first { it.id == 1L }
        assertEquals(1, d.status); assertEquals(hoy.toEpochDay(), d.start)
        // Un abandonado que retomas: conserva cuándo empezó y vuelve a estar en curso
        assertTrue(BookFiles.pasaALeyendo(ctx, 2, estado(10 * 60_000L, 10 * 60_000L), hoy))
        val u = s.loadPlays(BOOKS.id).first { it.id == 2L }
        assertEquals(1, u.status); assertEquals(100L, u.start); assertEquals(0L, u.end)
    }

    @Test fun unaVezAlDiaYNoTocaLoValorado() {
        val s = Store(ctx)
        s.savePlays(listOf(Play(1, "Dune", "", "", 0, emptyList())), BOOKS.id)
        s.save(listOf(Game(9, "Hades", "", 90)), BOOKS.id)
        val st = estado(10 * 60_000L)
        assertTrue(BookFiles.pasaALeyendo(ctx, 1, st, hoy))
        // Lo devuelves a la lista a mano: ese día no vuelve a moverse
        s.savePlays(s.loadPlays(BOOKS.id).map { it.copy(status = 0) }, BOOKS.id)
        assertFalse(BookFiles.pasaALeyendo(ctx, 1, st, hoy))
        assertEquals(0, s.loadPlays(BOOKS.id).first().status)
        // Ya valorado (sin ficha en curso): nada
        assertFalse(BookFiles.pasaALeyendo(ctx, 9, estado(30 * 60_000L), hoy))
        assertEquals(1, s.load(BOOKS.id).size)
    }
}
