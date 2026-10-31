package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Compartir con tu pareja (EscribirPareja.kt), con dos cuentas de verdad en el Firebase de mentira (con sus reglas):
 * lo compartido se lee, lo no compartido no llega, se anota y el autor ve la nota.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompartirParejaTest {
    companion object {
        private var proc: Process? = null
        private val port = 9900 + (Math.random() * 90).toInt()
        val base = "http://127.0.0.1:$port"

        @BeforeClass @JvmStatic fun up() {
            var dir = File("").absoluteFile
            while (!File(dir, "herramientas/fakefirebase.js").exists()) dir = dir.parentFile ?: error("no encuentro herramientas/")
            proc = ProcessBuilder("node", File(dir, "herramientas/fakefirebase.js").path, port.toString()).redirectErrorStream(true).start()
            Thread.sleep(800)
        }
        @AfterClass @JvmStatic fun down() { proc?.destroy() }
    }

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private class Cuenta(val uid: String, val token: String, val refresh: String)

    private fun alta(): Cuenta {
        val su = JSONObject(EscritosTest.http("POST", "$base/v1/accounts:signUp?key=k", JSONObject().put("email", "p${System.nanoTime()}@ronron.example.com").put("password", "secreto1").toString()))
        return Cuenta(su.getString("localId"), su.getString("idToken"), su.getString("refreshToken"))
    }

    /** Pone en el móvil (Store) la cuenta [c], con [pareja] como pareja. */
    private fun soy(c: Cuenta, pareja: Cuenta): Store {
        val s = Store(ctx)
        s.dbUrl = base; s.authBase = base; s.webKey = "k"
        s.accUser = "prueba"; s.accUid = c.uid; s.accToken = c.token; s.accRefresh = c.refresh; s.accExp = System.currentTimeMillis() + 3_600_000
        s.partnerUid = pareja.uid
        return s
    }

    @Test fun loCompartidoSeLeeSeAnotaYElAutorLoVe() = runBlocking {
        val yo = alta(); val ella = alta()
        // Las dos cuentas enlazadas (cada una dice quién es su pareja: es lo que miran las reglas)
        EscritosTest.http("PUT", "$base/users/${yo.uid}/partner.json?auth=${yo.token}", JSONObject.quote(ella.uid))
        EscritosTest.http("PUT", "$base/users/${ella.uid}/partner.json?auth=${ella.token}", JSONObject.quote(yo.uid))

        // Yo escribo: una obra con dos capítulos; comparto solo el primero y, aparte, una parte entera
        val stYo = soy(yo, ella)
        val e = Escritos(File(ctx.cacheDir, "comp_${System.nanoTime()}"))
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "Guadaña azabache"); put("meta", 90000) }
        val c1 = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "El juramento") }
        val c2 = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "Secreto") }
        val parte = e.nuevo(Escritos.T_PARTE, obra.id) { put("titulo", "Segunda parte") }
        val c3 = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "La corte"); put("aid", parte.id) }
        e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", c1.id); put("titulo", "Escena 1"); put("texto", "Primer párrafo del juramento.\n\n***\n\nSegundo párrafo, con **negrita**.") }
        e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", c2.id); put("texto", "Esto no lo debe leer nadie.") }
        e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", c3.id); put("texto", "En la corte del rey Boro.") }
        e.nuevo(Escritos.T_NOTA, obra.id) { put("titulo", "Nota privada"); put("texto", "solo mía") }
        e.edita(c1.id) { put("comp", 1); put("hecho", 1) }
        e.edita(parte.id) { put("comp", 1) }
        assertTrue(Compartir.compartido(e, e.get(c1.id)!!)); assertFalse(Compartir.compartido(e, e.get(c2.id)!!)); assertTrue(Compartir.compartido(e, e.get(c3.id)!!))
        assertTrue(Compartir.hecho(e, e.get(c1.id)!!)); assertFalse(Compartir.hecho(e, e.get(c3.id)!!))
        e.sincroniza(stYo)

        // Ella: ve los capítulos compartidos (el 1 y el de la parte), no el otro ni la nota
        soy(ella, yo)
        Pareja.reiniciaParaPruebas()
        assertTrue("baja lo compartido", Pareja.bajaLoCompartido(ctx))
        val pe = Pareja.escritos(ctx)
        assertEquals(listOf("El juramento", "La corte"), pe.capitulos(obra.id).map { it.s("titulo") })
        assertNull("lo no compartido no llega", pe.get(c2.id))
        assertTrue("ni sus escenas", pe.todos().none { it.s("texto").contains("no lo debe leer") })
        assertTrue("ni las notas", pe.notas(obra.id).isEmpty())
        assertFalse("ni la meta de la obra", pe.get(obra.id)!!.o.has("meta"))
        assertTrue(Compartir.hecho(pe, pe.get(c1.id)!!))
        // Los párrafos que se pueden anotar (sin el «***»)
        val esc = pe.escenas(c1.id).first()
        assertEquals(2, Compartir.parrafos(esc.s("texto")).size)
        // Anota el segundo párrafo
        val nota = NotaLectura(Pareja.nuevoId(), c1.id, esc.id, 1, Compartir.cita(Compartir.parrafos(esc.s("texto"))[1]), "Me encanta esta frase", System.currentTimeMillis())
        assertTrue("guarda la nota en su cuenta", Pareja.anota(ctx, nota))
        assertEquals(listOf("Me encanta esta frase"), Pareja.misNotas(ctx).map { it.texto })
        assertEquals("Segundo párrafo, con negrita.", Pareja.misNotas(ctx).first().cita)

        // Yo: veo su nota
        soy(yo, ella)
        val mias = Pareja.notasDeMiPareja(ctx)
        assertEquals(1, mias.size)
        assertEquals(c1.id, mias.first().cid); assertEquals(1, mias.first().p); assertEquals("Me encanta esta frase", mias.first().texto)

        // Dejo de compartir el capítulo 1: al volver a bajar, ella ya no lo tiene (ni en su móvil)
        e.edita(c1.id) { remove("comp") }
        e.sincroniza(stYo)
        soy(ella, yo)
        assertTrue(Pareja.bajaLoCompartido(ctx))
        assertEquals(listOf("La corte"), Pareja.escritos(ctx).capitulos(obra.id).map { it.s("titulo") })
        // Y borra su nota
        assertTrue(Pareja.borraNota(ctx, nota.id))
        assertTrue(Pareja.misNotas(ctx).isEmpty())
    }

    @Test fun sinParejaNoHayNadaQueCompartir() = runBlocking {
        val s = Store(ctx); s.partnerUid = ""
        assertFalse(Pareja.hay(ctx))
        assertFalse(Pareja.bajaLoCompartido(ctx))
        assertTrue(Pareja.notasDeMiPareja(ctx).isEmpty())
    }
}
