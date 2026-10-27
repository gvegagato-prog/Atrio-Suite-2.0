package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Actualizarse sola: qué versión es la última de la carpeta «versiones», si es más nueva, y que se lee de kDrive. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActualizadorTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var k: FakeKDrive
    @Before fun up() { k = FakeKDrive(); KDrive.api = k.url; KDrive.share = k.url }
    @After fun down() { k.stop() }

    @Test fun versiones() {
        assertEquals(listOf(6, 67), Actualizador.versionDe("Atrio-6.67.apk"))
        assertEquals(listOf(7), Actualizador.versionDe("Atrio-7.apk"))
        assertNull("otros archivos no cuentan", Actualizador.versionDe("Atrio-6.67-beta.apk"))
        assertNull(Actualizador.versionDe("notas.txt"))
        assertTrue("6.10 es más nueva que 6.9 (números, no texto)", Actualizador.mayor(listOf(6, 10), listOf(6, 9)))
        assertTrue(Actualizador.mayor(listOf(6, 67, 1), listOf(6, 67)))
        assertFalse("la misma no es más nueva", Actualizador.mayor(listOf(6, 67), listOf(6, 67, 0)))
        val u = Actualizador.ultima(listOf("Atrio-6.9.apk" to 1L, "Atrio-6.10.apk" to 2L, "léeme.txt" to 3L, "Atrio-6.67.apk" to 4L, "Atrio-6.8.apk" to 5L))
        assertEquals("Atrio-6.67.apk", u?.first); assertEquals(4L, u?.second)
        assertNull(Actualizador.ultima(emptyList()))
    }

    @Test fun leeLaCarpetaDeKDrive() {
        KDrive.save(ctx, "tok", "777")
        assertTrue("sin carpeta «versiones»: nada", KDrive.versiones(ctx).isEmpty())
        val a = File(ctx.cacheDir, "Atrio-6.67.apk").apply { writeBytes(ByteArray(3000) { (it % 7).toByte() }) }
        val b = File(ctx.cacheDir, "Atrio-6.68.apk").apply { writeBytes(ByteArray(4000) { (it % 5).toByte() }) }
        KDrive.upload(ctx, "versiones", a.name, a.length()) { a.inputStream() }
        KDrive.upload(ctx, "versiones", b.name, b.length()) { b.inputStream() }
        KDrive.upload(ctx, "otra", "Atrio-9.99.apk", a.length()) { a.inputStream() }   // en otra carpeta: no vale
        val l = KDrive.versiones(ctx)
        assertEquals(setOf("Atrio-6.67.apk", "Atrio-6.68.apk"), l.map { it.first }.toSet())
        val u = Actualizador.ultima(l)!!
        assertEquals("Atrio-6.68.apk", u.first)
        // Y se baja entera
        val dest = File(ctx.cacheDir, "bajada.apk")
        KDrive.download(ctx, u.second, dest)
        assertEquals(b.readBytes().toList(), dest.readBytes().toList())
    }
}
