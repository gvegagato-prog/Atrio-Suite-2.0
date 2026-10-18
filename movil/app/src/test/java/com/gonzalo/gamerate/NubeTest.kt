package com.gonzalo.gamerate

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Un kDrive de mentira (las mismas llamadas que herramientas/fakekdrive.js), con un servidor HTTP mínimo. */
class FakeKDrive(private val drive: String = "777", private val token: String = "tok") {
    class F(val id: Long, val name: String, val dir: Boolean, val parent: Long, val bytes: ByteArray = ByteArray(0))
    val files = java.util.Collections.synchronizedMap(linkedMapOf(1L to F(1, "Mi carpeta", true, 0)))
    private var next = 100L
    private val sock = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    val url get() = "http://127.0.0.1:${sock.localPort}"
    @Volatile private var alive = true

    fun pathOf(f: F): String { val out = mutableListOf<String>(); var c: F? = f; while (c != null && c.id != 1L) { out.add(0, c.name); c = files[c.parent] }; return out.joinToString("/") }
    fun byPath(p: String) = synchronized(files) { files.values.firstOrNull { pathOf(it) == p } }
    fun stop() { alive = false; sock.close() }

    private class Res(val code: Int, val body: ByteArray)
    private fun json(code: Int, o: JSONObject) = Res(code, o.toString().toByteArray())
    private fun ok(data: Any) = json(200, JSONObject().put("result", "success").put("data", data).put("has_more", false))
    private fun err(code: Int) = json(code, JSONObject().put("result", "error").put("error", JSONObject().put("code", "$code").put("description", "err $code")))

    private fun handle(method: String, target: String, auth: String?, body: ByteArray): Res {
        if (auth != "Bearer $token") return err(401)
        val path = target.substringBefore('?')
        val q = target.substringAfter('?', "").split('&').filter { '=' in it }.associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val m = Regex("""^/([23])/drive/(\d+)(/.*)$""").find(path) ?: return err(404)
        if (m.groupValues[2] != drive) return err(404)
        val p = m.groupValues[3]
        fun id(re: String) = Regex(re).find(p)?.groupValues?.get(1)?.toLong()
        synchronized(files) {
            if (method == "GET") id("""^/files/(\d+)/files$""")?.let { d ->
                return ok(JSONArray().apply { files.values.filter { it.parent == d }.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("type", if (it.dir) "dir" else "file")) } })
            }
            if (method == "POST") id("""^/files/(\d+)/directory$""")?.let { d ->
                val n = next++; files[n] = F(n, JSONObject(String(body)).getString("name"), true, d); return ok(JSONObject().put("id", n))
            }
            if (method == "POST" && p == "/upload") {
                if (q["total_size"]?.toLong() != body.size.toLong()) return err(400)
                val n = next++; files[n] = F(n, q["file_name"]!!, false, q["directory_id"]!!.toLong(), body); return ok(JSONObject().put("id", n))
            }
            if (method == "POST") id("""^/files/(\d+)/link$""")?.let { f -> return ok(JSONObject().put("url", "https://kdrive.example/app/share/$drive/$f")) }
            if (method == "GET") id("""^/files/(\d+)/download$""")?.let { f -> return files[f]?.let { Res(200, it.bytes) } ?: err(404) }
            if (method == "DELETE") id("""^/files/(\d+)$""")?.let { f -> files.remove(f); return ok(true) }
        }
        return err(404)
    }

    init {
        Thread {
            while (alive) {
                val c = runCatching { sock.accept() }.getOrNull() ?: break
                Thread {
                    c.use { s ->
                        val inp = java.io.BufferedInputStream(s.getInputStream())
                        fun line(): String { val b = java.io.ByteArrayOutputStream(); while (true) { val x = inp.read(); if (x < 0 || x == '\n'.code) break; if (x != '\r'.code) b.write(x) }; return b.toString("UTF-8") }
                        val req = line().split(' ')
                        if (req.size < 2) return@use
                        val h = HashMap<String, String>()
                        while (true) { val l = line(); if (l.isEmpty()) break; h[l.substringBefore(':').trim().lowercase()] = l.substringAfter(':').trim() }
                        val len = h["content-length"]?.toInt() ?: 0
                        val body = ByteArray(len); var r = 0; while (r < len) { val n = inp.read(body, r, len - r); if (n < 0) break; r += n }
                        val res = handle(req[0], req[1], h["authorization"], body)
                        val out = s.getOutputStream()
                        out.write("HTTP/1.1 ${res.code} X\r\nContent-Length: ${res.body.size}\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(res.body); out.flush()
                    }
                }.start()
            }
        }.apply { isDaemon = true }.start()
    }
}

/** Tu nube (kDrive): libros, fotos de portada y capturas en todos tus dispositivos, y lo leído de cada libro. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NubeTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var k: FakeKDrive

    @Before fun up() { k = FakeKDrive(); KDrive.api = k.url }
    @After fun down() { k.stop() }

    @Test fun conectar() = runBlocking {
        // El mismo id que el escritorio (herramientas/prueba-nube.js)
        assertEquals(2498222623274449L, nubeId("libro:123"))
        KDrive.save(ctx, "malo", "777")
        val e = runCatching { KDrive.test(ctx) }.exceptionOrNull()
        assertTrue("${e?.message}", e?.message?.contains("token") == true)
        // Con la dirección del navegador en lugar del número
        KDrive.save(ctx, "tok", "https://ksuite.infomaniak.com/kdrive/app/drive/777/files")
        assertEquals("777", KDrive.drive(ctx))
        assertTrue(KDrive.test(ctx).startsWith("Conectado"))
        // El token no viaja con la cuenta (tu pareja lee tus datos)
        assertTrue("tok" !in Store(ctx).syncData())
    }

    @Test fun libroYFotoYLectura() = runBlocking {
        KDrive.save(ctx, "tok", "777")
        val s = Store(ctx)
        // Un libro con su EPUB adjunto y una ficha con una foto tuya de portada
        val epub = File(ctx.cacheDir, "viento.epub").apply { writeBytes(ByteArray(20_000) { (it % 251).toByte() }) }
        val fotoDir = File(ctx.filesDir, "covers").apply { mkdirs() }
        val foto = File(fotoDir, "foto_1700000000000.jpg")
        Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }.let { b -> foto.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        s.save(listOf(Game(501, "El nombre del viento (Crónica del asesino de reyes, #1)", Uri.fromFile(foto).toString(), 92)), 1)
        BookFiles.attachFile(ctx, 501, epub, "viento.epub")
        BookFiles.put(ctx, 501, BookFiles.state(ctx, 501).put("fraction", 0.42).put("cfi", "epubcfi(/6/4)"))

        // Se sube todo: el libro con su enlace (para tu pareja) y la foto
        val msg = NubeSync.run(ctx)
        assertTrue(msg, msg.startsWith("2 subidos"))
        val libro = k.byPath("Atrio/Libros/El nombre del viento (501).epub")
        assertTrue("libro en Atrio/Libros", libro != null && libro.bytes.contentEquals(epub.readBytes()))
        assertTrue("foto en Atrio/Portadas", k.byPath("Atrio/Portadas/foto_1700000000000.jpg") != null)
        val reg = s.loadNube().associateBy { it.k }
        assertTrue(reg["libro:501"]!!.l.contains("share"))
        // La lista de lo subido y lo leído viajan con la cuenta
        val sync = JSONObject(s.syncData())
        assertEquals(2, sync.getJSONArray("nube").length())
        assertEquals(0.42, sync.getJSONArray("lectura").getJSONObject(0).getJSONObject("s").getDouble("fraction"), 0.001)
        assertTrue("sin la dirección del archivo", !sync.getJSONArray("lectura").getJSONObject(0).getJSONObject("s").has("uri"))
        // Otra vez: nada nuevo que subir
        assertEquals("Todo al día", NubeSync.run(ctx))

        // Otro dispositivo: sin el archivo ni la foto, pero con la cuenta
        ctx.getSharedPreferences("lector", Context.MODE_PRIVATE).edit().clear().commit()
        fotoDir.deleteRecursively()
        s.mergeRemote(sync.toString())
        // Lo leído llega (sin archivo todavía)
        assertEquals(0.42, BookFiles.progress(ctx, 501).toDouble(), 0.001)
        // La foto se baja sola
        assertTrue(NubeSync.run(ctx).contains("1 bajado"))
        assertTrue(Photos.here(ctx, Uri.fromFile(foto).toString()))
        // Y el libro, desde su ficha
        assertTrue(NubeSync.fetchBook(ctx, 501))
        val f = File(Uri.parse(BookFiles.get(ctx, 501)).path!!)
        assertTrue(f.readBytes().contentEquals(epub.readBytes()))
        assertEquals("El nombre del viento.epub", BookFiles.name(ctx, 501))
        assertEquals("se conserva lo leído", 0.42, BookFiles.progress(ctx, 501).toDouble(), 0.001)

        // Lo leído en otro dispositivo después, gana; lo de antes, no
        val r = JSONObject(s.syncData())
        val l = r.getJSONArray("lectura").getJSONObject(0)
        l.getJSONObject("s").put("fraction", 0.8); l.put("updated", System.currentTimeMillis() + 60_000)
        s.mergeRemote(r.toString())
        assertEquals(0.8, BookFiles.progress(ctx, 501).toDouble(), 0.001)
        assertTrue("el archivo de aquí sigue", BookFiles.get(ctx, 501) != null)
        l.getJSONObject("s").put("fraction", 0.1); l.put("updated", 1L)
        s.mergeRemote(r.toString())
        assertEquals(0.8, BookFiles.progress(ctx, 501).toDouble(), 0.001)
    }

    @Test fun capturas() = runBlocking {
        KDrive.save(ctx, "tok", "777")
        val s = Store(ctx)
        // Una captura subida por el PC
        val png = ByteArray(5000) { 7 }
        k.files[50] = FakeKDrive.F(50, "Hades II 2026-10-18 13.47.00.png", false, 1, png)
        val meta = JSONObject().put("game", "Hades II").put("sg", 1145360L).put("t", 1_760_000_000_000L).put("w", 3840).put("h", 2160).put("mime", "image/png")
        s.addNube(listOf(NubeFile("shot:abc", 50, "Hades II 2026-10-18 13.47.00.png", 5000, "", meta)))
        // Se puede quitar de la nube (y no se vuelve a subir)
        NubeSync.removeShot(ctx, "abc")
        assertTrue(k.files[50] == null)
        assertTrue(s.loadNube().none { it.k == "shot:abc" })
        assertTrue(s.shotNoCloud("abc"))
    }
}
