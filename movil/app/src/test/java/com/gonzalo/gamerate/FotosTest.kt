package com.gonzalo.gamerate

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/** Fotos de la galería entre dispositivos: el nombre, el original y la foto que llega de otro dispositivo. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FotosTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val covers get() = File(ctx.filesDir, "covers")

    @Test
    fun nombres() {
        assertEquals("foto_1700000000000.jpg", Photos.nameOf("file:///data/user/0/com.gonzalo.gamerate/files/covers/foto_1700000000000.jpg"))
        assertNull(Photos.nameOf("https://cdn2.steamgriddb.com/grid/x.png"))
        assertNull(Photos.nameOf("file:///data/x/../../etc/pass wd"))
    }

    @Test
    fun elOriginalAntesQueLaCopia() {
        File(covers, "orig").mkdirs()
        File(covers, "foto_1.jpg").writeBytes(ByteArray(10))
        assertEquals(false, Photos.best(ctx, "foto_1.jpg")?.second)
        File(covers, "orig/foto_1.png").writeBytes(ByteArray(20))
        val (f, orig) = Photos.best(ctx, "foto_1.jpg")!!
        assertTrue(orig)
        assertEquals("png", f.extension)
        assertNull(Photos.best(ctx, "foto_2.jpg"))
        // Una foto de antes de la 6.13 (solo la copia reducida), pedida con o sin extensión
        File(covers, "foto_3.jpg").writeBytes(ByteArray(30))
        assertEquals("foto_3.jpg", Photos.best(ctx, "foto_3")?.first?.name)
        assertEquals("foto_3.jpg", Photos.best(ctx, "foto_3.jpg")?.first?.name)
    }

    @Test
    fun llegaDeOtroDispositivo() = runBlocking {
        val url = "file://${File(covers, "foto_9.jpg").path}"
        assertFalse(Photos.here(ctx, url))
        val bm = Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 60, 40)) }
        val bytes = ByteArrayOutputStream().also { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        Photos.saveArrived(ctx, "foto_9.jpg", bytes, "image/png")
        // El original, tal cual; y la copia para verla, con el mismo nombre que en el móvil
        assertTrue(File(covers, "orig/foto_9.png").readBytes().contentEquals(bytes))
        assertTrue(Photos.here(ctx, url))
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(File(covers, "foto_9.jpg").path, o)
        assertEquals(1400, maxOf(o.outWidth, o.outHeight))
    }
}
