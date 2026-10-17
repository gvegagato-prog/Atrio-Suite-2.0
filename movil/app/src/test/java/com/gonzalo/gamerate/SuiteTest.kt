package com.gonzalo.gamerate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Lógica de la suite en el ordenador: el streaming contra el Firebase de mentira (herramientas/fakefirebase.js)
 * y la mezcla de apuntes rápidos y partes jugadas. Necesita node en el PATH.
 */
class SuiteTest {
    companion object {
        private var proc: Process? = null
        private val port = 9600 + (Math.random() * 300).toInt()
        val base = "http://127.0.0.1:$port"

        @BeforeClass @JvmStatic fun up() {
            var dir = File("").absoluteFile
            while (!File(dir, "herramientas/fakefirebase.js").exists()) dir = dir.parentFile ?: error("no encuentro herramientas/")
            proc = ProcessBuilder("node", File(dir, "herramientas/fakefirebase.js").path, port.toString()).redirectErrorStream(true).start()
            Thread.sleep(800)
        }

        @AfterClass @JvmStatic fun down() { proc?.destroy() }

        fun http(method: String, url: String, body: String? = null): String {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = method
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            return (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        }
    }

    @Test fun streamingRecibeCambiosAlMomento() {
        val su = JSONObject(http("POST", "$base/v1/accounts:signUp?key=k", JSONObject().put("email", "s${System.nanoTime()}@ronron.example.com").put("password", "secreto1").toString()))
        val uid = su.getString("localId")
        val tok = su.getString("idToken")
        val got = CountDownLatch(1)
        var last: JSONObject? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val s = FbStream("prueba", { "$base/users/$uid/suite.json?auth=$tok" }, { t, _ ->
            last = t
            if (t?.optJSONObject("live")?.optJSONObject("pc1")?.optBoolean("on") == true) got.countDown()
        })
        s.start(scope)
        Thread.sleep(600)
        val t0 = System.currentTimeMillis()
        http("PUT", "$base/users/$uid/suite/live/pc1.json?auth=$tok", JSONObject().put("on", true).put("name", "PC").toString())
        assertTrue("no ha llegado el cambio", got.await(5, TimeUnit.SECONDS))
        assertTrue("tarda demasiado", System.currentTimeMillis() - t0 < 2000)
        assertEquals("PC", last!!.getJSONObject("live").getJSONObject("pc1").getString("name"))
        // Borrar también llega
        val gone = CountDownLatch(1)
        val s2 = FbStream("prueba2", { "$base/users/$uid/suite.json?auth=$tok" }, { t, _ -> if (t?.optJSONObject("live") == null) gone.countDown() })
        s.stop()
        http("DELETE", "$base/users/$uid/suite/live/pc1.json?auth=$tok")
        s2.start(scope)
        assertTrue(gone.await(5, TimeUnit.SECONDS))
        s2.stop()
        scope.cancel()
    }

    @Test fun apuntesSeSumanAlMezclar() {
        val a = playToJson(Play(1, "Juego", "", "", 1, emptyList(), memos = listOf(Memo(10, "uno", u = 10))))
        val b = playToJson(Play(1, "Juego", "", "", 1, emptyList(), memos = listOf(Memo(10, "", del = true, u = 20), Memo(30, "tres"))))
        val m = playFromJson(uniteLists(JSONObject(a.toString()), JSONObject(b.toString())))
        assertEquals(listOf(30L), m.liveMemos.map { it.t }) // el borrado gana y no revive
        assertEquals(2, m.memos.size)
    }

    @Test fun lasPartidasDeVersionesAntiguasSeDescartan() {
        // Una ficha de antes, con partidas para valorar y apuntes que valoraban una de ellas
        val old = JSONObject().put("id", 4).put("name", "Antiguo").put("cover", "").put("hero", "").put("status", 1).put("minutes", 90)
            .put("entries", JSONArray().put(JSONObject().put("time", 1).put("general", 70).put("gfx", 50).put("story", 50).put("music", 50).put("gameplay", 50).put("note", "bien").put("seg", "a1").put("part", "Acto 2")))
            .put("segs", JSONArray().put(JSONObject().put("id", "a1").put("s", 1).put("e", 2).put("min", 30).put("src", "atrio")))
            .put("noParts", true)
        val p = playFromJson(old)
        assertEquals(90, p.minutes)
        assertEquals("bien", p.entries.single().note)
        val back = playToJson(p)
        assertTrue(!back.has("segs") && !back.has("noParts") && !back.getJSONArray("entries").getJSONObject(0).has("seg") && !back.getJSONArray("entries").getJSONObject(0).has("part"))
        // Y al mezclar con otro dispositivo que aún las tenga, tampoco vuelven
        assertTrue(!uniteLists(JSONObject(back.toString()), JSONObject(old.toString())).has("segs"))
    }

    @Test fun fichasAntiguasSinCamposNuevos() {
        val old = JSONObject().put("id", 3).put("name", "Viejo").put("cover", "").put("hero", "").put("status", 1)
            .put("entries", JSONArray().put(JSONObject().put("time", 1).put("general", 50).put("gfx", 50).put("story", 50).put("music", 50).put("gameplay", 50).put("note", "")))
        val p = playFromJson(old)
        assertTrue(p.memos.isEmpty())
        assertNotNull(playToJson(p))
        // Si no hay apuntes rápidos, la ficha se guarda igual que antes
        assertTrue(!playToJson(p).has("memos"))
    }
}
