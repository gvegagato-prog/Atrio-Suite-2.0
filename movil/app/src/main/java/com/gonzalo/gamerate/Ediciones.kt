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
}

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
    /** La línea bajo la cabecera; «%s» es tu nombre. */
    val lema: String,
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
    val widget: LetrasWidget = LetrasWidget(R.font.display_semibold, R.font.display_italic, R.font.body_bold, R.font.body_medium),
)

private fun familia(vararg f: Pair<Int, Pair<FontWeight, Boolean>>) = FontFamily(f.map { (id, wi) -> Font(id, wi.first, if (wi.second) FontStyle.Italic else FontStyle.Normal) })
private val N = FontWeight.Normal
private val M = FontWeight.Medium
private val SB = FontWeight.SemiBold
private val B = FontWeight.Bold
private val XB = FontWeight.ExtraBold
private val K = FontWeight.Black
private infix fun Int.de(w: FontWeight) = this to (w to false)
private infix fun Int.cursiva(w: FontWeight) = this to (w to true)

private val PLAYFAIR = familia(R.font.ed_playfair_bold de N, R.font.ed_playfair_bold de B, R.font.ed_playfair_bold de K, R.font.ed_playfair_italic cursiva N)
private val SOURCE_SERIF = familia(R.font.ed_sourceserif_regular de N, R.font.ed_sourceserif_semibold de M, R.font.ed_sourceserif_semibold de SB, R.font.ed_sourceserif_bold de B, R.font.ed_sourceserif_bold de K, R.font.ed_sourceserif_italic cursiva N)
private val FRAKTUR = familia(R.font.ed_unifraktur de N, R.font.ed_unifraktur de B)
private val BODONI = familia(R.font.ed_bodoni_regular de N, R.font.ed_bodoni_regular de M, R.font.ed_bodoni_bold de SB, R.font.ed_bodoni_bold de B, R.font.ed_bodoni_bold de K, R.font.ed_bodoni_italic cursiva N)
private val JOST = familia(R.font.ed_jost_regular de N, R.font.ed_jost_medium de M, R.font.ed_jost_semibold de SB, R.font.ed_jost_bold de B, R.font.ed_jost_bold de K, R.font.ed_jost_italic cursiva N)
private val CASLON_DISPLAY = familia(R.font.ed_caslondisplay de N, R.font.ed_caslondisplay de B, R.font.ed_caslon_italic cursiva N)
private val CASLON = familia(R.font.ed_caslon_regular de N, R.font.ed_caslon_regular de M, R.font.ed_caslon_bold de SB, R.font.ed_caslon_bold de B, R.font.ed_caslon_bold de K, R.font.ed_caslon_italic cursiva N)
private val GROTESK = familia(R.font.ed_spacegrotesk_medium de N, R.font.ed_spacegrotesk_medium de M, R.font.ed_spacegrotesk_bold de SB, R.font.ed_spacegrotesk_bold de B, R.font.ed_spacegrotesk_bold de K, R.font.ed_plexsans_italic cursiva N)
private val PLEX = familia(R.font.ed_plexsans_regular de N, R.font.ed_plexsans_medium de M, R.font.ed_plexsans_semibold de SB, R.font.ed_plexsans_bold de B, R.font.ed_plexsans_bold de K, R.font.ed_plexsans_italic cursiva N)
private val PLEX_MONO = familia(R.font.ed_plexmono_medium de N, R.font.ed_plexmono_medium de M, R.font.ed_plexmono_bold de B, R.font.ed_plexmono_bold de K)
private val ANTON = familia(R.font.ed_anton de N, R.font.ed_anton de B, R.font.ed_anton de K, R.font.ed_barlow_italic cursiva N)
private val BARLOW = familia(R.font.ed_barlow_regular de N, R.font.ed_barlow_medium de M, R.font.ed_barlow_semibold de SB, R.font.ed_barlow_bold de B, R.font.ed_barlow_bold de K, R.font.ed_barlow_italic cursiva N)
private val ABRIL = familia(R.font.ed_abril de N, R.font.ed_abril de B, R.font.ed_abril de K, R.font.ed_karla_italic cursiva N)
private val KARLA = familia(R.font.ed_karla_regular de N, R.font.ed_karla_medium de M, R.font.ed_karla_bold de SB, R.font.ed_karla_bold de B, R.font.ed_karla_bold de K, R.font.ed_karla_italic cursiva N)
private val ARCHIVO_BLACK = familia(R.font.ed_archivoblack de N, R.font.ed_archivoblack de B, R.font.ed_archivoblack de K, R.font.ed_courier_italic cursiva N)
private val COURIER = familia(R.font.ed_courier_regular de N, R.font.ed_courier_regular de M, R.font.ed_courier_bold de SB, R.font.ed_courier_bold de B, R.font.ed_courier_bold de K, R.font.ed_courier_italic cursiva N)
private val CORMORANT = familia(R.font.ed_cormorant_semibold de N, R.font.ed_cormorant_semibold de M, R.font.ed_cormorant_bold de SB, R.font.ed_cormorant_bold de B, R.font.ed_cormorant_bold de K, R.font.ed_cormorant_italic cursiva N)
private val LATO = familia(R.font.ed_lato_regular de N, R.font.ed_lato_regular de M, R.font.ed_lato_bold de SB, R.font.ed_lato_bold de B, R.font.ed_lato_black de XB, R.font.ed_lato_black de K, R.font.ed_lato_italic cursiva N)

// Papel, tarjetas, tinta, acento, y los criterios (gráficos, historia, música, jugabilidad), dorado, arena, línea, borde, gris
private val P_ATRIO = PALETTES.first { it.id == "revista" }
private val P_DOMINICAL = Palette("ed_dominical", "Dominical", "Ediciones",
    t(0xECE9E1, 0xF6F4EE, 0x1A1A1A, 0x1F4E79, 0x1A1A1A, 0x55524C, 0x8F8A82, 0x7A2E2E, 0xA27C1F, 0xE2DED5, 0xCFCAC0, 0xB3AEA3, 0x6E6A63),
    t(0x141516, 0x1C1D1F, 0xE8E6E1, 0x7FA9D6, 0xE8E6E1, 0xB7B3AB, 0x86827B, 0xD98C8C, 0xD8B45A, 0x222325, 0x2D2E31, 0x45474B, 0x9A968F))
private val P_MODA = Palette("ed_moda", "Moda", "Ediciones",
    t(0xFFFFFF, 0xF8F6F3, 0x0A0A0A, 0xB8336A, 0x0A0A0A, 0x5C5853, 0x9A958E, 0x7E2448, 0xB08A3E, 0xF2EFEB, 0xE6E2DC, 0xC9C4BC, 0x76716A),
    t(0x0A0A0A, 0x141414, 0xF5F2EE, 0xEE7FAD, 0xF5F2EE, 0xBDB8B0, 0x87827B, 0xF0A2C2, 0xD9B56C, 0x1A1A1A, 0x262626, 0x404040, 0x9E9992))
private val P_CULTURAL = Palette("ed_cultural", "Cultural", "Ediciones",
    t(0xF8F4E9, 0xFCFAF4, 0x1E1E1E, 0xA8322D, 0x1E1E1E, 0x57534A, 0x928C80, 0x7A2621, 0xA8842A, 0xEFEADC, 0xDDD6C5, 0xC0B8A5, 0x736E64),
    t(0x17150F, 0x1F1C16, 0xEDE7D8, 0xE07A6E, 0xEDE7D8, 0xB9B19F, 0x878070, 0xE8A096, 0xDDBA62, 0x24211A, 0x302C24, 0x4A443A, 0x9C9586))
private val P_TECNO = Palette("ed_tecno", "Tecnología", "Ediciones",
    t(0xF4F5F7, 0xFFFFFF, 0x0B0C0E, 0x1F4BFF, 0x0B0C0E, 0x4E535C, 0x8A9099, 0x0F2CA8, 0xC28A00, 0xE8EAEE, 0xDADDE3, 0xB9BEC7, 0x666C76),
    t(0x0B0C0E, 0x15171B, 0xEDEFF3, 0x7D95FF, 0xEDEFF3, 0xAEB4BE, 0x7B818B, 0xA9B8FF, 0xFFC94D, 0x1B1E23, 0x262A31, 0x3C424B, 0x959BA6))
private val P_MUSICA = Palette("ed_musica", "Música", "Ediciones",
    t(0xF2EFEA, 0xFAF8F5, 0x111111, 0xD7261E, 0x111111, 0x55524E, 0x928E88, 0x8E1712, 0xB98A16, 0xE7E3DC, 0xD6D1C9, 0xB7B1A7, 0x6C6862),
    t(0x111111, 0x1A1A1A, 0xF2EFEA, 0xFF4A3D, 0xF2EFEA, 0xB6B1A9, 0x85807A, 0xFF8A80, 0xE8BC4A, 0x1F1F1F, 0x2B2B2B, 0x454545, 0x9C978F))
private val P_CINE = Palette("ed_cine", "Cine", "Ediciones",
    t(0xF3E7C4, 0xF8EFD6, 0x1A160E, 0x1A160E, 0x1A160E, 0x5A5242, 0x938973, 0x7A2E1C, 0x9C6F00, 0xEADCB4, 0xDACB9E, 0xBFAE7E, 0x6F6650),
    t(0x14110A, 0x1D1910, 0xF2E5C0, 0xF2C94C, 0xF2E5C0, 0xBDB08A, 0x8A7F62, 0xF0A58A, 0xF2C94C, 0x221E14, 0x2E281B, 0x4A422E, 0xA0957A))
private val P_FANZINE = Palette("ed_fanzine", "Fanzine", "Ediciones",
    t(0xEDEDEA, 0xF7F7F5, 0x101010, 0xE6007E, 0x101010, 0x4F4F4C, 0x8D8D89, 0xA0005A, 0xB8860B, 0xE2E2DE, 0xD2D2CD, 0xB0B0AA, 0x666663),
    t(0x121212, 0x1B1B1B, 0xEDEDEA, 0xFF4DA6, 0xEDEDEA, 0xB4B4AF, 0x82827D, 0xFF8CC6, 0xE6C15A, 0x202020, 0x2C2C2C, 0x474747, 0x9A9A95))
private val P_VIAJES = Palette("ed_viajes", "Viajes", "Ediciones",
    t(0xFAF8F3, 0xFFFFFF, 0x1C1B19, 0xA87600, 0x1C1B19, 0x56534D, 0x928E86, 0x2F5D3A, 0xA87600, 0xEFECE4, 0xE1DDD3, 0xC4BFB3, 0x6F6B64),
    t(0x101112, 0x191A1C, 0xEEECE7, 0xFFCE00, 0xEEECE7, 0xB3AFA7, 0x82807A, 0x8FC79B, 0xFFCE00, 0x1F2022, 0x2A2B2E, 0x44464A, 0x9B988F))

/** El amarillo del marco de la revista de viajes. */
private const val AMARILLO_MARCO = 0xFFFFCE00

val EDICIONES: List<Edicion> = listOf(
    Edicion("atrio", "Atrio", "La de siempre: papel crema, serif cálida y rojo de imprenta", "ATRIO", "La colección de %s, al día", "Atrio · hecho en casa",
        P_ATRIO, AtrioDisplay, AtrioBody),
    Edicion("dominical", "Dominical", "Suplemento de periódico: papel de prensa, gótica en la cabecera y filete doble", "El Atrio", "Suplemento semanal · la colección de %s", "El Atrio · edición dominical",
        P_DOMINICAL, PLAYFAIR, SOURCE_SERIF, letraCabecera = FRAKTUR, cabPeso = FontWeight.Normal, cabTrack = 0f, cabCentro = true, doble = true, filete = 1f,
        widget = LetrasWidget(R.font.ed_playfair_bold, R.font.ed_playfair_italic, R.font.ed_sourceserif_bold, R.font.ed_sourceserif_regular)),
    Edicion("moda", "Moda", "Como una de moda: blanco, Bodoni enorme y filetes finísimos", "ATRIO", "Edición de colección · %s", "ATRIO · PARÍS · MADRID · TU SOFÁ",
        P_MODA, BODONI, JOST, cabPeso = FontWeight.Normal, cabTrack = -0.04f, cabCentro = true, filete = 0.5f,
        widget = LetrasWidget(R.font.ed_bodoni_regular, R.font.ed_bodoni_italic, R.font.ed_jost_semibold, R.font.ed_jost_regular)),
    Edicion("cultural", "Cultural", "Semanario de letras: Caslon, antetítulos en cursiva y mucho blanco", "Atrio", "Crítica, lecturas y partidas de %s", "Atrio · fundada en tu casa",
        P_CULTURAL, CASLON_DISPLAY, CASLON, cabPeso = FontWeight.Normal, cabTrack = 0.01f, cabCentro = true, filete = 0.75f, antetitulos = Antetitulos.CURSIVA,
        widget = LetrasWidget(R.font.ed_caslondisplay, R.font.ed_caslon_italic, R.font.ed_caslon_bold, R.font.ed_caslon_regular)),
    Edicion("tecno", "Tecnología", "Revista de tecnología: grotesca, letra de máquina y azul eléctrico", "[ATRIO]", "%s // tu colección, compilada", "[ATRIO] // fin de la transmisión",
        P_TECNO, GROTESK, PLEX, letraEtiquetas = PLEX_MONO, cabTrack = -0.05f, filete = 2f, antetitulos = Antetitulos.CODIGO,
        widget = LetrasWidget(R.font.ed_spacegrotesk_bold, R.font.ed_plexsans_italic, R.font.ed_plexsans_bold, R.font.ed_plexsans_regular)),
    Edicion("musica", "Música", "Revista de música: condensada, en mayúsculas y rojo de concierto", "ATRIO", "Lo que suena en casa de %s", "ATRIO · SUBE EL VOLUMEN",
        P_MUSICA, ANTON, BARLOW, letraEtiquetas = ANTON, cabPeso = FontWeight.Normal, cabTrack = -0.01f, cabEscala = 1.7f, filete = 1.5f, antetitulos = Antetitulos.CONDENSADA, titularesMayus = true,
        fotos = Fotos.CONTRASTE,
        widget = LetrasWidget(R.font.ed_anton, R.font.ed_barlow_italic, R.font.ed_barlow_bold, R.font.ed_barlow_medium)),
    Edicion("cine", "Cine", "Cuadernos de cine: papel amarillo, didona gorda y fotos en sepia", "Cuadernos de Atrio", "Las películas, series y libros de %s", "Cuadernos de Atrio · nº del día",
        P_CINE, ABRIL, KARLA, cabPeso = FontWeight.Normal, cabTrack = -0.02f, filete = 1f, fotos = Fotos.SEPIA,
        widget = LetrasWidget(R.font.ed_abril, R.font.ed_karla_italic, R.font.ed_karla_bold, R.font.ed_karla_medium)),
    Edicion("fanzine", "Fanzine", "Fotocopiado en casa: letras recortadas, máquina de escribir y fotos en blanco y negro", "¡ATRIO!", "hecho a mano por %s · tirada: 1", "¡ATRIO! · fotocopia y reparte",
        P_FANZINE, ARCHIVO_BLACK, COURIER, letraEtiquetas = COURIER, cabTrack = -0.04f, giro = -2.5f, filete = 2f, antetitulos = Antetitulos.INVERTIDA, fotos = Fotos.BN,
        widget = LetrasWidget(R.font.ed_archivoblack, R.font.ed_courier_italic, R.font.ed_courier_bold, R.font.ed_courier_regular)),
    Edicion("viajes", "Viajes", "Revista de viajes y naturaleza: el marco amarillo y una serif clásica", "ATRIO", "Expediciones y hallazgos de %s", "Atrio · explora tu colección",
        P_VIAJES, CORMORANT, LATO, cabTrack = 0.06f, cabCentro = true, marco = AMARILLO_MARCO, filete = 1f,
        widget = LetrasWidget(R.font.ed_cormorant_bold, R.font.ed_cormorant_italic, R.font.ed_lato_bold, R.font.ed_lato_regular)),
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

/** Filete de la revista (horizontal), con el grosor de la edición. */
@Composable
fun Filete(alto: Dp = 2.dp, color: Color = Charcoal, modifier: Modifier = Modifier) {
    val e = Ediciones.actual
    Box(modifier.fillMaxWidth().height((alto.value * e.filete).coerceAtLeast(0.5f).dp).background(color))
}

/** El filete bajo la cabecera: grueso (o doble, fino y grueso, en la dominical). */
@Composable
fun FileteCabecera(alto: Dp = 3.dp) {
    val e = Ediciones.actual
    if (e.doble) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Charcoal))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(alto).background(Charcoal))
    } else Filete(alto)
}

/** La cabecera de la revista (el nombre, enorme), como la pide la edición. */
@Composable
fun Cabecera(tam: TextUnit, modifier: Modifier = Modifier, color: Color = Charcoal, e: Edicion = Ediciones.actual) {
    val marco = e.marco != 0L
    val inner: @Composable () -> Unit = {
        FitTitle(
            e.cabecera, (tam.value * e.cabEscala).sp, (tam.value * e.cabEscala).sp, color = color, letterSpacing = e.cabTrack.em, fontWeight = e.cabPeso,
            fontStyle = if (e.cabCursiva) FontStyle.Italic else null, maxLines = 1, family = e.letraCabecera ?: e.titulos,
            align = if (e.cabCentro) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth().then(if (e.giro != 0f) Modifier.rotate(e.giro) else Modifier), minSize = 12f, unaLinea = true, conEdicion = false
        )
    }
    if (marco) Box(modifier.fillMaxWidth().border(if (tam.value >= 60f) 7.dp else 4.dp, Color(e.marco)).padding(horizontal = if (tam.value >= 60f) 14.dp else 8.dp, vertical = 4.dp)) { inner() }
    else Box(modifier.fillMaxWidth()) { inner() }
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
        Antetitulos.CONDENSADA -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            if (conCuadro) { Box(Modifier.size(width = (tam.value * 0.46f).dp, height = (tam.value * 1.38f).dp).background(acento)); Spacer(Modifier.width(8.dp)) }
            Text(texto.uppercase(), color = color, fontFamily = e.letraEtiquetas ?: e.titulos, fontSize = (tam.value * 1.35f).sp, letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Una portada en pequeño de la edición: así se ve antes de elegirla (en claro u oscuro, como esté la app). */
@Composable
fun MuestraEdicion(e: Edicion, elegida: Boolean, nombre: String, modifier: Modifier = Modifier) {
    val tn = if (Mode.dark) e.paleta.dark else e.paleta.light
    val papel = tn.cPaper; val tinta = tn.cInk; val gris = tn.cMuted
    val acento = Color(if (Mode.dark) e.paleta.dark.general else e.paleta.light.general)
    Column(
        modifier.fillMaxWidth().aspectRatio(0.74f).background(papel)
            .border(if (elegida) 3.dp else 1.dp, if (elegida) Charcoal else Line).padding(10.dp)
    ) {
        Cabecera(40.sp, color = tinta, e = e)
        Spacer(Modifier.height(3.dp))
        if (e.doble) { Box(Modifier.fillMaxWidth().height(0.5.dp).background(tinta)); Spacer(Modifier.height(1.5.dp)) }
        Box(Modifier.fillMaxWidth().height((2f * e.filete).coerceAtLeast(0.5f).dp).background(tinta))
        Text(e.lema.format(nombre), color = gris, fontFamily = e.titulos, fontStyle = FontStyle.Italic, fontSize = 10.sp, lineHeight = 12.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.weight(1f))
        Antetitulo("En portada", tam = 8.sp, color = tinta, e = e, acento = acento, papel = papel)
        Spacer(Modifier.height(4.dp))
        val muestra = if (e.titularesMayus) "HOLLOW KNIGHT" else "Hollow Knight"
        Text(muestra, color = tinta, fontFamily = e.titulos, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 19.sp, maxLines = 2)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text("92", color = acento, fontFamily = e.titulos, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 22.sp)
            Text("/100", color = gris, fontFamily = e.texto, fontSize = 8.sp, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        }
    }
}
