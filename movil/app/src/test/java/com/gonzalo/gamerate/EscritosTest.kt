package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Escribir (Escritos): guardado en archivos, escribir sin conexión y subir después, dos dispositivos a la vez
 * (mezcla y conflictos sin perder texto), borrados y palabras del día. Contra el Firebase de mentira.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EscritosTest {
    companion object {
        private var proc: Process? = null
        private val port = 9600 + (Math.random() * 280).toInt()
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
    private fun tmp(n: String) = File(ctx.cacheDir, "esc_${n}_${System.nanoTime()}")

    private fun cuenta(): Store {
        val su = JSONObject(http("POST", "$base/v1/accounts:signUp?key=k", JSONObject().put("email", "e${System.nanoTime()}@ronron.example.com").put("password", "secreto1").toString()))
        val s = Store(ctx)
        s.dbUrl = base; s.authBase = base; s.webKey = "k"
        s.accUser = "prueba"; s.accUid = su.getString("localId")
        s.accToken = su.getString("idToken"); s.accRefresh = su.getString("refreshToken"); s.accExp = System.currentTimeMillis() + 3_600_000
        return s
    }

    private fun sync(vararg e: Escritos, st: Store) = runBlocking { e.forEach { it.sincroniza(st) } }

    @Test fun palabrasBuscarYFichas() {
        val e = Escritos(tmp("a")) { "movil" }
        assertEquals(7, Escritos.palabras("# Capítulo uno\n\n*Hola* **mundo**, ¡qué tal-vez! No's"))
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "La torre") }
        val c = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s = e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("titulo", "Llegada"); put("texto", "Aldara subió la torre. El Cuervo la esperaba.") }
        e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "Aldara"); put("alias", "la hechicera, Ald") }
        e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "personaje"); put("nombre", "El Cuervo") }
        e.nuevo(Escritos.T_FICHA, o.id) { put("tipo", "lugar"); put("nombre", "Valle") }
        assertEquals(8, e.palabrasObra(o.id))
        assertEquals(listOf("Aldara", "El Cuervo"), e.fichasEn(o.id, e.get(s.id)!!.s("texto")).map { it.s("nombre") }.sorted())
        assertEquals("la búsqueda no mira acentos ni mayúsculas", 1, e.busca(o.id, "TORRE").filter { it.doc.t == Escritos.T_ESCENA }.size)
        assertEquals(1, e.reemplaza(o.id, "cuervo", "Halcón"))
        assertTrue(e.get(s.id)!!.s("texto").contains("El Halcón la esperaba"))
        // Escribir cuenta lo escrito hoy y la racha
        e.escribe(s.id, e.get(s.id)!!.s("texto") + " Y entonces ocurrió algo terrible.")
        assertTrue(e.escritas(Escritos.hoy(), o.id) >= 5)
        assertEquals(1, e.racha(o.id))
        // Orden y movimiento
        val c2 = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Dos") }
        e.mueve(c2.id, null, c.id)
        assertEquals(listOf("Dos", "Uno"), e.capitulos(o.id).map { it.s("titulo") })
        // Versiones
        assertTrue(e.instantanea(s.id, "primera", fuerza = true)); assertFalse(e.instantanea(s.id, "primera", fuerza = true))
        e.instantanea(s.id, "segunda", fuerza = true)
        assertEquals("antes de reemplazar se guarda una versión", listOf("Aldara subió la torre. El Cuervo la esperaba.", "primera", "segunda"), e.versiones(s.id).map { it.texto })
        // Borrar una obra borra todo lo suyo
        e.borra(o.id)
        assertTrue(e.todos().isEmpty())
    }

    @Test fun seGuardaEnDiscoYVuelve() {
        val d = tmp("disco")
        val e = Escritos(d) { "movil" }
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Persistente") }
        val c = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "Texto que no se pierde") }
        e.vacia()
        val otra = Escritos(d) { "movil" }
        assertEquals("Persistente", otra.obras().single().s("titulo"))
        assertEquals("Texto que no se pierde", otra.escenasDe(o.id).single().s("texto"))
    }

    @Test fun sinConexionSeEscribeYDespuesSube() {
        val st = cuenta()
        val bueno = st.dbUrl
        val e = Escritos(tmp("off")) { "movil" }
        // Sin red (un puerto que no responde): escribir funciona y la subida falla, sin perder nada
        st.dbUrl = "http://127.0.0.1:1"
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "En el tren") }
        val c = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s = e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "Escrito sin cobertura") }
        val fallo = runCatching { sync(e, st = st) }.exceptionOrNull()
        assertNotNull("sin conexión falla (y se reintenta)", fallo)
        assertEquals("todo queda pendiente", 3, e.pendientes().size)
        e.escribe(s.id, "Escrito sin cobertura, y seguí escribiendo")
        // Vuelve la red: sube todo, lo último también
        st.dbUrl = bueno
        sync(e, st = st)
        assertTrue("ya no queda nada pendiente", e.pendientes().isEmpty())
        val raw = http("GET", "$base/users/${st.accUid}/escritos/${s.id}.json?auth=${st.accToken}")
        assertTrue(raw, JSONObject(raw).getString("texto").endsWith("seguí escribiendo"))
        assertEquals(e.get(s.id)!!.u, http("GET", "$base/users/${st.accUid}/escritosIdx/${s.id}.json?auth=${st.accToken}").trim().toLong())
    }

    @Test fun dosDispositivosSeMezclan() {
        val st = cuenta()
        val a = Escritos(tmp("A")) { "tablet" }
        val b = Escritos(tmp("B")) { "movil" }
        val o = a.nuevo(Escritos.T_OBRA, "") { put("titulo", "Compartida") }
        val c = a.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s = a.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "Primera línea.") }
        sync(a, b, st = st)
        assertEquals("llega la obra entera al otro dispositivo", "Primera línea.", b.escenasDe(o.id).single().s("texto"))
        // B escribe más; A lo recibe
        b.escribe(s.id, "Primera línea. Segunda línea.")
        sync(b, a, st = st)
        assertEquals("Primera línea. Segunda línea.", a.get(s.id)!!.s("texto"))
        assertTrue(a.pendientes().isEmpty() && b.pendientes().isEmpty())
        // Un borrado se propaga
        a.borra(s.id)
        sync(a, b, st = st)
        assertNull(b.get(s.id))
        assertTrue(b.escenasDe(o.id).isEmpty())
    }

    @Test fun editarLoMismoALaVezNoPierdeTexto() {
        val st = cuenta()
        val a = Escritos(tmp("cA")) { "tablet" }
        val b = Escritos(tmp("cB")) { "movil" }
        val o = a.nuevo(Escritos.T_OBRA, "") { put("titulo", "Conflicto") }
        val c = a.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s = a.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "Base.") }
        sync(a, b, st = st)
        // Los dos cambian la escena sin conexión
        a.escribe(s.id, "Base. Lo que escribí en la tablet.")
        Thread.sleep(5)
        b.escribe(s.id, "Base. Lo que escribí en el móvil.")
        sync(a, st = st)          // A sube primero
        sync(b, st = st)          // B baja lo de A (conflicto) y sube la suya
        sync(a, st = st)          // A recibe el resultado
        val ganadora = b.get(s.id)!!.s("texto")
        assertEquals("el más nuevo gana", "Base. Lo que escribí en el móvil.", ganadora)
        assertEquals("los dos acaban igual", ganadora, a.get(s.id)!!.s("texto"))
        val bor = b.borradores(s.id)
        assertEquals("el texto del otro se guarda como borrador", 1, bor.size)
        assertEquals("Base. Lo que escribí en la tablet.", bor.single().s("texto"))
        assertTrue(bor.single().b("conflicto"))
        assertEquals("y el borrador llega también al otro dispositivo, una sola vez", 1, a.borradores(s.id).size)
        assertTrue(a.pendientes().isEmpty() && b.pendientes().isEmpty())
    }

    @Test fun lasPalabrasDelDiaSeSumanDeTodosLosDispositivos() {
        val st = cuenta()
        val a = Escritos(tmp("lA")) { "tablet" }
        val b = Escritos(tmp("lB")) { "movil" }
        val o = a.nuevo(Escritos.T_OBRA, "") { put("titulo", "Palabras") }
        sync(a, b, st = st)
        a.registra(o.id, 300)
        b.registra(o.id, 450)
        sync(a, b, st = st); sync(a, b, st = st)
        assertEquals(750, a.escritas(Escritos.hoy(), o.id))
        assertEquals(750, b.escritas(Escritos.hoy(), o.id))
        // Más en la tablet: no pisa lo del móvil
        a.registra(o.id, 50)
        sync(a, b, st = st)
        assertEquals(800, b.escritas(Escritos.hoy(), o.id))
    }

    @Test fun alCambiarDeCuentaTodoSeSubeALaNueva() {
        val st1 = cuenta(); val uid1 = st1.accUid
        val e = Escritos(tmp("cuentas")) { "movil" }
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Mía") }
        e.cuenta(uid1)
        sync(e, st = st1)
        assertTrue(e.pendientes().isEmpty())
        val st2 = cuenta()
        e.cuenta(st2.accUid)
        assertTrue("al cambiar de cuenta vuelve a estar todo pendiente", e.pendientes().isNotEmpty())
        sync(e, st = st2)
        assertTrue(http("GET", "$base/users/${st2.accUid}/escritos/${o.id}.json?auth=${st2.accToken}").contains("Mía"))
    }
}
