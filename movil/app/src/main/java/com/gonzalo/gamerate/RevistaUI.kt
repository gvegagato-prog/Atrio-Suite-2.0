package com.gonzalo.gamerate

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Las piezas comunes de la revista en el móvil, las mismas que en el PC (revista.css): el enlace en versalitas, el
 * «← Volver», las pestañas (versalitas, la elegida en tinta con el subrayado de acento y el número en acento) y los
 * filtros (versalitas, el elegido en acento y subrayado). Todas las pantallas usan estas, para que se vean igual.
 */

/** Un enlace de acción en versalitas («BUSCAR», «EDITAR», «+ AÑADIR»…). */
@Composable
fun EnlaceRevista(texto: String, modifier: Modifier = Modifier, acento: Boolean = false, onClick: () -> Unit) {
    Text(texto.uppercase(), color = if (acento) ColGeneral else Charcoal, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1,
        modifier = modifier.semantics { role = Role.Button }.press(haptic = false, onClick = onClick).padding(vertical = 10.dp))
}

/** «← VOLVER» (o «← ATRIO» en lo que cuelga de la portada): siempre el mismo, arriba a la izquierda. */
@Composable
fun VolverRevista(onClick: () -> Unit, texto: String = "Volver") = EnlaceRevista("← $texto", onClick = onClick)

/** Las pestañas de una página: versalitas; la elegida en tinta con el subrayado de acento; su número, en acento. */
@Composable
fun PestanasRevista(nombres: List<String>, sel: Int, modifier: Modifier = Modifier, cuentas: List<Int?>? = null, onSel: (Int) -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Row(Modifier.semantics { selected = on; role = Role.Tab }.press(haptic = false) { onSel(i) }
                    .drawBehind { if (on) drawRect(ColGeneral, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx())) }
                    .padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(n.uppercase(), color = if (on) Charcoal else Muted, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, maxLines = 1)
                    cuentas?.getOrNull(i)?.let { c -> Text(" $c", color = ColGeneral, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1) }
                }
            }
        }
        Filete(1.dp, Line)
    }
}

/** Filtros u opciones de una sola elección, en una fila que se desliza: versalitas; el elegido en acento y subrayado. */
@Composable
fun FiltrosRevista(opciones: List<String>, sel: Int, modifier: Modifier = Modifier, onSel: (Int) -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        opciones.forEachIndexed { i, o -> FiltroRevista(o, i == sel) { onSel(i) } }
    }
}

/** Un filtro suelto (que se enciende y se apaga): versalitas, encendido en acento y subrayado. */
@Composable
fun FiltroRevista(texto: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(texto.uppercase(), color = if (on) ColGeneral else Charcoal, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1,
        modifier = modifier.semantics { selected = on }.press(haptic = false, onClick = onClick).padding(vertical = 9.dp)
            .drawBehind { if (on) drawRect(ColGeneral, topLeft = Offset(0f, size.height - 5.dp.toPx()), size = Size(size.width, 1.5.dp.toPx())) })
}

/** Las dos pestañas de Ajustes en el móvil (una sola pantalla con dos partes, como las pestañas de Ajustes del PC). */
@Composable
fun PestanasAjustes(sel: Int, ir: (Screen) -> Unit) =
    PestanasRevista(listOf("General", "Servicios y copias"), sel, Modifier.padding(bottom = 14.dp)) { if (it != sel) ir(if (it == 0) Screen.Profile else Screen.Settings) }
