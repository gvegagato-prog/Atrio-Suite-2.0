package com.gonzalo.gamerate

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Un trozo del manuscrito ya ordenado, listo para sacarlo a un archivo. */
class Bloque(val tipo: Tipo, val texto: String) {
    enum class Tipo { TITULO, PARTE, CAPITULO, ESCENA, PARRAFO, CITA, SALTO, H1, H2, H3 }
}

/** Un trozo de texto con su formato (cursiva, negrita). */
class Trozo(val texto: String, val negrita: Boolean = false, val cursiva: Boolean = false)

/** Qué se saca al exportar. */
class OpcionesExport(
    val titulosDeCapitulo: Boolean = true,
    val titulosDeEscena: Boolean = false,
    val soloEstadoMinimo: Int = 0,
    val saltoEntreEscenas: Boolean = true,
    /** «libro» o «manuscrito» (el de enviar a editoriales), para Word y PDF. */
    val estilo: String = "libro",
    /** Interlineado del manuscrito: "1.5" o "2". */
    val interlineado: String = "1.5",
    val numerar: Boolean = true
)

/**
 * Sacar la obra a archivos: Markdown, texto, Word (.docx) y libro electrónico (.epub). Todo se hace aquí, sin enviar
 * tu texto a ningún sitio.
 */
object EscribirExport {
    /** Las líneas del texto de una escena como bloques (títulos, citas, saltos de escena y párrafos). */
    fun bloques(texto: String): List<Bloque> {
        val out = ArrayList<Bloque>()
        val par = StringBuilder()
        fun cierra() { if (par.isNotBlank()) out += Bloque(Bloque.Tipo.PARRAFO, par.toString().trim()); par.setLength(0) }
        for (l in texto.replace("\r\n", "\n").split('\n')) {
            val t = l.trim()
            when {
                t.isEmpty() -> cierra()
                t == "***" || t == "* * *" || t == "---" -> { cierra(); out += Bloque(Bloque.Tipo.SALTO, "") }
                t.startsWith("### ") -> { cierra(); out += Bloque(Bloque.Tipo.H3, t.drop(4)) }
                t.startsWith("## ") -> { cierra(); out += Bloque(Bloque.Tipo.H2, t.drop(3)) }
                t.startsWith("# ") -> { cierra(); out += Bloque(Bloque.Tipo.H1, t.drop(2)) }
                t.startsWith("> ") -> { cierra(); out += Bloque(Bloque.Tipo.CITA, t.drop(2)) }
                else -> { if (par.isNotEmpty()) par.append(' '); par.append(t) }
            }
        }
        cierra()
        return out
    }

    /** `**negrita**` y `*cursiva*` en trozos. */
    fun trozos(t: String): List<Trozo> {
        val out = ArrayList<Trozo>()
        var i = 0; var neg = false; var cur = false
        val b = StringBuilder()
        fun vuelca() { if (b.isNotEmpty()) { out += Trozo(b.toString(), neg, cur); b.setLength(0) } }
        while (i < t.length) {
            if (t.startsWith("**", i)) { vuelca(); neg = !neg; i += 2 }
            else if (t[i] == '*') { vuelca(); cur = !cur; i++ }
            else if (t[i] == '\\' && i + 1 < t.length && t[i + 1] in "*#>") { b.append(t[i + 1]); i += 2 }
            else { b.append(t[i]); i++ }
        }
        vuelca()
        return out
    }

    /** El manuscrito de la obra en orden, con la portada de título. */
    fun manuscrito(e: Escritos, pid: String, op: OpcionesExport = OpcionesExport()): List<Bloque> {
        val obra = e.get(pid) ?: return emptyList()
        val out = ArrayList<Bloque>()
        out += Bloque(Bloque.Tipo.TITULO, obra.s("titulo").ifBlank { "Sin título" })
        if (obra.s("autor").isNotBlank()) out += Bloque(Bloque.Tipo.PARRAFO, obra.s("autor"))
        var parte = ""
        for (c in e.capitulos(pid)) {
            val esc = e.escenas(c.id).filter { it.i("estado") >= op.soloEstadoMinimo }
            if (esc.isEmpty()) continue
            if (c.s("parte").isNotBlank() && c.s("parte") != parte) { parte = c.s("parte"); out += Bloque(Bloque.Tipo.PARTE, parte) }
            if (op.titulosDeCapitulo) out += Bloque(Bloque.Tipo.CAPITULO, c.s("titulo"))
            esc.forEachIndexed { i, s ->
                if (i > 0 && op.saltoEntreEscenas) out += Bloque(Bloque.Tipo.SALTO, "")
                if (op.titulosDeEscena && s.s("titulo").isNotBlank()) out += Bloque(Bloque.Tipo.ESCENA, s.s("titulo"))
                out += bloques(s.s("texto"))
            }
        }
        return out
    }

    fun plano(t: String) = trozos(t).joinToString("") { it.texto }

    fun markdown(e: Escritos, pid: String, op: OpcionesExport = OpcionesExport()): String {
        val sb = StringBuilder()
        for (b in manuscrito(e, pid, op)) when (b.tipo) {
            Bloque.Tipo.TITULO -> sb.append("# ").append(b.texto).append("\n\n")
            Bloque.Tipo.PARTE -> sb.append("# ").append(b.texto).append("\n\n")
            Bloque.Tipo.CAPITULO, Bloque.Tipo.H1 -> sb.append("## ").append(b.texto).append("\n\n")
            Bloque.Tipo.ESCENA, Bloque.Tipo.H2 -> sb.append("### ").append(b.texto).append("\n\n")
            Bloque.Tipo.H3 -> sb.append("#### ").append(b.texto).append("\n\n")
            Bloque.Tipo.CITA -> sb.append("> ").append(b.texto).append("\n\n")
            Bloque.Tipo.SALTO -> sb.append("* * *\n\n")
            Bloque.Tipo.PARRAFO -> sb.append(b.texto).append("\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    fun texto(e: Escritos, pid: String, op: OpcionesExport = OpcionesExport()): String {
        val sb = StringBuilder()
        for (b in manuscrito(e, pid, op)) when (b.tipo) {
            Bloque.Tipo.TITULO, Bloque.Tipo.PARTE -> sb.append(b.texto.uppercase()).append("\n\n")
            Bloque.Tipo.CAPITULO, Bloque.Tipo.ESCENA, Bloque.Tipo.H1, Bloque.Tipo.H2, Bloque.Tipo.H3 -> sb.append(b.texto).append("\n\n")
            Bloque.Tipo.CITA -> sb.append("    ").append(plano(b.texto)).append("\n\n")
            Bloque.Tipo.SALTO -> sb.append("        * * *\n\n")
            Bloque.Tipo.PARRAFO -> sb.append(plano(b.texto)).append("\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun zip(entradas: List<Triple<String, ByteArray, Boolean>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((nombre, datos, guardado) in entradas) {
                val en = ZipEntry(nombre)
                if (guardado) { en.method = ZipEntry.STORED; en.size = datos.size.toLong(); en.compressedSize = datos.size.toLong(); en.crc = CRC32().apply { update(datos) }.value }
                z.putNextEntry(en); z.write(datos); z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    // ---- Word ----
    private fun runs(t: String, extra: String = ""): String = trozos(t).joinToString("") { r ->
        "<w:r><w:rPr>$extra${if (r.negrita) "<w:b/>" else ""}${if (r.cursiva) "<w:i/>" else ""}</w:rPr><w:t xml:space=\"preserve\">${xml(r.texto)}</w:t></w:r>"
    }

    private val PALABRA = Regex("[\\p{L}\\p{N}]+(?:['’\\-][\\p{L}\\p{N}]+)*")
    /** Las palabras del texto que se exporta (párrafos y citas). */
    fun palabrasDe(bs: List<Bloque>): Int = bs.filter { it.tipo == Bloque.Tipo.PARRAFO || it.tipo == Bloque.Tipo.CITA }.sumOf { PALABRA.findAll(plano(it.texto)).count() }
    /** «unas 85.000 palabras» (redondeado, como se da en un manuscrito). */
    fun recuento(n: Int): String {
        if (n < 1000) return "$n palabras"
        val r = Math.round(n / 1000.0) * 1000
        return "unas " + (if (r < 10_000) r.toString() else java.text.NumberFormat.getIntegerInstance(java.util.Locale("es", "ES")).format(r)) + " palabras"
    }

    /**
     * Word en dos estilos: «libro» (Georgia, justificado, capítulos centrados) y «manuscrito» (el de enviar a editoriales:
     * Times New Roman 12, márgenes de 2,5 cm, interlineado 1,5 o doble, portada con el recuento de palabras y páginas
     * numeradas). No hay un formato único para todas las editoriales: si una pide otra cosa, sus normas mandan.
     * Igual que docx() de escritos-export.js en el PC.
     */
    fun docx(e: Escritos, pid: String, op: OpcionesExport = OpcionesExport()): ByteArray {
        val ms = op.estilo == "manuscrito"
        val obra = e.get(pid)
        val autor = obra?.s("autor").orEmpty()
        val bs = manuscrito(e, pid, op)
        val body = StringBuilder()
        var primera = true
        if (ms) {
            body.append("<w:p><w:pPr><w:jc w:val=\"left\"/></w:pPr>${runs(autor)}</w:p><w:p><w:pPr><w:jc w:val=\"left\"/></w:pPr>${runs(recuento(palabrasDe(bs)))}</w:p>")
            body.append("<w:p><w:pPr><w:pStyle w:val=\"Title\"/></w:pPr>${runs(obra?.s("titulo").orEmpty().ifBlank { "Sin título" }.uppercase(java.util.Locale("es")))}</w:p>")
            if (autor.isNotBlank()) body.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>${runs("de $autor")}</w:p>")
            obra?.s("genero")?.takeIf { it.isNotBlank() }?.let { body.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>${runs(it)}</w:p>") }
        }
        for (b in if (ms) bs.drop(if (autor.isNotBlank()) 2 else 1) else bs) when (b.tipo) {
            Bloque.Tipo.TITULO -> body.append("<w:p><w:pPr><w:pStyle w:val=\"Title\"/></w:pPr>${runs(b.texto)}</w:p>")
            Bloque.Tipo.PARTE -> { body.append("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/><w:pageBreakBefore/></w:pPr>${runs(b.texto)}</w:p>"); primera = true }
            Bloque.Tipo.CAPITULO, Bloque.Tipo.H1 -> { body.append("<w:p><w:pPr><w:pStyle w:val=\"Heading2\"/>${if (b.tipo == Bloque.Tipo.CAPITULO) "<w:pageBreakBefore/>" else ""}</w:pPr>${runs(b.texto)}</w:p>"); primera = true }
            Bloque.Tipo.ESCENA, Bloque.Tipo.H2, Bloque.Tipo.H3 -> { body.append("<w:p><w:pPr><w:pStyle w:val=\"Heading3\"/></w:pPr>${runs(b.texto)}</w:p>"); primera = true }
            Bloque.Tipo.CITA -> body.append("<w:p><w:pPr><w:pStyle w:val=\"Quote\"/></w:pPr>${runs(b.texto)}</w:p>")
            Bloque.Tipo.SALTO -> { body.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:t>* * *</w:t></w:r></w:p>"); primera = true }
            Bloque.Tipo.PARRAFO -> { body.append("<w:p><w:pPr><w:pStyle w:val=\"Normal\"/>${if (primera) "" else "<w:ind w:firstLine=\"${if (ms) 709 else 567}\"/>"}</w:pPr>${runs(b.texto)}</w:p>"); primera = false }
        }
        if (ms) body.append("<w:p><w:pPr><w:spacing w:before=\"480\"/><w:jc w:val=\"center\"/></w:pPr><w:r><w:t>FIN</w:t></w:r></w:p>")
        val W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
        val m = if (ms) 1418 else 1440
        val sect = "<w:sectPr>${if (op.numerar) "<w:footerReference w:type=\"default\" r:id=\"rId2\"/>" else ""}<w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"$m\" w:right=\"$m\" w:bottom=\"$m\" w:left=\"$m\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>${if (ms) "<w:titlePg/>" else ""}</w:sectPr>"
        val hdr = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        val doc = "$hdr<w:document $W><w:body>$body$sect</w:body></w:document>"
        val letra = if (ms) "Times New Roman" else "Georgia"
        val linea = if (ms && op.interlineado == "2") 480 else 360
        fun estilo(id: String, nombre: String, ppr: String, rpr: String) = "<w:style w:type=\"paragraph\" w:styleId=\"$id\"><w:name w:val=\"$nombre\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:pPr>$ppr</w:pPr><w:rPr>$rpr</w:rPr></w:style>"
        val estilos = "$hdr<w:styles $W><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"$letra\" w:hAnsi=\"$letra\" w:cs=\"$letra\" w:eastAsia=\"$letra\"/><w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/><w:lang w:val=\"es-ES\"/></w:rPr></w:rPrDefault>" +
            "<w:pPrDefault><w:pPr><w:spacing w:after=\"0\" w:line=\"$linea\" w:lineRule=\"auto\"/><w:jc w:val=\"${if (ms) "left" else "both"}\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:pPr><w:widowControl/></w:pPr></w:style>" +
            (if (ms) estilo("Title", "Title", "<w:spacing w:before=\"4800\" w:after=\"240\"/><w:jc w:val=\"center\"/>", "<w:b/>") +
                estilo("Heading1", "heading 1", "<w:keepNext/><w:spacing w:before=\"3600\" w:after=\"480\"/><w:jc w:val=\"center\"/><w:outlineLvl w:val=\"0\"/>", "<w:b/><w:caps/>") +
                estilo("Heading2", "heading 2", "<w:keepNext/><w:spacing w:before=\"2880\" w:after=\"480\"/><w:jc w:val=\"center\"/><w:outlineLvl w:val=\"1\"/>", "<w:b/>") +
                estilo("Heading3", "heading 3", "<w:keepNext/><w:spacing w:before=\"240\" w:after=\"240\"/><w:jc w:val=\"center\"/><w:outlineLvl w:val=\"2\"/>", "<w:i/>")
            else estilo("Title", "Title", "<w:spacing w:before=\"3000\" w:after=\"400\"/><w:jc w:val=\"center\"/>", "<w:b/><w:sz w:val=\"56\"/>") +
                estilo("Heading1", "heading 1", "<w:keepNext/><w:spacing w:before=\"2400\" w:after=\"600\"/><w:jc w:val=\"center\"/><w:outlineLvl w:val=\"0\"/>", "<w:b/><w:sz w:val=\"40\"/>") +
                estilo("Heading2", "heading 2", "<w:keepNext/><w:spacing w:before=\"1200\" w:after=\"480\"/><w:jc w:val=\"center\"/><w:outlineLvl w:val=\"1\"/>", "<w:b/><w:sz w:val=\"32\"/>") +
                estilo("Heading3", "heading 3", "<w:keepNext/><w:spacing w:before=\"360\" w:after=\"240\"/><w:outlineLvl w:val=\"2\"/>", "<w:b/><w:i/><w:sz w:val=\"26\"/>")) +
            estilo("Quote", "Quote", "<w:spacing w:before=\"240\" w:after=\"240\"/><w:ind w:left=\"720\" w:right=\"720\"/>", "<w:i/>") +
            "<w:style w:type=\"paragraph\" w:styleId=\"Footer\"><w:name w:val=\"footer\"/><w:basedOn w:val=\"Normal\"/></w:style></w:styles>"
        val pie = "$hdr<w:ftr $W><w:p><w:pPr><w:pStyle w:val=\"Footer\"/><w:jc w:val=\"${if (ms) "right" else "center"}\"/></w:pPr><w:fldSimple w:instr=\" PAGE \"><w:r><w:t>1</w:t></w:r></w:fldSimple></w:p></w:ftr>"
        val core = "$hdr<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><dc:title>${xml(obra?.s("titulo").orEmpty())}</dc:title><dc:creator>${xml(autor)}</dc:creator><dc:language>es-ES</dc:language></cp:coreProperties>"
        return zip(listOfNotNull(
            Triple("[Content_Types].xml", ("$hdr<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/><Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
                (if (op.numerar) "<Override PartName=\"/word/footer1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml\"/>" else "") +
                "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/></Types>").toByteArray(), false),
            Triple("_rels/.rels", "$hdr<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/></Relationships>".toByteArray(), false),
            Triple("word/_rels/document.xml.rels", ("$hdr<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                (if (op.numerar) "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer\" Target=\"footer1.xml\"/>" else "") + "</Relationships>").toByteArray(), false),
            Triple("word/document.xml", doc.toByteArray(), false),
            Triple("word/styles.xml", estilos.toByteArray(), false),
            if (op.numerar) Triple("word/footer1.xml", pie.toByteArray(), false) else null,
            Triple("docProps/core.xml", core.toByteArray(), false)
        ))
    }

    // ---- EPUB ----
    private fun html(t: String): String = trozos(t).joinToString("") { r ->
        var x = xml(r.texto); if (r.cursiva) x = "<em>$x</em>"; if (r.negrita) x = "<strong>$x</strong>"; x
    }

    fun epub(e: Escritos, pid: String, op: OpcionesExport = OpcionesExport()): ByteArray {
        val obra = e.get(pid) ?: return ByteArray(0)
        val titulo = obra.s("titulo").ifBlank { "Sin título" }
        // Una página por capítulo (y la portada con el título)
        class Pag(val titulo: String, val cuerpo: StringBuilder = StringBuilder())
        val pags = ArrayList<Pag>()
        pags += Pag(titulo).also { it.cuerpo.append("<div class=\"portada\"><h1>${xml(titulo)}</h1>${if (obra.s("autor").isNotBlank()) "<p class=\"autor\">${xml(obra.s("autor"))}</p>" else ""}</div>") }
        var ultimaPar = false
        for (b in manuscrito(e, pid, op).drop(if (obra.s("autor").isNotBlank()) 2 else 1)) {
            if (b.tipo == Bloque.Tipo.PARTE) { pags += Pag(b.texto).also { it.cuerpo.append("<h1 class=\"parte\">${xml(b.texto)}</h1>") }; ultimaPar = false; continue }
            if (b.tipo == Bloque.Tipo.CAPITULO || pags.size == 1) {
                if (b.tipo == Bloque.Tipo.CAPITULO) { pags += Pag(b.texto).also { it.cuerpo.append("<h2>${xml(b.texto)}</h2>") }; ultimaPar = false; continue }
                pags += Pag("Texto")
            }
            val c = pags.last().cuerpo
            when (b.tipo) {
                Bloque.Tipo.ESCENA, Bloque.Tipo.H2, Bloque.Tipo.H3 -> { c.append("<h3>${xml(b.texto)}</h3>"); ultimaPar = false }
                Bloque.Tipo.H1 -> { c.append("<h2>${xml(b.texto)}</h2>"); ultimaPar = false }
                Bloque.Tipo.CITA -> c.append("<blockquote><p>${html(b.texto)}</p></blockquote>")
                Bloque.Tipo.SALTO -> { c.append("<p class=\"salto\">* * *</p>"); ultimaPar = false }
                Bloque.Tipo.PARRAFO -> { c.append("<p${if (ultimaPar) "" else " class=\"primero\""}>${html(b.texto)}</p>"); ultimaPar = true }
                else -> {}
            }
        }
        val cab = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"es\"><head><meta charset=\"utf-8\"/><title>%s</title><link rel=\"stylesheet\" type=\"text/css\" href=\"estilo.css\"/></head><body>%s</body></html>"
        val id = "atrio-" + pid
        val manifiesto = pags.indices.joinToString("") { "<item id=\"p$it\" href=\"p$it.xhtml\" media-type=\"application/xhtml+xml\"/>" }
        val espina = pags.indices.joinToString("") { "<itemref idref=\"p$it\"/>" }
        val opf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\" xml:lang=\"es\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
            "<dc:identifier id=\"uid\">${xml(id)}</dc:identifier><dc:title>${xml(titulo)}</dc:title><dc:creator>${xml(obra.s("autor").ifBlank { "Anónimo" })}</dc:creator><dc:language>es</dc:language>" +
            "<meta property=\"dcterms:modified\">${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())}</meta></metadata>" +
            "<manifest><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/><item id=\"css\" href=\"estilo.css\" media-type=\"text/css\"/>$manifiesto</manifest><spine>$espina</spine></package>"
        val nav = String.format(cab.replace("<body>", "<body><nav xmlns:epub=\"http://www.idpf.org/2007/ops\" epub:type=\"toc\"><h1>Índice</h1><ol>").replace("%s</body>", "%s</ol></nav></body>"), xml(titulo),
            pags.mapIndexed { i, p -> "<li><a href=\"p$i.xhtml\">${xml(p.titulo)}</a></li>" }.joinToString(""))
        val css = "body{font-family:Georgia,serif;line-height:1.5;margin:5%}p{margin:0;text-align:justify;text-indent:1.4em}p.primero,p.autor,p.salto{text-indent:0}p.salto{text-align:center;margin:1.2em 0}" +
            "h1,h2,h3{text-align:center;text-indent:0;margin:1.6em 0 1em}.portada{margin-top:30%;text-align:center}blockquote{margin:1em 2em;font-style:italic}"
        return zip(
            listOf(Triple("mimetype", "application/epub+zip".toByteArray(), true),
                Triple("META-INF/container.xml", "<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>".toByteArray(), false),
                Triple("OEBPS/content.opf", opf.toByteArray(), false), Triple("OEBPS/nav.xhtml", nav.toByteArray(), false), Triple("OEBPS/estilo.css", css.toByteArray(), false)) +
                pags.mapIndexed { i, p -> Triple("OEBPS/p$i.xhtml", String.format(cab, xml(p.titulo), p.cuerpo.toString()).toByteArray(), false) }
        )
    }

    // ---- Importar ----
    /**
     * Un texto (Markdown o plano) a capítulos: los `# ` o `## ` abren un capítulo; sin títulos, todo va a un capítulo.
     * Devuelve las obras creadas por dentro: el id de la obra.
     */
    fun importaTexto(e: Escritos, titulo: String, texto: String): String {
        val obra = e.nuevo(Escritos.T_OBRA, "") { put("titulo", titulo.ifBlank { "Sin título" }) }
        val lineas = texto.replace("\r\n", "\n").split('\n')
        var cap: Doc? = null
        val cuerpo = StringBuilder()
        fun cierra() {
            val c = cap ?: return
            val t = cuerpo.toString().trim()
            if (t.isNotEmpty() || e.escenas(c.id).isEmpty()) e.nuevo(Escritos.T_ESCENA, obra.id) { put("cid", c.id); put("titulo", "Escena 1"); put("texto", t) }
            cuerpo.setLength(0)
        }
        for (l in lineas) {
            val m = Regex("^#{1,2}\\s+(.*)$").find(l.trim())
            if (m != null) { cierra(); cap = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", m.groupValues[1].trim()) } }
            else { if (cap == null && l.isBlank()) continue; if (cap == null) cap = e.nuevo(Escritos.T_CAP, obra.id) { put("titulo", "Capítulo 1") }; cuerpo.append(l).append('\n') }
        }
        cierra()
        return obra.id
    }

    /** El texto de un .docx (sus párrafos, con `# ` en los títulos), para importarlo. */
    fun textoDeDocx(datos: ByteArray): String {
        val zin = java.util.zip.ZipInputStream(datos.inputStream())
        var xmlDoc = ""
        while (true) { val en = zin.nextEntry ?: break; if (en.name == "word/document.xml") { xmlDoc = zin.readBytes().toString(Charsets.UTF_8); break } }
        val sb = StringBuilder()
        for (p in Regex("<w:p[ >].*?</w:p>", RegexOption.DOT_MATCHES_ALL).findAll(xmlDoc)) {
            val ps = p.value
            val titulo = Regex("<w:pStyle w:val=\"(?:Heading|Titulo|Ttulo)(\\d)\"").find(ps)?.groupValues?.get(1)
            val txt = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL).findAll(ps).joinToString("") { it.groupValues[1] }
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")
            if (titulo != null && txt.isNotBlank()) sb.append(if (titulo == "1" || titulo == "2") "# " else "## ").append(txt).append("\n\n") else sb.append(txt).append("\n\n")
        }
        return sb.toString()
    }
}
