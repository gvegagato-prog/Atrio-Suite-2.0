package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Ediciones de la revista: la misma maqueta (cabecera, filetes, casillas, cifras grandes) con el aire de distintas
 * revistas: un suplemento dominical, una de moda, una cultural, una de tecnología, de música, de cine, un fanzine o una
 * de viajes. Cada una trae su papel (claro y oscuro), sus letras (vienen con la app: se ven igual sin conexión), cómo
 * es la cabecera, cómo son los filetes y los antetítulos, y si las fotos van en color, en blanco y negro o en sepia.
 * El acento se puede seguir cambiando encima («El de la edición» deja el suyo).
 */

/** Cómo son los antetítulos de las casillas («EN PORTADA», «AHORA MISMO»…). */
enum class Antetitulos {
    /** Cuadradito de color y versalitas espaciadas (la de siempre). */
    VERSALITAS,
    /** En cursiva de la letra de títulos, sin mayúsculas (revista cultural). */
    CURSIVA,
    /** Recortado: letras claras sobre una banda de tinta (fanzine). */
    INVERTIDA,
    /** Como código: «// EN PORTADA», en letra de máquina (tecnología). */
    CODIGO,
    /** Condensada y gorda, en mayúsculas (música). */
    CONDENSADA,
    /** Una franja del color de la edición a todo lo ancho, con las letras claras (revista popular). */
    FRANJA,
}

/** Cómo se compone «En portada»: la foto y el titular debajo, el titular encima de la foto, o en un bloque de color. */
enum class Portada { CLASICA, SOBRE_FOTO, BLOQUE }

/** Cómo se ven las fotos de las portadas. */
enum class Fotos { COLOR, BN, SEPIA, CONTRASTE }

/** Las letras de una edición que también usan los widgets (que se dibujan sin Compose). */
data class LetrasWidget(val titular: Int, val cursiva: Int, val negrita: Int, val texto: Int)

data class Edicion(
    val id: String,
    val nombre: String,
    /** Qué revista recuerda (se ve en Apariencia). */
    val genero: String,
    /** La cabecera de la portada (el nombre de la revista). */
    val cabecera: String,
    /** El pie de la portada. */
    val pie: String,
    val paleta: Palette,
    val titulos: FontFamily,
    val texto: FontFamily,
    /** Letra de la cabecera (si no, la de títulos). */
    val letraCabecera: FontFamily? = null,
    /** Letra de los antetítulos (si no, la del texto). */
    val letraEtiquetas: FontFamily? = null,
    val cabPeso: FontWeight = FontWeight.Bold,
    val cabCursiva: Boolean = false,
    /** Espacio entre letras de la cabecera (em: negativo, más juntas). */
    val cabTrack: Float = -0.03f,
    val cabCentro: Boolean = false,
    /** Tamaño de la cabecera respecto al de siempre (una letra estrecha pide más: se reduce sola si no cabe). */
    val cabEscala: Float = 1f,
    /** Un marco de color alrededor de la cabecera (el rectángulo amarillo de la revista de viajes). */
    val marco: Long = 0L,
    /** La cabecera algo torcida (fanzine), en grados. */
    val giro: Float = 0f,
    /** Grosor de los filetes (1 = el de siempre). */
    val filete: Float = 1f,
    /** Filete doble bajo la cabecera (fino + grueso, como un periódico). */
    val doble: Boolean = false,
    val antetitulos: Antetitulos = Antetitulos.VERSALITAS,
    val fotos: Fotos = Fotos.COLOR,
    /** Titulares en mayúsculas (la letra condensada de la de música lo pide). */
    val titularesMayus: Boolean = false,
    /** La cabecera sobre una banda del color de la edición, con las letras del color del papel (revista popular). */
    val banda: Boolean = false,
    /** La cabecera en el color de la edición (en lugar de en tinta). */
    val cabColor: Boolean = false,
    /** Los filetes en el color de la edición. */
    val fileteColor: Boolean = false,
    /** Las cifras grandes (las notas) en el color de la edición. */
    val cifrasColor: Boolean = false,
    val portada: Portada = Portada.CLASICA,
    val widget: LetrasWidget = LetrasWidget(R.font.display_semibold, R.font.display_italic, R.font.body_bold, R.font.body_medium),
)

// Papel, tarjetas, tinta, el color de la edición, y los criterios (gráficos, historia, música, jugabilidad), dorado,
// arena, línea, borde y gris. Cada edición tiene su color, sobrio: rojo de imprenta, azul noche, granate, verde inglés,
// cereza, azul eléctrico, ocre, marrón tabaco, fucsia y el amarillo del marco.
private val P_ATRIO = PALETTES.first { it.id == "revista" }
val EDICIONES: List<Edicion> = listOf(
    Edicion("atrio", "Atrio", "La de siempre: papel crema, serif cálida y rojo de imprenta", "ATRIO", "Atrio · hecho en casa",
        P_ATRIO, AtrioDisplay, AtrioBody),
)

object Ediciones {
    fun porId(id: String?): Edicion = EDICIONES.firstOrNull { it.id == id } ?: EDICIONES.first()

    /** La edición de ahora (Skin.edicion). */
    val actual: Edicion get() = porId(Skin.edicion)

    /** La de las preferencias guardadas (para los widgets, que no tienen Skin en memoria). */
    fun guardada(ctx: Context): Edicion = porId(ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE).getString("skin_edicion", "atrio"))

    /** Elegir edición: su papel y sus letras, y el acento vuelve a ser el suyo. */
    fun aplicar(e: Edicion, ctx: Context) {
        Skin.edicion = e.id
        Skin.accentId = "edicion"
        Skin.ponEdicion()
        Skin.save(ctx)
    }

    /** El filtro de las fotos de la edición (null = en color). */
    val filtro: ColorFilter? get() = when (actual.fotos) {
        Fotos.COLOR -> null
        Fotos.BN -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }.also { m ->
            // blanco y negro con algo más de contraste, como una fotocopia
            m.timesAssign(ColorMatrix(floatArrayOf(1.25f, 0f, 0f, 0f, -30f, 0f, 1.25f, 0f, 0f, -30f, 0f, 0f, 1.25f, 0f, -30f, 0f, 0f, 0f, 1f, 0f)))
        })
        Fotos.SEPIA -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        Fotos.CONTRASTE -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            1.2f, 0f, 0f, 0f, -22f, 0f, 1.2f, 0f, 0f, -22f, 0f, 0f, 1.2f, 0f, -22f, 0f, 0f, 0f, 1f, 0f)).also { it.timesAssign(ColorMatrix().apply { setToSaturation(1.15f) }) })
    }

    /** Un titular como lo escribe la edición (en mayúsculas en la de música). */
    fun titular(t: String): String = if (actual.titularesMayus) t.uppercase() else t
}

/** Filete de la revista (horizontal), con el grosor de la edición (y su color, si lo pide). */
@Composable
fun Filete(alto: Dp = 2.dp, color: Color = Charcoal, modifier: Modifier = Modifier) {
    val e = Ediciones.actual
    val c = if (e.fileteColor && color == Charcoal) ColGeneral else color
    Box(modifier.fillMaxWidth().height((alto.value * e.filete).coerceAtLeast(0.5f).dp).background(c))
}

/** El filete bajo la cabecera: grueso (o doble, fino y grueso, en la dominical). */
@Composable
fun FileteCabecera(alto: Dp = 3.dp) {
    val e = Ediciones.actual
    if (e.banda) return   // la banda ya hace de filete
    val c = if (e.fileteColor) ColGeneral else Charcoal
    if (e.doble) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(alto).background(c))
    } else Filete(alto)
}

/** El color de las cifras grandes (las notas): el de la edición o la tinta. */
val ColorCifras: Color @Composable get() = if (Ediciones.actual.cifrasColor) ColGeneral else Charcoal

/** De los dos colores, el que mejor se lee sobre el fondo. */
fun sobre(fondo: Color, a: Color, b: Color): Color {
    val l = fondo.luminance()
    return if (kotlin.math.abs(a.luminance() - l) >= kotlin.math.abs(b.luminance() - l)) a else b
}

/** La cabecera de la revista (el nombre, enorme), como la pide la edición. */
@Composable
fun Cabecera(
    tam: TextUnit, modifier: Modifier = Modifier, color: Color = Charcoal, e: Edicion = Ediciones.actual,
    acento: Color = ColGeneral, papel: Color = Paper
) {
    val marco = e.marco != 0L
    val grande = tam.value >= 60f
    val letra = when {
        e.banda -> sobre(acento, papel, color)
        e.cabColor -> acento
        else -> color
    }
    val inner: @Composable () -> Unit = {
        FitTitle(
            e.cabecera, (tam.value * e.cabEscala).sp, (tam.value * e.cabEscala).sp, color = letra, letterSpacing = e.cabTrack.em, fontWeight = e.cabPeso,
            fontStyle = if (e.cabCursiva) FontStyle.Italic else null, maxLines = 1, family = e.letraCabecera ?: e.titulos,
            align = if (e.cabCentro) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth().then(if (e.giro != 0f) Modifier.rotate(e.giro) else Modifier), minSize = 12f, unaLinea = true, conEdicion = false
        )
    }
    when {
        marco -> Box(modifier.fillMaxWidth().border(if (grande) 7.dp else 4.dp, Color(e.marco)).padding(horizontal = if (grande) 14.dp else 8.dp, vertical = 4.dp)) { inner() }
        e.banda -> Box(modifier.fillMaxWidth().background(acento).padding(horizontal = if (grande) 14.dp else 7.dp, vertical = if (grande) 6.dp else 3.dp)) { inner() }
        else -> Box(modifier.fillMaxWidth()) { inner() }
    }
}

/** Antetítulo de casilla o de sección, como lo pide la edición. */
@Composable
fun Antetitulo(
    texto: String, modifier: Modifier = Modifier, tam: TextUnit = 13.sp, color: Color = Charcoal, conCuadro: Boolean = true,
    e: Edicion = Ediciones.actual, acento: Color = ColGeneral, papel: Color = Paper
) {
    when (e.antetitulos) {
        Antetitulos.VERSALITAS -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            if (conCuadro) { Box(Modifier.size((tam.value * 0.77f).dp).background(acento)); Spacer(Modifier.width(8.dp)) }
            Text(texto.uppercase(), color = color, fontFamily = e.texto, fontSize = tam, fontWeight = FontWeight.Black, letterSpacing = (tam.value * 0.15f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Antetitulos.CURSIVA -> Text(texto.lowercase().replaceFirstChar { it.uppercase() }, color = color, fontFamily = e.titulos, fontStyle = FontStyle.Italic,
            fontSize = (tam.value * 1.45f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        Antetitulos.INVERTIDA -> Box(modifier.background(color).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(texto.uppercase(), color = papel, fontFamily = e.letraEtiquetas ?: e.texto, fontSize = tam, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Antetitulos.CODIGO -> Text("// " + texto.uppercase(), color = color, fontFamily = e.letraEtiquetas ?: e.texto, fontSize = tam, fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        Antetitulos.FRANJA -> Box(modifier.fillMaxWidth().background(acento).padding(horizontal = 8.dp, vertical = (tam.value * 0.25f).dp)) {
            Text(texto.uppercase(), color = sobre(acento, papel, color), fontFamily = e.letraEtiquetas ?: e.texto, fontSize = tam, fontWeight = FontWeight.Black,
                letterSpacing = (tam.value * 0.1f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Antetitulos.CONDENSADA -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            if (conCuadro) { Box(Modifier.size(width = (tam.value * 0.46f).dp, height = (tam.value * 1.38f).dp).background(acento)); Spacer(Modifier.width(8.dp)) }
            Text(texto.uppercase(), color = color, fontFamily = e.letraEtiquetas ?: e.titulos, fontSize = (tam.value * 1.35f).sp, letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
