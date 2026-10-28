package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
 * El móvil y Atrio en el PC (el módulo de JavaScript de verdad, en node) con la misma cuenta: lo que escribe uno lo lee el otro
 * y viceversa (mismo formato, mismas reglas de mezcla). Necesita node en el PATH.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EscritosInteropTest {
    companion object {
        private var proc: Process? = null
        private val port = 9900 + (Math.random() * 90).toInt()
        val base = "http://127.0.0.1:$port"
        lateinit var raiz: File

        @BeforeClass @JvmStatic fun up() {
            var dir = File("").absoluteFile
            while (!File(dir, "herramientas/fakefirebase.js").exists()) dir = dir.parentFile ?: error("no encuentro herramientas/")
            raiz = dir
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

    @Test fun movilYPcSeLeenYSeEscriben() = runBlocking {
        val su = JSONObject(http("POST", "$base/v1/accounts:signUp?key=k", JSONObject().put("email", "i${System.nanoTime()}@ronron.example.com").put("password", "secreto1").toString()))
        val st = Store(ctx)
        st.dbUrl = base; st.authBase = base; st.webKey = "k"; st.accUser = "prueba"; st.accUid = su.getString("localId")
        st.accToken = su.getString("idToken"); st.accRefresh = su.getString("refreshToken"); st.accExp = System.currentTimeMillis() + 3_600_000
        val m = Escritos(File(ctx.cacheDir, "interop_${System.nanoTime()}")) { "movil" }
        val o = m.nuevo(Escritos.T_OBRA, "") { put("titulo", "Cruzada"); put("autor", "Gonzalo") }
        val c = m.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s = m.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("titulo", "Escena"); put("texto", "Escrito en el móvil, con ñ y «comillas» y emoji 🙂.") }
        m.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "Aldara") }
        m.registra(o.id, 120)
        m.sincroniza(st)
        // Atrio en el PC: baja lo del móvil, escribe, añade una ficha y sube
        val p = ProcessBuilder("node", File(raiz, "herramientas/escritos-pc.js").path, base, st.accUid, st.accToken).redirectErrorStream(true).start()
        val salida = p.inputStream.bufferedReader().readText().trim()
        assertEquals(salida, 0, p.waitFor())
        val r = JSONObject(salida.lines().last())
        val leido = r.getJSONObject("leido")
        assertEquals("el PC lee la obra del móvil", "Cruzada", leido.getJSONArray("obras").getString(0))
        assertEquals("con sus ñ, comillas y emoji", "Escrito en el móvil, con ñ y «comillas» y emoji 🙂.", leido.getString("texto"))
        assertEquals("Aldara", leido.getJSONArray("fichas").getString(0))
        assertEquals("y las palabras escritas", 120, leido.getInt("hoyMovil"))
        assertEquals(0, r.getInt("pendientes"))
        // El móvil recibe lo del PC
        m.sincroniza(st)
        assertTrue(m.get(s.id)!!.s("texto").endsWith("Y lo continué en el PC."))
        assertTrue("la ficha creada en el PC", m.fichas(o.id).any { it.s("nombre") == "Cala del PC" })
        assertEquals("120 del móvil + 77 apuntadas en el PC + las 6 palabras que escribió el PC", 203, m.escritas(Escritos.hoy(), o.id))
        assertTrue("nada pendiente", m.pendientes().isEmpty())
    }

    private fun pc(st: Store, vararg mas: String): JSONObject {
        val p = ProcessBuilder(listOf("node", File(raiz, "herramientas/escritos-pc.js").path, base, st.accUid, st.accToken) + mas).redirectErrorStream(true).start()
        val salida = p.inputStream.bufferedReader().readText().trim()
        assertEquals(salida, 0, p.waitFor())
        return JSONObject(salida.lines().last())
    }

    @Test fun movilYPcJuntanLoQueCambianALaVez() = runBlocking {
        val su = JSONObject(http("POST", "$base/v1/accounts:signUp?key=k", JSONObject().put("email", "m${System.nanoTime()}@ronron.example.com").put("password", "secreto1").toString()))
        val st = Store(ctx)
        st.dbUrl = base; st.authBase = base; st.webKey = "k"; st.accUser = "prueba"; st.accUid = su.getString("localId")
        st.accToken = su.getString("idToken"); st.accRefresh = su.getString("refreshToken"); st.accExp = System.currentTimeMillis() + 3_600_000
        val m = Escritos(File(ctx.cacheDir, "interop_m_${System.nanoTime()}")) { "movil" }
        val o = m.nuevo(Escritos.T_OBRA, "") { put("titulo", "Mezcla") }
        val c = m.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s = m.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "Primer párrafo.\n\nSegundo párrafo.") }
        val n = m.nuevo(Escritos.T_NOTA, o.id) { put("titulo", "Idea"); put("texto", "Una idea") }
        m.sincroniza(st)
        val dirPc = File(ctx.cacheDir, "interop_pc_${System.nanoTime()}").path
        pc(st, "mezcla2", dirPc)
        // A la vez: el móvil cambia el primer párrafo (sin conexión) y el PC el segundo, y además borra la nota
        m.escribe(s.id, "Primer párrafo, en el móvil.\n\nSegundo párrafo.")
        val r1 = pc(st, "mezcla1", dirPc)
        assertEquals("Primer párrafo.\n\nSegundo párrafo. Y esto, en el PC.", r1.getString("texto"))
        m.sincroniza(st)
        val junto = "Primer párrafo, en el móvil.\n\nSegundo párrafo. Y esto, en el PC."
        assertEquals("el móvil junta lo del PC con lo suyo", junto, m.get(s.id)!!.s("texto"))
        assertTrue("la nota que borró el PC está en la papelera del móvil", m.papelera().any { it.id == n.id })
        m.restaura(n.id)
        m.sincroniza(st)
        val r2 = pc(st, "mezcla2", dirPc)
        assertEquals("y el PC acaba con lo mismo", junto, r2.getString("texto"))
        assertEquals("con la nota recuperada", 1, r2.getInt("notas"))
        assertEquals(0, r2.getInt("papelera")); assertEquals(0, r2.getInt("borradores")); assertEquals(0, r2.getInt("pendientes"))
        assertTrue(m.pendientes().isEmpty() && m.borradores(s.id).isEmpty())
    }
}
