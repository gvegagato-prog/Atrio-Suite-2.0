package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun datesText(s: Long, e: Long): String {
    val a = if (s > 0L) "Inicio " + fmtDay(s) else ""
    val b = if (e > 0L) "Fin " + fmtDay(e) else ""
    return listOf(a, b).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
fun PartnerGauges(v: List<Int>, labels: List<String>) {
    StaticGauge(labels[0], v[0], ColGeneral, 150.dp)
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        val cols = critColors()
        for (i in 1 until labels.size.coerceAtMost(5)) StaticGauge(labels[i], v[i], cols[i - 1], 72.dp, Modifier.weight(1f))
    }
}

/** Fila de emojis para reaccionar a una ficha de tu pareja. */
@Composable
fun ReactBar(current: String?, onReact: (String?) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReactEmojis.forEach { e ->
            val on = e == current
            Box(
                Modifier.pop { onReact(if (on) null else e) }.size(46.dp)
                    .background(if (on) Sand else Color.Transparent, CircleShape)
                    .border(if (on) 1.5.dp else 1.dp, if (on) Charcoal else Line, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(e, fontSize = 22.sp) }
        }
    }
}

/** La página del juego o libro tal como la tiene tu pareja (solo lectura). */
@Composable
fun PartnerPage(
    side: PartnerSide, id: Long, kind: Kind, name: String, cover: String, hero: String,
    onBack: () -> Unit, onFlip: (() -> Unit)?, myReact: String?, onReact: (String?) -> Unit
) {
    val g = side.games.find { it.id == id }
    val p = if (g == null) side.plays.find { it.id == id } else null
    val c = (g?.cover ?: p?.cover ?: "").ifEmpty { cover }
    val h = (g?.hero ?: p?.hero ?: "").ifEmpty { hero }
    val creator = g?.creator ?: p?.creator ?: ""
    val platform = g?.platform ?: p?.platform ?: ""
    val minutes = g?.minutes ?: p?.minutes ?: 0
    val pages = g?.pages ?: p?.pages ?: 0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ArtBanner(h, c, name, kind.isBook, onBack, onFlip)
        Column(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 760.dp).fillMaxWidth().padding(16.dp)
                .card(24).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Overline("Ficha de " + side.name, Modifier.padding(bottom = 4.dp))
            Text(name, fontSize = 28.sp, lineHeight = 32.sp, fontFamily = Display, textAlign = TextAlign.Center)
            val extra = listOfNotNull(
                creator.ifBlank { null },
                platform.ifBlank { null },
                if (!kind.isBook && minutes > 0) hoursText(minutes) else null,
                if (kind.isBook && pages > 0) "$pages págs." else null
            ).joinToString(" · ")
            if (extra.isNotEmpty()) Text(extra, color = UiSoft, fontSize = 15.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))

            if (g != null) {
                Text(if (g.fav) "Valorado · ★ favorito" else "Valorado", color = UiSoft)
                val d = datesText(g.start, g.end)
                if (d.isNotEmpty()) Text(d, color = UiSoft, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                PartnerGauges(g.scores(), kind.labels)
                if (g.review.isNotBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Overline("Su opinión", Modifier.align(Alignment.Start))
                    Spacer(Modifier.height(4.dp))
                    Text(g.review, modifier = Modifier.align(Alignment.Start))
                }
            } else if (p != null) {
                Text(kind.statuses[p.status.coerceIn(0, 2)], fontWeight = FontWeight.SemiBold)
                if (kind.isBook && p.chapter > 0) Text("Capítulo " + p.chapter, color = UiSoft)
                val d = datesText(p.start, p.end)
                if (d.isNotEmpty()) Text(d, color = UiSoft, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                if (!kind.isBook && (p.parts.isNotEmpty() || p.ttbMain > 0)) {
                    ProgressPanel(p, kind, readOnly = true) {}
                    Spacer(Modifier.height(16.dp))
                }
                val b = p.baremo()
                if (b == null) {
                    Text("Todavía no ha apuntado nada.", color = UiSoft)
                } else {
                    Overline("Baremo · media de " + p.entries.size + " apuntes")
                    Spacer(Modifier.height(12.dp))
                    PartnerGauges(b.scores(), kind.labels)
                    Spacer(Modifier.height(20.dp))
                    Overline("Apuntes", Modifier.align(Alignment.Start).padding(bottom = 4.dp))
                    p.entries.sortedByDescending { it.time }.forEach { e ->
                        val vals = e.scores()
                        val line = kind.labels.mapIndexed { i, l -> l.take(3) + " " + vals[i] }.joinToString(" · ")
                        val chap = if (kind.isBook && e.chapter > 0) " · cap. " + e.chapter else ""
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                .border(1.dp, Line, shp(14.dp)).padding(14.dp)
                        ) {
                            Text(fmt(e.time) + chap, color = UiSoft, fontSize = 14.sp)
                            Text(line, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            if (e.note.isNotBlank()) Text(e.note, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            } else {
                Text(side.name + " todavía no lo tiene.", color = UiSoft, textAlign = TextAlign.Center)
            }

            if (g != null || p != null) {
                Spacer(Modifier.height(22.dp))
                Overline("Reacciona")
                Spacer(Modifier.height(8.dp))
                ReactBar(myReact, onReact)
                Spacer(Modifier.height(10.dp))
                PartnerFileLink(id, kind, always = true)
            }

            if (onFlip != null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Toca dos veces la portada para volver a lo tuyo.",
                    color = UiSoft, fontSize = 14.sp, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
