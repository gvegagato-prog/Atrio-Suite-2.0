package com.gonzalo.gamerate

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Base64

/**
 * La app del móvil contestando de verdad a «dame esta foto» (contra el Firebase de mentira de herramientas/):
 * como la pide el PC (por su nombre, con o sin extensión) y como la pide otro móvil o la tableta (Suite.fetchPhoto).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FotosSuiteTest {
    companion object {
        private var proc: Process? = null
        private val port = 9300 + (Math.random() * 250).toInt()
        val base = "http://127.0.0.1:$port"

        @BeforeClass @JvmStatic fun up() {
            var dir = File("").absoluteFile
            while (!File(dir, "herramientas/fakefirebase.js").exists()) dir = dir.parentFile ?: error("no encuentro herramientas/")
            proc = ProcessBuilder("node", File(dir, "herramientas/fakefirebase.js").path, port.toString()).redirectErrorStream(true).start()
            Thread.sleep(800)
        }
        @AfterClass @JvmStatic fun down() { proc?.destroy() }
    }

    private fun until(ms: Long, f: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (f()) return true; Thread.sleep(150) }
        return false
    }

    @Test
    fun elMovilMandaSusFotos() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = Store(ctx)
        store.dbUrl = base; store.authBase = base; store.webKey = "prueba"
        Account.signUp(store, "fotos${System.nanoTime() % 100000}", "secreto1")
        val covers = File(ctx.filesDir, "covers").apply { mkdirs() }
        // Una foto de antes de la 6.13: solo la copia reducida
        val vieja = ByteArray(700_000) { (it * 31 % 251).toByte() }
        File(covers, "foto_1700000000001.jpg").writeBytes(vieja)
        Suite.start(ctx, store)
        try {
            // Que la escucha en directo esté ya conectada
            Thread.sleep(2500)
            val me = store.accDevice
            val uid = store.accUid
            // 1) Como la pide el PC: una orden «foto» con el nombre SIN extensión (lo que fallaba)
            val tok = Account.token(store)
            val body = JSONObject().put("op", "foto").put("a", JSONObject().put("name", "foto_1700000000001"))
                .put("t", JSONObject().put(".sv", "timestamp")).put("by", "PC de prueba")
            val id = JSONObject(SuiteTest.http("POST", "$base/users/$uid/suite/cmd/$me.json?auth=$tok", body.toString())).getString("name")
            var res: JSONObject? = null
            assertTrue("el móvil no contesta", until(20_000) {
                val r = SuiteTest.http("GET", "$base/users/$uid/suite/cmd/$me/$id/res.json?auth=$tok")
                res = if (r.trim() == "null") null else JSONObject(r)
                res != null
            })
            assertTrue(res!!.optString("msg"), res!!.optBoolean("ok"))
            val data = res!!.getString("data")
            val sb = StringBuilder()
            for (i in 0 until res!!.getInt("n")) sb.append(JSONTokener(SuiteTest.http("GET", "$base/users/$uid/fotodata/$data/$i.json?auth=$tok")).nextValue() as String)
            assertTrue("llega idéntica", Base64.getDecoder().decode(sb.toString()).contentEquals(vieja))
            assertEquals(false, res!!.optBoolean("orig"))

            // 2) Como la pide la tableta: Suite.fetchPhoto la guarda con el mismo nombre (aquí, a sí mismo)
            File(covers, "orig").mkdirs()
            val bm = android.graphics.Bitmap.createBitmap(2400, 1600, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
            val png = java.io.ByteArrayOutputStream().also { bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            File(covers, "orig/foto_1700000000002.png").writeBytes(png)
            val url = "file://${File(covers, "foto_1700000000002.jpg").path}"
            val (ok, msg) = Suite.fetchPhoto(me, url)
            assertTrue(msg, ok)
            assertTrue("el original sigue igual", File(covers, "orig/foto_1700000000002.png").readBytes().contentEquals(png))
            assertNotNull(Photos.best(ctx, "foto_1700000000002.jpg"))
            assertTrue("y se puede ver aquí", Photos.here(ctx, url))
            // Los trozos no se quedan en la nube
            assertTrue(until(5_000) { SuiteTest.http("GET", "$base/users/$uid/fotodata.json?auth=$tok").trim().let { it == "null" || !it.contains("foto") && JSONObject(it).length() <= 1 } })
        } finally {
            Suite.stop()
        }
    }
}
