package com.gonzalo.gamerate

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp

val GingGreen = Color(0xFF8BA777)
val GingBlue = Color(0xFF6FB1E8)
val GingCherry = Color(0xFFB0102F)

/** Fondo de cuadros vichy: blanco con rayas horizontales y verticales translúcidas. */
fun Modifier.gingham(color: Color, alpha: Float = 0.5f): Modifier = this.drawBehind {
    drawRect(Color.White)
    val period = 26.dp.toPx()
    val stripe = period / 2f
    val c = color.copy(alpha = alpha)
    var x = 0f
    while (x < size.width) {
        drawRect(c, Offset(x, 0f), Size(stripe, size.height))
        x += period
    }
    var y = 0f
    while (y < size.height) {
        drawRect(c, Offset(0f, y), Size(size.width, stripe))
        y += period
    }
}

/**
 * Fondo con el patrón elegido en Ajustes, sobre el color de papel (claro u oscuro).
 * type: 0 vichy, 1 lunares, 2 rayas, 3 cuadrícula, 4 liso. intensity: 0 suave, 1 media, 2 intensa.
 */
fun Modifier.pattern(type: Int, color: Color, intensity: Int): Modifier = this.drawBehind {
    val k = when (intensity) { 0 -> 0.6f; 2 -> 1.4f; else -> 1f }
    val dark = Mode.dark
    val base = Paper
    // En oscuro los patrones se atenúan para no deslumbrar.
    val a = if (dark) 0.45f else 1f
    when (type) {
        1 -> {
            drawRect(base)
            val c = color.copy(alpha = (0.35f * k * a).coerceAtMost(1f))
            val step = 28.dp.toPx()
            val r = 4.dp.toPx()
            var row = 0
            var y = step / 2f
            while (y < size.height + step) {
                var x = if (row % 2 == 0) step / 2f else step
                while (x < size.width + step) {
                    drawCircle(c, r, Offset(x, y))
                    x += step
                }
                y += step
                row++
            }
        }
        2 -> {
            drawRect(base)
            val c = color.copy(alpha = (0.22f * k * a).coerceAtMost(1f))
            val step = 30.dp.toPx()
            val w = 9.dp.toPx()
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(c, Offset(x, 0f), Offset(x + size.height, size.height), w)
                x += step
            }
        }
        3 -> {
            drawRect(base)
            val c = color.copy(alpha = (0.25f * k * a).coerceAtMost(1f))
            val step = 24.dp.toPx()
            val w = 1.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(c, Offset(x, 0f), Offset(x, size.height), w); x += step }
            var y = 0f
            while (y < size.height) { drawLine(c, Offset(0f, y), Offset(size.width, y), w); y += step }
        }
        4 -> drawRect(lerp(base, color, (0.05f * (k + 0.4f) * a).coerceAtMost(0.25f)))
        // Aurora (Atrio 6): papel con dos luces difusas, una del color de la sección arriba y otra del acento abajo
        5 -> {
            drawRect(base)
            val glow = (if (dark) 0.22f else 0.16f) * k
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(color.copy(alpha = glow), Color.Transparent),
                center = Offset(size.width * 0.15f, -size.height * 0.05f), radius = size.maxDimension * 0.75f))
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(ColGeneral.copy(alpha = glow * 0.55f), Color.Transparent),
                center = Offset(size.width * 1.05f, size.height * 0.85f), radius = size.maxDimension * 0.6f))
        }
        else -> {
            drawRect(base)
            val period = 26.dp.toPx()
            val stripe = period / 2f
            val c = color.copy(alpha = (0.4f * k * a).coerceAtMost(0.9f))
            var x = 0f
            while (x < size.width) {
                drawRect(c, Offset(x, 0f), Size(stripe, size.height))
                x += period
            }
            var y = 0f
            while (y < size.height) {
                drawRect(c, Offset(0f, y), Size(size.width, stripe))
                y += period
            }
        }
    }
}

/**
 * Fondo detrás de un texto para que se lea sobre los patrones.
 * Con el fondo liso no hace falta: solo deja el mismo espacio, sin caja.
 */
fun Modifier.pill(radius: Int = 16, h: Int = 14, v: Int = 8): Modifier =
    if (Mode.look.pattern >= 4 || Skin.revista) this.padding(horizontal = if (Skin.revista) 0.dp else h.dp, vertical = v.dp)
    else this.background(UiPanel, shp(radius.dp)).padding(horizontal = h.dp, vertical = v.dp)

/** Tarjeta sobria: superficie con línea fina. */
fun Modifier.card(radius: Int = 20): Modifier =
    this.surface(radius.dp, UiPanel)

/** Aparece con fundido y subiendo un poco, tras un retraso (el fondo ya está puesto). */
fun Modifier.appear(delayMs: Int, on: Boolean = true): Modifier =
    if (!on) this else composed {
        if (LocalInspectionMode.current) return@composed this
        val a = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            // Entradas cortas: la mitad de retraso y de duración que antes, para que todo se sienta inmediato.
            a.animateTo(1f, tween(260, delayMillis = (delayMs / 3).coerceAtMost(180), easing = FastOutSlowInEasing))
        }
        this.graphicsLayer {
            alpha = a.value
            translationY = (1f - a.value) * 14.dp.toPx()
        }
    }

/** Clic con rebote: se encoge al pulsar y vuelve con un muelle. */
fun Modifier.pop(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "pop"
    )
    this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}
