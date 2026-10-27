package com.gonzalo.gamerate

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

/** Iconos de la app: alias de la actividad principal (Manifest), uno activo a la vez. */
object AppIcon {
    val ALL = listOf(
        "atrio" to "Atrio", "clasico" to "Clásico", "noche" to "Noche", "neon" to "Neón", "papel" to "Papel", "arcade" to "Arcade",
        "oro" to "Oro", "oceano" to "Océano", "sakura" to "Sakura", "fuego" to "Fuego", "bosque" to "Bosque", "oled" to "OLED"
    )

    private fun comp(ctx: Context, id: String) = ComponentName(ctx.packageName, "com.gonzalo.gamerate.Icon" + id.replaceFirstChar { it.uppercase() })

    fun current(ctx: Context): String = ALL.firstOrNull { (id, _) ->
        val st = ctx.packageManager.getComponentEnabledSetting(comp(ctx, id))
        st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (st == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && id == "atrio")
    }?.first ?: "atrio"

    fun set(ctx: Context, id: String) {
        val pm = ctx.packageManager
        // Primero se enciende el nuevo y luego se apagan los demás (nunca queda la app sin icono)
        pm.setComponentEnabledSetting(comp(ctx, id), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for ((o, _) in ALL) if (o != id) pm.setComponentEnabledSetting(comp(ctx, o), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
    }

    fun drawableId(ctx: Context, id: String) = ctx.resources.getIdentifier("icon_$id", "mipmap", ctx.packageName)
}

/** Resumen de la apariencia (para la fila de Perfil): «Edición Moda · claro · acento de la edición». */
fun skinSummary(): String {
    val tema = when (Mode.themeMode) { 1 -> "claro"; 2 -> "oscuro"; else -> "claro u oscuro según el móvil" }
    val acento = ACCENT_COLORS.firstOrNull { it.id == Skin.accentId }?.name?.lowercase() ?: "rojo"
    val texto = Skin.ESCALAS.firstOrNull { kotlin.math.abs(Skin.textScale * 100 - it.first) < 1f }?.takeIf { it.first != 100 }?.second?.lowercase()
    return "$tema · acento $acento" + (texto?.let { " · texto $it" } ?: "")
}

/**
 * Apariencia: si la revista va en claro u
 * oscuro (o como el móvil) y el color del acento (antetítulos, cifras, enlaces y botones). Se ve al momento en toda la app.
 */
@Composable
fun StyleScreen(themeMode: Int, onTheme: (Int) -> Unit, @Suppress("UNUSED_PARAMETER") onLook: (Look) -> Unit = {}, onClose: () -> Unit) {
    val ctx = LocalContext.current
    // Capa a pantalla completa por encima de toda la app (la pone MainActivity): atrás la cierra
    androidx.activity.compose.BackHandler { onClose() }
    Column(
        Modifier.fillMaxSize().background(Paper)
            // Los toques no pasan a la pantalla de debajo
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding().navigationBarsPadding()
            // En una tableta, una columna cómoda en el centro (como el resto de ajustes)
            .wrapContentWidth().widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
    ) {
        TopBar("Ajustes", onClose, "Apariencia")

        // Tema
        SectionTitle("Tema")
        listOf(1 to "Claro", 2 to "Oscuro", 0 to "Como el móvil").forEachIndexed { i, (mode, name) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            val on = themeMode == mode
            Row(
                Modifier.fillMaxWidth().clickable { if (!on) onTheme(mode) }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, fontFamily = Display, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 24.sp,
                    color = if (on) Charcoal else Muted, modifier = Modifier.weight(1f))
                if (on) Text("✓", color = ColGeneral, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))

        // Fondo de color para toda la app
        SectionTitle("Fondo")
        Text("Colores hondos y sobrios para toda la app, en lugar de papel claro u oscuro. «Papel» vuelve al tema de arriba.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        listOf(null as Fondo?) .plus(FONDOS).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { f ->
                    val on = Skin.fondoId == (f?.id ?: "")
                    Column(
                        Modifier.weight(1f).clickable { if (!on) withReveal { Skin.fondoId = f?.id ?: ""; Skin.save(ctx) } },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val papel = f?.tones?.cPaper ?: Color(if (Mode.themeMode == 1) 0xFFF3EEE4 else 0xFF141210)
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).background(papel).border(if (on) 3.dp else 1.dp, if (on) ColGeneral else Line),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aa", color = f?.tones?.cInk ?: Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            if (on) Text("✓", color = f?.tones?.cInk ?: Charcoal, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
                        Text((f?.name ?: "Papel").uppercase(), color = if (on) Charcoal else Muted, fontSize = 9.sp, fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))

        // Tamaño del texto de toda la app
        SectionTitle("Tamaño del texto")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            Skin.ESCALAS.forEach { (pct, nombre) ->
                val on = kotlin.math.abs(Skin.textScale * 100 - pct) < 1f
                Column(Modifier.weight(1f).clickable { if (!on) Skin.ponEscala(ctx, pct) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // (la muestra, sin la escala ya puesta: cada «Aa» a su tamaño real)
                    Text("Aa", fontFamily = Display, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = (22f * pct / 100f / Skin.textScale).sp, color = if (on) Charcoal else Muted)
                    Text(nombre.uppercase(), color = if (on) ColGeneral else Muted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))

        // Acento
        SectionTitle("Acento")
        Text("El color de los antetítulos, las cifras, los enlaces y los botones. ", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        // (el rojo de siempre: sin ediciones, «el de la edición» y «Rojo» eran el mismo color repetido)
        ACCENT_COLORS.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { a ->
                    val on = Skin.accentId == a.id || (a.id == "rojo" && Skin.accentId == "edicion")
                    val c = Color(if (Mode.dark) a.dark else a.light)
                    Column(
                        // Se guarda dentro del cambio: withReveal lo hace más tarde (tras la foto de la pantalla)
                        Modifier.weight(1f).clickable { if (!on) withReveal { Skin.accentId = a.id; Skin.save(ctx) } },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).background(c)
                                .then(if (on) Modifier.border(3.dp, Charcoal) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            if (on) Text("✓", color = if (a.id == "tinta" || (a.id == "edicion" && c.luminance() < 0.12f && !Mode.dark)) Paper else Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                            else if (a.id == "edicion") Text("Aa", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Text(a.name.uppercase(), color = if (on) Charcoal else Muted, fontSize = 10.sp, fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))

        // Muestra: así se ve una ficha con lo elegido
        SectionTitle("Así queda")
        Antetitulo("Jugando · Videojuegos · Team Cherry", tam = 11.sp, color = ColGeneral, conCuadro = false)
        FitTitle("Hollow Knight: Silksong", 44.sp, 46.sp, letterSpacing = (-1).sp)
        Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Charcoal))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("92", color = ColorCifras, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp)
            Text("/100", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            Spacer(Modifier.weight(1f))
            Text("APUNTAR NOTAS\u00A0→", color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}
