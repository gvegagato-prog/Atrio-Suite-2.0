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
        // Borrar una obra la manda a la papelera con todo lo suyo (menos las palabras escritas cada día)
        val palabras = e.palabrasObra(o.id)
        e.borra(o.id)
        assertTrue(e.todos().none { it.t != Escritos.T_LOG })
        assertEquals("en la papelera solo se ve la obra (lo suyo va dentro)", listOf(o.id), e.papelera().map { it.id })
        assertTrue("las palabras escritas hoy se quedan", e.escritas(Escritos.hoy(), o.id) >= 5)
        e.restaura(o.id)
        assertEquals("vuelve con todo", palabras, e.palabrasObra(o.id))
        assertEquals(2, e.capitulos(o.id).size); assertEquals(3, e.fichas(o.id).size)
        assertTrue(e.papelera().isEmpty())
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
        assertEquals("el mismo párrafo cambiado en los dos: el texto del otro se guarda como borrador", 1, bor.size)
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

    @Test fun parrafosYCamposDistintosSeJuntanSinPerderNada() {
        val st = cuenta()
        val a = Escritos(tmp("jA")) { "tablet" }
        val b = Escritos(tmp("jB")) { "movil" }
        val o = a.nuevo(Escritos.T_OBRA, "") { put("titulo", "Juntar") }
        val c = a.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s = a.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("titulo", "Escena"); put("texto", "Primer párrafo.\n\nSegundo párrafo.\n\nTercero.") }
        sync(a, b, st = st)
        // Sin conexión, cada uno cambia un párrafo distinto y un campo distinto
        a.escribe(s.id, "Primer párrafo, en la tablet.\n\nSegundo párrafo.\n\nTercero.")
        a.edita(s.id) { put("titulo", "Título de la tablet") }
        Thread.sleep(5)
        b.escribe(s.id, "Primer párrafo.\n\nSegundo párrafo.\n\nTercero, en el móvil.\n\nY uno nuevo.")
        b.edita(s.id) { put("resumen", "Resumen del móvil") }
        sync(a, st = st); sync(b, st = st); sync(a, st = st)
        val esperado = "Primer párrafo, en la tablet.\n\nSegundo párrafo.\n\nTercero, en el móvil.\n\nY uno nuevo."
        assertEquals("se juntan los dos textos", esperado, b.get(s.id)!!.s("texto"))
        assertEquals("y los dos acaban igual", esperado, a.get(s.id)!!.s("texto"))
        assertEquals("Título de la tablet", a.get(s.id)!!.s("titulo")); assertEquals("Resumen del móvil", a.get(s.id)!!.s("resumen"))
        assertEquals("Título de la tablet", b.get(s.id)!!.s("titulo")); assertEquals("Resumen del móvil", b.get(s.id)!!.s("resumen"))
        assertEquals("las palabras se cuentan del texto juntado", Escritos.palabras(esperado), a.get(s.id)!!.i("palabras"))
        assertTrue("sin choques no hay borradores", a.borradores(s.id).isEmpty() && b.borradores(s.id).isEmpty())
        assertTrue(a.pendientes().isEmpty() && b.pendientes().isEmpty())
    }

    @Test fun unaSubidaQueNoVioLoDelOtroNoLoPisa() {
        val st = cuenta()
        val a = Escritos(tmp("rA")) { "tablet" }
        val b = Escritos(tmp("rB")) { "movil" }
        val o = a.nuevo(Escritos.T_OBRA, "") { put("titulo", "Carrera") }
        val c = a.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s = a.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "Uno.\n\nDos.") }
        sync(a, b, st = st)
        a.escribe(s.id, "Uno (tablet).\n\nDos.")
        sync(a, st = st)
        // B cambia otro párrafo y lo sube sin haber bajado lo de A (como si las dos subidas se cruzaran)
        b.escribe(s.id, "Uno.\n\nDos (móvil).")
        val doc = b.get(s.id)!!
        http("PATCH", "$base/users/${st.accUid}.json?auth=${st.accToken}", JSONObject().put("escritos/${s.id}", doc.o).put("escritosIdx/${s.id}", doc.u).toString())
        sync(a, st = st)
        assertEquals("A no pierde lo suyo: junta lo de B", "Uno (tablet).\n\nDos (móvil).", a.get(s.id)!!.s("texto"))
        sync(b, st = st); sync(a, st = st)
        assertEquals("Uno (tablet).\n\nDos (móvil).", b.get(s.id)!!.s("texto"))
        assertTrue(a.borradores(s.id).isEmpty() && b.borradores(s.id).isEmpty())
        assertTrue(a.pendientes().isEmpty() && b.pendientes().isEmpty())
    }

    @Test fun laPapeleraSeRecuperaEnTodosLosDispositivos() {
        val st = cuenta()
        val a = Escritos(tmp("pA")) { "tablet" }
        val b = Escritos(tmp("pB")) { "movil" }
        val o = a.nuevo(Escritos.T_OBRA, "") { put("titulo", "Papelera") }
        val c = a.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s1 = a.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "La primera escena.") }
        val s2 = a.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "La segunda escena.") }
        val n = a.nuevo(Escritos.T_NOTA, o.id) { put("titulo", "Idea"); put("texto", "Algo") }
        sync(a, b, st = st)
        // Una escena sola y luego el capítulo entero
        a.borra(s1.id)
        a.borra(c.id)
        a.borra(n.id)
        sync(a, b, st = st)
        assertTrue(b.escenasDe(o.id).isEmpty() && b.capitulos(o.id).isEmpty() && b.notas(o.id).isEmpty())
        assertEquals("en la papelera: la nota, el capítulo (con su escena dentro) y la escena que se borró antes", setOf(n.id, c.id, s1.id), b.papelera().map { it.id }.toSet())
        assertEquals(listOf(s2.id), b.conElBorrado(c.id).map { it.id })
        // Se recupera en el otro dispositivo y vuelve en los dos
        b.restaura(c.id)
        sync(b, a, st = st)
        assertEquals("el capítulo vuelve con la escena que se fue con él", listOf(s2.id), a.escenasDe(o.id).map { it.id })
        assertEquals("La segunda escena.", a.get(s2.id)!!.s("texto"))
        // Recuperar la escena que se borró sola
        a.restaura(s1.id)
        sync(a, b, st = st)
        assertEquals(setOf(s1.id, s2.id), b.escenasDe(o.id).map { it.id }.toSet())
        // Vaciar: ya no se puede recuperar, en ningún sitio
        b.vaciaPapelera()
        sync(b, a, st = st)
        assertTrue(a.papelera().isEmpty() && b.papelera().isEmpty())
        a.restaura(n.id)
        assertNull(a.get(n.id))
        assertTrue("queda solo la marca de borrado", Escritos.lapida(JSONObject(http("GET", "$base/users/${st.accUid}/escritos/${n.id}.json?auth=${st.accToken}"))))
        // Una escena recuperada cuyo capítulo también estaba borrado trae su capítulo (con lo que se fue con él)
        a.borra(s1.id); a.borra(c.id)
        a.restaura(s1.id)
        assertEquals(setOf(s1.id, s2.id), a.escenasDe(o.id).map { it.id }.toSet())
        assertTrue(a.papelera().isEmpty())
        a.borra(s1.id)
        // Lo de hace más de 30 días se vacía solo
        a.purga(antesDe = System.currentTimeMillis() + 1)
        assertTrue(a.papelera().isEmpty())
    }

    @Test fun loBorradoEnUnSitioYEscritoEnOtroNoSePierde() {
        val st = cuenta()
        val a = Escritos(tmp("bA")) { "tablet" }
        val b = Escritos(tmp("bB")) { "movil" }
        val o = a.nuevo(Escritos.T_OBRA, "") { put("titulo", "Cruce") }
        val c = a.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s = a.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "Antes.") }
        sync(a, b, st = st)
        a.borra(s.id)
        Thread.sleep(5)
        b.escribe(s.id, "Antes. Y lo que escribí sin saber que se había borrado.")
        sync(a, st = st); sync(b, st = st); sync(a, st = st)
        assertNull("la escena queda borrada", b.get(s.id))
        assertEquals("pero en la papelera, con lo último escrito", "Antes. Y lo que escribí sin saber que se había borrado.", b.papelera().single { it.id == s.id }.s("texto"))
        a.restaura(s.id)
        sync(a, b, st = st)
        assertEquals("Antes. Y lo que escribí sin saber que se había borrado.", b.get(s.id)!!.s("texto"))
    }

    @Test fun seguirEscribiendoMientrasSubeNoCreaChoques() {
        val st = cuenta()
        val a = Escritos(tmp("sA")) { "tablet" }
        val b = Escritos(tmp("sB")) { "movil" }
        val o = a.nuevo(Escritos.T_OBRA, "") { put("titulo", "Seguido") }
        val c = a.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno") }
        val s = a.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("texto", "Una") }
        sync(a, b, st = st)
        // Se escribe en A a ratos; B va recibiendo y también escribe en otro párrafo; nunca hay choques
        var txt = "Una"
        for (i in 1..6) {
            txt += " palabra"
            a.escribe(s.id, txt)
            sync(a, st = st)
            sync(b, st = st)
        }
        assertEquals(txt, b.get(s.id)!!.s("texto"))
        b.escribe(s.id, txt + "\n\nDesde el móvil.")
        a.escribe(s.id, txt + " final")
        sync(b, st = st); sync(a, st = st); sync(b, st = st)
        assertEquals(txt + " final\n\nDesde el móvil.", a.get(s.id)!!.s("texto"))
        assertEquals(a.get(s.id)!!.s("texto"), b.get(s.id)!!.s("texto"))
        assertTrue(a.borradores(s.id).isEmpty() && b.borradores(s.id).isEmpty())
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
