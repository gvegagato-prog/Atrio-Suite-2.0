package com.gonzalo.gamerate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/*
 * Formas: además de colores y letras, cada estilo dibuja las cosas a su manera. Esquinas, bordes, sombras,
 * cómo se ven las notas (anillos, barras, bloques de píxel, dial, estrellas…), los botones, las portadas y un
 * fondo propio (líneas de televisor, rejilla de neón, grano de papel, trama de cómic).
 */

enum class Corner { Round, Cut, Square }
enum class Shade { None, Soft, Hard, Glow }
enum class GaugeKind { Ring, Bar, Blocks, Ascii, Dial, Fat, BigNum, Stars, Pips, Segments }
enum class ChipKind { Pill, Underline, Block, Bracket, Cut, Filled }
enum class CoverKind { Plain, Polaroid, Pixel, Glow, Hard, Soft }
enum class Overlay { None, Grain, Scanlines, Grid, Halftone }
/** Cómo se colocan las cosas en las cuadrículas de la biblioteca. */
enum class GridKind { Poster, Dense, List, Wide, Caption }

data class Form(
    val id: String, val name: String, val desc: String,
    val corner: Corner, val radius: Float, val border: Dp, val inkBorder: Boolean,
    val shade: Shade, val gauge: GaugeKind, val chip: ChipKind, val cover: CoverKind, val overlay: Overlay,
    val grid: GridKind = GridKind.Poster,
    /** Desplazamiento de la sombra dura. */
    val shadeOffset: Dp = 4.dp
)

val FORMS: List<Form> = listOf(
    Form("vitrina", "Vitrina", "Atrio 6: esquinas grandes, sin bordes, portadas protagonistas", Corner.Round, 1.3f, 0.dp, false, Shade.None, GaugeKind.Ring, ChipKind.Filled, CoverKind.Soft, Overlay.None, GridKind.Poster),
    Form("clasico", "Clásica", "Anillos, esquinas suaves y línea fina", Corner.Round, 1f, 1.dp, false, Shade.None, GaugeKind.Ring, ChipKind.Pill, CoverKind.Plain, Overlay.None, GridKind.Poster),
    Form("editorial", "Editorial", "Barras finas con cifras de revista, marcos de foto y grano de papel", Corner.Round, 0.12f, 1.dp, false, Shade.Soft, GaugeKind.Bar, ChipKind.Underline, CoverKind.Polaroid, Overlay.Grain, GridKind.Wide),
    Form("pixel", "Píxel", "Bloques de 8 bits, bordes gruesos, sombra dura y líneas de televisor", Corner.Square, 0f, 3.dp, true, Shade.Hard, GaugeKind.Blocks, ChipKind.Block, CoverKind.Pixel, Overlay.Scanlines, GridKind.Dense),
    Form("terminal", "Terminal", "Barras de texto ▓▓▓░░, [ botones ] y fósforo de monitor", Corner.Square, 0f, 1.dp, true, Shade.None, GaugeKind.Ascii, ChipKind.Bracket, CoverKind.Pixel, Overlay.Scanlines, GridKind.List),
    Form("neon", "Neón", "Diales de aguja, esquinas biseladas y bordes que brillan sobre rejilla", Corner.Cut, 0.6f, 1.5.dp, false, Shade.Glow, GaugeKind.Dial, ChipKind.Cut, CoverKind.Glow, Overlay.Grid, GridKind.Caption),
    Form("suave", "Suave", "Todo muy redondo, sin bordes, barras gorditas", Corner.Round, 1.8f, 0.dp, false, Shade.Soft, GaugeKind.Fat, ChipKind.Filled, CoverKind.Soft, Overlay.None, GridKind.Poster),
    Form("brutal", "Brutalista", "Cifras enormes, negro grueso y sombras desplazadas", Corner.Square, 0f, 3.dp, true, Shade.Hard, GaugeKind.BigNum, ChipKind.Block, CoverKind.Hard, Overlay.None, GridKind.Caption, 6.dp),
    Form("comic", "Cómic", "Estrellas, tinta gruesa, sombra de viñeta y trama de puntos", Corner.Round, 0.7f, 2.5.dp, true, Shade.Hard, GaugeKind.Stars, ChipKind.Pill, CoverKind.Hard, Overlay.Halftone, GridKind.Caption),
    Form("minimal", "Minimal", "Puntos en vez de anillos, sin bordes, nada que sobre", Corner.Round, 0.4f, 0.dp, false, Shade.None, GaugeKind.Pips, ChipKind.Underline, CoverKind.Plain, Overlay.None, GridKind.List),
    Form("militar", "Militar", "Barras de segmentos, esquinas cortadas y bordes de plantilla", Corner.Cut, 0.35f, 2.dp, true, Shade.None, GaugeKind.Segments, ChipKind.Cut, CoverKind.Pixel, Overlay.None, GridKind.Dense),
)

val form: Form get() = Skin.form

/** Marcador de notas en uso: el elegido aparte o, si no, el de la forma. */
val gaugeKind: GaugeKind get() = Skin.gauge ?: form.gauge

/** Nombre y descripción de cada marcador de notas. */
val GAUGE_NAMES: Map<GaugeKind, Pair<String, String>> = mapOf(
    GaugeKind.Ring to ("Anillos" to "El de siempre: un arco que se llena"),
    GaugeKind.Bar to ("Barra fina" to "Cifra grande y una línea debajo"),
    GaugeKind.Fat to ("Barra gordita" to "Redonda y blandita"),
    GaugeKind.Blocks to ("Bloques" to "Diez casillas de 8 bits"),
    GaugeKind.Ascii to ("Texto" to "▓▓▓▓░░ como en una terminal"),
    GaugeKind.Segments to ("Segmentos" to "Barras inclinadas de panel militar"),
    GaugeKind.Dial to ("Dial" to "Medio círculo con aguja"),
    GaugeKind.BigNum to ("Cifra enorme" to "El número manda"),
    GaugeKind.Stars to ("Estrellas" to "Cinco estrellas con medias"),
    GaugeKind.Pips to ("Puntos" to "Diez puntos en círculo"),
)

/** Forma de una esquina de radio [r] (en la forma clásica): redonda, biselada o recta según el estilo. */
fun shp(r: Dp): Shape = shpOf(form, r)

fun shpOf(f: Form, r: Dp): Shape {
    val k = (r.value * f.radius).dp
    return when (f.corner) {
        Corner.Square -> if (k.value < 0.5f) RectangleShape else RoundedCornerShape(k)
        Corner.Cut -> if (k.value < 0.5f) RectangleShape else CutCornerShape(k)
        Corner.Round -> RoundedCornerShape(k)
    }
}

/** Forma de los botones y chips «píldora». */
fun pillShape(): Shape = when (form.corner) {
    Corner.Round -> if (form.radius < 0.3f) RoundedCornerShape((14 * form.radius).coerceAtLeast(2f).dp) else RoundedCornerShape(50)
    Corner.Cut -> CutCornerShape(30)
    Corner.Square -> RectangleShape
}

/** Forma de los botones redondos de icono (volver, pareja…). */
fun roundShape(): Shape = when (form.corner) {
    Corner.Round -> if (form.radius < 0.3f) RoundedCornerShape(6.dp) else CircleShape
    Corner.Cut -> CutCornerShape(30)
    Corner.Square -> RectangleShape
}

/** Grosor y color del borde de tarjetas y paneles. */
val borderW: Dp get() = form.border
val borderC: Color get() = if (form.inkBorder) Charcoal else if (form.shade == Shade.Glow) Color(Skin.tones.gfx).copy(alpha = 0.75f) else Line

/** Sombra de la forma (dura desplazada, suave, brillo de neón o nada) detrás de una superficie. */
fun Modifier.formShade(shape: Shape): Modifier = when (form.shade) {
    Shade.None -> this
    Shade.Soft -> this.shadow(6.dp, shape, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x33000000))
    Shade.Glow -> {
        val c = Color(Skin.tones.gfx)
        this.shadow(14.dp, shape, clip = false, ambientColor = c, spotColor = c)
    }
    Shade.Hard -> {
        val off = form.shadeOffset
        val ink = Charcoal
        this.drawBehind {
            val o = off.toPx()
            translate(o, o) { drawOutline(shape.createOutline(size, layoutDirection, this), ink) }
        }
    }
}

/** Superficie con la forma del estilo: sombra, fondo y borde. */
fun Modifier.surface(r: Dp, fill: Color): Modifier {
    val s = shp(r)
    val base = this.formShade(s).background(if (form.border.value == 0f && form.corner == Corner.Round && form.radius > 1.5f) lerpTo(fill, Charcoal, 0.03f) else fill, s)
    return if (form.border.value > 0f) base.border(form.border, borderC, s) else base
}

private fun lerpTo(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

// ---------------------------------------------------------------- Marcadores de nota

/** Una nota 0–100 dibujada según la forma, dentro de un cuadro de [size]. */
@Composable
fun FormGauge(value: Float, color: Color, size: Dp, center: String, kind: GaugeKind = gaugeKind) {
    val big = size >= 70.dp
    val numSize = (size.value * if (big) 0.30f else 0.28f).sp
    val numFont: FontFamily = if (big) Display else Body
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when (kind) {
            GaugeKind.Ring -> {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = (this.size.minDimension * 0.075f).coerceAtLeast(2.5.dp.toPx())
                    val tl = Offset(sw / 2, sw / 2); val sz = Size(this.size.width - sw, this.size.height - sw)
                    drawArc(Line, 120f, 300f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                    drawArc(color, 120f, 300f * value / 100f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Text(center, fontSize = numSize, fontFamily = numFont, color = Charcoal)
            }
            GaugeKind.Bar -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(center, fontSize = (size.value * 0.36f).sp, fontFamily = Display, color = Charcoal)
                    Spacer(Modifier.height(size * 0.06f))
                    Canvas(Modifier.fillMaxWidth(0.86f).height((size * 0.05f).coerceAtLeast(2.dp))) {
                        drawRect(Line)
                        drawRect(color, size = Size(this.size.width * value / 100f, this.size.height))
                    }
                }
            }
            GaugeKind.Fat -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(center, fontSize = numSize, fontFamily = numFont, fontWeight = FontWeight.SemiBold, color = Charcoal)
                    Spacer(Modifier.height(size * 0.07f))
                    Canvas(Modifier.fillMaxWidth(0.9f).height(size * 0.16f)) {
                        val r = this.size.height / 2
                        drawRoundRect(color.copy(alpha = 0.2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
                        drawRoundRect(color, size = Size((this.size.width * value / 100f).coerceAtLeast(this.size.height), this.size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
                    }
                }
            }
            GaugeKind.Blocks -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(center, fontSize = numSize, fontFamily = numFont, fontWeight = FontWeight.Bold, color = Charcoal)
                    Spacer(Modifier.height(size * 0.05f))
                    Canvas(Modifier.size(size * 0.86f, size * 0.30f)) {
                        // 2 filas de 5 bloques: cada bloque, 10 puntos
                        val cols = 5; val rows = 2
                        val gap = this.size.width * 0.05f
                        val cw = (this.size.width - gap * (cols - 1)) / cols
                        val ch = (this.size.height - gap * (rows - 1)) / rows
                        val filled = (value / 10f).toInt()
                        for (i in 0 until cols * rows) {
                            val cx = (i % cols) * (cw + gap); val cy = (i / cols) * (ch + gap)
                            drawRect(if (i < filled) color else Line, Offset(cx, cy), Size(cw, ch))
                        }
                    }
                }
            }
            GaugeKind.Segments -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(center, fontSize = numSize, fontFamily = numFont, fontWeight = FontWeight.Bold, color = Charcoal)
                    Spacer(Modifier.height(size * 0.05f))
                    Canvas(Modifier.size(size * 0.92f, size * 0.18f)) {
                        val n = 10
                        val gap = this.size.width * 0.025f
                        val w = (this.size.width - gap * (n - 1)) / n
                        val filled = (value / 10f).toInt()
                        for (i in 0 until n) {
                            val x = i * (w + gap)
                            val p = Path().apply {
                                moveTo(x + w * 0.25f, 0f); lineTo(x + w, 0f); lineTo(x + w * 0.75f, this@Canvas.size.height); lineTo(x, this@Canvas.size.height); close()
                            }
                            drawPath(p, if (i < filled) color else Line)
                        }
                    }
                }
            }
            GaugeKind.Ascii -> {
                val cells = 10
                val n = (value / 10f).toInt().coerceIn(0, cells)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(center.padStart(3, ' '), fontSize = numSize, fontFamily = Body, fontWeight = FontWeight.Bold, color = color)
                    Text("▓".repeat(n) + "░".repeat(cells - n), fontSize = (size.value * 0.085f).sp, fontFamily = Body, color = color, maxLines = 1, softWrap = false)
                }
            }
            GaugeKind.Dial -> {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = (this.size.minDimension * 0.07f).coerceAtLeast(2.dp.toPx())
                    val r = this.size.minDimension / 2 - sw
                    val c = Offset(this.size.width / 2, this.size.height * 0.62f)
                    val tl = Offset(c.x - r, c.y - r); val sz = Size(r * 2, r * 2)
                    drawArc(Line, 180f, 180f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Butt))
                    drawArc(color, 180f, 180f * value / 100f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Butt))
                    // Marcas cada 10 y aguja
                    for (i in 0..10) {
                        val a = Math.toRadians(180.0 + i * 18.0)
                        val p1 = Offset(c.x + (r - sw * 1.6f) * cos(a).toFloat(), c.y + (r - sw * 1.6f) * sin(a).toFloat())
                        val p2 = Offset(c.x + (r - sw * 0.9f) * cos(a).toFloat(), c.y + (r - sw * 0.9f) * sin(a).toFloat())
                        drawLine(Muted, p1, p2, 1.dp.toPx())
                    }
                    val a = Math.toRadians(180.0 + 180.0 * value / 100.0)
                    drawLine(color, c, Offset(c.x + (r * 0.78f) * cos(a).toFloat(), c.y + (r * 0.78f) * sin(a).toFloat()), (sw * 0.4f).coerceAtLeast(1.5.dp.toPx()), cap = StrokeCap.Round)
                    drawCircle(color, sw * 0.6f, c)
                }
                Text(center, fontSize = (size.value * 0.22f).sp, fontFamily = numFont, fontWeight = FontWeight.SemiBold, color = Charcoal,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.02f))
            }
            GaugeKind.BigNum -> {
                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth(0.9f)) {
                    Text(center, fontSize = (size.value * 0.46f).sp, fontFamily = Display, fontWeight = FontWeight.Black, color = Charcoal, lineHeight = (size.value * 0.48f).sp)
                    Canvas(Modifier.fillMaxWidth().height(size * 0.12f)) {
                        drawRect(color, size = Size(this.size.width * value / 100f, this.size.height))
                        drawRect(Charcoal, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
            GaugeKind.Stars -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(center, fontSize = numSize, fontFamily = numFont, fontWeight = FontWeight.Bold, color = Charcoal)
                    Canvas(Modifier.size(size * 0.92f, size * 0.22f)) {
                        val n = 5
                        val w = this.size.width / n
                        val r = minOf(w, this.size.height) * 0.48f
                        val score = value / 20f
                        for (i in 0 until n) {
                            val cx = w * i + w / 2; val cy = this.size.height / 2
                            val star = starPath(cx, cy, r)
                            drawPath(star, Line)
                            val fill = (score - i).coerceIn(0f, 1f)
                            if (fill > 0f) {
                                val clip = Path().apply { addRect(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx - r + 2 * r * fill, cy + r)) }
                                val part = Path().apply { op(star, clip, androidx.compose.ui.graphics.PathOperation.Intersect) }
                                drawPath(part, color)
                            }
                            drawPath(star, Charcoal, style = Stroke(1.dp.toPx()))
                        }
                    }
                }
            }
            GaugeKind.Pips -> {
                Canvas(Modifier.fillMaxSize()) {
                    val n = 10
                    val r = this.size.minDimension / 2 * 0.84f
                    val dot = this.size.minDimension * 0.045f
                    val c = Offset(this.size.width / 2, this.size.height / 2)
                    val filled = (value / 10f).toInt()
                    for (i in 0 until n) {
                        val a = Math.toRadians(-90.0 + i * 36.0)
                        drawCircle(if (i < filled) color else Line, dot, Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()))
                    }
                }
                Text(center, fontSize = numSize, fontFamily = numFont, fontWeight = FontWeight.Light, color = Charcoal)
            }
        }
    }
}

private fun starPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = Math.toRadians(-90.0 + i * 36.0)
        val x = cx + rr * cos(a).toFloat(); val y = cy + rr * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** ¿El marcador se toca en círculo (anillo, dial, puntos) o de izquierda a derecha? */
val gaugeIsRadial: Boolean get() = gaugeKind == GaugeKind.Ring || gaugeKind == GaugeKind.Pips

// ---------------------------------------------------------------- Fondo de cada forma

/** Capa encima de todo (no se puede tocar): líneas de TV, rejilla, grano o trama. */
@Composable
fun FormOverlay() {
    val ov = form.overlay
    if (ov == Overlay.None) return
    val ink = Charcoal
    val accent = Color(Skin.tones.gfx)
    Canvas(Modifier.fillMaxSize()) {
        when (ov) {
            Overlay.Scanlines -> {
                val step = 3.dp.toPx()
                var y = 0f
                val c = Color.Black.copy(alpha = if (Mode.dark) 0.18f else 0.06f)
                while (y < size.height) { drawRect(c, Offset(0f, y), Size(size.width, 1.dp.toPx())); y += step }
            }
            Overlay.Grid -> {
                val step = 28.dp.toPx()
                val c = accent.copy(alpha = 0.07f)
                var x = 0f
                while (x < size.width) { drawLine(c, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                var y = 0f
                while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), 1f); y += step }
            }
            Overlay.Grain -> {
                val rnd = java.util.Random(7)
                val c = ink.copy(alpha = 0.05f)
                repeat(2600) { drawCircle(c, 0.7.dp.toPx(), Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)) }
            }
            Overlay.Halftone -> {
                val step = 9.dp.toPx()
                val c = ink.copy(alpha = 0.045f)
                var row = 0
                var y = 0f
                while (y < size.height) {
                    var x = if (row % 2 == 0) 0f else step / 2
                    while (x < size.width) { drawCircle(c, 1.4.dp.toPx(), Offset(x, y)); x += step }
                    y += step * 0.86f; row++
                }
            }
            Overlay.None -> Unit
        }
    }
}

/** Marco de una portada según la forma. */
fun Modifier.coverFrame(): Modifier = when (form.cover) {
    CoverKind.Plain, CoverKind.Soft -> this
    CoverKind.Polaroid -> this.border(3.dp, Color(0xFFF7F3EA), RectangleShape)
    CoverKind.Pixel -> this.border(if (form.gauge == GaugeKind.Ascii) 1.dp else 3.dp, Charcoal, RectangleShape)
    CoverKind.Glow -> this.border(1.5.dp, Color(Skin.tones.gfx), shp(10.dp))
    CoverKind.Hard -> this.border(form.border, Charcoal, shp(10.dp))
}

/** Dibuja un trazo discontinuo (para quien lo quiera usar en separadores). */
fun DrawScope.dashedLine(c: Color, from: Offset, to: Offset) =
    drawLine(c, from, to, 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))

// ---------------------------------------------------------------- Chips y botones

/** Texto de un chip: en la forma terminal va entre corchetes. */
fun chipLabel(t: String): String = if (form.chip == ChipKind.Bracket) "[ $t ]" else t

/** Color del texto de un chip (subrayado: siempre tinta). */
fun chipInk(selected: Boolean, on: Color = Crudo): Color = if (selected && form.chip != ChipKind.Underline) on else Charcoal

/** Fondo, borde y forma de un chip o botón según la forma del estilo. */
fun Modifier.chipLook(selected: Boolean, fill: Color, idle: Color = Color.Transparent): Modifier = when (form.chip) {
    ChipKind.Underline -> this.drawBehind {
        val h = if (selected) 2.5.dp.toPx() else 1.dp.toPx()
        drawRect(if (selected) fill else Line, Offset(0f, size.height - h), Size(size.width, h))
    }
    ChipKind.Filled -> this.background(if (selected) fill else Sand, pillShape())
    ChipKind.Block -> {
        val s = RectangleShape
        val base = if (selected) this.formShade(s) else this
        base.background(if (selected) fill else idle, s).border(form.border.coerceAtLeast(1.5.dp), Charcoal, s)
    }
    ChipKind.Bracket -> this.background(if (selected) fill else Color.Transparent, RectangleShape)
    ChipKind.Cut, ChipKind.Pill -> {
        val s = pillShape()
        val w = if (form.inkBorder) form.border.coerceAtMost(2.dp) else 1.dp
        val bc = if (selected) fill else if (form.inkBorder) Charcoal else if (form.shade == Shade.Glow) Color(Skin.tones.gfx).copy(alpha = 0.6f) else Line
        this.background(if (selected) fill else idle, s).border(w, bc, s)
    }
}

// ---------------------------------------------------------------- Barras de progreso

/** Barra de progreso (0–1) dibujada según la forma: lisa, en bloques, segmentos, puntos o con marco. */
fun Modifier.formBar(frac: Float, color: Color, track: Color, h: Dp): Modifier = this.drawBehind {
    val f = frac.coerceIn(0f, 1f)
    val w = size.width; val hh = size.height
    when (gaugeKind) {
        GaugeKind.Blocks, GaugeKind.Ascii, GaugeKind.Segments -> {
            val n = 20
            val gap = (w * 0.008f).coerceAtLeast(1.5f)
            val cw = (w - gap * (n - 1)) / n
            val on = (f * n).toInt().let { if (f > 0f && it == 0) 1 else it }
            for (i in 0 until n) {
                val x = i * (cw + gap)
                if (gaugeKind == GaugeKind.Segments) {
                    val p = Path().apply { moveTo(x + cw * 0.3f, 0f); lineTo(x + cw, 0f); lineTo(x + cw * 0.7f, hh); lineTo(x, hh); close() }
                    drawPath(p, if (i < on) color else track)
                } else drawRect(if (i < on) color else track, Offset(x, 0f), Size(cw, hh))
            }
        }
        GaugeKind.Pips, GaugeKind.Stars -> {
            val r = hh / 2
            val n = (w / (r * 3.2f)).toInt().coerceAtLeast(5)
            val step = (w - 2 * r) / (n - 1)
            val on = (f * n).toInt().let { if (f > 0f && it == 0) 1 else it }
            for (i in 0 until n) drawCircle(if (i < on) color else track, r, Offset(r + i * step, r))
        }
        GaugeKind.BigNum -> {
            drawRect(track)
            if (f > 0f) drawRect(color, size = Size((w * f).coerceAtLeast(hh), hh))
            drawRect(Charcoal, style = Stroke(1.5.dp.toPx()))
        }
        GaugeKind.Dial -> {
            drawRect(track)
            if (f > 0f) {
                drawRect(color, size = Size(w * f, hh))
                drawRect(color.copy(alpha = 0.35f), Offset(0f, -hh * 0.5f), Size(w * f, hh * 2f))
            }
        }
        else -> {
            val round = form.corner == Corner.Round && form.radius > 0.3f
            val cr = if (round) androidx.compose.ui.geometry.CornerRadius(hh / 2, hh / 2) else androidx.compose.ui.geometry.CornerRadius.Zero
            drawRoundRect(track, cornerRadius = cr)
            if (f > 0f) drawRoundRect(color, size = Size((w * f).coerceAtLeast(if (round) hh else 2f), hh), cornerRadius = cr)
        }
    }
}

// ---------------------------------------------------------------- Cuadrículas de la biblioteca

/** Columnas de la cuadrícula según la forma. */
fun formCells(): androidx.compose.foundation.lazy.grid.GridCells = when (form.grid) {
    GridKind.Poster, GridKind.Caption -> androidx.compose.foundation.lazy.grid.GridCells.Adaptive(150.dp)
    GridKind.Dense -> androidx.compose.foundation.lazy.grid.GridCells.Adaptive(100.dp)
    GridKind.List -> androidx.compose.foundation.lazy.grid.GridCells.Fixed(1)
    GridKind.Wide -> androidx.compose.foundation.lazy.grid.GridCells.Adaptive(300.dp)
}

/** Separación entre fichas (horizontal, vertical). */
fun formGap(): Pair<Dp, Dp> = when (form.grid) {
    GridKind.Dense -> 10.dp to 14.dp
    GridKind.List -> 0.dp to 10.dp
    GridKind.Wide -> 16.dp to 18.dp
    else -> 16.dp to 22.dp
}

/**
 * Una ficha de la biblioteca colocada según la forma: póster con título debajo, mosaico apretado, fila de lista,
 * tarjeta ancha de revista o título encima de la portada.
 */
@Composable
fun FormTile(
    cover: String, name: String, sub: String?, alpha: Float = 1f, dot: Color? = null,
    gauge: (@Composable (Dp) -> Unit)? = null, badges: @Composable BoxScope.() -> Unit = {}
) {
    val subLine: @Composable () -> Unit = {
        if (!sub.isNullOrBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) { Box(Modifier.size(6.dp).background(dot, CircleShape)); Spacer(Modifier.width(6.dp)) }
            Text(sub, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
    val coverMod = Modifier.fillMaxWidth().aspectRatio(2f / 3f).formShade(shp(10.dp)).clip(shp(10.dp)).background(Sand)
        .let { if (form.border.value > 0f) it.border(form.border.coerceAtMost(2.dp), borderC, shp(10.dp)) else it }
    when (form.grid) {
        GridKind.Poster -> Column {
            Box { Cover(cover, name, coverMod, alpha); badges() }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp)
                    subLine()
                }
                if (gauge != null) { Spacer(Modifier.width(6.dp)); gauge(36.dp) }
            }
        }
        GridKind.Dense -> Column {
            Box { Cover(cover, name, coverMod, alpha); badges() }
            Spacer(Modifier.height(6.dp))
            Text(name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            if (gauge != null) gauge(44.dp) else subLine()
        }
        GridKind.List -> Row(
            Modifier.fillMaxWidth().surface(12.dp, Crudo).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(54.dp)) { Cover(cover, name, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shp(6.dp)).background(Sand), alpha) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 19.sp)
                subLine()
            }
            if (gauge != null) { Spacer(Modifier.width(8.dp)); gauge(56.dp) }
            Box(Modifier.size(1.dp)) { badges() }
        }
        GridKind.Wide -> Column(Modifier.fillMaxWidth().surface(16.dp, Crudo).clip(shp(16.dp))) {
            Box { Cover(cover, name, Modifier.fillMaxWidth().aspectRatio(16f / 8f).background(Sand), alpha); badges() }
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontFamily = Display, fontSize = 21.sp, lineHeight = 25.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    subLine()
                }
                if (gauge != null) { Spacer(Modifier.width(10.dp)); gauge(62.dp) }
            }
        }
        GridKind.Caption -> Box {
            Cover(cover, name, coverMod, alpha)
            val ink = form.inkBorder
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(if (ink) 6.dp else 0.dp)
                    .let {
                        if (ink) it.background(Charcoal, shp(6.dp)) else it.clip(shp(10.dp)).background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xD0000000)))
                        )
                    }
                    .padding(start = 10.dp, end = 10.dp, top = if (ink) 6.dp else 28.dp, bottom = 8.dp)
            ) {
                Text(name, color = if (ink) Crudo else Color.White, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontFamily = Display, fontSize = 16.sp, lineHeight = 19.sp)
                if (!sub.isNullOrBlank()) Text(sub, color = (if (ink) Crudo else Color.White).copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (gauge != null) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).surface(10.dp, Crudo).padding(3.dp)) { gauge(44.dp) }
            badges()
        }
    }
}
