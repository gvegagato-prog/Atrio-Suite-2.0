package com.gonzalo.gamerate

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Paleta: papel, superficie, una línea fina y tinta. Cada color se lee en el momento de la paleta elegida
 * (Skin, en Ajustes → Estilo) y de su versión clara u oscura (Mode.dark).
 */
private fun pick(light: Long, dark: Long) = Color(if (Mode.dark) dark else light)

/** Fondo de la app. */
val Paper: Color get() = Skin.tones.cPaper

/** Superficie de tarjetas y paneles (antes "crudo"). */
val Crudo: Color get() = Skin.tones.cCrudo

/** Relleno suave (casillas, chips apagados). */
val Sand: Color get() = Skin.tones.cSand

/** Línea fina de separación. */
val Line: Color get() = Skin.tones.cLine

/** Tinta principal. */
val Charcoal: Color get() = Skin.tones.cInk

/** Borde de campos de texto: algo más visible que la línea fina. */
val Edge: Color get() = Skin.tones.cEdge

/** Texto secundario. */
val Muted: Color get() = Skin.tones.cMuted

val ColGeneral: Color get() = Color(Skin.tones.general)
val ColGfx: Color get() = Color(Skin.tones.gfx)
val ColStory: Color get() = Color(Skin.tones.story)
val ColMusic: Color get() = Color(Skin.tones.music)
val ColGameplay: Color get() = Color(Skin.tones.gameplay)
val ColAvg: Color get() = pick(0xFF9A968E, 0xFF77736C)
val Gold: Color get() = Color(Skin.tones.gold)

/** Dorado legible sobre fondo de tinta (avisos), en claro y en oscuro. */
val GoldOnInk: Color get() = pick(0xFFE2B947, 0xFF8A6A12)

/** Esquema de Material a partir de la paleta. */
fun appColors() = if (Mode.dark) darkColorScheme(
    primary = Charcoal, onPrimary = Crudo, secondary = Charcoal, onSecondary = Crudo,
    background = Paper, onBackground = Charcoal, surface = Crudo, onSurface = Charcoal,
    surfaceVariant = Sand, onSurfaceVariant = Charcoal, surfaceContainer = Crudo,
    surfaceContainerHigh = Crudo, surfaceContainerHighest = Sand, surfaceContainerLow = Crudo,
    outline = Edge, outlineVariant = Line, error = ColGeneral
) else lightColorScheme(
    primary = Charcoal, onPrimary = Crudo, secondary = Charcoal, onSecondary = Crudo,
    background = Paper, onBackground = Charcoal, surface = Crudo, onSurface = Charcoal,
    surfaceVariant = Sand, onSurfaceVariant = Charcoal, surfaceContainer = Crudo,
    surfaceContainerHigh = Crudo, surfaceContainerHighest = Sand, surfaceContainerLow = Crudo,
    outline = Edge, outlineVariant = Line, error = ColGeneral
)

/** Letra de títulos de la app: serif clásica (Fraunces). */
val AtrioDisplay = FontFamily(
    Font(R.font.display_regular, FontWeight.Normal),
    Font(R.font.display_medium, FontWeight.Medium),
    Font(R.font.display_semibold, FontWeight.SemiBold),
    Font(R.font.display_semibold, FontWeight.Bold),
    Font(R.font.display_italic, FontWeight.Normal, FontStyle.Italic)
)

/** Letra de títulos de Atrio 6: Bricolage Grotesque (variable: grosor y anchura). */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val VitrinaDisplay = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.bricolage, FontWeight(w), variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
            androidx.compose.ui.text.font.FontVariation.weight(w), androidx.compose.ui.text.font.FontVariation.width(if (w >= 700) 92f else 100f)))
    }
)

/** Letra del resto de la app: sans neutra (Inter). */
val AtrioBody = FontFamily(
    Font(R.font.body_regular, FontWeight.Normal),
    Font(R.font.body_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.body_medium, FontWeight.Medium),
    Font(R.font.body_semibold, FontWeight.SemiBold),
    Font(R.font.body_bold, FontWeight.Bold),
    Font(R.font.body_bold, FontWeight.ExtraBold)
)

/** Letra de títulos: la de la edición de la revista (Ediciones.kt). */
val Display: FontFamily get() = if (Skin.revista) Ediciones.actual.titulos else Skin.family(Skin.display) ?: AtrioDisplay

/** Letra del texto: la de la edición. */
val Body: FontFamily get() = if (Skin.revista) Ediciones.actual.texto else Skin.family(Skin.body) ?: AtrioBody

private val base = Typography()

private fun TextStyle.body() = copy(fontFamily = Body)
private fun TextStyle.display() = copy(fontFamily = Display, fontWeight = FontWeight.Normal)

val AppTypography: Typography get() = Typography(
    displayLarge = base.displayLarge.display(),
    displayMedium = base.displayMedium.display(),
    displaySmall = base.displaySmall.display(),
    headlineLarge = base.headlineLarge.display(),
    headlineMedium = base.headlineMedium.display(),
    headlineSmall = base.headlineSmall.display(),
    titleLarge = base.titleLarge.display(),
    titleMedium = base.titleMedium.body(),
    titleSmall = base.titleSmall.body(),
    bodyLarge = base.bodyLarge.body().copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = base.bodyMedium.body(),
    bodySmall = base.bodySmall.body(),
    labelLarge = base.labelLarge.body().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.body().copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.body()
)

fun fmt(t: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(t))

/** "12 h 30 min", "45 min" o "0 h". */
fun hoursText(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        m > 0 -> "$m min"
        else -> "0 h"
    }
}
