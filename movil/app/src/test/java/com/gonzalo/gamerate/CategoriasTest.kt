package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Películas y Series: sin repetidas, sin perder una al juntar dispositivos y sin ids que se pisen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoriasTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun fila(id: Long, n: String) = Game(id, n, "", 80)

    @Test fun dosSeriesYNingunaPelicula() {
        val s = Store(ctx)
        // Lo que se veía: dos «Series» y ninguna «Películas» (una de ellas, titulada Películas, quedó buscando series)
        s.kinds = listOf(
            customKind(2, "Películas", listOf("Guion"), "Viendo", 0, SRC_TV),
            customKind(3, "Series", listOf("Guion"), "Viendo", 0, SRC_TV)
        )
        s.save(listOf(fila(1, "Dune")), 2)
        s.save(listOf(fila(2, "Dark")), 3)
        s.ensureMediaKinds()
        val ks = s.kinds
        assertEquals(2, ks.size)
        assertEquals("una de Películas", 1, ks.count { it.isMovie })
        assertEquals("una de Series", 1, ks.count { it.isTv })
        assertEquals("la que se llama Películas vuelve a buscar películas", SRC_MOVIE, ks.first { it.title == "Películas" }.source)
        assertEquals("y conserva sus fichas", listOf("Dune"), s.load(2).map { it.name })
        assertEquals(listOf("Dark"), s.load(3).map { it.name })
        // Otra vez: no toca nada
        s.ensureMediaKinds()
        assertEquals(ks.map { it.id }, s.kinds.map { it.id })
    }

    @Test fun repetidasSeJuntan() {
        val s = Store(ctx)
        s.kinds = listOf(
            customKind(2, "Series", listOf("Guion"), "Viendo", 0, SRC_TV),
            customKind(5, "Series", listOf("Guion"), "Viendo", 0, SRC_TV),
            customKind(3, "Películas", listOf("Guion"), "Viendo", 0, SRC_MOVIE)
        )
        s.save(listOf(fila(1, "Dark")), 2)
        s.save(listOf(fila(2, "Severance"), fila(1, "Dark")), 5)
        s.ensureMediaKinds()
        assertEquals(listOf(2, 3), s.kinds.map { it.id }.sorted())
        assertEquals("las fichas de las dos, sin repetir", setOf("Dark", "Severance"), s.load(2).map { it.name }.toSet())
        assertEquals(2, s.load(2).size)
        assertTrue("la que sobraba no vuelve al juntar con otro dispositivo", s.tombs.has("kind:5"))
    }

    @Test fun faltaPeliculasSeCrea() {
        val s = Store(ctx)
        s.kinds = listOf(customKind(2, "Series", listOf("Guion"), "Viendo", 0, SRC_TV))
        s.ensureMediaKinds()
        assertTrue(s.kinds.any { it.isMovie })
        assertEquals(1, s.kinds.count { it.isTv })
    }

    @Test fun siLaBorrasTuNoVuelve() {
        val s = Store(ctx)
        s.ensureMediaKinds()
        val peli = s.kinds.first { it.isMovie }
        s.deleteKind(peli.id)
        s.ensureMediaKinds()
        assertTrue("borrada a propósito, no se recrea", s.kinds.none { it.isMovie })
    }

    @Test fun idsNuevosNoSePisan() {
        val a = Store(ctx)
        val ids = (1..50).map { a.nextKindId().also { id -> a.kinds = a.kinds + customKind(id, "Cat$id", listOf("x"), "En curso", 0) } }
        assertEquals(50, ids.toSet().size)
        assertTrue("ya no son 2, 3, 4…: dos dispositivos no coinciden", ids.all { it >= 1000 })
    }
}
