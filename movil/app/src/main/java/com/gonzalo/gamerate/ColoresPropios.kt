package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Apariencia → A tu manera: el color que quieras para el fondo, el texto y el acento (tono, saturación y brillo, o su código),
 * y las letras de los títulos y del texto. Se ve al momento; se guarda al soltar.
 */

private fun hexDe(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
private fun largo(c: Color) = c.toArgb().toLong() and 0xFFFFFFFFL

/** Un color elegido a mano: la muestra y su código; al tocarlo se abren el tono, la saturación, el brillo y el código. */
@Composable
fun SelectorColor(nombre: String, valor: Long, actual: Color, onCambia: (Long) -> Unit, onGuarda: () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { abierto = !abierto }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(actual).border(1.dp, Charcoal))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(nombre, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Charcoal)
            Text(if (valor == 0L) "Automático" else hexDe(Color(valor)), color = Muted, fontSize = 12.sp)
        }
        if (valor != 0L) EnlaceRevista("Quitar", Modifier.padding(end = 12.dp)) { onCambia(0L); onGuarda(); abierto = false }
        Text(if (abierto) "▴" else "▾", color = Muted, fontSize = 16.sp)
    }
    if (!abierto) return
    // Tono, saturación y brillo de lo que se ve ahora (el automático, si aún no hay ninguno elegido)
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(actual.toArgb(), it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    var texto by remember { mutableStateOf(hexDe(actual)) }
    fun pon() { val c = Color.hsv(h, s, v); texto = hexDe(c); onCambia(largo(c)) }
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Barra("Tono", h / 360f, Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }), { h = it * 360f; pon() }, onGuarda)
        Barra("Saturación", s, Brush.horizontalGradient(listOf(Color.hsv(h, 0f, v), Color.hsv(h, 1f, v))), { s = it; pon() }, onGuarda)
        Barra("Brillo", v, Brush.horizontalGradient(listOf(Color.Black, Color.hsv(h, s, 1f))), { v = it; pon() }, onGuarda)
        OutlinedTextField(
            texto, { t ->
                texto = t.uppercase().take(7)
                Regex("^#?([0-9A-F]{6})$").find(texto)?.let { m ->
                    val c = Color(("FF" + m.groupValues[1]).toLong(16))
                    FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }.let { x -> h = x[0]; s = x[1]; v = x[2] }
                    onCambia(largo(c)); onGuarda()
                }
            },
            label = { Text("Código (#RRGGBB)") }, singleLine = true, shape = shp(12.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Una barra con su degradado debajo (tono, saturación o brillo). */
@Composable
private fun Barra(nombre: String, valor: Float, fondo: Brush, onCambia: (Float) -> Unit, onSuelta: () -> Unit) {
    Column {
        Text(nombre.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(12.dp).background(fondo).border(1.dp, Line))
            Slider(valor, onCambia, onValueChangeFinished = onSuelta, modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(thumbColor = Charcoal, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
        }
    }
}

/** Una letra elegida a mano: su nombre escrito en ella; al tocarla, la lista de letras (cada una en sí misma). */
@Composable
fun SelectorLetra(nombre: String, valor: String, opciones: List<String>, onCambia: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { abierto = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Aa", fontFamily = Skin.family(valor) ?: Display, fontSize = 28.sp, color = Charcoal, modifier = Modifier.width(50.dp))
        Column(Modifier.weight(1f)) {
            Text(nombre, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Charcoal)
            Text(valor.ifEmpty { "La de la edición" }, color = Muted, fontSize = 12.sp)
        }
        if (valor.isNotEmpty()) EnlaceRevista("Quitar", Modifier.padding(end = 12.dp)) { onCambia("") }
        Text("▾", color = Muted, fontSize = 16.sp)
    }
    if (abierto) AlertDialog(
        onDismissRequest = { abierto = false },
        title = { Text(nombre, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    Text("La de la edición", fontSize = 18.sp, color = if (valor.isEmpty()) ColGeneral else Charcoal,
                        modifier = Modifier.fillMaxWidth().clickable { onCambia(""); abierto = false }.padding(vertical = 10.dp))
                }
                items(opciones) { o ->
                    // (cada letra se baja de Google Fonts la primera vez: hasta entonces sale con la del sistema)
                    Text(o, fontFamily = Skin.family(o), fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (o == valor) ColGeneral else Charcoal,
                        modifier = Modifier.fillMaxWidth().clickable { onCambia(o); abierto = false }.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = { TextButton({ abierto = false }) { Text("Cerrar") } },
        shape = shp(20.dp)
    )
}
