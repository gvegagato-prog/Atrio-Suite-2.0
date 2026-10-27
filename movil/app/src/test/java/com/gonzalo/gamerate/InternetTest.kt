package com.gonzalo.gamerate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InternetTest {
    @Test fun imgres() {
        assertEquals("https://a.com/x y.jpg", Internet.deImgres("https://www.google.com/imgres?imgurl=https%3A%2F%2Fa.com%2Fx%20y.jpg&imgrefurl=z&tbnid=1"))
        assertNull(Internet.deImgres("https://www.google.com/search?q=a"))
    }

    @Test fun datos() {
        val (b, m) = Internet.descargar("data:image/png;base64,aGVsbG8=", "x")
        assertEquals("hello", String(b)); assertEquals("image/png", m)
    }

    @Test fun buscar() {
        assertEquals("https://www.google.com/search?udm=2&hl=es&q=red+rising+portada", Internet.searchUrl("  red rising portada "))
    }

    @Test fun fuentes() {
        assertEquals(listOf("google", "artstation", "pinterest", "newgrounds"), Internet.fuentes.map { it.id })
        assertEquals("https://www.newgrounds.com/search/conduct/art?terms=red+rising", Internet.searchUrl("red rising", Internet.fuente("newgrounds")))
        assertEquals("https://www.artstation.com/search?sort_by=relevance&query=red+rising", Internet.searchUrl("red rising", Internet.fuente("artstation")))
        assertEquals("https://www.pinterest.com/search/pins/?q=red+rising", Internet.searchUrl("red rising", Internet.fuente("pinterest")))
        assertEquals("google", Internet.fuente("otra").id)
    }

    @Test fun mejoresDirecciones() {
        val pin = "https://i.pinimg.com/236x/ab/cd/ef/abcdef.jpg"
        assertEquals(listOf("https://i.pinimg.com/originals/ab/cd/ef/abcdef.jpg", "https://i.pinimg.com/736x/ab/cd/ef/abcdef.jpg", pin), Internet.candidatas(pin))
        assertEquals(listOf("https://i.pinimg.com/originals/a.jpg", "https://i.pinimg.com/736x/a.jpg"), Internet.candidatas("https://i.pinimg.com/474x/a.jpg").dropLast(1))
        val art = "https://cdna.artstation.com/p/assets/images/images/001/small_square/x.jpg?1"
        assertEquals("https://cdna.artstation.com/p/assets/images/images/001/8k/x.jpg?1", Internet.candidatas(art)[0])
        assertEquals("https://cdna.artstation.com/p/assets/images/images/001/large/x.jpg?1", Internet.candidatas(art)[2])
        assertEquals(art, Internet.candidatas(art).last())
        assertEquals(listOf("https://a.com/x.jpg"), Internet.candidatas("https://a.com/x.jpg"))
    }
}
