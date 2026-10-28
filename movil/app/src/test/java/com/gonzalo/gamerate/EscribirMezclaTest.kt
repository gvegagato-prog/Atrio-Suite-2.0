package com.gonzalo.gamerate

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** La mezcla a tres bandas con los casos de herramientas/casos/mezcla.json (los mismos que prueba el PC). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EscribirMezclaTest {
    private fun casos(): JSONObject {
        var dir = File("").absoluteFile
        while (!File(dir, "herramientas/casos/mezcla.json").exists()) dir = dir.parentFile ?: error("no encuentro herramientas/casos")
        return JSONObject(File(dir, "herramientas/casos/mezcla.json").readText())
    }

    @Test fun textos() {
        val a = casos().getJSONArray("texto")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            val m = EscribirMezcla.texto(c.getString("base"), c.getString("nuestro"), c.getString("suyo"), c.getString("prefiere") == "nuestro", c.optBoolean("ambos"))
            assertEquals(c.getString("nombre"), c.getString("esperado"), m.texto)
            assertEquals(c.getString("nombre") + " (choques)", c.getInt("choques"), m.choques)
        }
    }

    @Test fun documentos() {
        val a = casos().getJSONArray("docs")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            val m = EscribirMezcla.docs(c.optJSONObject("base"), c.getJSONObject("nuestro"), c.getJSONObject("suyo"), c.getString("prefiere") == "nuestro")
            assertEquals(c.getString("nombre"), EscribirMezcla.canon(c.getJSONObject("esperado")), EscribirMezcla.canon(m.o))
            assertEquals(c.getString("nombre") + " (choques)", c.getInt("choques"), m.choques)
            c.optJSONArray("chocan")?.let { ch -> assertEquals(c.getString("nombre") + " (dónde)", (0 until ch.length()).map { ch.getString(it) }.toSet(), m.chocan) }
        }
    }

    @Test fun textosLargos() {
        // Una escena de 3.000 párrafos con cambios en los dos extremos: se junta sin choques y deprisa
        val base = (1..3000).joinToString("\n") { "Párrafo $it." }
        val nuestro = base.replace("Párrafo 2.\n", "Párrafo 2 (móvil).\n")
        val suyo = base.replace("Párrafo 2999.", "Párrafo 2999 (PC).")
        val t0 = System.currentTimeMillis()
        val m = EscribirMezcla.texto(base, nuestro, suyo, true)
        assertEquals(0, m.choques)
        assertEquals(base.replace("Párrafo 2.\n", "Párrafo 2 (móvil).\n").replace("Párrafo 2999.", "Párrafo 2999 (PC)."), m.texto)
        assert(System.currentTimeMillis() - t0 < 3000) { "demasiado lento" }
    }
}
