package com.gonzalo.gamerate

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/** Los colores y el tamaño con los que se pinta el texto al escribir. */
class AspectoTexto(
    val tinta: Color, val tenue: Color, val acento: Color, val titulos: FontFamily,
    /** Tamaño del texto en sp. */
    val tam: Float
)

/**
 * El Markdown ligero de «Escribir», pintado mientras escribes: los títulos grandes, las citas en cursiva, `**negrita**` y
 * `*cursiva*` con su formato, y los signos del formato apagados (siguen ahí, para poder cambiarlos). Las fichas de la obra
 * que nombras quedan subrayadas, y lo que buscas, resaltado. No cambia el texto (los desplazamientos son los mismos).
 */
class FormatoMarkdown(private val a: AspectoTexto, private val fichas: Regex? = null, private val busca: Regex? = null) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(estilo(text.text), OffsetMapping.Identity)

    fun estilo(t: String): AnnotatedString = AnnotatedString.Builder(t).also { b ->
        val tenue = SpanStyle(color = a.tenue)
        var i = 0
        while (i <= t.length) {
            var fin = t.indexOf('\n', i); if (fin < 0) fin = t.length
            val l = t.substring(i, fin)
            val s = l.trim()
            when {
                s == "***" || s == "* * *" -> {
                    b.addStyle(tenue, i, fin)
                    b.addStyle(ParagraphStyle(textAlign = TextAlign.Center), i, minOf(t.length, fin + 1))
                }
                l.startsWith("### ") -> { b.addStyle(SpanStyle(fontFamily = a.titulos, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = (a.tam * 1.12f).sp), i, fin); b.addStyle(tenue, i, i + 4) }
                l.startsWith("## ") -> { b.addStyle(SpanStyle(fontFamily = a.titulos, fontWeight = FontWeight.Bold, fontSize = (a.tam * 1.3f).sp), i, fin); b.addStyle(tenue, i, i + 3) }
                l.startsWith("# ") -> { b.addStyle(SpanStyle(fontFamily = a.titulos, fontWeight = FontWeight.Bold, fontSize = (a.tam * 1.6f).sp), i, fin); b.addStyle(tenue, i, i + 2) }
                l.startsWith("> ") -> { b.addStyle(SpanStyle(fontStyle = FontStyle.Italic), i, fin); b.addStyle(tenue, i, i + 2) }
            }
            if (!(s == "***" || s == "* * *")) inline(b, l, i)
            i = fin + 1
        }
        fichas?.findAll(t)?.forEach { m -> b.addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = a.acento), m.range.first, m.range.last + 1) }
        busca?.findAll(t)?.forEach { m -> b.addStyle(SpanStyle(background = a.acento.copy(alpha = 0.32f)), m.range.first, m.range.last + 1) }
    }.toAnnotatedString()

    private fun inline(b: AnnotatedString.Builder, l: String, base: Int) {
        val tenue = SpanStyle(color = a.tenue)
        var neg = -1; var cur = -1
        var k = 0
        while (k < l.length) {
            val c = l[k]
            if (c == '\\' && k + 1 < l.length) { k += 2; continue }
            if (c == '*') {
                val doble = k + 1 < l.length && l[k + 1] == '*'
                if (doble) {
                    b.addStyle(tenue, base + k, base + k + 2)
                    if (neg < 0) neg = k + 2 else { b.addStyle(SpanStyle(fontWeight = FontWeight.Bold), base + neg, base + k); neg = -1 }
                    k += 2; continue
                }
                b.addStyle(tenue, base + k, base + k + 1)
                if (cur < 0) cur = k + 1 else { b.addStyle(SpanStyle(fontStyle = FontStyle.Italic), base + cur, base + k); cur = -1 }
            }
            k++
        }
    }
}

/** Cambios al texto (con la selección) para los botones del formato. */
object EdicionTexto {
    private fun rango(v: TextFieldValue) = TextRange(minOf(v.selection.start, v.selection.end), maxOf(v.selection.start, v.selection.end))

    /** Pone [marca] a los dos lados de lo elegido (o la quita si ya está). Sin selección, deja el cursor en medio. */
    fun envuelve(v: TextFieldValue, marca: String): TextFieldValue {
        val r = rango(v); val t = v.text
        val sel = t.substring(r.start, r.end)
        val m = marca.length
        // Ya lleva la marca (alrededor o dentro): se quita. Con asteriscos se cuentan los de cada lado (*** es negrita y cursiva)
        val nb = t.substring(0, r.start).takeLastWhile { it == '*' }.length
        val na = t.substring(r.end).takeWhile { it == '*' }.length
        val fuera = if (marca == "**" || marca == "*") minOf(nb, na).let { if (marca == "*") it % 2 == 1 else it >= 2 }
            else r.start >= m && r.end + m <= t.length && t.substring(r.start - m, r.start) == marca && t.substring(r.end, r.end + m) == marca
        if (fuera) return TextFieldValue(t.removeRange(r.end, r.end + m).removeRange(r.start - m, r.start), TextRange(r.start - m, r.end - m))
        if (sel.length >= 2 * m && sel.startsWith(marca) && sel.endsWith(marca) && !(marca == "*" && sel.startsWith("**") && sel.length < 5))
            return TextFieldValue(t.replaceRange(r.start, r.end, sel.substring(m, sel.length - m)), TextRange(r.start, r.end - 2 * m))
        return TextFieldValue(t.replaceRange(r.start, r.end, marca + sel + marca), if (sel.isEmpty()) TextRange(r.start + m) else TextRange(r.start + m, r.end + m))
    }

    /** Pone o quita [prefijo] ("# ", "> "…) al principio de las líneas elegidas (cambia el título anterior por este). */
    fun prefijoLinea(v: TextFieldValue, prefijo: String): TextFieldValue {
        val r = rango(v); val t = v.text
        val ini = t.lastIndexOf('\n', r.start - 1) + 1
        var fin = t.indexOf('\n', maxOf(r.end, ini)); if (fin < 0) fin = t.length
        val lineas = t.substring(ini, fin).split('\n')
        val todasLo = lineas.all { it.startsWith(prefijo) }
        val nuevo = lineas.joinToString("\n") { l -> if (todasLo) l.removePrefix(prefijo) else prefijo + l.replace(Regex("^(#{1,3} |> )"), "") }
        val dif = nuevo.length - (fin - ini)
        val primero = lineas.first()
        val cambioPrimera = if (todasLo) -prefijo.length else prefijo.length - (primero.length - primero.replace(Regex("^(#{1,3} |> )"), "").length)
        return TextFieldValue(t.replaceRange(ini, fin, nuevo), TextRange((r.start + cambioPrimera).coerceAtLeast(ini), (r.end + dif).coerceAtLeast(ini)))
    }

    /** Escribe [x] donde está el cursor (cambia lo elegido). */
    fun inserta(v: TextFieldValue, x: String): TextFieldValue {
        val r = rango(v)
        return TextFieldValue(v.text.replaceRange(r.start, r.end, x), TextRange(r.start + x.length))
    }

    /** Un salto de escena (`***` en su línea) donde está el cursor. */
    fun salto(v: TextFieldValue): TextFieldValue {
        val r = rango(v); val t = v.text
        val antes = t.substring(0, r.start).trimEnd('\n'); val despues = t.substring(r.end).trimStart('\n')
        val ini = (if (antes.isEmpty()) "" else "$antes\n\n") + "***\n\n"
        return TextFieldValue(ini + despues, TextRange(ini.length))
    }

    /** « » alrededor de lo elegido (o el par vacío con el cursor dentro). */
    fun comillas(v: TextFieldValue): TextFieldValue {
        val r = rango(v); val sel = v.text.substring(r.start, r.end)
        return TextFieldValue(v.text.replaceRange(r.start, r.end, "«$sel»"), if (sel.isEmpty()) TextRange(r.start + 1) else TextRange(r.start + 1, r.end + 1))
    }
}

/**
 * Tipografía automática al escribir: dos guiones son una raya (—), un guion y un espacio al empezar una línea abren un
 * diálogo (—), tres puntos son puntos suspensivos (…), las comillas rectas son « o » según dónde estén, y << >> son « ».
 * Igual que ET.tipografia del PC (los dos se prueban con herramientas/casos/tipografia.json).
 */
object Tipografia {
    private const val ABREN = " \t\n([{«“—-"

    /** [texto] es como queda justo después de escribir [ch], con el cursor en [cursor]. Devuelve el cambio, o null. */
    fun aplica(texto: String, cursor: Int, ch: Char): Pair<String, Int>? {
        if (cursor < 1 || cursor > texto.length || texto[cursor - 1] != ch) return null
        fun cambia(n: Int, por: String) = (texto.substring(0, cursor - n) + por + texto.substring(cursor)) to (cursor - n + por.length)
        val antes = if (cursor >= 2) texto[cursor - 2] else '\n'
        return when (ch) {
            '-' -> if (cursor >= 2 && antes == '-') cambia(2, "—") else null
            ' ' -> if (cursor >= 2 && antes == '-' && (cursor < 3 || texto[cursor - 3] == '\n')) cambia(2, "—") else null
            '.' -> if (cursor >= 3 && texto.substring(cursor - 3, cursor) == "...") cambia(3, "…") else null
            '"' -> cambia(1, if (cursor < 2 || antes in ABREN) "«" else "»")
            '<' -> if (cursor >= 2 && antes == '<') cambia(2, "«") else null
            '>' -> if (cursor >= 2 && antes == '>') cambia(2, "»") else null
            else -> null
        }
    }
}

/** Deshacer y rehacer lo escrito (instantáneas del texto, unidas si caen muy seguidas). */
class HistorialTexto(inicial: TextFieldValue, private val tope: Int = 200) {
    private val atras = ArrayDeque<TextFieldValue>()
    private val adelante = ArrayDeque<TextFieldValue>()
    private var actual = inicial
    private var ultima = 0L

    /** Apunta el cambio [v]. Los que llegan en menos de [junta] ms de otro cambio pequeño se funden con él. */
    fun apunta(v: TextFieldValue, ahora: Long = System.currentTimeMillis(), junta: Long = 900L) {
        if (v.text == actual.text) { actual = v; return }
        val grande = kotlin.math.abs(v.text.length - actual.text.length) > 24
        if (atras.isEmpty() || grande || ahora - ultima > junta) { atras.addLast(actual); if (atras.size > tope) atras.removeFirst() }
        ultima = ahora
        actual = v
        adelante.clear()
    }

    /** El próximo cambio va aparte (no se junta con el anterior): así se deshace solo lo que hizo la tipografía automática. */
    fun corte() { ultima = 0L }

    val puedeDeshacer get() = atras.isNotEmpty()
    val puedeRehacer get() = adelante.isNotEmpty()

    fun deshace(): TextFieldValue? {
        val v = atras.removeLastOrNull() ?: return null
        adelante.addLast(actual); actual = v; ultima = 0L
        return v
    }

    fun rehace(): TextFieldValue? {
        val v = adelante.removeLastOrNull() ?: return null
        atras.addLast(actual); actual = v; ultima = 0L
        return v
    }
}
