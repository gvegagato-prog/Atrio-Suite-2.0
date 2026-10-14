package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val MS_DAY = 86_400_000L

fun today(): Long = LocalDate.now().toEpochDay()

fun fmtDay(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, day: Long, modifier: Modifier = Modifier, onSet: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = UiSoft, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            if (day > 0L) fmtDay(day) else "Elegir fecha",
            Modifier.clip(shp(12.dp)).background(UiTint).clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            fontWeight = FontWeight.Medium
        )
        if (day > 0L) {
            TextButton({ onSet(0L) }) { Text("Quitar", color = UiSoft, fontSize = 12.sp) }
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = if (day > 0L) day * MS_DAY else null)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    val ms = state.selectedDateMillis
                    if (ms != null) onSet(ms / MS_DAY)
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton({ open = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun DatesRow(start: Long, end: Long, onStart: (Long) -> Unit, onEnd: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        DateField("Inicio", start, Modifier.weight(1f), onStart)
        DateField("Fin", end, Modifier.weight(1f), onEnd)
    }
}
