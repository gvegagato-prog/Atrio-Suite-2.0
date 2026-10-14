@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Mic

/** Elegir plataforma (juegos) o formato (libros): chips rápidos y un campo libre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformPicker(kind: Kind, value: String, onChange: (String) -> Unit) {
    // En categorías propias no hay opciones rápidas: el campo libre siempre está a la vista.
    var custom by remember { mutableStateOf(kind.platforms.isEmpty() || (value.isNotBlank() && value !in kind.platforms)) }
    Column(Modifier.fillMaxWidth()) {
        if (kind.platforms.isNotEmpty()) {
            Text(kind.platformWord, color = UiSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
        }
        if (kind.platforms.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            kind.platforms.forEach { p ->
                SoftChip(p, value == p && !custom, onPanel = true) {
                    custom = false
                    onChange(if (value == p) "" else p)
                }
            }
            if (kind.platforms.isNotEmpty()) SoftChip("Otra…", custom, onPanel = true) { custom = !custom; if (!custom) onChange("") }
        }
        if (custom) {
            if (kind.platforms.isNotEmpty()) Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value, onChange, label = { Text(kind.platformWord) }, singleLine = true,
                shape = shp(14.dp), modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Horas jugadas con botones de media hora. */
@Composable
fun MinutesEditor(minutes: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Horas jugadas", color = UiSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SoftChip("−1h", false, onPanel = true) { onChange((minutes - 60).coerceAtLeast(0)) }
            SoftChip("−½", false, onPanel = true) { onChange((minutes - 30).coerceAtLeast(0)) }
            Text(
                hoursText(minutes), fontFamily = Display, fontSize = 20.sp,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp)
            )
            SoftChip("+½", false, onPanel = true) { onChange(minutes + 30) }
            SoftChip("+1h", false, onPanel = true) { onChange(minutes + 60) }
        }
    }
}

/** Número de páginas del libro. */
@Composable
fun PagesField(pages: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        if (pages > 0) pages.toString() else "",
        { t -> onChange(t.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0) },
        label = { Text("Páginas") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = shp(14.dp), modifier = Modifier.fillMaxWidth()
    )
}

/** Diálogo para recomendar algo a tu pareja con una nota opcional. */
@Composable
fun RecommendDialog(partnerName: String, itemName: String, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recomendar a $partnerName", fontFamily = Display) },
        text = {
            Column {
                Text("Le aparecerá $itemName en su menú, con lo que le escribas.", color = Muted)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    note, { note = it }, label = { Text("¿Por qué le va a gustar? (opcional)") },
                    shape = shp(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
                )
            }
        },
        confirmButton = { TextButton({ onSend(note.trim()) }) { Text("Recomendar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
        shape = shp(24.dp)
    )
}

/** Pastilla con la reacción que ha puesto tu pareja a una ficha tuya. */
@Composable
fun TheirReaction(name: String, emoji: String) {
    Row(
        Modifier.background(Sand, pillShape()).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(6.dp))
        Text("$name ha reaccionado", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Botones de micrófono y de "Pulir" para un texto.
 * Lo dictado se limpia con reglas fijas (quita muletillas y palabras repetidas, sin internet ni IA) y se añade al final.
 */
@Composable
fun VoiceTools(text: String, about: String, onText: (String) -> Unit) {
    var before by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    val current by androidx.compose.runtime.rememberUpdatedState(text)

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val said = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) {
            before = current
            onText(listOf(current.trim(), Polish.local(said)).filter { it.isNotEmpty() }.joinToString(" "))
        }
    }

    Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill(Icons.Rounded.Mic, "Dictar", dark = true) {
                val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Cuenta qué te ha parecido")
                }
                try { launcher.launch(i) } catch (e: Exception) { msg = "Este dispositivo no tiene dictado por voz." }
            }
            if (text.isNotBlank()) {
                ActionPill(Icons.Rounded.Spellcheck, "Pulir") {
                    before = current
                    onText(Polish.local(current))
                }
            }
            val b = before
            if (b != null) {
                Text("Deshacer", color = ColGeneral, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.pop { onText(b); before = null }.padding(8.dp))
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = UiSoft, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Botón para rellenar el estudio buscándolo en internet. */
@Composable
fun DetectStudio(name: String, onFound: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { Store(ctx) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var state by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SoftChip(if (state == "…") "Buscando…" else "Detectar estudio", false, onPanel = true) {
            if (state == "…") return@SoftChip
            state = "…"
            scope.launch {
                val s = Studio.detect(name, store.rawgKey)
                if (s != null) { onFound(s); state = "" } else state = "No lo he encontrado"
            }
        }
        if (state.isNotEmpty() && state != "…") {
            Spacer(Modifier.width(8.dp))
            Text(state, color = UiSoft, fontSize = 14.sp)
        }
    }
}
