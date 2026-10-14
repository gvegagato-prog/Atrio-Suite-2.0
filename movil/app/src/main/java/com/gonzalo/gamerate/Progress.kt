@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Progreso de algo en curso: duración media (si se sabe), capítulos o actos para ir marcando
 * y el porcentaje completado. [find] busca duración y capítulos en internet (solo juegos).
 */
@Composable
fun ProgressPanel(
    p: Play, kind: Kind, readOnly: Boolean = false,
    find: (suspend (String) -> TtbResult)? = null,
    /** Series: trae los episodios emitidos para marcarlos. */
    episodes: (suspend () -> List<String>)? = null,
    /** Se acaba de marcar como completado un capítulo (para valorarlo). */
    onPartDone: ((String) -> Unit)? = null,
    onChange: (Play) -> Unit
) {
    val scope = rememberCoroutineScope()
    val cur by androidx.compose.runtime.rememberUpdatedState(p)
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var newPart by remember { mutableStateOf("") }
    val pct = p.percent()
    val shape = shp(16.dp)
    val partWord = if (kind.isTv) "episodio" else if (kind.custom) "capítulo o episodio" else "capítulo o acto"

    Column(
        Modifier.fillMaxWidth().surface(20.dp, Crudo).padding(16.dp).animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("Progreso")
                Text(
                    when {
                        p.parts.isNotEmpty() -> "${p.parts.count { it.done }} de ${p.parts.size} completados"
                        pct != null -> "Por horas jugadas"
                        readOnly -> "Sin capítulos"
                        episodes != null -> "Trae los episodios para ver tu porcentaje"
                        else -> "Añade capítulos para ver tu porcentaje"
                    },
                    color = Muted, fontSize = 15.sp
                )
            }
            if (pct != null) Text("$pct %", fontFamily = Display, fontSize = 30.sp)
        }
        if (pct != null) {
            Spacer(Modifier.height(10.dp))
            Bar(pct / 100f, if (pct >= 100) Gold else Charcoal, height = 4.dp)
        }

        // Duración media
        if (p.ttbMain > 0 || p.ttbExtra > 0 || p.ttbFull > 0) {
            Spacer(Modifier.height(14.dp))
            TtbTiles(p.ttbMain, p.ttbExtra, p.ttbFull)
            if (p.minutes > 0 && p.ttbMain > 0) {
                Spacer(Modifier.height(8.dp))
                val left = p.ttbMain - p.minutes
                Text(
                    (if (readOnly) "Lleva" else "Llevas") + " ${hoursText(p.minutes)} de unas ${hoursText(p.ttbMain)}" +
                        if (left > 0) " · ${if (readOnly) "le" else "te"} quedan ~${hoursText(left)}"
                        else if (readOnly) " · ya debería estar acabando" else " · ya deberías estar acabando",
                    fontSize = 15.sp
                )
            }
            if (p.ttbSrc.isNotBlank()) {
                Text("Fuente: ${p.ttbSrc}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Capítulos / actos
        if (p.parts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            p.parts.forEachIndexed { i, part ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Row(
                    Modifier.fillMaxWidth().clip(shp(8.dp))
                        .combinedClickable(
                            enabled = !readOnly && !editing,
                            // Mantener pulsado: marca como visto todo hasta aquí.
                            onLongClick = { onChange(p.copy(parts = p.parts.mapIndexed { j, x -> if (j <= i) x.copy(done = true) else x })) }
                        ) {
                            onChange(p.copy(parts = p.parts.mapIndexed { j, x -> if (j == i) x.copy(done = !x.done) else x }))
                            if (!part.done) onPartDone?.invoke(part.name)
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(22.dp)
                            .background(if (part.done) Charcoal else Color.Transparent, CircleShape)
                            .border(1.dp, if (part.done) Charcoal else Line, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (part.done) Icon(Icons.Rounded.Check, null, tint = Crudo, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        part.name, Modifier.weight(1f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (part.done) Muted else Charcoal,
                        textDecoration = if (part.done) TextDecoration.LineThrough else null
                    )
                    if (editing) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).clickable {
                                onChange(p.copy(parts = p.parts.filterIndexed { j, _ -> j != i }))
                            },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Close, "Quitar", tint = Muted, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }

        if (!readOnly) {
            if (editing || p.parts.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                fun add() {
                    val n = newPart.trim()
                    if (n.isNotEmpty()) { onChange(p.copy(parts = p.parts + Part(n))); newPart = "" }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newPart, { newPart = it }, Modifier.weight(1f),
                        label = { Text("Añadir $partWord") }, singleLine = true, shape = shp(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() })
                    )
                    Spacer(Modifier.width(8.dp))
                    IconPill(Icons.Rounded.Add, "Añadir") { add() }
                }
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (find != null) {
                    ActionPill(Icons.Rounded.Search, if (busy) "Buscando…" else "Buscar duración") {
                        if (busy) return@ActionPill
                        busy = true
                        msg = ""
                        scope.launch {
                            try {
                                val r = find(p.name)
                                val now = cur
                                onChange(
                                    now.copy(
                                        ttbMain = if (r.main > 0) r.main else now.ttbMain,
                                        ttbExtra = if (r.extra > 0) r.extra else now.ttbExtra,
                                        ttbFull = if (r.full > 0) r.full else now.ttbFull,
                                        ttbSrc = r.src
                                    )
                                )
                            } catch (e: Exception) {
                                msg = "No se pudo: ${e.message}"
                            }
                            busy = false
                        }
                    }
                }
                if (episodes != null) {
                    ActionPill(Icons.Rounded.Search, if (busy) "Buscando…" else if (p.parts.isEmpty()) "Traer episodios" else "Actualizar episodios") {
                        if (busy) return@ActionPill
                        busy = true
                        msg = ""
                        scope.launch {
                            try {
                                val names = episodes()
                                val now = cur
                                // Lo que ya habías marcado se conserva; lo que añadiste a mano y no está, va al final.
                                val done = now.parts.filter { it.done }.map { it.name }.toSet()
                                val fresh = names.map { Part(it, it in done) }
                                val extra = now.parts.filter { it.name !in names }
                                onChange(now.copy(parts = fresh + extra))
                                msg = if (names.isEmpty()) "Todavía no hay episodios emitidos." else "${names.size} episodios. Mantén pulsado uno para marcar todo hasta ahí."
                            } catch (e: Exception) {
                                msg = "No se pudo: ${e.message}"
                            }
                            busy = false
                        }
                    }
                }
                if (p.parts.isNotEmpty()) {
                    TextButton({ editing = !editing }) { Text(if (editing) "Listo" else "Editar lista", color = Muted) }
                }
            }
            if (msg.isNotEmpty()) Text(msg, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Casillas con las horas habituales: deprisa, normal y al 100 %. */
@Composable
fun TtbTiles(main: Int, extra: Int, full: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Deprisa" to extra, "Normal" to main, "Al 100 %" to full)
            .filter { it.second > 0 }
            .forEach { (l, m) ->
                Column(
                    Modifier.weight(1f).clip(shp(14.dp)).background(Sand).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(shortHours(m), fontFamily = Display, fontSize = 20.sp, maxLines = 1, softWrap = false,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(l, color = Muted, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
            }
    }
}

/** Horas en corto para casillas estrechas: "15 h", "24,5 h" (sin minutos para que nunca se parta). */
fun shortHours(minutes: Int): String {
    val h = minutes / 60.0
    val r = Math.round(h * 2) / 2.0
    return if (r % 1.0 == 0.0) "${r.toInt()} h" else String.format(java.util.Locale("es", "ES"), "%.1f h", r)
}
