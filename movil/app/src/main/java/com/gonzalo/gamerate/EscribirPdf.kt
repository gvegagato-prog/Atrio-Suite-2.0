package com.gonzalo.gamerate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import androidx.core.content.res.ResourcesCompat
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * El PDF de una obra, hecho en el móvil (sin enviar el texto a ningún sitio). Dos estilos, como en el PC:
 * «libro» (A5, con las letras de la revista, justificado y con guiones, capítulos en página nueva) y «manuscrito» (A4,
 * letra con serifa de 12 puntos, interlineado 1,5 o doble, portada con el recuento de palabras). Numeradas abajo; la
 * portada, sin número.
 */
object EscribirPdf {
    private fun mm(x: Float) = x * 72f / 25.4f

    private class Hoja(val ancho: Int, val alto: Int, val arriba: Float, val abajo: Float, val izq: Float, val der: Float)

    /** Dónde se dibuja cada página (en puntos): el PDF de verdad o, en las pruebas, imágenes. */
    interface Papel { fun empieza(n: Int, ancho: Int, alto: Int): Canvas; fun termina() }

    fun pdf(ctx: Context, e: Escritos, pid: String, op: OpcionesExport = OpcionesExport()): ByteArray {
        val doc = PdfDocument()
        var pagina: PdfDocument.Page? = null
        dibuja(ctx, e, pid, op, object : Papel {
            override fun empieza(n: Int, ancho: Int, alto: Int): Canvas { pagina = doc.startPage(PdfDocument.PageInfo.Builder(ancho, alto, n).create()); return pagina!!.canvas }
            override fun termina() { pagina?.let { doc.finishPage(it) }; pagina = null }
        })
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /** Maqueta la obra y la dibuja página a página en [papel]. Devuelve cuántas páginas. */
    fun dibuja(ctx: Context, e: Escritos, pid: String, op: OpcionesExport, papel: Papel): Int {
        val ms = op.estilo == "manuscrito"
        val obra = e.get(pid) ?: return 0
        val bs = EscribirExport.manuscrito(e, pid, op)
        val autor = obra.s("autor")
        val hoja = if (ms) Hoja(595, 842, mm(25f), mm(25f), mm(25f), mm(25f)) else Hoja(420, 595, mm(19f), mm(21f), mm(16f), mm(16f))
        val ancho = (hoja.ancho - hoja.izq - hoja.der).toInt()
        val fondo = hoja.alto - hoja.abajo
        fun fuente(id: Int, def: Typeface): Typeface = runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: def
        val normal = if (ms) Typeface.SERIF else fuente(R.font.display_regular, Typeface.SERIF)
        val cursiva = if (ms) Typeface.create(Typeface.SERIF, Typeface.ITALIC) else fuente(R.font.display_italic, Typeface.create(Typeface.SERIF, Typeface.ITALIC))
        val negrita = if (ms) Typeface.create(Typeface.SERIF, Typeface.BOLD) else fuente(R.font.display_semibold, Typeface.create(Typeface.SERIF, Typeface.BOLD))
        val cuerpo = if (ms) 12f else 10.5f
        val linea = if (ms) (if (op.interlineado == "2") 2f else 1.5f) else 1.42f
        val sangria = if (ms) mm(12.5f) else cuerpo * 1.3f
        val es = Locale("es", "ES")

        var n = 0
        var c: Canvas? = null
        var y = 0f
        fun numero(p: Canvas, num: Int) {
            val tp = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { typeface = normal; textSize = if (ms) 12f else 9f; color = if (ms) Color.BLACK else Color.rgb(85, 85, 85) }
            val t = num.toString()
            val x = if (ms) hoja.ancho - hoja.der - tp.measureText(t) else (hoja.ancho - tp.measureText(t)) / 2f
            p.drawText(t, x, hoja.alto - hoja.abajo / 2f + tp.textSize / 3f, tp)
        }
        fun cierra() { c?.let { if (n > 1) numero(it, n); papel.termina() }; c = null }
        fun nueva() { cierra(); n++; c = papel.empieza(n, hoja.ancho, hoja.alto); y = hoja.arriba }

        fun texto(t: String, tam: Float = cuerpo, tipo: Typeface = normal, centro: Boolean = false, justifica: Boolean = !ms, primera: Float = 0f, margen: Float = 0f, cursivaTodo: Boolean = false, mult: Float = linea, literal: Boolean = false): StaticLayout {
            val sb = SpannableStringBuilder()
            // (literal: sin mirar el formato; si no, «* * *» serían marcas de cursiva)
            for (r in if (literal) listOf(Trozo(t)) else EscribirExport.trozos(t)) {
                val ini = sb.length
                sb.append(r.texto)
                val estilo = when { r.negrita && (r.cursiva || cursivaTodo) -> Typeface.BOLD_ITALIC; r.negrita -> Typeface.BOLD; r.cursiva || cursivaTodo -> Typeface.ITALIC; else -> Typeface.NORMAL }
                if (estilo != Typeface.NORMAL && sb.length > ini) sb.setSpan(StyleSpan(estilo), ini, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (primera > 0f || margen > 0f) sb.setSpan(LeadingMarginSpan.Standard((primera + margen).toInt(), margen.toInt()), 0, sb.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            val tp = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { typeface = if (cursivaTodo && tipo == normal) cursiva else tipo; textSize = tam; color = Color.BLACK; textLocale = es }
            val w = (ancho - margen).toInt().coerceAtLeast(50)
            return StaticLayout.Builder.obtain(sb, 0, sb.length, tp, w)
                .setAlignment(if (centro) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, mult)
                .setIncludePad(false)
                .setJustificationMode(if (justifica && !centro) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE)
                .setHyphenationFrequency(if (justifica && !centro) Layout.HYPHENATION_FREQUENCY_NORMAL else Layout.HYPHENATION_FREQUENCY_NONE)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .build()
        }
        /** Pone el bloque partiéndolo entre páginas por líneas (sin dejar una sola línea suelta al pie si se puede). */
        fun pon(l: StaticLayout, antes: Float = 0f, despues: Float = 0f, entero: Boolean = false) {
            if (c == null) nueva()
            y += antes
            // Un título no se queda solo al pie: con él, al menos dos líneas de lo que sigue
            if (entero && y + l.height + cuerpo * linea * 2 > fondo && y > hoja.arriba + 1f) nueva()
            var i = 0
            while (i < l.lineCount) {
                var j = i
                while (j < l.lineCount && y + (l.getLineBottom(j) - l.getLineTop(i)) <= fondo) j++
                // Ni una sola línea al pie si el párrafo sigue (ni un título solo): a la página siguiente
                if (j == i || (entero && j < l.lineCount) || (i == 0 && j == 1 && l.lineCount > 1)) {
                    if (y > hoja.arriba + 1f) { nueva(); continue }
                    if (j == i) j = i + 1
                }
                val alto = (l.getLineBottom(j - 1) - l.getLineTop(i)).toFloat()
                c!!.save()
                c!!.clipRect(0f, y, hoja.ancho.toFloat(), y + alto)
                c!!.translate(hoja.izq, y - l.getLineTop(i))
                l.draw(c!!)
                c!!.restore()
                y += alto
                i = j
                if (i < l.lineCount) nueva()
            }
            y += despues
        }

        // Portada
        nueva()
        if (ms) {
            pon(texto(autor + "\n" + EscribirExport.recuento(EscribirExport.palabrasDe(bs)), mult = 1.2f, justifica = false, literal = true))
            y = mm(80f)
            pon(texto(obra.s("titulo").ifBlank { "Sin título" }.uppercase(es), tipo = negrita, centro = true), despues = cuerpo)
            if (autor.isNotBlank()) pon(texto("de $autor", centro = true))
            if (obra.s("genero").isNotBlank()) pon(texto(obra.s("genero"), centro = true))
        } else {
            y = mm(52f)
            pon(texto(obra.s("titulo").ifBlank { "Sin título" }, tam = 26f, tipo = negrita, centro = true, mult = 1.1f), despues = 14f)
            if (autor.isNotBlank()) pon(texto(autor, tam = 12f, centro = true, cursivaTodo = true))
        }
        var primera = true
        for (b in bs.drop(if (autor.isNotBlank()) 2 else 1)) when (b.tipo) {
            Bloque.Tipo.PARTE -> { nueva(); y = mm(60f); pon(texto(b.texto.uppercase(es), tam = if (ms) cuerpo else 16f, tipo = negrita, centro = true, mult = 1.2f)); primera = true }
            Bloque.Tipo.CAPITULO -> { nueva(); y = if (ms) mm(50f) else mm(38f); pon(texto(b.texto, tam = if (ms) cuerpo else 15f, tipo = negrita, centro = true, mult = 1.2f), despues = cuerpo * 1.6f); primera = true }
            Bloque.Tipo.H1 -> { pon(texto(b.texto, tam = if (ms) cuerpo else 13f, tipo = negrita, centro = true, mult = 1.2f), antes = cuerpo, despues = cuerpo, entero = true); primera = true }
            Bloque.Tipo.ESCENA, Bloque.Tipo.H2, Bloque.Tipo.H3 -> { pon(texto(b.texto, tam = if (ms) cuerpo else 11f, centro = true, cursivaTodo = true, mult = 1.2f), antes = cuerpo, despues = cuerpo * 0.6f, entero = true); primera = true }
            Bloque.Tipo.CITA -> pon(texto(b.texto, margen = sangria * 1.4f, cursivaTodo = true), antes = cuerpo * 0.6f, despues = cuerpo * 0.6f)
            Bloque.Tipo.SALTO -> { pon(texto("* * *", centro = true, literal = true), antes = cuerpo * 0.6f, despues = cuerpo * 0.6f); primera = true }
            Bloque.Tipo.PARRAFO -> { pon(texto(b.texto, primera = if (primera) 0f else sangria)); primera = false }
            Bloque.Tipo.TITULO -> {}
        }
        if (ms) pon(texto("FIN", centro = true, literal = true), antes = cuerpo * 2)
        cierra()
        return n
    }
}
