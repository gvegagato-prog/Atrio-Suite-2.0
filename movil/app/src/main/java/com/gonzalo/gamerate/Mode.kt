package com.gonzalo.gamerate

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Qué se está viendo: lo tuyo o lo de tu pareja. */
object Mode {
    /** Calendario y estadísticas de tu pareja. */
    var calP by mutableStateOf(false)

    /** Página de un juego o libro de tu pareja. */
    var pageP by mutableStateOf(false)

    /** Aspecto elegido en Ajustes. */
    var look by mutableStateOf(Look())

    /** Modo oscuro activo ahora mismo. */
    var dark by mutableStateOf(false)

    /** Tema elegido: 0 como el sistema, 1 claro, 2 oscuro. */
    var themeMode by mutableStateOf(2)

    /** Toda la app en modo "lo de tu pareja" (solo lectura). */
    var viewP by mutableStateOf(false)

    /** Ficha de tu pareja abierta desde tu propia app. */
    var itemP by mutableStateOf(false)

    /** Se está viendo algo de tu pareja: va con el tema contrario al tuyo. */
    val partnerShown: Boolean get() = viewP || pageP || calP || itemP

    /** Tus categorías propias (para buscarlas por id desde cualquier sitio). */
    var kinds by mutableStateOf<List<Kind>>(emptyList())

    /** Cambio con animación de tema (lo pone la actividad): foto de la pantalla, cambio y círculo que se abre. */
    var reveal: ((() -> Unit) -> Unit)? = null

    /** Último sitio de la pantalla que has tocado (de ahí sale el cambio de tema). */
    var touch = androidx.compose.ui.geometry.Offset.Unspecified
}

/** Fondo de los paneles. */
val UiPanel: Color get() = Crudo

/** Fondo algo más oscuro (casillas, cajitas). */
val UiTint: Color get() = Sand

/** Texto secundario. */
val UiSoft: Color get() = Muted

/** Colores para elegir en Ajustes. */
val Swatches: List<Pair<String, Int>> = listOf(
    "Cereza" to 0xFFB0102F.toInt(),
    "Cielo" to 0xFF6FB1E8.toInt(),
    "Salvia" to 0xFF8BA777.toInt(),
    "Lavanda" to 0xFFA48BD8.toInt(),
    "Melocotón" to 0xFFF4A259.toInt(),
    "Chicle" to 0xFFEE8FB4.toInt(),
    "Turquesa" to 0xFF4FB3A9.toInt(),
    "Mostaza" to 0xFFD9B44A.toInt(),
    "Teja" to 0xFFC8553D.toInt(),
    "Marino" to 0xFF2F4B7C.toInt(),
    "Grafito" to 0xFF5B5A63.toInt(),
    "Bosque" to 0xFF2E6B4F.toInt()
)

val PatternNames = listOf("Vichy", "Lunares", "Rayas", "Cuadrícula", "Liso", "Aurora")
val ThemeNames = listOf("Como el sistema", "Claro", "Oscuro")
val IntensityNames = listOf("Suave", "Media", "Intensa")

/** Hace un cambio que puede cambiar el tema (pasar a lo de tu pareja, elegir tema) con la animación del círculo. */
fun withReveal(change: () -> Unit) {
    val r = Mode.reveal
    if (r != null) r(change) else change()
}
