package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Las fichas de la interfaz Revista: sin imágenes, el titular enorme y la página dividida en casillas con filetes,
 * como una revista de verdad (Guías | Nota, Opinión, Horas | Enlaces…). Cada casilla lleva su antetítulo.
 */

/** Antetítulo de casilla (con una acción a la derecha, opcional). */
@Composable
fun MagLabel(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) Text(action.uppercase(), color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
            modifier = Modifier.clickable(onClick = onAction).padding(4.dp))
    }
}

/** Casilla a todo lo ancho, con filete grueso arriba. */
@Composable
fun MagFull(label: String?, action: String? = null, onAction: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        // Sin antetítulo, lo de dentro (paneles de progreso, partes…) ya trae su propio filete
        if (label != null) Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
        Column(Modifier.fillMaxWidth().padding(top = if (label != null) 12.dp else 0.dp, bottom = 18.dp)) {
            if (label != null) MagLabel(label, action, onAction)
            content()
        }
    }
}

/** Dos casillas lado a lado, separadas por un filete vertical que llega de arriba abajo. */
@Composable
fun MagSplit(
    left: String, right: String,
    leftContent: @Composable ColumnScope.() -> Unit, rightContent: @Composable ColumnScope.() -> Unit,
    leftWeight: Float = 1f, rightWeight: Float = 1f,
    leftAction: Pair<String, () -> Unit>? = null, rightAction: Pair<String, () -> Unit>? = null
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Charcoal))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(Modifier.weight(leftWeight).padding(top = 12.dp, bottom = 18.dp, end = 12.dp)) {
                MagLabel(left, leftAction?.first, leftAction?.second); leftContent()
            }
            Box(Modifier.fillMaxHeight().width(1.5.dp).background(Charcoal))
            Column(Modifier.weight(rightWeight).padding(top = 12.dp, bottom = 18.dp, start = 12.dp)) {
                MagLabel(right, rightAction?.first, rightAction?.second); rightContent()
            }
        }
    }
}

/** Un enlace de texto de la casilla «Enlaces» (o cualquier acción): serif, con flecha. */
@Composable
fun MagLink(text: String, sub: String? = null, accent: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp)) {
        Text("$text →", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 21.sp, color = if (accent) ColGeneral else Charcoal)
        if (sub != null) Text(sub.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

/** Elegir una de pocas opciones en una casilla: mayúsculas pequeñas, la elegida en acento y subrayada. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MagChoice(options: List<String>, selected: Int, label: String? = null, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (label != null) Text(label.uppercase(), color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                Text(
                    o.uppercase(), color = if (on) ColGeneral else Charcoal, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                    modifier = Modifier.clickable(enabled = !on) { onPick(i) }.padding(vertical = 8.dp)
                        .drawBehind { if (on) drawRect(ColGeneral, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 6.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx())) }
                )
            }
        }
    }
}

/** Una línea dato · valor con puntos guía (como el índice de una revista). */
@Composable
fun MagFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, fontSize = 14.sp, color = Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Box(Modifier.weight(1f).height(12.dp).padding(horizontal = 6.dp).drawBehind {
            drawLine(Muted.copy(alpha = 0.6f), Offset(0f, size.height - 2), Offset(size.width, size.height - 2), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 6f)))
        })
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** La nota en cifras enormes y, debajo, los criterios con puntos guía. */
@Composable
fun MagScore(general: Int, scores: List<Int>, kind: Kind, big: Dp = 0.dp) {
    // En una casilla estrecha, la cifra encoge (nunca se parte en dos líneas)
    val k = (LocalCellFraction.current / 0.5f).coerceIn(0.55f, 1f)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$general", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (84 * k).sp, lineHeight = (80 * k).sp, letterSpacing = (-3 * k).sp, maxLines = 1, softWrap = false)
        Text("/100", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
    }
    if (kind.crit > 1) {
        Spacer(Modifier.height(6.dp))
        for (i in 1..kind.crit) MagFact(kind.labels.getOrElse(i) { "" }, "${scores.getOrElse(i) { 0 }}")
    }
}

/** El texto de la opinión con capitular (la primera letra grande y en rojo). */
@Composable
fun MagBody(text: String) {
    val t = text.trim()
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, color = ColGeneral, fontFamily = Display)) { append(t.take(1)) }
            append(t.drop(1))
        },
        fontFamily = Display, fontSize = 19.sp, lineHeight = 29.sp
    )
}

/** Las notas de Rotten Tomatoes en grande (crítica y público). */
@Composable
fun MagCritics(kind: Kind, id: Long, name: String) {
    val rt = rememberRt(kind, id, name)
    if (rt == null || (rt.tomato < 0 && rt.popcorn < 0)) { Text("Sin notas en Rotten Tomatoes.", color = Muted, fontStyle = FontStyle.Italic, fontFamily = Display, fontSize = 15.sp); return }
    val ctx = LocalContext.current
    Column(Modifier.clickable { if (rt.url.isNotBlank()) openUrl(ctx, rt.url) }) {
        if (rt.tomato >= 0) {
            Row(verticalAlignment = Alignment.CenterVertically) { TomatoIcon(rt.fresh, 20.dp); Spacer(Modifier.width(6.dp)); Text("${rt.tomato}%", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 40.sp) }
            Text("CRÍTICA", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        if (rt.popcorn >= 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { PopcornIcon(20.dp); Spacer(Modifier.width(6.dp)); Text("${rt.popcorn}%", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp) }
            Text("PÚBLICO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

/** Horas: las tuyas en grande y las habituales (historia, extras, completo) con puntos guía. */
@Composable
fun MagHours(mine: Int, main: Int, extra: Int, full: Int, msg: String = "") {
    if (mine > 0) {
        val k = (LocalCellFraction.current / 0.5f).coerceIn(0.6f, 1f)
        Text(shortHours(mine), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (40 * k).sp, lineHeight = (42 * k).sp, maxLines = 1, softWrap = false)
        Text("TUS HORAS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
    }
    if (main > 0) MagFact("Historia", shortHours(main))
    if (extra > 0) MagFact("+ Extras", shortHours(extra))
    if (full > 0) MagFact("Completo", shortHours(full))
    if (mine <= 0 && main <= 0 && extra <= 0 && full <= 0) Text(msg.ifBlank { "Sin horas todavía." }, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
}

/** Final de la ficha: «Editar» grande, como pie de página. */
@Composable
fun MagFooter(text: String = "Editar", onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Charcoal))
        Text("${text.uppercase()} →", color = ColGeneral, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
            modifier = Modifier.clickable(onClick = onClick).padding(vertical = 18.dp))
    }
}

/** Ficha de revista de algo ya valorado (modo lectura; «Editar» abre el diseño de la página). */
@Composable
fun RevistaGameRead(
    g: Game, kind: Kind, partnerName: String, theirs: String?, ttbMsg: String,
    onBack: () -> Unit, onFav: () -> Unit, onPartner: (() -> Unit)?,
    onExport: (Game, ExportMode) -> Unit, onRecommend: (() -> Unit)?,
    onDelete: (() -> Unit)? = null, onArt: ((hero: Boolean) -> Unit)? = null, onEdit: () -> Unit
) {
    val ctx = LocalContext.current
    var designing by remember(g.id) { mutableStateOf(false) }
    RevistaTopBar(onBack) {
        if (onPartner != null && !designing) Text("PAREJA", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.clickable(onClick = onPartner).padding(8.dp))
        if (!designing) Text(if (g.fav) "★" else "☆", color = if (g.fav) ColGeneral else Charcoal, fontSize = 18.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.clickable(onClick = onFav).padding(8.dp))
        Text(if (designing) "LISTO" else "EDITAR", color = if (designing) ColGeneral else Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
            modifier = Modifier.clickable { designing = !designing }.padding(8.dp))
    }
    Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(3.dp).background(Charcoal))
    Spacer(Modifier.height(14.dp))
    RevistaHeadline(g.name, listOfNotNull(kind.title, g.creator.ifBlank { null }).joinToString(" · "))
    val deck = listOfNotNull(
        g.platform.ifBlank { null },
        if (g.end > 0L) "terminado el ${fmtDay(g.end)}" else null
    ).joinToString(" · ")
    if (deck.isNotBlank()) Text(deck, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
    if (theirs != null) Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) { TheirReaction(partnerName, theirs) }
    if (designing) DesignHint(onData = onEdit, name = g.name, onDelete = onDelete, onArt = onArt, hasHero = kind.hasHero)
    Spacer(Modifier.height(14.dp))

    val defs = linkedMapOf<String, MagDef>()
    if (kind.isGame) defs["guias"] = MagDef("Guías") { GuidesSection(g.id, g.name, inCell = true) }
    defs["nota"] = MagDef("Nota") { MagScore(g.general, g.scores(), kind) }
    if (kind.isMovie || kind.isTv) defs["critica"] = MagDef("Crítica") { MagCritics(kind, g.id, g.name) }
    if (kind.isBook) defs["lectura"] = MagDef("Lectura") { BookFileBar(g.id, g.name) }
    if (kind.isBook) defs["subrayados"] = MagDef("Subrayados y notas") { BookNotesBox(g.id, g.name) }
    defs["opinion"] = MagDef("Opinión", "Escribir" to onEdit) {
        if (g.review.isNotBlank()) MagBody(g.review)
        else Text("Aún no has escrito nada.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp)
    }
    if (kind.isGame) defs["horas"] = MagDef("Horas") { MagHours(g.minutes, g.ttbMain, g.ttbExtra, g.ttbFull, ttbMsg) }
    defs["enlaces"] = MagDef("Enlaces") {
        if (kind.isGame) FichaPlayButtons(g.id, g.name, Modifier.padding(bottom = 6.dp))
        if (kind.hasSoundtrack) MagLink("Banda sonora", "YouTube Music") { openSoundtrack(ctx, g.name) }
        MagLink("Compartir") { onExport(g, ExportMode.Share) }
        // Exportar la ficha: un solo enlace y debajo el formato
        Text("Exportar", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 5.dp))
        MagChoice(listOf("JPG", "PDF"), -1) { onExport(g, if (it == 0) ExportMode.Jpg else ExportMode.Pdf) }
        if (onRecommend != null) MagLink("Recomendar", "a $partnerName", accent = true, onClick = onRecommend)
    }
    defs["datos"] = MagDef("Datos") { GameFacts(g, kind) }
    val defaults = when {
        kind.isGame -> magRows(listOf("guias", "nota"), listOf("opinion"), listOf("horas", "enlaces"), listOf("datos"))
        kind.isMovie || kind.isTv -> magRows(listOf("critica", "nota"), listOf("opinion"), listOf("datos", "enlaces"))
        kind.isBook -> magRows(listOf("datos", "nota"), listOf("lectura"), listOf("subrayados"), listOf("opinion"), listOf("enlaces"))
        else -> magRows(listOf("datos", "nota"), listOf("opinion"), listOf("enlaces"))
    }
    MagPage("v:${kind.id}:${g.id}", "tpl:v:${kind.id}", kind.title, defaults, defs, kind, g.id, g.name, designing)
    if (designing) MagFooter("Listo") { designing = false } else MagFooter { designing = true }
}

/** Aviso del modo edición: qué se puede hacer y el enlace a los datos (nota, horas, fechas…). */
@Composable
fun DesignHint(onData: () -> Unit, name: String = "", onDelete: (() -> Unit)? = null, onArt: ((hero: Boolean) -> Unit)? = null, hasHero: Boolean = true) {
    var sure by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).background(Sand).padding(12.dp)) {
        // La portada y el fondo de la ficha (de internet, de tus archivos, de la galería o de otro dispositivo tuyo)
        if (onArt != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton({ onArt(false) }, shape = shp(4.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Cambiar portada")
                }
                if (hasHero) OutlinedButton({ onArt(true) }, shape = shp(4.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Wallpaper, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Cambiar fondo")
                }
            }
        }
        Text("Toca una casilla para elegir qué va en ella (o texto libre, o una imagen). Arrastra el círculo de la línea para moverla.",
            fontSize = 13.sp, lineHeight = 18.sp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("DATOS Y NOTA →", color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.clickable(onClick = onData).padding(vertical = 4.dp))
            Spacer(Modifier.weight(1f))
            // Por si lo añadiste por error
            if (onDelete != null) Text("ELIMINAR DE LA APP", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.clickable { sure = true }.padding(vertical = 4.dp))
        }
    }
    if (sure && onDelete != null) AlertDialog(
        onDismissRequest = { sure = false },
        title = { Text(if (name.isNotBlank()) "¿Eliminar «$name»?" else "¿Eliminar esta ficha?", fontFamily = Display) },
        text = { Text("Se quita de tu colección con sus notas y apuntes, también en tus otros dispositivos. No se puede deshacer.") },
        confirmButton = { TextButton({ sure = false; onDelete() }) { Text("Eliminar", color = ColGeneral, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton({ sure = false }) { Text("Cancelar") } },
        containerColor = Paper, shape = shp(4.dp)
    )
}

/** Los datos de la ficha con puntos guía. */
@Composable
fun GameFacts(g: Game, kind: Kind) {
    var any = false
    if (g.creator.isNotBlank()) { MagFact(kind.creatorWord, g.creator); any = true }
    if (g.platform.isNotBlank()) { MagFact(kind.platformWord, g.platform); any = true }
    if (kind.isBook && g.pages > 0) { MagFact("Páginas", "${g.pages}"); any = true }
    if (g.start > 0L) { MagFact("Empezado", fmtDay(g.start)); any = true }
    if (g.end > 0L) { MagFact("Terminado", fmtDay(g.end)); any = true }
    if (!any) Text("Sin datos.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp)
}
