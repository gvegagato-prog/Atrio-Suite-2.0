@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Añadir algo a mano: en una categoría tuya sin buscador, o cuando lo que buscas no aparece (juegos, libros,
 * películas, series). Se escribe el nombre y se eligen portada y fondo de tus archivos o de la galería.
 */
@Composable
fun CustomAddScreen(
    kind: Kind, forPlay: Boolean,
    /** Elegir una imagen: de tus archivos (true) o de la galería (false). */
    onPhoto: (files: Boolean, (String) -> Unit) -> Unit,
    onCreate: (name: String, creator: String, cover: String, hero: String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var creator by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    var hero by remember { mutableStateOf("") }
    // Para qué se está eligiendo imagen ahora (null: nada): false portada, true fondo
    var asking by remember { mutableStateOf<Boolean?>(null) }
    asking?.let { forHero ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(if (forHero) "Foto de fondo" else "Foto de portada", fontFamily = Display) },
            text = { Text("¿De dónde la sacas?") },
            confirmButton = { TextButton({ asking = null; onPhoto(true) { if (forHero) hero = it else cover = it } }) { Text("De mis archivos") } },
            dismissButton = { TextButton({ asking = null; onPhoto(false) { if (forHero) hero = it else cover = it } }) { Text("De la galería") } }
        )
    }
    Column(
        Modifier.fillMaxSize().imePadding().wrapContentWidth().widthIn(max = 560.dp).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onBack)
            Column {
                Overline(kind.title)
                Text("Añadir", fontFamily = Display, fontSize = 26.sp, lineHeight = 30.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        val shape = shp(12.dp)
        Box(
            Modifier.align(Alignment.CenterHorizontally).width(150.dp).aspectRatio(2f / 3f).clip(shape)
                .background(Sand).border(1.dp, Line, shape).pop { asking = false },
            contentAlignment = Alignment.Center
        ) {
            if (cover.isNotEmpty()) {
                AsyncImage(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Muted, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Foto de portada", color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (kind.hasHero) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(3.1f).clip(shape).background(Sand).border(1.dp, Line, shape).pop { asking = true },
                contentAlignment = Alignment.Center
            ) {
                if (hero.isNotEmpty()) AsyncImage(hero, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Muted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Foto de fondo (opcional)", color = Muted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true,
            shape = shp(14.dp)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            creator, { creator = it }, Modifier.fillMaxWidth(), label = { Text("${kind.creatorWord} (opcional)") },
            singleLine = true, shape = shp(14.dp)
        )
        Spacer(Modifier.height(20.dp))
        Button(
            { if (name.isNotBlank()) onCreate(name.trim(), creator.trim(), cover, hero) },
            enabled = name.isNotBlank(), shape = pillShape(),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text(if (forPlay) "Añadir a la lista" else "Valorar ahora") }
        Spacer(Modifier.height(10.dp))
        Text(
            "Las fotos que elijas solo se ven en este móvil: no se suben a internet, así que tu pareja y el PC verán la ficha sin ellas.",
            color = Muted, fontSize = 12.sp
        )
    }
}

/** Crear o editar una categoría propia: nombre, lo que se valora, cómo se llama "en curso" y su color. */
@Composable
fun KindDialog(
    initial: Kind?, items: Int,
    onSave: (title: String, criteria: List<String>, slots: List<Int>, progress: String, color: Int, source: String) -> Unit,
    onDelete: (() -> Unit)?, onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    val crit = remember { mutableStateOf((initial?.labels?.drop(1) ?: emptyList()) + List(4) { "" }) }
    var progress by remember { mutableStateOf(initial?.statuses?.getOrNull(1) ?: "") }
    var color by remember { mutableStateOf(initial?.color?.takeIf { it != 0 } ?: Swatches[6].second) }
    var sure by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(initial?.source ?: "") }
    val hints = listOf("Historia", "Actuación", "Música", "Ritmo")
    val oldCount = initial?.crit ?: 0
    // Cada criterio recuerda de qué casilla viene, para que sus notas no se mezclen al editar.
    val filledIdx = (0 until 4).filter { crit.value[it].isNotBlank() }
    val filled = filledIdx.map { crit.value[it].trim() }
    val slots = filledIdx.map { if (it < oldCount) it else -1 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva categoría" else "Editar categoría", fontFamily = Display) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Nombre (p. ej. Películas)") },
                    singleLine = true, shape = shp(14.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text("Qué valoras", fontFamily = Display, fontSize = 17.sp)
                Text(
                    "Además de la nota general, de 1 a 4 cosas." + if (initial != null) " Si vacías un criterio, se borran sus notas." else "",
                    color = Muted, fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                for (i in 0 until 4) {
                    OutlinedTextField(
                        crit.value[i], { v -> crit.value = crit.value.toMutableList().also { it[i] = v } },
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        placeholder = { Text(hints[i], color = Muted) }, label = { Text("Criterio ${i + 1}") },
                        singleLine = true, shape = shp(14.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    progress, { progress = it }, Modifier.fillMaxWidth(),
                    label = { Text("Lo que tienes empezado se llama…") }, placeholder = { Text("Viendo", color = Muted) },
                    singleLine = true, shape = shp(14.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text("Buscar en internet", fontFamily = Display, fontSize = 17.sp)
                Text("Para añadir buscando el título y elegir portada y fondo. Sin buscador, se añade a mano con una foto.",
                    color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to "A mano", SRC_MOVIE to "Películas (TMDB)", SRC_TV to "Series (TVmaze)").forEach { (v, l) ->
                        SoftChip(l, source == v, onPanel = true) { source = v }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Color", fontFamily = Display, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Swatches.forEach { (_, c) ->
                        val on = c == color
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                .border(if (on) 2.dp else 0.dp, Charcoal, CircleShape).pop { color = c },
                            contentAlignment = Alignment.Center
                        ) { if (on) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(16.dp))
                    if (!sure) {
                        TextButton({ sure = true }) { Text("Borrar categoría", color = ColGeneral) }
                    } else {
                        Text(
                            when (items) {
                                0 -> "No tiene fichas. ¿La borro?"
                                1 -> "Se borrará también su única ficha. No se puede deshacer."
                                else -> "Se borrarán también sus $items fichas. No se puede deshacer."
                            },
                            color = ColGeneral, fontSize = 13.sp
                        )
                        Row {
                            TextButton({ onDelete() }) { Text("Sí, borrar", color = ColGeneral) }
                            TextButton({ sure = false }) { Text("No") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                {
                    if (filled.isEmpty()) onSave(title.trim(), listOf(hints[0]), listOf(-1), progress.trim().ifEmpty { "En curso" }, color, source)
                    else onSave(title.trim(), filled, slots, progress.trim().ifEmpty { "En curso" }, color, source)
                },
                enabled = title.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
        shape = shp(24.dp)
    )
}
