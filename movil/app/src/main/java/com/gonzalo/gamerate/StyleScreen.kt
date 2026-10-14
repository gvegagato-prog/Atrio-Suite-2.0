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

private enum class StyleTab(val title: String) { Mixes("Combinaciones"), Forms("Forma"), Gauges("Notas"), Colors("Colores"), Titles("Títulos"), Text("Texto"), Size("Tamaño"), Back("Fondo"), Icon("Icono") }

/** Nombre corto de lo elegido ahora (para la tarjeta de Ajustes). */
fun skinSummary(): String = Skin.currentStyle()?.name
    ?: ("${Skin.palette.name} · ${Skin.display} / ${Skin.body}".replace("Atrio / Atrio", "letras de Atrio") +
        " · ${Skin.form.name}" + (Skin.gauge?.let { " · notas: ${GAUGE_NAMES[it]?.first}" } ?: ""))

/** Tarjeta de Ajustes → Apariencia que abre la pantalla de Estilo. */
@Composable
fun StyleEntry(themeMode: Int, onTheme: (Int) -> Unit) {
    val t = Skin.tones
    Row(
        Modifier.fillMaxWidth().clip(shp(20.dp))
            .background(Brush.linearGradient(listOf(Color(t.general).copy(alpha = 0.22f), Color(t.gameplay).copy(alpha = 0.18f), Color(t.gfx).copy(alpha = 0.14f))))
            .border(1.dp, Line, shp(20.dp)).clickable { Skin.open = true }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Palette, null, tint = Charcoal, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Estilo", fontFamily = Display, fontSize = 22.sp)
            Text(skinSummary(), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${STYLES.size} combinaciones · ${PALETTES.size} paletas · ${DISPLAY_FONTS.size + BODY_FONTS.size} letras · ${AppIcon.ALL.size} iconos", color = Muted, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            listOf(t.general, t.gfx, t.story, t.music, t.gameplay).forEach { c ->
                Box(Modifier.size(18.dp).background(Color(c), CircleShape).border(2.dp, Crudo, CircleShape))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StyleScreen(themeMode: Int, onTheme: (Int) -> Unit, onLook: (Look) -> Unit = {}, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(StyleTab.Mixes) }
    fun save() = Skin.save(ctx)
    // Capa a pantalla completa por encima de toda la app (la pone MainActivity): atrás la cierra
    androidx.activity.compose.BackHandler { onClose() }
    run {
        // Los toques no pasan a la pantalla de debajo
        Column(Modifier.fillMaxSize().background(Paper)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClose)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Overline("Ajustes")
                    Text("Estilo", fontFamily = Display, fontSize = 28.sp)
                }
                // Sorpréndeme: todo al azar, con el círculo de cambio
                ActionPill(Icons.Rounded.Casino, "Sorpréndeme") { withReveal { Skin.surprise(); save() } }
            }
            Preview(Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeNames.forEachIndexed { i, n -> SoftChip(n, themeMode == i) { onTheme(i) } }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StyleTab.entries.forEach { t -> SoftChip(t.title, tab == t) { tab = t } }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    StyleTab.Mixes -> Grid(2) {
                        items(STYLES, key = { it.id }) { s ->
                            MixCard(s, Skin.currentStyle()?.id == s.id) { withReveal { Skin.apply(s); save() } }
                        }
                    }
                    StyleTab.Forms -> Grid(2) {
                        item(span = { GridItemSpan(2) }) {
                            Text("Cómo se dibuja todo: notas, tarjetas, botones, portadas y el fondo. Cada combinación trae la suya; aquí la cambias sin tocar colores ni letras.",
                                color = Muted, fontSize = 13.sp)
                        }
                        items(FORMS, key = { it.id }) { f -> FormCard(f, Skin.form.id == f.id) { withReveal { Skin.form = f; save() } } }
                    }
                    StyleTab.Gauges -> Grid(2) {
                        item(span = { GridItemSpan(2) }) {
                            Text("Cómo se ven y se tocan las notas, aparte de la forma: mezcla, por ejemplo, tarjetas de Píxel con estrellas.",
                                color = Muted, fontSize = 13.sp)
                        }
                        item(span = { GridItemSpan(2) }) {
                            GaugeCard(null, "La de la forma", "Ahora: " + (GAUGE_NAMES[Skin.form.gauge]?.first ?: ""), Skin.form.gauge, Skin.gauge == null) {
                                withReveal { Skin.gauge = null; save() }
                            }
                        }
                        items(GaugeKind.entries.toList(), key = { it.name }) { g ->
                            val (n, d) = GAUGE_NAMES[g] ?: (g.name to "")
                            GaugeCard(g, n, d, g, Skin.gauge == g) { withReveal { Skin.gauge = g; save() } }
                        }
                    }
                    StyleTab.Back -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Camaleón", fontWeight = FontWeight.SemiBold)
                                Text("Al abrir una ficha, la app se tiñe con el color de su portada y se abre en círculo desde donde tocaste. Al salir vuelve tu paleta.",
                                    color = Muted, fontSize = 13.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            androidx.compose.material3.Switch(Skin.chameleon, { Skin.chameleon = it; save() })
                        }
                        Spacer(Modifier.height(18.dp))
                        BackgroundPicker(onLook)
                    }
                    StyleTab.Colors -> Grid(2) {
                        PALETTES.groupBy { it.group }.forEach { (g, list) ->
                            item(span = { GridItemSpan(2) }) { Overline(if (g == "Editores") "De editores de código (colores oficiales)" else g, Modifier.padding(top = 8.dp)) }
                            items(list, key = { it.id }) { p -> PaletteCard(p, Skin.palette.id == p.id) { withReveal { Skin.palette = p; save() } } }
                        }
                    }
                    StyleTab.Titles -> Grid(1) {
                        item { FontRow("Atrio", "Fraunces, la de siempre", AtrioDisplay, Skin.display == "Atrio", big = true) { Skin.display = "Atrio"; save() } }
                        items(DISPLAY_FONTS, key = { it.name }) { f -> FontRow(f.name, null, Skin.family(f.name)!!, Skin.display == f.name, big = true) { Skin.display = f.name; save() } }
                    }
                    StyleTab.Text -> Grid(1) {
                        item { FontRow("Atrio", "Inter, la de siempre", AtrioBody, Skin.body == "Atrio", big = false) { Skin.body = "Atrio"; save() } }
                        items(BODY_FONTS, key = { it.name }) { f -> FontRow(f.name, null, Skin.family(f.name)!!, Skin.body == f.name, big = false) { Skin.body = f.name; save() } }
                    }
                    StyleTab.Size -> Column(Modifier.padding(16.dp)) {
                        Text("Tamaño del texto", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TEXT_SCALES.forEach { (v, n) -> SoftChip(n, Skin.textScale == v) { Skin.textScale = v; save() } }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Se suma al tamaño que tengas en Android. Todo se recoloca solo.", color = Muted, fontSize = 13.sp)
                    }
                    StyleTab.Icon -> IconPicker()
                }
            }
            Text(
                "Las letras se bajan de Google Fonts la primera vez que las eliges (hace falta conexión). Sin los servicios de Google, se ve la letra del sistema.",
                color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun Grid(cols: Int, content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        GridCells.Fixed(cols), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content
    )
}

/** Muestra de cómo queda: una ficha de mentira con lo elegido. */
@Composable
private fun Preview(modifier: Modifier) {
    val t = Skin.tones
    Column(modifier.fillMaxWidth().surface(22.dp, Crudo).padding(16.dp)) {
        Overline("Jugando ahora")
        Text("Clair Obscur: Expedition 33", fontFamily = Display, fontSize = 24.sp, lineHeight = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Acto II · 34 h jugadas · cada partida se apunta sola para valorarla.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(88 to t.general, 74 to t.gfx, 92 to t.story, 61 to t.music).forEach { (v, c) -> Ring(v, Color(c), 48.dp) }
            Spacer(Modifier.weight(1f))
            ActionPill(Icons.Rounded.Check, "Jugar", dark = true) {}
        }
    }
}

@Composable
private fun Selected(on: Boolean) {
    if (on) Box(Modifier.size(24.dp).background(Charcoal, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Check, null, tint = Paper, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MixCard(s: SkinStyle, on: Boolean, onClick: () -> Unit) {
    val p = PALETTES.firstOrNull { it.id == s.palette } ?: PALETTES[0]
    val tn = if (Mode.dark) p.dark else p.light
    val disp: FontFamily = Skin.family(s.display) ?: AtrioDisplay
    val body: FontFamily = Skin.family(s.body) ?: AtrioBody
    Box(
        Modifier.fillMaxWidth().height(150.dp).clip(shp(20.dp)).background(tn.cPaper)
            .border(if (on) 3.dp else 1.dp, if (on) Charcoal else Line, shp(20.dp)).clickable { onClick() }.padding(12.dp)
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(tn.general, tn.gfx, tn.story, tn.music, tn.gameplay).forEach { c -> Box(Modifier.size(12.dp).background(Color(c), CircleShape)) }
            }
            Spacer(Modifier.weight(1f))
            Text(s.name, fontFamily = disp, color = tn.cInk, fontSize = 21.sp, lineHeight = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(s.desc, fontFamily = body, color = tn.cMuted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Forma: " + (FORMS.firstOrNull { it.id == s.form }?.name ?: ""), fontFamily = body, color = tn.cInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.align(Alignment.TopEnd)) { Selected(on) }
    }
}

@Composable
private fun PaletteCard(p: Palette, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(shp(18.dp)).border(if (on) 3.dp else 1.dp, if (on) Charcoal else Line, shp(18.dp))
            .clickable { onClick() }
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            for (tn in listOf(p.light, p.dark)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(tn.cPaper).padding(8.dp)) {
                    Box(Modifier.fillMaxSize().clip(shp(10.dp)).background(tn.cCrudo).padding(6.dp)) {
                        Column {
                            Box(Modifier.width(34.dp).height(5.dp).background(tn.cInk, shp(3.dp)))
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.width(22.dp).height(4.dp).background(tn.cMuted, shp(3.dp)))
                            Spacer(Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(tn.general, tn.gfx, tn.story, tn.music).forEach { c -> Box(Modifier.size(7.dp).background(Color(c), CircleShape)) }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Crudo).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Selected(on)
        }
    }
}

@Composable
private fun FontRow(name: String, sub: String?, family: FontFamily, on: Boolean, big: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(shp(16.dp)).background(if (on) Sand else Crudo)
            .border(1.dp, if (on) Charcoal else Line, shp(16.dp)).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (big) "Jugando ahora" else "Cada partida se apunta sola para valorarla después.",
                fontFamily = family, fontSize = if (big) 26.sp else 16.sp, lineHeight = if (big) 30.sp else 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(name + (sub?.let { " · $it" } ?: ""), color = Muted, fontSize = 12.sp)
        }
        Selected(on)
    }
}

@Composable
private fun IconPicker() {
    val ctx = LocalContext.current
    var cur by remember { mutableStateOf(AppIcon.current(ctx)) }
    Column {
        Text(
            "El icono de la app en el móvil. Al cambiarlo, algunos móviles quitan el de la pantalla de inicio: vuelve a ponerlo desde el cajón de aplicaciones.",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)
        )
        Grid(3) {
            items(AppIcon.ALL, key = { it.first }) { (id, n) ->
                val bmp = remember(id) {
                    try { ctx.getDrawable(AppIcon.drawableId(ctx, id))?.toBitmap(192, 192)?.asImageBitmap() } catch (e: Exception) { null }
                }
                Column(
                    Modifier.clip(shp(18.dp)).border(if (cur == id) 3.dp else 1.dp, if (cur == id) Charcoal else Line, shp(18.dp))
                        .clickable { if (cur != id) { AppIcon.set(ctx, id); cur = id } }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (bmp != null) Image(bmp, n, Modifier.size(64.dp).clip(CircleShape))
                    Spacer(Modifier.height(6.dp))
                    Text(n, fontSize = 13.sp, fontWeight = if (cur == id) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Muestra de una forma: su marcador de nota, una esquina de tarjeta y un botón. */
@Composable
private fun FormCard(f: Form, on: Boolean, onClick: () -> Unit) {
    val t = Skin.tones
    val sh = shpOf(f, 18.dp)
    Column(
        Modifier.fillMaxWidth().clip(sh).background(Crudo)
            .border(if (on) 3.dp else f.border.coerceAtLeast(1.dp), if (on || f.inkBorder) Charcoal else Line, sh)
            .clickable { onClick() }.padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormGauge(78f, Color(t.gfx), 58.dp, "78", f.gauge)
            Spacer(Modifier.weight(1f))
            Selected(on)
        }
        Spacer(Modifier.height(8.dp))
        Text(f.name, fontFamily = Display, fontSize = 19.sp)
        Text(f.desc, color = Muted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Muestra de un marcador de notas. */
@Composable
private fun GaugeCard(g: GaugeKind?, name: String, desc: String, kind: GaugeKind, on: Boolean, onClick: () -> Unit) {
    val t = Skin.tones
    Row(
        Modifier.fillMaxWidth().clip(shp(18.dp)).background(Crudo)
            .border(if (on) 3.dp else 1.dp, if (on) Charcoal else Line, shp(18.dp))
            .clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FormGauge(if (g == null) 64f else 78f, Color(if (g == null) t.story else t.gfx), 56.dp, if (g == null) "64" else "78", kind)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = Display, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(desc, color = Muted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Selected(on)
    }
}

/** Fondo de las pantallas: dibujo, intensidad y colores (antes estaba en Ajustes). */
@Composable
private fun BackgroundPicker(onLook: (Look) -> Unit) {
    val look = Mode.look
    Text("Dibujo del fondo", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(5, 4, 0, 1, 2, 3).forEach { i ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pop { onLook(look.copy(pattern = i)) }) {
                Box(
                    Modifier.size(64.dp, 48.dp).clip(shp(12.dp))
                        .pattern(i, Color(look.menu), look.intensity)
                        .border(if (look.pattern == i) 3.dp else 1.dp, if (look.pattern == i) Charcoal else Sand, shp(12.dp))
                )
                Spacer(Modifier.height(4.dp))
                Text(PatternNames[i], fontSize = 12.sp, fontWeight = if (look.pattern == i) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Intensidad", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IntensityNames.forEachIndexed { i, n -> SoftChip(n, look.intensity == i) { onLook(look.copy(intensity = i)) } }
    }
    Spacer(Modifier.height(12.dp))
    ColorRow("Menú principal", look.menu) { onLook(look.copy(menu = it)) }
    ColorRow(GAMES.title, look.games) { onLook(look.copy(games = it)) }
    ColorRow(BOOKS.title, look.books) { onLook(look.copy(books = it)) }
    Spacer(Modifier.height(6.dp))
    androidx.compose.material3.TextButton({ onLook(Look()) }) { Text("Volver al fondo por defecto", color = Muted) }
}
