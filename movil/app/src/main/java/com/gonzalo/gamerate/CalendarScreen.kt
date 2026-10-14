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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
    val rated: Boolean = false, val score: Int = 0
)

fun calItems(games: List<Game>, plays: List<Play>): List<CalItem> {
    val t = today()
    val out = ArrayList<CalItem>()
    fun add(id: Long, name: String, cover: String, start: Long, end: Long, isPlay: Boolean, score: Int = -1) {
        if (start <= 0L && end <= 0L) return
        val s = if (start > 0L) start else end
        val e = if (end > 0L) maxOf(end, s) else maxOf(t, s)
        out.add(CalItem(id, name, cover, s, e, end, isPlay, score >= 0, maxOf(score, 0)))
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

@Composable
fun CalendarScreen(
    kind: Kind, games: List<Game>, plays: List<Play>, partner: PartnerSide?,
    onMenu: () -> Unit, heading: String? = null, top: @Composable () -> Unit = {}, onOpen: (CalItem) -> Unit
) {
    var sel by remember { mutableStateOf<Long?>(null) }
    val showPartner = Mode.calP
    val useP = showPartner && partner != null
    val cal = remember(games, plays, partner, useP) {
        if (useP) calItems(partner!!.games, partner.plays) else calItems(games, plays)
    }
    var ym by remember { mutableStateOf(YearMonth.now()) }

    val monthName = ym.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("es", "ES"))
    val monthLen = ym.lengthOfMonth()
    val lead = ym.atDay(1).dayOfWeek.value - 1
    val weeks = (lead + monthLen + 6) / 7
    val todayDay = today()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = if (heading != null) 104.dp else 8.dp)
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

        Column(Modifier.fillMaxWidth().weight(1f)) {
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
                            DayCell(
                                dayNum, ep, items, ep == todayDay,
                                cellMod.appear(220 + w * 70 + c * 15).then(
                                    if (items.isNotEmpty()) Modifier.pop { sel = ep } else Modifier
                                )
                            )
                        }
                    }
                }
            }
        }
    }
    val d = sel
    if (d != null) {
        val items = cal.filter { it.s <= d && d <= it.e }
        DayDialog(d, items, if (useP) partner?.name else null, { c -> sel = null; onOpen(c) }) { sel = null }
    }
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.pop(onClick).size(36.dp).border(1.dp, Line, CircleShape),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Charcoal, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun DayDialog(ep: Long, items: List<CalItem>, partnerName: String?, onOpen: (CalItem) -> Unit, onDismiss: () -> Unit) {
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
fun DayCell(day: Int, ep: Long, cs: List<CalItem>, isToday: Boolean, modifier: Modifier) {
    val shape = shp(10.dp)
    Column(
        modifier.padding(2.dp).clip(shape).background(UiPanel)
            .border(if (isToday) 1.5.dp else 1.dp, if (isToday) Charcoal else Line, shape),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // El número va encima de la portada, sin taparla.
        Text(
            "" + day,
            modifier = Modifier.padding(top = 3.dp, bottom = 2.dp),
            color = if (cs.isEmpty() && !isToday) Muted else Charcoal,
            fontSize = 11.sp,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 2.dp)) {
            if (cs.isNotEmpty()) {
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
