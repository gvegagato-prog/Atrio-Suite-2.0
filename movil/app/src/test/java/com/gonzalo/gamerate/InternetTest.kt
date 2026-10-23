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
}
