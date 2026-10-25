package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ES = Locale("es", "ES")

private fun f1(x: Double): String = String.format(ES, "%.1f", x)

private fun daysOf(g: Game): Long = g.end - g.start + 1

private fun monthLabel(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.SHORT, ES) + " " + m.year

private fun yearOf(day: Long): Int = LocalDate.ofEpochDay(day).year

private class Stats(
    val done: Int, val listed: Int, val inProgress: Int, val dropped: Int,
    val dropRate: Int?,
    val avg: Double, val cats: List<Double>,
    val best: Game?, val worst: Game?, val masterpieces: Int, val reviews: Int, val favs: Int,
    val hist: List<Int>,
    val dated: Int, val avgDays: Double, val fastest: Game?, val longest: Game?, val totalDays: Long,
    val months: List<Pair<String, Int>>, val bestMonth: Pair<String, Int>?,
    val creators: List<Pair<String, Int>>, val bestCreator: Pair<String, Double>?, val withCreator: Int,
    val platforms: List<Pair<String, Int>>,
    val minutes: Int, val withMinutes: Int, val mostPlayed: Game?,
    val pages: Int, val withPages: Int, val pagesPerDay: Double, val thickest: Game?
)

/** year: null = todo; si no, solo lo terminado ese año. */
private fun compute(allGames: List<Game>, plays: List<Play>, year: Int?): Stats {
    val games = if (year == null) allGames else allGames.filter { it.end > 0L && yearOf(it.end) == year }
    val drops = plays.filter { it.status == 2 && (year == null || (it.end > 0L && yearOf(it.end) == year)) }
    val done = games.size
    val dropped = drops.size
    val finishedOrDropped = done + dropped
    val avg = if (done == 0) 0.0 else games.map { it.general }.average()
    val cats = listOf(
        games.map { it.gfx }, games.map { it.story }, games.map { it.music }, games.map { it.gameplay }
    ).map { if (it.isEmpty()) 0.0 else it.average() }

    val hist = IntArray(5)
    games.forEach { hist[(it.general / 20).coerceIn(0, 4)]++ }

    val dated = games.filter { it.start > 0L && it.end >= it.start }
    val avgDays = if (dated.isEmpty()) 0.0 else dated.map { daysOf(it).toDouble() }.average()

    // Gráfico mensual: los 12 meses del año elegido, o los últimos 12 si es "Todo".
    val monthList = if (year == null) {
        val now = YearMonth.now()
        (11 downTo 0).map { now.minusMonths(it.toLong()) }
    } else (1..12).map { YearMonth.of(year, it) }
    val months = monthList.map { m ->
        val n = allGames.count { g -> g.end > 0L && YearMonth.from(LocalDate.ofEpochDay(g.end)) == m }
        m.month.getDisplayName(TextStyle.SHORT, ES).take(1).uppercase() to n
    }
    val byMonth = games.filter { it.end > 0L }.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.end)) }
    val bm = byMonth.maxByOrNull { it.value.size }

    val withCreator = games.filter { it.creator.isNotBlank() }
    val groups = withCreator.groupBy { it.creator.trim() }
    val creators = groups.map { it.key to it.value.size }.sortedByDescending { it.second }.take(5)
    val bestCreator = groups.filter { it.value.size >= 2 }
        .map { it.key to it.value.map { g -> g.general }.average() }
        .maxByOrNull { it.second }

    val platforms = games.filter { it.platform.isNotBlank() }.groupBy { it.platform.trim() }
        .map { it.key to it.value.size }.sortedByDescending { it.second }.take(6)

    val withMin = games.filter { it.minutes > 0 }
    val withPages = games.filter { it.pages > 0 }
    val datedPages = dated.filter { it.pages > 0 }
    val ppd = if (datedPages.isEmpty()) 0.0 else datedPages.sumOf { it.pages }.toDouble() / datedPages.sumOf { daysOf(it) }

    return Stats(
        done, plays.count { it.status == 0 }, plays.count { it.status == 1 }, dropped,
        if (finishedOrDropped == 0) null else (dropped * 100.0 / finishedOrDropped).roundToInt(),
        avg, cats,
        games.maxByOrNull { it.general }, games.minByOrNull { it.general },
        games.count { it.general >= 90 }, games.count { it.review.isNotBlank() }, games.count { it.fav },
        hist.toList(),
        dated.size, avgDays, dated.minByOrNull { daysOf(it) }, dated.maxByOrNull { daysOf(it) },
        dated.sumOf { daysOf(it) },
        months, bm?.let { monthLabel(it.key) to it.value.size },
        creators, bestCreator, withCreator.size,
        platforms,
        withMin.sumOf { it.minutes }, withMin.size, withMin.maxByOrNull { it.minutes },
        withPages.sumOf { it.pages }, withPages.size, ppd, withPages.maxByOrNull { it.pages }
    )
}

private class Cmp(
    val common: Int, val avgDiff: Double, val mine: Double, val theirs: Double,
    val closest: Triple<String, Int, Int>, val farthest: Triple<String, Int, Int>,
    val bothLoved: List<String>, val onlyMine: Int, val onlyTheirs: Int
)

private fun compare(mine: List<Game>, theirs: List<Game>): Cmp? {
    val pairs = mine.mapNotNull { m -> theirs.find { it.id == m.id }?.let { m to it } }
    if (pairs.isEmpty()) return null
    val diffs = pairs.map { abs(it.first.general - it.second.general) }
    val close = pairs.minByOrNull { abs(it.first.general - it.second.general) }!!
    val far = pairs.maxByOrNull { abs(it.first.general - it.second.general) }!!
    val ids = pairs.map { it.first.id }.toSet()
    return Cmp(
        pairs.size, diffs.average(),
        pairs.map { it.first.general }.average(), pairs.map { it.second.general }.average(),
        Triple(close.first.name, close.first.general, close.second.general),
        Triple(far.first.name, far.first.general, far.second.general),
        pairs.filter { it.first.general >= 85 && it.second.general >= 85 }.map { it.first.name }.take(5),
        mine.count { it.id !in ids }, theirs.count { it.id !in ids }
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Panel(Modifier.padding(bottom = 12.dp), title, content)
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = UiSoft, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
    }
}

@Composable
private fun LabeledBar(label: String, value: String, frac: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
        Spacer(Modifier.height(5.dp))
        Bar(frac, color, height = 4.dp)
    }
}

/** Cifra grande con su etiqueta. */
@Composable
private fun BigNumber(value: String, label: String, modifier: Modifier = Modifier) {
    if (Skin.revista) {
        Column(modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.Start) {
            Text(value, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 54.sp)
            Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, color = Muted, maxLines = 2)
        }
        return
    }
    Column(modifier.border(1.dp, Line, shp(14.dp)).padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Display, fontSize = 30.sp, lineHeight = 34.sp)
        Text(label, fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Lo leído con el lector en un año (o en todo, con null): total, días, racha más larga, por meses y por libro. */
class TiempoLeyendo(val total: Long, val dias: Int, val racha: Int, val meses: List<Pair<String, Long>>, val libros: List<Pair<Long, Long>>)

fun tiempoLeyendo(leido: Map<Long, Map<Long, Long>>, year: Int?, hoy: LocalDate = LocalDate.now()): TiempoLeyendo {
    val dentro = leido.filterKeys { d -> year == null || LocalDate.ofEpochDay(d).year == year }
    val porDia = dentro.mapValues { it.value.values.sum() }.filterValues { it >= 60_000L }
    var racha = 0; var cur = 0; var prev = Long.MIN_VALUE
    for (d in porDia.keys.sorted()) { cur = if (d == prev + 1) cur + 1 else 1; racha = maxOf(racha, cur); prev = d }
    val meses = if (year != null) (1..12).map { YearMonth.of(year, it) } else (11 downTo 0).map { YearMonth.from(hoy).minusMonths(it.toLong()) }
    val porMes = meses.map { m -> monthLabel(m) to dentro.filterKeys { YearMonth.from(LocalDate.ofEpochDay(it)) == m }.values.sumOf { it.values.sum() } }
    val libros = HashMap<Long, Long>()
    dentro.values.forEach { m -> m.forEach { (id, ms) -> libros[id] = (libros[id] ?: 0L) + ms } }
    return TiempoLeyendo(dentro.values.sumOf { it.values.sum() }, porDia.size, racha, porMes, libros.entries.sortedByDescending { it.value }.map { it.key to it.value })
}

private fun nameScore(g: Game?): String = if (g == null) "—" else g.name + " (" + g.general + ")"

@Composable
fun StatsScreen(
    kind: Kind, games: List<Game>, plays: List<Play>, partner: PartnerSide?,
    goal: Int, partnerGoal: Int, onShareYear: ((Int) -> Unit)? = null,
    kinds: List<Kind> = emptyList(), onKind: (Kind) -> Unit = {}, onSpecial: ((Int) -> Unit)? = null, onMenu: () -> Unit
) {
    val showPartner = Mode.calP
    val useP = showPartner && partner != null
    val pName = partner?.name ?: ""
    val g = if (useP) partner!!.games else games
    val p = if (useP) partner!!.plays else plays
    val thisYear = LocalDate.now().year
    val years = remember(g) {
        (g.filter { it.end > 0L }.map { yearOf(it.end) } + thisYear).distinct().sortedDescending()
    }
    var year by remember { mutableStateOf<Int?>(null) }
    val st = remember(g, p, year) { compute(g, p, year) }
    val cmp = remember(games, partner) { if (partner == null) null else compare(games, partner.games) }
    val doneThisYear = remember(g) { g.count { it.end > 0L && yearOf(it.end) == thisYear } }
    val goalNow = if (useP) partnerGoal else goal

    val creatorWord = kind.creatorWord
    val unit = kind.unit

    Column(
        Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 760.dp).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
    ) {
        TopBar(kind.title, onMenu, "Estadísticas") {
            if (partner != null) PartnerToggle(showPartner) { withReveal { Mode.calP = !Mode.calP } }
        }
        if (useP) {
            Overline("Estadísticas de ${partner!!.name}", Modifier.padding(start = 4.dp, bottom = 10.dp))
        }
        // Desde la portada: todas las categorías aquí mismo
        if (kinds.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            kinds.forEach { k -> SoftChip(k.title, k.id == kind.id) { onKind(k) } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SoftChip("Todo", year == null) { year = null }
            years.forEach { y -> SoftChip("$y", year == y) { year = y } }
        }
        // Tu año entero, como número especial de la revista (el año elegido arriba; con «Todo», este)
        if (onSpecial != null) {
            val y = year ?: thisYear
            Text("VER TU $y, NÚMERO ESPECIAL\u00A0→", color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 14.dp).press { onSpecial(y) }.padding(vertical = 6.dp))
        }

        if (g.isEmpty() && p.isEmpty()) {
            Text(
                if (useP) "Tu pareja aún no tiene nada en esta sección." else "Todavía no hay datos. Añade y valora algo y aparecerán aquí.",
                color = UiSoft, modifier = Modifier.pill()
            )
            return@Column
        }

        if (!kind.custom && (year == null || year == thisYear)) {
            Section("Objetivo de $thisYear") {
                if (goalNow <= 0) {
                    Text(
                        when {
                            useP -> "Tu pareja no se ha puesto objetivo este año."
                            onShareYear == null -> "No hay objetivo para este año."
                            else -> "Ponte un objetivo en Ajustes y aquí verás cómo vas."
                        },
                        color = UiSoft
                    )
                } else {
                    val day = LocalDate.now().dayOfYear
                    val len = LocalDate.now().lengthOfYear()
                    val expected = goalNow * day.toDouble() / len
                    val diff = doneThisYear - expected
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(((doneThisYear * 100.0) / goalNow).roundToInt().coerceAtMost(100), if (doneThisYear >= goalNow) Gold else Charcoal, 86.dp, "$doneThisYear")
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("$doneThisYear de $goalNow", fontFamily = Display, fontSize = 24.sp)
                            Text(
                                when {
                                    doneThisYear >= goalNow -> "¡Objetivo cumplido!"
                                    diff >= 0.5 -> "Vas ${f1(diff)} por delante del ritmo."
                                    diff <= -0.5 -> "Vas ${f1(-diff)} por detrás del ritmo."
                                    else -> "Vas justo al ritmo."
                                },
                                color = UiSoft
                            )
                            if (doneThisYear < goalNow) {
                                val left = goalNow - doneThisYear
                                val weeks = ((len - day) / 7.0).coerceAtLeast(1.0)
                                Text("Te quedan $left: unos ${f1(left / weeks)} por semana.", color = UiSoft, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        Section("Resumen") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigNumber("${st.done}", kind.doneWord, Modifier.weight(1f))
                BigNumber("${st.inProgress}", kind.statuses[1], Modifier.weight(1f))
                BigNumber("${st.listed}", "En lista", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Stat(kind.statuses[2] + "s", "" + st.dropped)
            Stat("Tasa de abandono", if (st.dropRate == null) "—" else st.dropRate.toString() + " %")
            Stat("Favoritos", "" + st.favs)
        }

        if (st.done > 0) {
            Section("Notas") {
                Stat("Nota media", f1(st.avg))
                Stat("Mejor valorado", nameScore(st.best))
                Stat("Peor valorado", nameScore(st.worst))
                Stat("Con 90 o más", "" + st.masterpieces)
                Stat("Con opinión escrita", "" + st.reviews)
                Spacer(Modifier.height(6.dp))
                Text("Media por criterio", color = UiSoft, fontSize = 13.sp)
                val cols = listOf(ColGfx, ColStory, ColMusic, ColGameplay)
                for (i in 0 until kind.crit) {
                    LabeledBar(kind.labels[i + 1], f1(st.cats[i]), (st.cats[i] / 100.0).toFloat(), cols[i])
                }
            }

            Section("Reparto de notas") {
                val labels = listOf("0–19", "20–39", "40–59", "60–79", "80–100")
                val mx = (st.hist.maxOrNull() ?: 1).coerceAtLeast(1)
                for (i in 0..4) {
                    LabeledBar(labels[i], "" + st.hist[i], st.hist[i].toFloat() / mx, Charcoal)
                }
            }

            if (kind.isGame) {
                Section("Horas jugadas") {
                    if (st.withMinutes == 0) {
                        Text("Juega en el PC con Atrio o apunta las horas en cada juego para ver estos datos.", color = UiSoft)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BigNumber("${st.minutes / 60}", "horas en total", Modifier.weight(1f))
                            BigNumber("${st.minutes / 60 / st.withMinutes}", "horas por juego", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Stat("El que más", st.mostPlayed?.let { it.name + " (" + hoursText(it.minutes) + ")" } ?: "—")
                        Stat("Con horas apuntadas", "${st.withMinutes} de ${st.done}")
                    }
                }
            } else if (kind.isBook) {
                Section("Páginas") {
                    if (st.withPages == 0) {
                        Text("Pon las páginas de cada libro para ver estos datos (se rellenan solas al añadirlo si Open Library las tiene).", color = UiSoft)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BigNumber("${st.pages}", "páginas leídas", Modifier.weight(1f))
                            BigNumber("${st.pages / st.withPages}", "por libro", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        if (st.pagesPerDay > 0) Stat("Ritmo de lectura", f1(st.pagesPerDay) + " págs./día")
                        Stat("El más gordo", st.thickest?.let { it.name + " (" + it.pages + ")" } ?: "—")
                    }
                }
            }
            if (kind.isBook && !useP) TiempoLeyendoSection(year, games, plays)

            Section("Tiempo") {
                if (st.dated == 0) {
                    Text("Pon fecha de inicio y fin a tus ${kind.title.lowercase()} para ver estos datos.", color = UiSoft)
                } else {
                    Stat("Tiempo medio por $unit", f1(st.avgDays) + " días")
                    Stat("Más rápido", if (st.fastest == null) "—" else st.fastest.name + " (" + daysOf(st.fastest) + " d)")
                    Stat("Más largo", if (st.longest == null) "—" else st.longest.name + " (" + daysOf(st.longest) + " d)")
                    Stat("Días totales", "" + st.totalDays)
                    Stat("Con fechas completas", "" + st.dated + " de " + st.done)
                }
            }

            Section("Ritmo") {
                Stat("Mejor mes", if (st.bestMonth == null) "—" else st.bestMonth.first + " (" + st.bestMonth.second + ")")
                Spacer(Modifier.height(6.dp))
                Text(if (year == null) "Terminados en los últimos 12 meses" else "Terminados en $year", color = UiSoft, fontSize = 13.sp)
                val mx = (st.months.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    st.months.forEach { m ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (m.second > 0) "" + m.second else "", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Box(
                                Modifier.fillMaxWidth(0.45f).height((m.second.toFloat() / mx * 62f).dp.coerceAtLeast(2.dp))
                                    .background(if (m.second > 0) Charcoal else Line, shp(3.dp))
                            )
                            Text(m.first, fontSize = 11.sp, color = UiSoft)
                        }
                    }
                }
            }

            Section(kind.creatorPlural) {
                if (st.creators.isEmpty()) {
                    Text(
                        if (kind.isMovie || kind.isTv) "Se rellena solo al añadir desde el buscador, o escríbelo en cada ficha."
                        else if (kind.custom) "Escribe el creador en cada ficha para ver el ranking."
                        else "Escribe el " + creatorWord.lowercase() + " en la ficha de cada " + unit + " para ver el ranking.",
                        color = UiSoft
                    )
                } else {
                    Text(
                        (if (kind.isBook) "Escritor más leído" else if (kind.isMovie) "Director más visto" else if (kind.isTv) "Creador más visto"
                        else if (kind.custom) "El que más se repite" else "Estudio con más juegos") + ": " + st.creators[0].first,
                        fontWeight = FontWeight.Medium
                    )
                    if (st.bestCreator != null) {
                        Text(
                            "Mejor nota media (2 o más): " + st.bestCreator.first + " (" + f1(st.bestCreator.second) + ")",
                            color = UiSoft, fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val mx = st.creators[0].second.toFloat()
                    st.creators.forEach { c -> LabeledBar(c.first, "" + c.second, c.second / mx, Charcoal) }
                    if (st.withCreator < st.done) {
                        Text(
                            "Solo cuentan los " + st.withCreator + " de " + st.done + " que tienen " + creatorWord.lowercase() + ".",
                            color = UiSoft, fontSize = 12.sp
                        )
                    }
                }
            }

            Section(if (kind.isMovie || kind.isTv) "Dónde ves" else kind.platformWord + "s") {
                if (st.platforms.isEmpty()) {
                    Text(
                        if (kind.isGame) "Elige la plataforma en cada ficha para ver dónde juegas más."
                        else if (kind.isBook) "Elige el formato en cada ficha para ver cómo lees más."
                        else if (kind.isMovie || kind.isTv) "Elige dónde la ves en cada ficha para verlo aquí."
                        else "Escribe el formato en cada ficha para verlo aquí.",
                        color = UiSoft
                    )
                } else {
                    val mx = st.platforms[0].second.toFloat()
                    st.platforms.forEach { c -> LabeledBar(c.first, "" + c.second, c.second / mx, Charcoal) }
                }
            }
        }

        val shareY = year ?: thisYear
        val shareN = if (year == null) doneThisYear else st.done
        if (onShareYear != null && !useP && shareN > 0) {
            val y = shareY
            ActionPill(Icons.Rounded.IosShare, "Compartir mi $y", Modifier.padding(bottom = 12.dp)) { onShareYear(y) }
        }

        if (partner != null) {
            Section("Vosotros dos") {
                if (cmp == null) {
                    Text("Todavía no habéis valorado nada en común.", color = UiSoft)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring((100 - cmp.avgDiff).roundToInt().coerceIn(0, 100), Charcoal, 86.dp, "${(100 - cmp.avgDiff).roundToInt().coerceIn(0, 100)}%")
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Afinidad", fontFamily = Display, fontSize = 22.sp)
                            Text("Os separan ${f1(cmp.avgDiff)} puntos de media en ${cmp.common} en común.", color = UiSoft)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Stat("Tu media en común", f1(cmp.mine))
                    Stat(pName + " (media en común)", f1(cmp.theirs))
                    Stat("Más de acuerdo", cmp.closest.first + " (" + cmp.closest.second + " y " + cmp.closest.third + ")")
                    Stat("Mayor discrepancia", cmp.farthest.first + " (" + cmp.farthest.second + " y " + cmp.farthest.third + ")")
                    Stat("Solo tuyos", "" + cmp.onlyMine)
                    Stat("Solo de $pName", "" + cmp.onlyTheirs)
                    if (cmp.bothLoved.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Os han encantado a los dos (85+): " + cmp.bothLoved.joinToString(", "), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Cifras de libros: el tiempo leído con el lector de Atrio (de todos tus dispositivos). */
@Composable
private fun TiempoLeyendoSection(year: Int?, games: List<Game>, plays: List<Play>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val vuelve = rememberResumeTick()
    val t = remember(year, vuelve) { tiempoLeyendo(BookFiles.leidoPorDia(ctx), year) }
    if (t.total < 60_000L) return
    Section("Tiempo leyendo") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigNumber(readTime(t.total / 60_000L), if (year == null) "leyendo en total" else "leyendo en $year", Modifier.weight(1f))
            BigNumber("${t.dias}", if (t.dias == 1) "día con lectura" else "días con lectura", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        if (t.dias > 0) Stat("Media los días que lees", readTime(t.total / t.dias / 60_000L))
        if (t.racha > 1) Stat("Racha más larga", "${t.racha} días seguidos")
        val nombre = { id: Long -> (plays.find { it.id == id }?.name ?: games.find { it.id == id }?.name) }
        t.libros.mapNotNull { (id, ms) -> nombre(id)?.let { it to ms } }.take(3).forEachIndexed { i, (n, ms) ->
            Stat(if (i == 0) "El que más tiempo te ha llevado" else "", "$n (${readTime(ms / 60_000L)})")
        }
        Spacer(Modifier.height(6.dp))
        Text(if (year == null) "Horas leyendo en los últimos 12 meses" else "Horas leyendo en $year", color = UiSoft, fontSize = 13.sp)
        val mx = (t.meses.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
        Row(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            t.meses.forEach { (m, ms) ->
                val h = ms / 3_600_000.0
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (ms >= 60_000L) (if (h >= 10) "${h.roundToInt()}" else f1(h)) else "", fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Box(
                        Modifier.fillMaxWidth(0.45f).height((ms.toFloat() / mx * 62f).dp.coerceAtLeast(2.dp))
                            .background(if (ms >= 60_000L) ColGeneral else Line, shp(3.dp))
                    )
                    Text(m, fontSize = 11.sp, color = UiSoft)
                }
            }
        }
    }
}
