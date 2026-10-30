package com.gonzalo.gamerate

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** La estructura de Escribir con los casos de herramientas/casos/estructura.json (los mismos que el PC). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EscribirEstructuraTest {
    private fun casos(): JSONObject {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "herramientas/casos/estructura.json").exists()) dir = dir.parentFile
        return JSONObject(File(dir ?: error("no encuentro herramientas/casos"), "herramientas/casos/estructura.json").readText())
    }
    private fun docs(a: JSONArray) = (0 until a.length()).map { Doc(a.getJSONObject(it)) }
    private fun strs(a: JSONArray) = (0 until a.length()).map { a.getString(it) }

    @Test fun idsIgualQueElPc() {
        val a = casos().getJSONArray("ids")
        for (i in 0 until a.length()) { val c = a.getJSONObject(i); assertEquals(c.getString("nombre"), c.getString("esperado"), EscribirEstructura.idParte(c.getString("pid"), c.getString("nombre"))) }
    }

    @Test fun migracionIgualQueElPc() {
        val a = casos().getJSONArray("migracion")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i); val e = c.getJSONObject("esperado")
            val m = EscribirEstructura.planMigracion(c.getString("pid"), docs(c.getJSONArray("caps")), docs(c.getJSONArray("partes")))
            val ep = e.getJSONArray("partes"); val ea = e.getJSONArray("asigna")
            assertEquals(c.getString("nombre"), (0 until ep.length()).map { ep.getJSONObject(it).let { p -> "${p.getString("id")}|${p.getString("titulo")}|${p.getDouble("orden")}" } },
                m.partes.map { "${it.id}|${it.titulo}|${it.orden}" })
            assertEquals(c.getString("nombre"), (0 until ea.length()).map { ea.getJSONObject(it).let { x -> "${x.getString("cid")}>${x.getString("aid")}" } }, m.asigna.map { "${it.first}>${it.second}" })
        }
    }

    @Test fun estructuraIgualQueElPc() {
        val a = casos().getJSONArray("estructura")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i); val e = c.getJSONObject("esperado")
            val r = EscribirEstructura.estructura(docs(c.getJSONArray("caps")), docs(c.getJSONArray("partes")))
            val items = e.getJSONArray("items")
            val esperado = (0 until items.length()).map { items.getJSONObject(it).let { x -> if (x.has("parte")) "P${x.getString("parte")}(${strs(x.getJSONArray("caps")).joinToString(",")})" else x.getString("cap") } }
            assertEquals(c.getString("nombre"), esperado, r.items.map { when (it) { is EscribirEstructura.Item.Parte -> "P${it.parte.id}(${it.caps.joinToString(",") { x -> x.id }})"; is EscribirEstructura.Item.Cap -> it.cap.id } })
            assertEquals(c.getString("nombre"), strs(e.getJSONArray("libro")), r.libro.map { it.id })
        }
    }

    @Test fun lineaIgualQueElPc() {
        val a = casos().getJSONArray("linea")
        for (i in 0 until a.length()) {
            val c = a.getJSONObject(i)
            val l = EscribirEstructura.linea(docs(c.getJSONArray("hitos")), docs(c.getJSONArray("ramas")))
            assertEquals(c.getString("nombre"), strs(c.getJSONArray("esperado")), l.filas.map { "${it.tipo}:${it.id}@${it.col}[${it.activas.joinToString(",")}]" })
            assertEquals(c.getString("nombre"), c.getInt("columnas"), l.columnas)
        }
    }
}
