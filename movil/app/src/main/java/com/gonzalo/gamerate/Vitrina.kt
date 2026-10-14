@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.gonzalo.gamerate

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/*
 * Atrio 6 · Vitrina: el lenguaje visual de la app nueva.
 * Portadas a lo grande con su título encima, un dock flotante abajo, cabeceras con el arte del juego o del libro,
 * y movimiento con muelles: lo que tocas se hunde un poco, lo que entra sube en cascada y la portada que abres
 * viaja hasta la ficha (transición compartida).
 */

// ---------------------------------------------------------------- Movimiento

/** Cuánto se mueve la app (Estilo → Movimiento): 0 vivo, 1 sobrio, 2 mínimo. */
object Motion {
    val level: Int get() = Skin.motion
    val on: Boolean get() = Skin.motion < 2
    /** Muelle de la app: con rebote en «vivo», sin rebote en «sobrio». */
    fun <T> bouncy() = spring<T>(dampingRatio = if (level == 0) 0.62f else 1f, stiffness = if (level == 0) 420f else 700f)
    fun <T> smooth() = spring<T>(dampingRatio = 1f, stiffness = if (level == 0) 380f else 900f)
    fun ms(base: Int) = when (level) { 0 -> base; 1 -> (base * 0.6f).toInt(); else -> 0 }
}

/** Clic que se hunde con un muelle y vuelve (y un toque de vibración). */
fun Modifier.press(haptic: Boolean = true, onLong: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hf = LocalHapticFeedback.current
    val sc by animateFloatAsState(if (pressed && Motion.on) 0.955f else 1f, Motion.bouncy(), label = "press")
    @OptIn(ExperimentalFoundationApi::class)
    this.graphicsLayer { scaleX = sc; scaleY = sc }
        .combinedClickable(
            interactionSource = src, indication = null,
            onLongClick = onLong?.let { l -> { hf.performHapticFeedback(HapticFeedbackType.LongPress); l() } },
            onClick = { if (haptic) hf.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
        )
}

/** Entrada en cascada: cada elemento sube y aparece un poco después que el anterior. */
fun Modifier.rise(index: Int = 0, on: Boolean = true): Modifier =
    if (!on) this else composed {
        if (LocalInspectionMode.current || !Motion.on) return@composed this
        val a = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(Motion.ms((index.coerceAtMost(10)) * 38).toLong())
            a.animateTo(1f, Motion.bouncy())
        }
        val dy = with(LocalDensity.current) { 26.dp.toPx() }
        this.graphicsLayer {
            alpha = a.value.coerceIn(0f, 1f)
            translationY = (1f - a.value) * dy
            val s = 0.94f + 0.06f * a.value
            scaleX = s; scaleY = s
        }
    }

// ---------------------------------------------------------------- Transición compartida (portada → ficha)

val LocalShared = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalAnim = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** La misma portada en la lista y en la ficha: al abrirla, viaja de un sitio a otro. */
@Composable
fun Modifier.sharedCover(key: String?): Modifier {
    val s = LocalShared.current ?: return this
    val a = LocalAnim.current ?: return this
    if (key == null || !Motion.on) return this
    return with(s) {
        this@sharedCover.sharedElement(
            rememberSharedContentState(key), a,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 360f) }
        )
    }
}

/** Clave de la portada de un elemento (igual en la lista, en Inicio y en la ficha). */
fun coverKey(kind: Int, id: Long) = "cover-$kind-$id"

// ---------------------------------------------------------------- Piezas

/** Título grande de pantalla, con una línea pequeña encima y botones a la derecha. */
@Composable
fun BigHeader(title: String, overline: String? = null, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (overline != null) Text(overline.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
            Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Encabezado de un bloque (con un «ver todo» opcional). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null && onAction != null) Text(action, color = ColGeneral, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.press(onClick = onAction).padding(6.dp))
    }
}

/** Botón redondo de icono (sobre imágenes, translúcido). */
@Composable
fun RoundIcon(icon: ImageVector, desc: String, onImage: Boolean = false, active: Boolean = false, size: Dp = 42.dp, onClick: () -> Unit) {
    val bg = when {
        active -> Charcoal
        onImage -> Color.Black.copy(alpha = 0.38f)
        else -> Crudo
    }
    Box(
        Modifier.size(size).press(onClick = onClick).clip(roundShape()).background(bg)
            .then(if (!onImage && !active) Modifier.border(1.dp, Line, roundShape()) else Modifier),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = if (active) Paper else if (onImage) Color.White else Charcoal, modifier = Modifier.size(size * 0.48f)) }
}

/** Botón de volver para pantallas sin dock. */
@Composable
fun BackRound(onImage: Boolean = false, onBack: () -> Unit) = RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onImage = onImage, onClick = onBack)

/** Botón grande de acción (relleno de acento o de tinta). */
@Composable
fun BigAction(icon: ImageVector?, text: String, modifier: Modifier = Modifier, accent: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (accent) ColGeneral else Charcoal
    Row(
        modifier.heightIn(min = 52.dp).press(onClick = { if (enabled) onClick() }).clip(pillShape())
            .background(if (enabled) bg else bg.copy(alpha = 0.35f)).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Icon(icon, null, tint = Paper, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Paper, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Opciones con un indicador que se desliza hasta la elegida. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, counts: List<Int>? = null, onSelect: (Int) -> Unit) {
    val hf = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxWidth().clip(pillShape()).background(Sand).padding(4.dp)) {
        val w = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(w * selected, Motion.bouncy(), label = "seg")
        Box(Modifier.offset(x = x).width(w).height(38.dp).shadow(if (Motion.on) 4.dp else 0.dp, pillShape()).clip(pillShape()).background(Charcoal))
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                val c by animateColorAsState(if (on) Paper else Muted, label = "segc")
                Row(
                    Modifier.weight(1f).height(38.dp).clip(pillShape())
                        .clickable(remember { MutableInteractionSource() }, null) { if (!on) { hf.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) } },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(o, color = c, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val n = counts?.getOrNull(i)
                    if (n != null && n > 0) Text(" $n", color = c.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Una pastilla de dato (horas, plataforma, año…). */
@Composable
fun InfoChip(text: String, icon: ImageVector? = null, onImage: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(pillShape()).background(if (onImage) Color.White.copy(alpha = 0.16f) else Sand).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = if (onImage) Color.White else Muted, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)) }
        Text(text, color = if (onImage) Color.White else Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Nota pequeña en una esquina de portada. */
@Composable
fun ScoreBadge(value: Int, color: Color = ColGeneral, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(pillShape()).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text("$value", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

/**
 * Portada de la biblioteca: la imagen manda, con el título encima sobre un degradado, una insignia arriba
 * (nota, Rotten Tomatoes…) y la barra de progreso abajo. Es la que viaja a la ficha al abrirla.
 */
@Composable
fun CoverCard(
    cover: String, name: String, sub: String?, sharedKey: String?, modifier: Modifier = Modifier,
    progress: Float? = null, dim: Boolean = false, ratio: Float = 2f / 3f,
    badge: (@Composable BoxScope.() -> Unit)? = null, badgeStart: (@Composable BoxScope.() -> Unit)? = null,
    onLong: (() -> Unit)? = null, interactive: Boolean = true, onClick: () -> Unit
) {
    val shape = shp(20.dp)
    Box(modifier.fillMaxWidth().aspectRatio(ratio).then(if (interactive) Modifier.press(onLong = onLong, onClick = onClick) else Modifier)) {
        Box(Modifier.matchParentSize().sharedCover(sharedKey).clip(shape).background(Sand), contentAlignment = Alignment.Center) {
            if (cover.isBlank()) {
                Text(name.trim().take(1).uppercase(), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 54.sp, color = Muted)
            } else {
                AsyncImage(cover, name, Modifier.fillMaxSize().graphicsLayer { alpha = if (dim) 0.45f else 1f }, contentScale = ContentScale.Crop)
            }
        }
        // Degradado y título
        Box(Modifier.matchParentSize().clip(shape).background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = if (progress != null) 14.dp else 11.dp)) {
            Text(name, color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrBlank()) Text(sub, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (progress != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp).height(3.dp).clip(pillShape()).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).fillMaxHeight().background(ColGeneral))
            }
        }
        if (badge != null) Box(Modifier.align(Alignment.TopEnd).padding(8.dp), content = badge)
        if (badgeStart != null) Box(Modifier.align(Alignment.TopStart).padding(8.dp), content = badgeStart)
    }
}

/** Tarjeta de superficie de Atrio 6: sin borde, esquinas grandes. */
@Composable
fun Card6(modifier: Modifier = Modifier, padding: Dp = 18.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier)
            .surface(24.dp, Crudo).padding(padding),
        content = content
    )
}

/**
 * Cabecera de ficha: el arte (fondo, o la portada difuminada) a sangre, con la portada encima (la que viene
 * volando desde la lista), el título grande y los datos. Se funde con el fondo de la app.
 */
@Composable
fun HeroHeader(
    cover: String, hero: String, title: String, overline: String?, sharedKey: String?,
    onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, chips: @Composable () -> Unit = {}
) {
    val art = hero.ifBlank { cover }
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            if (art.isNotBlank()) {
                AsyncImage(
                    art, null,
                    Modifier.fillMaxSize().then(if (hero.isBlank()) Modifier.blur(28.dp) else Modifier).graphicsLayer { alpha = if (hero.isBlank()) 0.7f else 0.9f },
                    contentScale = ContentScale.Crop
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.45f to Color.Transparent, 1f to Paper)))
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BackRound(onImage = true, onBack = onBack)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            }
            Spacer(Modifier.height(96.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    Modifier.width(118.dp).aspectRatio(2f / 3f).sharedCover(sharedKey).shadow(22.dp, shp(18.dp)).clip(shp(18.dp)).background(Sand),
                    contentAlignment = Alignment.Center
                ) {
                    if (cover.isBlank()) Text(title.take(1).uppercase(), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = Muted)
                    else AsyncImage(cover, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    if (overline != null) Text(overline.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = if (title.length > 28) 24.sp else 29.sp, lineHeight = if (title.length > 28) 27.sp else 32.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(14.dp))
            chips()
        }
    }
}

// ---------------------------------------------------------------- Dock

class DockItem(val icon: ImageVector, val label: String)

/** Dock flotante: el destino elegido se abre en pastilla con su nombre; el resto, solo icono. */
@Composable
/** Espacio que hay que dejar abajo para que el dock (y la barra de navegación de Android) no tape nada. */
@Composable
fun dockSpace(extra: androidx.compose.ui.unit.Dp = 16.dp): androidx.compose.ui.unit.Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp + extra

@Composable
fun Dock(items: List<DockItem>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val hf = LocalHapticFeedback.current
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().shadow(24.dp, pillShape(), ambientColor = Color.Black.copy(alpha = 0.5f))
                .clip(pillShape()).background(Crudo.copy(alpha = 0.97f)).border(1.dp, Line, pillShape()).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { i, it ->
                val on = i == selected
                val bg by animateColorAsState(if (on) Charcoal else Color.Transparent, tween(Motion.ms(220)), label = "dockbg")
                val fg by animateColorAsState(if (on) Paper else Muted, tween(Motion.ms(220)), label = "dockfg")
                Row(
                    Modifier.height(48.dp).clip(pillShape()).background(bg)
                        .clickable(remember { MutableInteractionSource() }, null) { if (!on) hf.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) }
                        .animateContentSize(Motion.bouncy()).padding(horizontal = if (on) 16.dp else 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(it.icon, it.label, tint = fg, modifier = Modifier.size(22.dp))
                    if (on && Skin.dockLabels) { Spacer(Modifier.width(7.dp)); Text(it.label, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
                }
            }
        }
    }
}

/** Botón flotante redondo (añadir). */
@Composable
fun Fab6(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(60.dp).press(onClick = onClick).shadow(16.dp, roundShape(), ambientColor = ColGeneral, spotColor = ColGeneral)
            .clip(roundShape()).background(ColGeneral),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Paper, modifier = Modifier.size(28.dp)) }
}
