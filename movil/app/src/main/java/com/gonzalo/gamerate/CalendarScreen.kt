package com.gonzalo.gamerate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** s y e: primer y último día que ocupa en el calendario (días desde 1970). */
class CalItem(
    val id: Long, val name: String, val cover: String,
    val s: Long, val e: Long, val realEnd: Long, val isPlay: Boolean,
    val rated: Boolean = false, val score: Int = 0,
    /** De qué categoría es (en el Diario con «Todo» se mezclan). */
    val kind: Int = -1
)

fun calItems(games: List<Game>, plays: List<Play>, kind: Int = -1): List<CalItem> {
    val t = today()
    val out = ArrayList<CalItem>()
    fun add(id: Long, name: String, cover: String, start: Long, end: Long, isPlay: Boolean, score: Int = -1) {
        if (start <= 0L && end <= 0L) return
        val s = if (start > 0L) start else end
        val e = if (end > 0L) maxOf(end, s) else maxOf(t, s)
        out.add(CalItem(id, name, cover, s, e, end, isPlay, score >= 0, maxOf(score, 0), kind))
    }
    plays.forEach { add(it.id, it.name, it.cover, it.start, it.end, true) }
    games.forEach { add(it.id, it.name, it.cover, it.start, it.end, false, it.general) }
    return out
}

private val DAY_FMT = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))

fun rangeText(c: CalItem): String {
    val a = LocalDate.ofEpochDay(c.s).format(DAY_FMT)
    val days = c.e - c.s + 1
    return if (c.realEnd > 0L) {
        a + " → " + LocalDate.ofEpochDay(c.e).format(DAY_FMT) + " · " + days + " d"
    } else {
        a + " → hoy · " + days + " d"
    }
}

/** «14 h 20 min», «45 min». */
private fun durMs(ms: Long): String = readTime((ms + 30_000L) / 60_000L)

@Composable
fun CalendarScreen(
    kind: Kind, games: List<Game>, plays: List<Play>, partner: PartnerSide?,
    onMenu: () -> Unit, heading: String? = null, top: @Composable () -> Unit = {},
    /** Lo que se enseña ya preparado (el Diario, con varias categorías): lo tuyo y lo de tu pareja. */
    items: List<CalItem>? = null, itemsP: List<CalItem>? = null,
    /** Lo leído con el lector: día → libro → ms (solo lo tuyo). */
    leido: Map<Long, Map<Long, Long>> = emptyMap(), nombreLibro: (Long) -> String = { "" }, onOpenLeido: (Long) -> Unit = {},
    onOpen: (CalItem) -> Unit
) {
    var sel by remember { mutableStateOf<Long?>(null) }
    val showPartner = Mode.calP
    val useP = showPartner && partner != null
    val cal = remember(games, plays, partner, useP, items, itemsP) {
        if (items != null) (if (useP) itemsP ?: emptyList() else items)
        else if (useP) calItems(partner!!.games, partner.plays) else calItems(games, plays)
    }
    val lee = if (useP) emptyMap() else leido
    var ym by remember { mutableStateOf(YearMonth.now()) }

    val monthName = ym.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("es", "ES"))
    val monthLen = ym.lengthOfMonth()
    val lead = ym.atDay(1).dayOfWeek.value - 1
    val weeks = (lead + monthLen + 6) / 7
    val todayDay = today()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = if (heading != null) dockSpace(8.dp) else 8.dp)
    ) {
        if (heading != null) BigHeader(heading) {
            if (partner != null) PartnerToggle(showPartner) { withReveal { Mode.calP = !Mode.calP } }
        } else TopBar(kind.title, onMenu, "Calendario") {
            if (partner != null) PartnerToggle(showPartner) { withReveal { Mode.calP = !Mode.calP } }
        }
        top()

        // Mes: título en cursiva, año pequeño y flechas en círculos finos.
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).clickable { ym = YearMonth.now() }.padding(start = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    monthName.replaceFirstChar { ch -> ch.uppercase() },
                    fontSize = 30.sp, fontFamily = Display, fontStyle = FontStyle.Italic, color = Charcoal
                )
                Text(
                    "" + ym.year, fontSize = 13.sp, color = Muted,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                )
            }
            MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mes anterior") { ym = ym.minusMonths(1) }
            Spacer(Modifier.width(8.dp))
            MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Mes siguiente") { ym = ym.plusMonths(1) }
        }

        // Lo del mes en una línea: terminados, empezados y el tiempo leído
        run {
            val d0 = ym.atDay(1).toEpochDay(); val d1 = ym.atEndOfMonth().toEpochDay()
            val fin = cal.count { it.realEnd in d0..d1 }
            val emp = cal.count { it.s in d0..d1 }
            val ms = (d0..d1).sumOf { d -> lee[d]?.values?.sum() ?: 0L }
            val dias = (d0..d1).count { d -> (lee[d]?.values?.sum() ?: 0L) >= 60_000L }
            val partes = listOfNotNull(
                if (fin > 0) "$fin terminado${if (fin == 1) "" else "s"}" else null,
                if (emp > 0) "$emp empezado${if (emp == 1) "" else "s"}" else null,
                if (ms >= 60_000L) "${durMs(ms)} leyendo · $dias día${if (dias == 1) "" else "s"}" else null
            )
            Text(if (partes.isEmpty()) "Nada este mes" else partes.joinToString(" · "), color = Muted, fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { d ->
                Text(
                    d, Modifier.weight(1f),
                    textAlign = TextAlign.Center, fontSize = 11.sp, letterSpacing = 1.2.sp, color = Muted,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.size(4.dp))

        // Al cambiar de categoría (o a lo de tu pareja), el mes se funde con lo nuevo en lugar de saltar
        val gridKey = kind.id to useP
        val snaps = remember { mutableMapOf<Pair<Int, Boolean>, List<CalItem>>() }
        snaps[gridKey] = cal
        // Deslizar de lado cambia de mes
        var arrastre by remember { mutableStateOf(0f) }
        val umbral = with(androidx.compose.ui.platform.LocalDensity.current) { 70.dp.toPx() }
        val desliza = Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { arrastre = 0f },
                onDragEnd = { if (arrastre < -umbral) ym = ym.plusMonths(1) else if (arrastre > umbral) ym = ym.minusMonths(1); arrastre = 0f },
                onDragCancel = { arrastre = 0f }
            ) { _, dx -> arrastre += dx }
        }
        androidx.compose.animation.Crossfade(gridKey, Modifier.fillMaxWidth().weight(1f).then(desliza), androidx.compose.animation.core.tween(Motion.ms(220)), label = "diario") { gk ->
        val cal = snaps[gk] ?: cal
        Column(Modifier.fillMaxSize()) {
            for (w in 0 until weeks) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until 7) {
                        val dayNum = w * 7 + c - lead + 1
                        val cellMod = Modifier.weight(1f).fillMaxHeight()
                        if (dayNum < 1 || dayNum > monthLen) {
                            Box(cellMod)
                        } else {
                            val ep = ym.atDay(dayNum).toEpochDay()
                            val items = cal.filter { it.s <= ep && ep <= it.e }
                            val ms = lee[ep]?.values?.sum() ?: 0L
                            DayCell(
                                dayNum, ep, items, ep == todayDay,
                                cellMod.appear(220 + w * 70 + c * 15).then(
                                    if (items.isNotEmpty() || ms >= 60_000L) Modifier.pop { sel = ep } else Modifier
                                ), leido = ms
                            )
                        }
                    }
                }
            }
        }
        }
    }
    val d = sel
    if (d != null) {
        val items = cal.filter { it.s <= d && d <= it.e }
        val dia = lee[d].orEmpty().filterValues { it >= 60_000L }.entries.sortedByDescending { it.value }.map { (id, ms) -> Triple(id, nombreLibro(id), ms) }
        DayDialog(d, items, if (useP) partner?.name else null, { c -> sel = null; onOpen(c) }, dia, { id -> sel = null; onOpenLeido(id) }) { sel = null }
    }
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.pop(onClick).size(36.dp).then(if (Skin.revista) Modifier else Modifier.border(1.dp, Line, CircleShape)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Charcoal, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun DayDialog(
    ep: Long, items: List<CalItem>, partnerName: String?, onOpen: (CalItem) -> Unit,
    leido: List<Triple<Long, String, Long>> = emptyList(), onOpenLeido: (Long) -> Unit = {}, onDismiss: () -> Unit
) {
    val title = LocalDate.ofEpochDay(ep).format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "ES")))
        .replaceFirstChar { it.uppercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontFamily = Display)
                if (partnerName != null) Text("Calendario de $partnerName", fontSize = 13.sp, color = Muted)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).pop { onOpen(c) }
                            .border(1.dp, Line, shp(14.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniCover(c.cover, 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(rangeText(c), color = Muted, fontSize = 12.sp)
                        }
                        if (c.rated) Mark(c, 0L, Modifier)
                    }
                }
                // Lo que leíste ese día con el lector
                if (leido.isNotEmpty()) {
                    Text("LEÍSTE ${durMs(leido.sumOf { it.third }).uppercase()}", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp,
                        modifier = Modifier.padding(top = if (items.isEmpty()) 0.dp else 14.dp, bottom = 4.dp))
                    leido.forEach { (id, nm, ms) ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = nm.isNotBlank()) { onOpenLeido(id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(nm.ifBlank { "Un libro que ya no está" }, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = Display, fontSize = 16.sp)
                            Text(durMs(ms), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar") } },
        shape = shp(24.dp)
    )
}

private val TriTop = GenericShape { size, _ ->
    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(0f, size.height); close()
}
private val TriBottom = GenericShape { size, _ ->
    moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close()
}

@Composable
fun Mark(c: CalItem, ep: Long, modifier: Modifier) {
    if (c.rated) {
        Box(
            modifier.size(22.dp).background(Crudo, CircleShape).border(1.dp, Line, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("" + c.score, color = Charcoal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    } else if (c.realEnd > 0L && c.e == ep) {
        Text(
            "✓",
            modifier = modifier.background(Color(0x99000000), shp(8.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DayCell(day: Int, ep: Long, cs: List<CalItem>, isToday: Boolean, modifier: Modifier, leido: Long = 0L) {
    // Los días que leíste con el lector: una raya del acento (más gruesa cuanto más leíste: hasta una hora)
    val lee = leido >= 60_000L
    val raya = if (lee) (1.5f + 2.5f * (leido.coerceAtMost(3_600_000L) / 3_600_000f)) else 0f
    if (Skin.revista) {
        // Revista: sin cajas; filete fino arriba, el número en la letra de titulares (hoy, en rojo) y el título debajo
        Column(modifier.padding(horizontal = 2.dp).drawBehind {
            drawLine(if (isToday) ColGeneral else Charcoal.copy(alpha = 0.35f), Offset(0f, 0f), Offset(size.width, 0f), if (isToday) 3.dp.toPx() else 1.dp.toPx())
        }.padding(top = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$day", color = when { isToday -> ColGeneral; cs.isEmpty() && !lee -> Muted; else -> Charcoal }, fontFamily = Display,
                    fontWeight = if (isToday || cs.isNotEmpty() || lee) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                if (lee) Box(Modifier.padding(start = 4.dp).weight(1f).height(raya.dp).background(ColGeneral.copy(alpha = 0.75f)))
            }
            if (cs.isNotEmpty()) {
                // Lo que terminaste ese día, con su portada (como en el widget del Diario); si no, el título
                val fin = cs.firstOrNull { it.realEnd > 0L && it.e == ep && it.cover.isNotBlank() }
                if (fin != null) Box(Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp, bottom = 3.dp).clip(shp(5.dp))) {
                    AsyncImage(fin.cover, fin.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (cs.size > 1) Text("+${cs.size - 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomEnd).background(ColGeneral, shp(4.dp)).padding(horizontal = 4.dp))
                } else {
                    Text(cs[0].name, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (cs.size > 1) Text("+${cs.size - 1}", color = ColGeneral, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }
    val shape = shp(10.dp)
    Column(
        modifier.padding(2.dp).clip(shape).background(UiPanel)
            .border(if (isToday) 1.5.dp else 1.dp, if (isToday) Charcoal else Line, shape),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // El número va encima de la portada, sin taparla.
        Row(Modifier.padding(top = 3.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "" + day,
                color = if (cs.isEmpty() && !isToday && !lee) Muted else Charcoal,
                fontSize = 11.sp,
                fontWeight = if (isToday || lee) FontWeight.SemiBold else FontWeight.Normal
            )
            if (lee) Box(Modifier.padding(start = 3.dp).size(5.dp).background(ColGeneral, CircleShape))
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 2.dp)) {
            if (cs.isNotEmpty() && Skin.revista) {
                // Revista: sin portadas; el título en letra pequeña y cuántos más hay
                Column(Modifier.fillMaxSize()) {
                    Text(cs[0].name, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (cs.size > 1) Text("+${cs.size - 1}", color = ColGeneral, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            } else if (cs.isNotEmpty()) {
                Box(Modifier.fillMaxSize().clip(shp(7.dp))) {
                    if (cs.size == 1) {
                        AsyncImage(cs[0].cover, cs[0].name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        AsyncImage(
                            cs[0].cover, cs[0].name, Modifier.fillMaxSize().clip(TriTop),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            cs[1].cover, cs[1].name, Modifier.fillMaxSize().clip(TriBottom),
                            contentScale = ContentScale.Crop
                        )
                        Canvas(Modifier.fillMaxSize()) {
                            drawLine(UiPanel, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 3.dp.toPx())
                        }
                    }
                    if (cs.size == 1) {
                        Mark(cs[0], ep, Modifier.align(Alignment.BottomEnd).padding(3.dp))
                    } else {
                        Mark(cs[1], ep, Modifier.align(Alignment.BottomEnd).padding(3.dp))
                        Mark(cs[0], ep, Modifier.align(Alignment.TopStart).padding(3.dp))
                    }
                    if (cs.size > 2) {
                        Text(
                            "+" + (cs.size - 2),
                            modifier = Modifier.align(Alignment.BottomStart).padding(3.dp)
                                .background(Color(0x99000000), shp(8.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
