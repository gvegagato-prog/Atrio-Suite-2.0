package com.gonzalo.gamerate

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * Secciones especiales de la revista: «La cita» (un subrayado de tus libros, distinto cada día), «Tal día como hoy»
 * (lo que terminaste esta misma fecha otros años) y el «Número especial» (tu año entero, en cifras y titulares).
 */

private val ES = Locale("es", "ES")

@Composable
private fun EsRule(h: Dp, c: Color = Charcoal) = Box(Modifier.fillMaxWidth().height(h).background(c))

@Composable
private fun EsSection(t: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(bottom = 10.dp)) {
        Box(Modifier.size(10.dp).background(ColGeneral)); Spacer(Modifier.width(8.dp))
        Text(t.uppercase(), color = Charcoal, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}

@Composable
private fun EsLink(t: String, color: Color = ColGeneral, onClick: () -> Unit) =
    Text(t, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp))

// ================================================================ La cita

/** Un subrayado de un libro que tienes en el móvil. */
class Cita(val bookId: Long, val bookName: String, val author: String, val note: BookNote)

/** Todos tus subrayados que se leen bien sueltos (ni una palabra suelta ni media página). */
fun citas(ctx: Context, books: List<ShellEntry>): List<Cita> = books.distinctBy { it.id }.flatMap { e ->
    if (BookFiles.get(ctx, e.id) == null) emptyList()
    else bookNotes(ctx, e.id).filter { it.text.length in 25..600 }.map { Cita(e.id, e.name, e.creator, it) }
}

/** «La cita»: uno de tus subrayados, uno distinto cada día. Tocarlo abre el libro ahí. */
@Composable
fun RevistaCita(books: List<ShellEntry>) {
    val ctx = LocalContext.current
    // Se leen al cambiar los libros (no en cada repintado: cada libro guarda sus subrayados aparte)
    val all = remember(books.map { it.id }) { citas(ctx, books) }
    if (all.isEmpty()) return
    var shift by remember { mutableStateOf(0) }
    val i = Math.floorMod(LocalDate.now().toEpochDay() + shift, all.size.toLong()).toInt()
    val c = all[i]
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        EsSection("La cita")
        Text("«", color = ColGeneral, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 44.sp)
        Text(
            c.note.text, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 24.sp, lineHeight = 32.sp,
            modifier = Modifier.clickable { ReaderActivity.open(ctx, c.bookId, c.bookName, c.note.value) }
        )
        if (c.note.note.isNotBlank()) Text(c.note.note, color = ColGeneral, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
        Text(
            ("— " + listOf(c.bookName, c.author).filter { it.isNotBlank() }.joinToString(", ")).uppercase(),
            color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 10.dp)
        )
        if (all.size > 1) EsLink("OTRA CITA →") { shift++ }
    }
}

// ================================================================ Tal día como hoy

/** Lo que terminaste esta misma fecha en otros años: (ficha, hace cuántos años). */
fun efemerides(all: List<ShellEntry>, today: LocalDate = LocalDate.now()): List<Pair<ShellEntry, Int>> = all.mapNotNull { e ->
    val g = e.game ?: return@mapNotNull null
    if (g.end <= 0L) return@mapNotNull null
    val d = LocalDate.ofEpochDay(g.end)
    if (d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth && d.year < today.year) e to (today.year - d.year) else null
}.sortedBy { it.second }

/** El verbo de terminar según la categoría: «Lo pasaste», «Lo leíste», «La viste»… */
private fun doneVerb(k: Kind): String = when {
    k.isGame -> "Lo pasaste"
    k.isBook -> "Lo leíste"
    k.isMovie || k.isTv -> "La viste"
    else -> "Lo terminaste"
}

@Composable
fun RevistaEfemerides(all: List<ShellEntry>, open: (ShellEntry) -> Unit) {
    val hits = remember(all) { efemerides(all) }
    if (hits.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        EsSection("Tal día como hoy")
        hits.take(4).forEachIndexed { i, (e, years) ->
            if (i > 0) EsRule(1.dp, Line)
            Row(Modifier.fillMaxWidth().clickable { open(e) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(76.dp)) {
                    Text("$years", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp)
                    Text(if (years == 1) "AÑO" else "AÑOS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                }
                Column(Modifier.weight(1f)) {
                    FitTitle(e.name, 22.sp, 25.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(doneVerb(e.kind), e.score?.let { "nota $it" }).joinToString(" · ").uppercase(),
                        color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp
                    )
                }
            }
        }
    }
}

// ================================================================ Número especial: tu año

/** Los años en los que terminaste algo (y el actual), del más reciente al más antiguo. */
fun especialYears(all: List<ShellEntry>): List<Int> =
    (all.mapNotNull { e -> e.game?.end?.takeIf { it > 0L }?.let { LocalDate.ofEpochDay(it).year } } + LocalDate.now().year).distinct().sortedDescending()

/** El número especial: tu año en la revista. Se abre a pantalla completa desde el Sumario de la portada. */
@Composable
fun NumeroEspecial(name: String, kinds: List<Kind>, entries: (Kind) -> List<ShellEntry>, onOpen: (ShellEntry) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val everything = remember(kinds) { kinds.flatMap { entries(it) } }
    val years = remember(everything) { especialYears(everything) }
    var year by remember { mutableStateOf(years.first()) }
    // Lo terminado ese año (lo que tiene nota y fecha de fin)
    val done = remember(everything, year) {
        everything.filter { e -> e.game?.end?.let { it > 0L && LocalDate.ofEpochDay(it).year == year } == true }
            .sortedByDescending { it.game!!.general }
    }
    val quotes = remember(everything, year) {
        citas(ctx, everything.filter { it.kind.isBook }).filter { c -> c.note.created > 0 &&
            java.time.Instant.ofEpochMilli(c.note.created).atZone(java.time.ZoneId.systemDefault()).year == year }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Paper).systemBarsPadding().wrapContentWidth().widthIn(max = 900.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                EsLink("← CERRAR", Charcoal, onDismiss)
                Spacer(Modifier.weight(1f))
                val i = years.indexOf(year)
                if (i < years.size - 1) EsLink("${years[i + 1]}", Muted) { year = years[i + 1] }
                Spacer(Modifier.width(16.dp))
                if (i > 0) EsLink("${years[i - 1]}", Muted) { year = years[i - 1] }
            }
            Text("ATRIO", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 6.dp))
            EsRule(3.dp)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("NÚMERO ESPECIAL", color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                Spacer(Modifier.weight(1f))
                Text("$year", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            }
            EsRule(1.dp)
            FitTitle("Tu $year", 104.sp, 100.sp, letterSpacing = (-3).sp, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Text("El año de $name, en cifras y en titulares.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 25.sp)
            Spacer(Modifier.height(18.dp))

            if (done.isEmpty()) {
                Text(
                    if (year == LocalDate.now().year) "Aún no has terminado nada este año. Esta revista se escribe sola en cuanto acabes el primero."
                    else "Ese año no terminaste nada.",
                    fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = Muted, modifier = Modifier.padding(vertical = 30.dp)
                )
            } else {
                // Cifras: cuántos de cada cosa, horas jugadas y páginas leídas
                EsRule(2.dp)
                val counts = kinds.mapNotNull { k -> done.count { it.kind.id == k.id }.takeIf { it > 0 }?.let { k to it } }
                val hours = done.filter { it.kind.isGame }.sumOf { it.game!!.minutes }
                val pages = done.filter { it.kind.isBook }.sumOf { it.game!!.pages }
                val cells = counts.map { (k, n) -> "$n" to k.doneWord } +
                    listOfNotNull(if (hours >= 60) "${hours / 60}" to "horas jugadas" else null, if (pages > 0) "$pages" to "páginas leídas" else null)
                cells.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        row.forEachIndexed { j, (n, l) ->
                            if (j > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Charcoal))
                            Column(Modifier.weight(1f).padding(start = if (j > 0) 14.dp else 0.dp, top = 12.dp, bottom = 12.dp)) {
                                Text(n, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 56.sp)
                                Text(l.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    EsRule(1.dp, Line)
                }

                // La portada del año: lo mejor valorado
                val top = done.first()
                Spacer(Modifier.height(26.dp))
                EsSection("La portada del año")
                FitTitle(top.name, 52.sp, 54.sp, letterSpacing = (-1.5).sp, modifier = Modifier.fillMaxWidth().clickable { onOpen(top) })
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                    Text("${top.score}", color = ColGeneral, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp)
                    Text("/100", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(listOf(top.kind.title, top.creator).filter { it.isNotBlank() }.joinToString(" · ").uppercase(),
                        color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.padding(bottom = 14.dp).weight(1f))
                }
                top.game?.review?.takeIf { it.isNotBlank() }?.let { r ->
                    Text("«${r.trim().take(280)}${if (r.trim().length > 280) "…" else ""}»", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 6.dp))
                }

                // Lo mejor del año
                if (done.size > 1) {
                    Spacer(Modifier.height(26.dp))
                    EsSection("Lo mejor del año")
                    done.drop(1).take(10).forEachIndexed { i, e ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d".format(i + 2), color = ColGeneral, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, modifier = Modifier.width(50.dp))
                            Column(Modifier.weight(1f)) {
                                FitTitle(e.name, 21.sp, 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(e.kind.title.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                            }
                            Text("${e.score}", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                        }
                        EsRule(1.dp, Line)
                    }
                }

                // Mes a mes
                Spacer(Modifier.height(26.dp))
                EsSection("Mes a mes")
                val byMonth = done.groupBy { LocalDate.ofEpochDay(it.game!!.end).monthValue }.toSortedMap()
                byMonth.forEach { (month, list) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text(java.time.Month.of(month).getDisplayName(TextStyle.SHORT, ES).replace(".", "").uppercase(),
                            color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.width(56.dp).padding(top = 4.dp))
                        Text(list.sortedBy { it.game!!.end }.joinToString(" · ") { it.name }, fontFamily = Display, fontSize = 18.sp, lineHeight = 24.sp, modifier = Modifier.weight(1f))
                    }
                    EsRule(1.dp, Line)
                }

                // La media de cada cosa
                val avgs = kinds.mapNotNull { k ->
                    done.filter { it.kind.id == k.id }.takeIf { it.isNotEmpty() }?.let { l -> k to l.map { it.game!!.general }.average() }
                }
                if (avgs.size > 1 || (avgs.size == 1 && done.size >= 3)) {
                    Spacer(Modifier.height(26.dp))
                    EsSection("Tu nota media")
                    avgs.forEach { (k, a) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(k.title, fontFamily = Display, fontSize = 20.sp, modifier = Modifier.weight(1f))
                            Text(String.format(ES, "%.1f", a), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                        }
                    }
                }

                // La decepción: solo si hay de qué hablar
                val worst = done.last()
                if (done.size >= 4 && worst.score != null && worst.score!! < 60) {
                    Spacer(Modifier.height(26.dp))
                    EsSection("La decepción")
                    Row(Modifier.fillMaxWidth().clickable { onOpen(worst) }, verticalAlignment = Alignment.CenterVertically) {
                        FitTitle(worst.name, 30.sp, 33.sp, modifier = Modifier.weight(1f))
                        Text("${worst.score}", color = Muted, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp)
                    }
                }
            }

            // Lo que subrayaste ese año
            if (quotes.isNotEmpty()) {
                Spacer(Modifier.height(26.dp))
                EsSection("Lo que subrayaste")
                quotes.shuffled(kotlin.random.Random(year)).take(3).forEach { c ->
                    Text("«${c.note.text}»", fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 26.sp,
                        modifier = Modifier.padding(top = 6.dp).clickable { ReaderActivity.open(ctx, c.bookId, c.bookName, c.note.value) })
                    Text("— ${c.bookName}".uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                }
            }
            Spacer(Modifier.height(30.dp))
            Text("Atrio · número especial · $year", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ================================================================ Tu lectura (en la portada)

/** Minutos leídos por día (todos los libros juntos), con lo que guarda el lector de cada libro. */
fun readingDays(ctx: Context, books: List<ShellEntry>): Map<String, Long> {
    val out = HashMap<String, Long>()
    for (e in books.distinctBy { it.id }) {
        val days = BookFiles.state(ctx, e.id).optJSONObject("stats")?.optJSONObject("days") ?: continue
        for (k in days.keys()) out[k] = (out[k] ?: 0L) + days.optLong(k)
    }
    return out
}

/** Días seguidos leyendo (al menos un minuto), hasta hoy o hasta ayer. */
fun readingStreak(days: Map<String, Long>, today: LocalDate = LocalDate.now()): Int {
    var d = if ((days[today.toString()] ?: 0L) >= 60_000L) today else today.minusDays(1)
    var n = 0
    while ((days[d.toString()] ?: 0L) >= 60_000L) { n++; d = d.minusDays(1) }
    return n
}

/** Una línea en la portada: lo que has leído hoy y cuántos días llevas seguidos. */
@Composable
fun RevistaLectura(books: List<ShellEntry>) {
    val ctx = LocalContext.current
    val days = remember(books.map { it.id }) { readingDays(ctx, books) }
    val todayMin = (days[LocalDate.now().toString()] ?: 0L) / 60_000L
    val streak = readingStreak(days)
    if (todayMin < 1 && streak < 2) return
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(ColGeneral)); Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(
                "LECTURA",
                if (todayMin >= 1) (if (todayMin < 60) "$todayMin MIN HOY" else "${todayMin / 60} H ${todayMin % 60} MIN HOY") else null,
                if (streak >= 2) "$streak DÍAS SEGUIDOS" else null
            ).joinToString(" · "),
            fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp
        )
    }
}
