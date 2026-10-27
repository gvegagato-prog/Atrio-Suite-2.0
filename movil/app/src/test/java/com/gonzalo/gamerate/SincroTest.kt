package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tu cuenta al momento (Sincro), contra el Firebase de mentira (herramientas/fakefirebase.js): lo que cambia aquí sube
 * en un segundo (también lo que no es una ficha, como una saga o el diario), lo que sube otro dispositivo llega solo, y un
 * cambio que no toca la hora de edición también gana al mezclar. Necesita node en el PATH.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SincroTest {
    companion object {
        private var proc: Process? = null
        private val port = 9300 + (Math.random() * 280).toInt()
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
            if (method == "PATCH") { c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", "PATCH") } else c.requestMethod = method
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            return (if (c.responseCode in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        }
    }

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** Una cuenta nueva en el Firebase de mentira, con la sesión puesta en este dispositivo. Devuelve (store, token). */
    private fun cuenta(): Pair<Store, String> {
        val su = JSONObject(http("POST", "$base/v1/accounts:signUp?key=k", JSONObject().put("email", "s${System.nanoTime()}@ronron.example.com").put("password", "secreto1").toString()))
        val s = Store(ctx)
        s.dbUrl = base; s.authBase = base; s.webKey = "k"
        s.accUser = "prueba"; s.accUid = su.getString("localId")
        s.accToken = su.getString("idToken"); s.accRefresh = su.getString("refreshToken"); s.accExp = System.currentTimeMillis() + 3_600_000
        return s to su.getString("idToken")
    }

    private fun espera(ms: Long, que: () -> Boolean): Long {
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < ms) { if (runCatching(que).getOrDefault(false)) return System.currentTimeMillis() - t0; Thread.sleep(100) }
        return -1
    }

    private fun subido(s: Store, tok: String): JSONObject? {
        val raw = http("GET", "$base/users/${s.accUid}/dev/${s.accDevice}/data.json?auth=$tok")
        val txt = JSONTokener(raw).nextValue() as? String ?: return null
        return JSONObject(txt)
    }

    @Test fun unaSagaSubeEnSeguidaAunqueNoSeaUnaFicha() {
        val (s, tok) = cuenta()
        Sincro.init(ctx)
        s.saveSagas(listOf(Saga(7, "Cosmere", listOf(1L, 2L), updated = System.currentTimeMillis())))
        val t = espera(6000) { subido(s, tok)?.optJSONArray("sagas")?.toString()?.contains("Cosmere") == true }
        assertTrue("la saga sube a la cuenta (tardó $t ms)", t in 0..3500)
        // Y una entrada del diario, igual
        s.saveDiario(listOf(Nota(1, 20000, "Hoy he leído mucho", updated = System.currentTimeMillis())))
        val t2 = espera(6000) { subido(s, tok)?.optJSONArray("diario")?.toString()?.contains("leído mucho") == true }
        assertTrue("el diario sube a la cuenta (tardó $t2 ms)", t2 in 0..3500)
        assertTrue("y ya no queda nada pendiente", espera(3000) { !s.pendiente } >= 0)
    }

    @Test fun loQueSubeOtroDispositivoLlegaSolo() {
        val (s, tok) = cuenta()
        Sincro.init(ctx)
        Sincro.alVolver()
        Thread.sleep(700)
        // La tablet sube un juego nuevo (como lo hace cualquier dispositivo de la cuenta: su hueco y su hora en «stamps»)
        val data = JSONObject().put("games", JSONArray().put(gameToJson(Game(42, "Hades II", "", 91, updated = System.currentTimeMillis()))))
        http("PUT", "$base/users/${s.accUid}/dev/tablet.json?auth=$tok", JSONObject().put("name", "t").put("user", "prueba").put("device", "android")
            .put("updated", JSONObject().put(".sv", "timestamp")).put("data", data.toString()).toString())
        val ts = http("GET", "$base/users/${s.accUid}/dev/tablet/updated.json?auth=$tok").trim()
        http("PATCH", "$base/users/${s.accUid}.json?auth=$tok", JSONObject().put("stamps/tablet", ts.toLong()).toString())
        val t = espera(6000) { s.load(0).any { it.id == 42L } }
        Sincro.alSalir()
        assertTrue("llega en un par de segundos sin tocar nada (tardó $t ms)", t in 0..3000)
    }

    @Test fun loDeTuParejaLlegaSolo() {
        val (s, tok) = cuenta()
        // Tu pareja: otra cuenta que te deja ver lo suyo
        val su = JSONObject(http("POST", "$base/v1/accounts:signUp?key=k", JSONObject().put("email", "p${System.nanoTime()}@ronron.example.com").put("password", "secreto1").toString()))
        val puid = su.getString("localId"); val ptok = su.getString("idToken")
        http("PUT", "$base/users/$puid/partner.json?auth=$ptok", JSONObject.quote(s.accUid))
        s.partnerUid = puid
        Sincro.init(ctx)
        Sincro.alVolver()
        Thread.sleep(700)
        // Valora algo en su móvil
        val data = JSONObject().put("games", JSONArray().put(gameToJson(Game(77, "Balatro", "", 95, updated = System.currentTimeMillis()))))
        http("PUT", "$base/users/$puid/dev/m1.json?auth=$ptok", JSONObject().put("name", "Laura").put("user", "laura").put("device", "android")
            .put("updated", JSONObject().put(".sv", "timestamp")).put("data", data.toString()).toString())
        val ts = http("GET", "$base/users/$puid/dev/m1/updated.json?auth=$ptok").trim()
        http("PATCH", "$base/users/$puid.json?auth=$ptok", JSONObject().put("stamps/m1", ts.toLong()).toString())
        val t = espera(6000) { Couple.cached(s)?.games(GAMES)?.any { it.id == 77L } == true }
        Sincro.alSalir()
        assertTrue("lo de tu pareja llega solo (tardó $t ms)", t in 0..3000)
        s.partnerUid = ""
    }

    @Test fun unCambioSinTocarLaHoraTambienGana() {
        val s = Store(ctx)
        // Las dos versiones tienen la misma hora de edición (p. ej. la portada se buscó sola)
        s.save(listOf(Game(5, "Celeste", "", 80, updated = 1000L)), 0)
        s.save(listOf(Game(5, "Celeste", "https://img/celeste.jpg", 80, updated = 1000L)), 0)
        val aqui = JSONArray(s.syncData().let { JSONObject(it).getJSONArray("games").toString() }).getJSONObject(0)
        assertTrue("el cambio lleva su versión de sincronización", aqui.optLong("sv") > 1000L)
        // Otro dispositivo con la versión vieja (misma hora, sin «sv»): al mezclar, gana la nueva de aquí…
        s.mergeRemote(JSONObject().put("games", JSONArray().put(gameToJson(Game(5, "Celeste", "", 80, updated = 1000L)))).toString())
        assertEquals("https://img/celeste.jpg", s.load(0).first().cover)
        // …y si el otro la cambió después (con su «sv» más nuevo), gana la suya
        s.mergeRemote(JSONObject().put("games", JSONArray().put(gameToJson(Game(5, "Celeste", "https://img/otra.jpg", 80, updated = 1000L)).put("sv", System.currentTimeMillis() + 60_000))).toString())
        assertEquals("https://img/otra.jpg", s.load(0).first().cover)
        // Guardar lo mismo otra vez no lo marca como cambiado (ni lo vuelve a subir)
        val sv = JSONObject(s.syncData()).getJSONArray("games").getJSONObject(0).optLong("sv")
        val antes = s.dirtyAt
        Thread.sleep(5)
        s.save(s.load(0), 0)
        assertEquals(sv, JSONObject(s.syncData()).getJSONArray("games").getJSONObject(0).optLong("sv"))
        assertEquals("sin cambios no queda nada pendiente de subir", antes, s.dirtyAt)
    }

    @Test fun cambiarAlgoMientrasLlegaOtraCosaNoLaPierde() {
        val antes = listOf(Game(1, "A", ""), Game(2, "B", ""))
        // Aquí: cambias A y quitas B (sobre lo que tenías en pantalla)
        val nueva = listOf(Game(1, "A editado", ""))
        // Mientras tanto llegó C de otro dispositivo, y B cambió allí
        val ahora = listOf(Game(1, "A", ""), Game(2, "B de la tablet", ""), Game(3, "C", ""))
        val r = rebase(antes, nueva, ahora) { it.id }
        assertEquals(listOf("A editado", "C"), r.map { it.name })
        // Sin tocar nada aquí, lo de fuera se queda tal cual (y lo borrado fuera, fuera)
        val r2 = rebase(antes, antes, listOf(Game(1, "A de fuera", ""), Game(4, "D", ""))) { it.id }
        assertEquals(listOf("A de fuera", "D"), r2.map { it.name })
    }
}
