package com.gonzalo.gamerate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.atan2
import kotlin.math.roundToInt

@Composable
fun Ring(value: Int, color: Color, diameter: Dp, center: String? = null) {
    val shown by animateFloatAsState(value.toFloat(), tween(450), label = "anillo")
    val track = Line
    if (gaugeKind != GaugeKind.Ring) {
        FormGauge(shown, color, diameter, center ?: "$value")
        return
    }
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = (size.minDimension * 0.075f).coerceAtLeast(2.5.dp.toPx())
            val arcSize = Size(size.width - sw, size.height - sw)
            val topLeft = Offset(sw / 2, sw / 2)
            drawArc(color = track, startAngle = 120f, sweepAngle = 300f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round))
            if (shown > 0.5f) {
                drawArc(color = color, startAngle = 120f, sweepAngle = 300f * shown / 100f, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round))
            }
        }
        val big = diameter >= 70.dp
        Text(
            center ?: "$value", fontSize = (diameter.value * if (big) 0.34f else 0.3f).sp,
            fontFamily = if (big) Display else Body, fontWeight = if (big) FontWeight.Normal else FontWeight.SemiBold,
            color = Charcoal
        )
    }
}

/** Etiqueta de un anillo: punto de color y nombre discreto. */
@Composable
private fun GaugeLabel(label: String, color: Color, big: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        // Lo más grande que quepa entera: las largas («Jugabilidad») se encogen un poco en vez de cortarse.
        BoxWithConstraints(Modifier.weight(1f, fill = false)) {
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val sizes = if (big) listOf(15, 14, 13, 12, 11) else listOf(13, 12, 11, 10, 9)
            val maxW = constraints.maxWidth
            // Se mide con la misma letra que se dibuja (la del tema), para que el cálculo sea exacto.
            val base = androidx.compose.material3.LocalTextStyle.current
            val fs = remember(label, maxW, big, base) {
                sizes.firstOrNull { sz ->
                    measurer.measure(label, base.copy(fontSize = sz.sp, fontWeight = FontWeight.Medium), maxLines = 1, softWrap = false)
                        .size.width <= maxW
                } ?: sizes.last()
            }
            Text(label, color = Muted, fontWeight = FontWeight.Medium, fontSize = fs.sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun StaticGauge(label: String, value: Int, color: Color, diameter: Dp, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(value, color, diameter)
        GaugeLabel(label, color, diameter > 100.dp)
    }
}

/** Valor 0–100 según dónde toques del anillo (empieza abajo a la izquierda y gira en sentido horario). */
private fun angleToValue(x: Float, y: Float, w: Float, h: Float): Int {
    // Barras, bloques, estrellas…: de izquierda a derecha
    if (!gaugeIsRadial) return (((x - w * 0.05f) / (w * 0.9f)) * 100f).roundToInt().coerceIn(0, 100)
    val deg = Math.toDegrees(atan2((y - h / 2f).toDouble(), (x - w / 2f).toDouble())).toFloat()
    // Puntos: vuelta entera empezando arriba
    if (gaugeKind == GaugeKind.Pips) return (((deg + 90f + 360f) % 360f) / 360f * 100f).roundToInt().coerceIn(0, 100)
    var a = (deg - 120f + 720f) % 360f
    if (a > 300f) a = if (a < 330f) 300f else 0f
    return (a / 300f * 100f).roundToInt().coerceIn(0, 100)
}

/** Anillo editable: se arrastra con el dedo o se escribe el número debajo. */
@Composable
fun Gauge(label: String, value: Int, color: Color, diameter: Dp, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val haptic = LocalHapticFeedback.current
    val cb by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(value)
    fun set(v: Int) {
        if (v != current) {
            if (v / 5 != current / 5) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            cb(v)
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .pointerInput(Unit) {
                    detectTapGestures { o -> set(angleToValue(o.x, o.y, size.width.toFloat(), size.height.toFloat())) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        set(angleToValue(change.position.x, change.position.y, size.width.toFloat(), size.height.toFloat()))
                    }
                }
        ) {
            Ring(value, color, diameter)
        }
        GaugeLabel(label, color, diameter > 100.dp)
        BasicTextField(
            value = text,
            onValueChange = { t ->
                val f = t.filter { c -> c.isDigit() }.take(3)
                text = f
                f.toIntOrNull()?.let { n -> onChange(n.coerceIn(0, 100)) }
            },
            singleLine = true,
            textStyle = TextStyle(color = Charcoal, fontSize = 14.sp, textAlign = TextAlign.Center, fontFamily = Body),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(color),
            modifier = Modifier.width(48.dp).padding(top = 2.dp),
            decorationBox = { inner ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    inner()
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                }
            }
        )
    }
}

/** Chip de estado: contorno fino; relleno con su color cuando está elegido. */
@Composable
fun Chip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Text(chipLabel(text), color = chipInk(selected), fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, softWrap = false,
        modifier = Modifier.pop(onClick)
            .chipLook(selected, color)
            .padding(horizontal = 14.dp, vertical = 8.dp))
}

/** Chip sobrio: contorno fino apagado, tinta cuando está elegido. */
@Composable
fun SoftChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onPanel: Boolean = false, onClick: () -> Unit) {
    Text(
        chipLabel(text), color = chipInk(selected), fontWeight = FontWeight.Medium, fontSize = 13.sp,
        maxLines = 1,
        modifier = modifier.pop(onClick)
            .chipLook(selected, Charcoal, if (onPanel) Color.Transparent else Crudo)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    )
}

/** Botón redondo de icono. */
@Composable
fun IconPill(icon: ImageVector, desc: String, active: Boolean = false, onClick: () -> Unit) {
    if (Skin.revista) {
        Box(Modifier.pop(onClick).size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = if (active) ColGeneral else Charcoal, modifier = Modifier.size(22.dp))
        }
        return
    }
    Box(
        Modifier.pop(onClick).size(40.dp)
            .background(if (active) Charcoal else Color.Transparent, roundShape())
            .border(borderW.coerceIn(1.dp, 2.dp), if (active) Charcoal else borderC, roundShape()),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = if (active) Crudo else Charcoal, modifier = Modifier.size(20.dp))
    }
}

/** Botón con icono y texto: contorno fino, o relleno de tinta si es el principal. */
@Composable
fun ActionPill(icon: ImageVector, text: String, modifier: Modifier = Modifier, dark: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.pop(onClick).chipLook(dark, Charcoal)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = chipInk(dark), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(chipLabel(text), color = chipInk(dark), fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, softWrap = false)
    }
}

/** Botón de volver: flecha en un círculo de línea fina. */
@Composable
fun BackButton(onBack: () -> Unit) {
    if (Skin.revista) { RevistaBack(onBack); return }
    Box(
        Modifier.pop(onBack).size(40.dp).border(borderW.coerceIn(1.dp, 2.dp), borderC, roundShape()),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = Charcoal, modifier = Modifier.size(20.dp)) }
}

/** Texto pequeño en mayúsculas para encabezar grupos. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    if (Skin.revista) { Text(text.uppercase(), color = Charcoal, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = modifier); return }
    Text(text.uppercase(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, modifier = modifier)
}

/** Cabecera de pantalla (Atrio 6): volver, la sección pequeña encima y el título grande; botones a la derecha. */
@Composable
fun TopBar(back: String, onBack: () -> Unit, title: String, actions: @Composable RowScope.() -> Unit = {}) {
    if (Skin.revista) {
        // Revista: «← VOLVER», la sección en rojo y el título grande con filete grueso
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RevistaBack(onBack); Spacer(Modifier.weight(1f)); actions()
            }
            Text(back.uppercase(), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            FitTitle(title, 44.sp, 46.sp, letterSpacing = (-1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).background(Charcoal))
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BackRound(onBack = onBack)
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(back.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp, maxLines = 1)
            Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 31.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** Título que es a la vez selector («Películas · Series»): la opción elegida en grande y en tinta. */
@Composable
fun TitleTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val size by animateFloatAsState(if (on) 34f else 22f, Motion.bouncy(), label = "tt")
            val c by androidx.compose.animation.animateColorAsState(if (on) Charcoal else Muted, label = "ttc")
            Text(o, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size + 4).sp, color = c,
                modifier = Modifier.press { if (!on) onSelect(i) })
        }
    }
}

/** Buscador sobrio. */
@Composable
fun SearchPill(value: String, placeholder: String, modifier: Modifier = Modifier, onSearch: (() -> Unit)? = null, onChange: (String) -> Unit) {
    Row(
        // Revista: sin caja, solo un filete grueso debajo
        (if (Skin.revista) modifier.drawBehind { drawRect(Charcoal, androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())) }.padding(end = 6.dp)
        else modifier.background(Sand, shp(14.dp)).padding(start = 14.dp, end = 6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = if (Skin.revista) Charcoal else Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).padding(vertical = 11.dp)) {
            if (value.isEmpty()) Text(placeholder, color = Muted, fontSize = 14.sp, maxLines = 1)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = Charcoal, fontSize = 14.sp, fontFamily = Body),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch?.invoke() }),
                cursorBrush = SolidColor(Charcoal), modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.size(32.dp).clip(CircleShape).clickable { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Borrar", tint = Muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Tarjeta: superficie con línea fina y título opcional. */
@Composable
fun Panel(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (Skin.revista) {
        // Revista: sin caja; filete arriba y el título en versalitas con el cuadradito rojo
        Column(modifier.fillMaxWidth().surface(20.dp, Crudo).padding(top = 14.dp, bottom = 12.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(ColGeneral)); Spacer(Modifier.width(8.dp))
                    Text(title.uppercase(), fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 2.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
        return
    }
    Column(
        modifier.fillMaxWidth().surface(20.dp, Crudo).padding(18.dp)
    ) {
        if (title != null) {
            Text(title, fontFamily = Display, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/** Barra de progreso fina. */
@Composable
fun Bar(frac: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), tween(600), label = "barra")
    // Revista: todas las barras en tinta (sin colores por criterio)
    Box(modifier.fillMaxWidth().height(height).formBar(f, if (Skin.revista) Charcoal else color, Sand, height))
}

/** Portada pequeña redondeada. */
@Composable
fun MiniCover(url: String, w: Dp = 44.dp, modifier: Modifier = Modifier) {
    if (Skin.revista) return  // Revista: sin portadas
    AsyncImage(
        url, null,
        modifier.width(w).aspectRatio(2f / 3f).clip(shp(6.dp)).background(Sand).coverFrame(),
        contentScale = ContentScale.Crop
    )
}

/** Botón de pareja: gato dibujado; al activarlo, se rellena de tinta. */
@Composable
fun PartnerToggle(on: Boolean, onClick: () -> Unit) {
    val bg = if (on) Charcoal else Color.Transparent
    val fg = if (on) Crudo else Charcoal
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.pop { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
            .size(40.dp).background(bg, roundShape()).border(borderW.coerceIn(1.dp, 2.dp), if (on) Charcoal else borderC, roundShape()),
        contentAlignment = Alignment.Center
    ) {
        CatFace(fg, if (on) Charcoal else Paper, Modifier.size(22.dp))
    }
}

/** Cara de gato dibujada (orejas, cabeza, ojos, hocico y bigotes). */
@Composable
fun CatFace(fg: Color, bg: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ears = Path().apply {
            moveTo(w * 0.10f, h * 0.52f); lineTo(w * 0.14f, h * 0.06f); lineTo(w * 0.44f, h * 0.28f); close()
            moveTo(w * 0.90f, h * 0.52f); lineTo(w * 0.86f, h * 0.06f); lineTo(w * 0.56f, h * 0.28f); close()
        }
        drawPath(ears, fg)
        drawOval(fg, Offset(w * 0.07f, h * 0.22f), Size(w * 0.86f, h * 0.72f))
        drawCircle(bg, w * 0.065f, Offset(w * 0.34f, h * 0.55f))
        drawCircle(bg, w * 0.065f, Offset(w * 0.66f, h * 0.55f))
        val nose = Path().apply {
            moveTo(w * 0.46f, h * 0.70f); lineTo(w * 0.54f, h * 0.70f); lineTo(w * 0.50f, h * 0.75f); close()
        }
        drawPath(nose, bg)
        val sw = 1.dp.toPx()
        drawLine(fg, Offset(w * 0.02f, h * 0.66f), Offset(w * 0.22f, h * 0.70f), sw)
        drawLine(fg, Offset(w * 0.02f, h * 0.80f), Offset(w * 0.22f, h * 0.76f), sw)
        drawLine(fg, Offset(w * 0.98f, h * 0.66f), Offset(w * 0.78f, h * 0.70f), sw)
        drawLine(fg, Offset(w * 0.98f, h * 0.80f), Offset(w * 0.78f, h * 0.76f), sw)
    }
}

/** Colores de los criterios (2º a 5º anillo). */
fun critColors() = listOf(ColGfx, ColStory, ColMusic, ColGameplay)

/** Portada con su inicial detrás (se ve si no hay imagen o mientras carga). */
@Composable
fun Cover(url: String, name: String, modifier: Modifier, alpha: Float = 1f) {
    // Revista: sin portadas; el nombre en tipografía, como un lomo
    if (Skin.revista) {
        Box(modifier.background(Crudo).border(1.dp, Charcoal).padding(8.dp), contentAlignment = Alignment.BottomStart) {
            Text(name, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 18.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Box(modifier.coverFrame(), contentAlignment = Alignment.Center) {
        if (url.isBlank()) {
            Text(name.trim().take(1).uppercase(), fontFamily = Display, fontSize = 44.sp, color = Muted)
        } else {
            AsyncImage(url, name, Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, contentScale = ContentScale.Crop)
        }
    }
}

/** Sube cada vez que la pantalla vuelve a verse (al volver del lector, de otra app…): para releer lo que cambia fuera. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableStateOf(0) }
    val life = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(life) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }
    return tick
}
