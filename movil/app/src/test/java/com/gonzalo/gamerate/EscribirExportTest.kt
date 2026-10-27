package com.gonzalo.gamerate

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Sacar la obra a Markdown, texto, Word y EPUB (archivos bien formados) e importar de vuelta. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EscribirExportTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun obra(e: Escritos): String {
        val o = e.nuevo(Escritos.T_OBRA, "") { put("titulo", "La torre & el <cuervo>"); put("autor", "Gonzalo") }
        val c1 = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Uno"); put("parte", "Parte I") }
        val c2 = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Dos") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c1.id); put("titulo", "Llegada"); put("estado", 2); put("texto", "Aldara subió la torre.\nSe quedó *mirando* el valle.\n\n> Dicen que **nadie** vuelve.") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c1.id); put("titulo", "Noche"); put("estado", 1); put("texto", "La noche cayó.\n\n***\n\nOtra escena dentro.") }
        e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c2.id); put("titulo", "Fin"); put("estado", 0); put("texto", "# Un título dentro\n\nEl final.") }
        return o.id
    }

    private fun entradas(b: ByteArray): Map<String, ByteArray> {
        val m = LinkedHashMap<String, ByteArray>()
        val z = ZipInputStream(b.inputStream())
        while (true) { val en = z.nextEntry ?: break; m[en.name] = z.readBytes() }
        return m
    }

    private fun bienFormado(b: ByteArray) { DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(b.inputStream()) }

    @Test fun formatos() {
        val e = Escritos(File(ctx.cacheDir, "exp_${System.nanoTime()}")) { "movil" }
        val pid = obra(e)
        val md = EscribirExport.markdown(e, pid)
        assertTrue(md, md.startsWith("# La torre & el <cuervo>\n\nGonzalo\n\n# Parte I\n\n## Uno\n\nAldara subió la torre. Se quedó *mirando* el valle.\n\n> Dicen que **nadie** vuelve.\n\n* * *\n\nLa noche cayó."))
        assertTrue(md.contains("* * *\n\nOtra escena dentro."))
        assertTrue("solo las escenas en revisión o finales", !EscribirExport.markdown(e, pid, OpcionesExport(soloEstadoMinimo = 1)).contains("El final."))
        val txt = EscribirExport.texto(e, pid)
        assertTrue(txt, txt.contains("Se quedó mirando el valle.") && !txt.contains("*mirando*") && txt.startsWith("LA TORRE & EL <CUERVO>"))
        // Word: bien formado, con los estilos y las marcas
        val dx = entradas(EscribirExport.docx(e, pid))
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "word/_rels/document.xml.rels", "word/document.xml", "word/styles.xml"), dx.keys)
        dx.values.forEach { bienFormado(it) }
        val doc = dx["word/document.xml"]!!.toString(Charsets.UTF_8)
        assertTrue(doc.contains("<w:b/>") && doc.contains("<w:i/>") && doc.contains("La torre &amp; el &lt;cuervo&gt;") && doc.contains("w:val=\"Heading2\""))
        // EPUB: «mimetype» primero y sin comprimir, todo bien formado, un archivo por capítulo
        val bytes = EscribirExport.epub(e, pid)
        assertTrue(String(bytes, 30, 8) == "mimetype")
        val ep = entradas(bytes)
        assertEquals("mimetype", ep.keys.first())
        ep.filterKeys { !it.endsWith("css") && it != "mimetype" }.values.forEach { bienFormado(it) }
        assertTrue(ep.keys.containsAll(listOf("OEBPS/content.opf", "OEBPS/nav.xhtml", "OEBPS/p0.xhtml", "OEBPS/p1.xhtml", "OEBPS/p2.xhtml", "OEBPS/p3.xhtml")))
        val p2 = ep["OEBPS/p2.xhtml"]!!.toString(Charsets.UTF_8)
        assertTrue(p2, p2.contains("<em>mirando</em>") && p2.contains("<strong>nadie</strong>") && p2.contains("* * *"))
    }

    @Test fun importar() {
        val e = Escritos(File(ctx.cacheDir, "imp_${System.nanoTime()}")) { "movil" }
        val pid = obra(e)
        val md = EscribirExport.markdown(e, pid)
        val id = EscribirExport.importaTexto(e, "Importada", "# Uno\n\nTexto uno.\n\n## Dos\n\nTexto dos.\n\nMás texto.")
        assertEquals(listOf("Uno", "Dos"), e.capitulos(id).map { it.s("titulo") })
        assertEquals("Texto dos.\n\nMás texto.", e.escenas(e.capitulos(id)[1].id).single().s("texto"))
        val sinTitulos = EscribirExport.importaTexto(e, "Plana", "Solo texto\nsin capítulos.")
        assertEquals(1, e.capitulos(sinTitulos).size)
        // Word de ida y vuelta
        val texto = EscribirExport.textoDeDocx(EscribirExport.docx(e, pid))
        assertTrue(texto, texto.contains("# Uno") && texto.contains("Aldara subió la torre."))
        assertTrue(md.isNotBlank())
    }
}
