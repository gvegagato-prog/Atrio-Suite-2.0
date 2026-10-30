@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gonzalo.gamerate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject

/** Los colores con los que se marcan obras, fichas y notas. */
val COLORES_ESCRITO = listOf("#B0102F", "#D9822B", "#C9A227", "#5B8C5A", "#3E8E9E", "#4A6FA5", "#7B5EA7", "#A8577E", "#6B6B6B")

fun colorEscrito(hex: String, def: Color = ColGeneral): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(def)

fun singularFicha(t: String) = when (t) { "personaje" -> "Personaje"; "lugar" -> "Lugar"; "objeto" -> "Objeto"; "mundo" -> "Mundo"; else -> "Otro" }

val ESTADOS_ESCENA = listOf("Borrador", "Revisión", "Final")
fun colorEstado(e: Int): Color = when (e) { 0 -> Color(0xFFB8B2A6); 1 -> Color(0xFFD9A441); else -> Color(0xFF5B8C5A) }

val TIPOS_FICHA = listOf("personaje" to "Personajes", "lugar" to "Lugares", "objeto" to "Objetos", "mundo" to "Mundo", "otro" to "Otros")
val TIPOS_NOTA = listOf("nota" to "Notas", "idea" to "Ideas", "resumen" to "Resúmenes", "esquema" to "Esquemas", "investigacion" to "Investigación", "plantilla" to "Plantillas")
/** El nombre de un tipo de nota en singular («Resumen», «Idea»…). */
fun singularNota(t: String) = when (t) { "idea" -> "Idea"; "resumen" -> "Resumen"; "esquema" -> "Esquema"; "investigacion" -> "Investigación"; "plantilla" -> "Plantilla"; else -> "Nota" }

/** Elegir varios (vincular a partes y capítulos): chips que se encienden y se apagan. */
@Composable
fun VinculosEscribir(e: Escritos, pid: String, elegidos: List<String>, onCambia: (List<String>) -> Unit) {
    val destinos = remember(e.version, pid) {
        e.partes(pid).map { it.id to "Parte · ${it.s("titulo").ifBlank { "Sin título" }}" } +
            e.capitulos(pid).mapIndexed { i, c -> c.id to "Cap. ${i + 1} · ${c.s("titulo").ifBlank { "Sin título" }}" }
    }
    if (destinos.isEmpty()) return
    EtiquetaEscribir("Vinculada a")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        destinos.forEach { (id, n) -> SoftChip(n, id in elegidos) { onCambia(if (id in elegidos) elegidos - id else elegidos + id) } }
    }
    Text("Sale también en la página de esa parte o ese capítulo.", color = Muted, fontSize = 12.sp)
}
fun Doc.vin(): List<String> = lista("vin")
fun JSONObject.ponVin(l: List<String>) { put("vin", JSONArray().also { a -> l.forEach { a.put(it) } }) }

/** Una hoja a pantalla completa: cabecera con título y cerrar, y el cuerpo con scroll. */
@Composable
fun HojaEscribir(titulo: String, onCerrar: () -> Unit, acciones: @Composable RowScope.() -> Unit = {}, contenido: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // (FitTitle: la letra baja hasta que cabe la palabra más larga; nunca se parte una palabra por la mitad)
                FitTitle(titulo, 28.sp, 31.sp, Modifier.weight(1f), color = Charcoal, maxLines = 2, conEdicion = false)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = acciones)
                // (cerrar con texto, como el resto de la revista y los diálogos del PC)
                EnlaceRevista("Cerrar", Modifier.padding(start = 12.dp).semantics { contentDescription = "Cerrar" }, onClick = onCerrar)
            }
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(if (Skin.revista) 3.dp else 1.dp).background(if (Skin.revista) Charcoal else Line))
            Column(Modifier.weight(1f).fillMaxWidth().widthIn(max = 720.dp).align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = contenido)
        }
    }
}

@Composable
fun CampoEscribir(valor: String, onCambia: (String) -> Unit, etiqueta: String, lineas: Int = 1, modifier: Modifier = Modifier.fillMaxWidth(), numero: Boolean = false, mayus: Boolean = true) {
    OutlinedTextField(
        valor, onCambia, modifier, label = { Text(etiqueta) }, shape = shp(14.dp),
        singleLine = lineas == 1, minLines = lineas, maxLines = if (lineas == 1) 1 else 40,
        keyboardOptions = KeyboardOptions(capitalization = if (mayus && !numero) KeyboardCapitalization.Sentences else KeyboardCapitalization.None, keyboardType = if (numero) KeyboardType.Number else KeyboardType.Text)
    )
}

@Composable
fun ColoresEscribir(elegido: String, onElige: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        COLORES_ESCRITO.forEach { c ->
            val on = c.equals(elegido, true)
            Box(Modifier.size(30.dp).clip(CircleShape).background(colorEscrito(c)).border(if (on) 3.dp else 0.dp, Charcoal, CircleShape).press(haptic = false) { onElige(c) })
        }
    }
}

@Composable
fun EtiquetaEscribir(t: String) = Text(t.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)

@Composable
fun BotonEscribir(texto: String, modifier: Modifier = Modifier, peligro: Boolean = false, suave: Boolean = false, onClick: () -> Unit) {
    Text(
        texto.uppercase(), color = if (suave) (if (peligro) ColGeneral else Charcoal) else Paper, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
        modifier = modifier.clip(pillShape()).then(if (suave) Modifier.border(1.dp, if (peligro) ColGeneral else Charcoal, pillShape()) else Modifier.background(if (peligro) ColGeneral else Charcoal))
            .press(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp)
    )
}

/** Una opción de una lista desplegable. */
@Composable
fun SelectorEscribir(etiqueta: String, valor: String, opciones: List<Pair<String, String>>, onElige: (String) -> Unit, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(Modifier.fillMaxWidth().clip(shp(14.dp)).border(1.dp, Line, shp(14.dp)).press(haptic = false) { abierto = true }.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text(etiqueta, color = Muted, fontSize = 11.sp)
            Text(opciones.find { it.first == valor }?.second ?: "—", color = Charcoal, fontSize = 15.sp, maxLines = 1)
        }
        DropdownMenu(abierto, { abierto = false }, containerColor = Paper) {
            opciones.forEach { (k, n) -> DropdownMenuItem({ Text(n, fontWeight = if (k == valor) FontWeight.Bold else FontWeight.Normal) }, { abierto = false; onElige(k) }) }
        }
    }
}

// ---------------------------------------------------------------- Obra

@Composable
fun ObraDialogo(e: Escritos, obra: Doc?, onCerrar: () -> Unit, onCreada: (Doc) -> Unit = {}) {
    var titulo by remember { mutableStateOf(obra?.s("titulo").orEmpty()) }
    var autor by remember { mutableStateOf(obra?.s("autor").orEmpty()) }
    var genero by remember { mutableStateOf(obra?.s("genero").orEmpty()) }
    var sinopsis by remember { mutableStateOf(obra?.s("sinopsis").orEmpty()) }
    var color by remember { mutableStateOf(obra?.s("color")?.ifBlank { null } ?: COLORES_ESCRITO[0]) }
    var meta by remember { mutableStateOf(obra?.i("meta")?.takeIf { it > 0 }?.toString() ?: if (obra == null) "80000" else "") }
    var diaria by remember { mutableStateOf(obra?.i("diaria")?.takeIf { it > 0 }?.toString() ?: if (obra == null) "500" else "") }
    var guiada by remember { mutableStateOf(if (obra == null) true else GuiaNovela.activa(obra)) }
    var borrar by remember { mutableStateOf(false) }
    HojaEscribir(if (obra == null) "Nueva obra" else "Ajustes de la obra", onCerrar) {
        CampoEscribir(titulo, { titulo = it }, "Título")
        CampoEscribir(autor, { autor = it }, "Autor o autora")
        CampoEscribir(genero, { genero = it }, "Género (fantasía, thriller…)")
        CampoEscribir(sinopsis, { sinopsis = it }, "Sinopsis", lineas = 4)
        EtiquetaEscribir("Color")
        ColoresEscribir(color) { color = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoEscribir(meta, { meta = it.filter(Char::isDigit).take(7) }, "Meta de palabras", modifier = Modifier.weight(1f), numero = true)
            CampoEscribir(diaria, { diaria = it.filter(Char::isDigit).take(6) }, "Palabras al día", modifier = Modifier.weight(1f), numero = true)
        }
        Text("Las metas son tuyas: sirven para ver cuánto llevas, no para presionarte.", color = Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftChip(if (guiada) "Guiarme paso a paso ✓" else "Guiarme paso a paso", guiada) { guiada = !guiada }
            Text("Una guía de 11 pasos, de la idea al manuscrito final. Puedes quitarla cuando quieras.", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonEscribir(if (obra == null) "Crear" else "Guardar") {
                if (titulo.isBlank()) return@BotonEscribir
                val cambia: JSONObject.() -> Unit = {
                    put("titulo", titulo.trim()); put("autor", autor.trim()); put("genero", genero.trim()); put("sinopsis", sinopsis.trim()); put("color", color)
                    put("meta", meta.toIntOrNull() ?: 0); put("diaria", diaria.toIntOrNull() ?: 0); put("gOn", if (guiada) 1 else 0)
                }
                if (obra == null) {
                    val o = e.nuevo(Escritos.T_OBRA, "", cambia)
                    // Un capítulo y una escena para empezar a escribir ya
                    val c = e.nuevo(Escritos.T_CAP, o.id) { put("titulo", "Capítulo 1") }
                    e.nuevo(Escritos.T_ESCENA, o.id) { put("cid", c.id); put("titulo", "Escena 1") }
                    if (guiada) EscribirHub.guiaAlEntrar = o.id to System.currentTimeMillis()
                    onCreada(o)
                } else e.edita(obra.id, cambia)
                onCerrar()
            }
            if (obra != null) BotonEscribir(if (obra.b("arch")) "Sacar del archivo" else "Archivar", suave = true) { e.edita(obra.id) { put("arch", if (obra.b("arch")) 0 else 1) }; onCerrar() }
        }
        if (obra != null) {
            Spacer(Modifier.height(20.dp))
            if (!borrar) BotonEscribir("Borrar la obra", peligro = true, suave = true) { borrar = true }
            else {
                Text("Irá a la papelera con sus capítulos, escenas, fichas y notas, también en tus otros dispositivos. Podrás recuperarla durante 30 días.", color = ColGeneral, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonEscribir("Sí, a la papelera", peligro = true) { aLaPapelera(e, obra); onCerrar(); EscribirHub.obraBorrada = obra.id }
                    BotonEscribir("No", suave = true) { borrar = false }
                }
            }
        }
    }
}

/** Para avisar a la pantalla de que la obra que tenía abierta se ha borrado. */
object EscribirHub {
    var obraBorrada by mutableStateOf("")
    /** Cuántos editores hay abiertos (durante una transición puede haber dos un momento). */
    var editores by mutableIntStateOf(0)
    /** El editor está abierto (la app esconde el dock). */
    val enEditor: Boolean get() = editores > 0
    /** Una escena que abrir al entrar en Escribir (desde la portada), y cuándo se pidió. */
    var abrir by mutableStateOf<Pair<String, Long>?>(null)
    /** Una obra recién creada con la guía: se abre en la pestaña Guía (y cuándo se pidió). */
    var guiaAlEntrar: Pair<String, Long>? = null
    /** «Ver en el árbol» desde una parte o un capítulo: la ficha que enseñar al volver a la obra. */
    var irArbol by mutableStateOf<String?>(null)
    /** El manuscrito en lista (y no en cuadrados). */
    var listaManuscrito by mutableStateOf(false)
    /** Avisar abajo con «Deshacer» (lo pone la app). */
    var deshacer: ((String, () -> Unit) -> Unit)? = null
}

@Composable
fun CapituloDialogo(e: Escritos, pid: String, cap: Doc?, onCerrar: () -> Unit, aidNuevo: String = "") {
    var titulo by remember { mutableStateOf(cap?.s("titulo").orEmpty()) }
    var aid by remember { mutableStateOf(cap?.s("aid") ?: aidNuevo) }
    var resumen by remember { mutableStateOf(cap?.s("resumen").orEmpty()) }
    var color by remember { mutableStateOf(cap?.s("color").orEmpty()) }
    var borrar by remember { mutableStateOf(false) }
    val partes = remember(e.version, pid) { listOf("" to "Ninguna (capítulo suelto)") + e.partes(pid).map { it.id to it.s("titulo").ifBlank { "Sin título" } } }
    HojaEscribir(if (cap == null) "Nuevo capítulo" else "Capítulo", onCerrar) {
        CampoEscribir(titulo, { titulo = it }, "Título")
        if (partes.size > 1) SelectorEscribir("Parte", aid, partes, { aid = it }, Modifier.fillMaxWidth())
        CampoEscribir(resumen, { resumen = it }, "Resumen", lineas = 3)
        EtiquetaEscribir("Color de su cuadrado")
        ColoresEscribir(color) { color = it }
        BotonEscribir("Guardar") {
            if (cap == null) {
                val c = e.nuevo(Escritos.T_CAP, pid) { put("titulo", titulo.trim().ifBlank { "Capítulo ${e.capitulos(pid).size + 1}" }); put("resumen", resumen.trim()); if (color.isNotBlank()) put("color", color); if (aid.isNotBlank()) put("aid", aid) }
                e.nuevo(Escritos.T_ESCENA, pid) { put("cid", c.id); put("titulo", "Escena 1") }
            } else {
                e.edita(cap.id) { put("titulo", titulo.trim()); put("resumen", resumen.trim()); if (color.isNotBlank()) put("color", color) else remove("color") }
                if (cap.s("aid") != aid) e.mueveCapitulo(cap.id, aid, null, null)
            }
            onCerrar()
        }
        if (cap != null) {
            if (!borrar) BotonEscribir("Borrar el capítulo", peligro = true, suave = true) { borrar = true }
            else {
                val n = e.escenas(cap.id).size
                Text("Irá a la papelera con ${if (n == 1) "su escena" else "sus $n escenas"}. Podrás recuperarlo durante 30 días.", color = ColGeneral, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonEscribir("Sí, a la papelera", peligro = true) { aLaPapelera(e, cap); onCerrar() }
                    BotonEscribir("No", suave = true) { borrar = false }
                }
            }
        }
    }
}

private val NUMEROS_PARTE = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

/** Una parte (acto, libro…): título, resumen y color. */
@Composable
fun ParteDialogo(e: Escritos, pid: String, parte: Doc?, onCerrar: () -> Unit, onBorrada: () -> Unit = {}) {
    val porDefecto = "Parte " + (NUMEROS_PARTE.getOrNull(e.partes(pid).size) ?: "${e.partes(pid).size + 1}")
    var titulo by remember { mutableStateOf(parte?.s("titulo").orEmpty()) }
    var resumen by remember { mutableStateOf(parte?.s("resumen").orEmpty()) }
    var color by remember { mutableStateOf(parte?.s("color")?.ifBlank { null } ?: COLORES_ESCRITO[0]) }
    var borrar by remember { mutableStateOf(false) }
    HojaEscribir(if (parte == null) "Nueva parte" else "Parte", onCerrar) {
        CampoEscribir(titulo, { titulo = it }, "Título ($porDefecto, Primer acto, Libro dos…)")
        CampoEscribir(resumen, { resumen = it }, "Qué pasa en esta parte", lineas = 4)
        EtiquetaEscribir("Color")
        ColoresEscribir(color) { color = it }
        BotonEscribir(if (parte == null) "Crear" else "Guardar") {
            val cambia: JSONObject.() -> Unit = { put("titulo", titulo.trim().ifBlank { porDefecto }); put("resumen", resumen.trim()); put("color", color) }
            if (parte == null) e.nuevo(Escritos.T_PARTE, pid, cambia) else e.edita(parte.id, cambia)
            onCerrar()
        }
        if (parte != null) {
            val n = remember(e.version) { e.capitulos(pid).count { it.s("aid") == parte.id } }
            if (!borrar) BotonEscribir("Borrar la parte", peligro = true, suave = true) { borrar = true }
            else {
                Text(if (n > 0) "Irá a la papelera con ${if (n == 1) "su capítulo" else "sus $n capítulos"} (y sus escenas). Podrás recuperarla durante 30 días." else "Irá a la papelera. Podrás recuperarla durante 30 días.", color = ColGeneral, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonEscribir("Sí, a la papelera", peligro = true) { aLaPapelera(e, parte); onCerrar(); onBorrada() }
                    BotonEscribir("No", suave = true) { borrar = false }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Fichas

@Composable
fun FichaDialogo(e: Escritos, pid: String, ficha: Doc?, tipoNuevo: String = "personaje", onCerrar: () -> Unit, abrirEscena: (Doc) -> Unit = {}, camposNuevos: List<String>? = null, onCreada: (Doc) -> Unit = {}) {
    var nombre by remember { mutableStateOf(ficha?.s("nombre").orEmpty()) }
    var tipo by remember { mutableStateOf(ficha?.s("tipo")?.ifBlank { null } ?: tipoNuevo) }
    var alias by remember { mutableStateOf(ficha?.s("alias").orEmpty()) }
    var resumen by remember { mutableStateOf(ficha?.s("resumen").orEmpty()) }
    var texto by remember { mutableStateOf(ficha?.s("texto").orEmpty()) }
    var color by remember { mutableStateOf(ficha?.s("color")?.ifBlank { null } ?: COLORES_ESCRITO[5]) }
    val campos = remember {
        mutableStateListOf<Pair<String, String>>().also { l ->
            ficha?.o?.optJSONArray("campos")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { l.add(it.optString("k") to it.optString("v")) } }
            if (l.isEmpty() && ficha == null && camposNuevos != null) camposNuevos.forEach { l.add(it to "") }
            else if (l.isEmpty() && ficha == null) when (tipo) { "personaje" -> { l.add("Edad" to ""); l.add("Aspecto" to ""); l.add("Quiere" to ""); l.add("Teme" to "") }; "lugar" -> { l.add("Clima" to ""); l.add("Gente" to "") } }
        }
    }
    var vin by remember { mutableStateOf(ficha?.vin().orEmpty()) }
    var tam by remember { mutableIntStateOf(ficha?.i("tam") ?: 0) }
    HojaEscribir(if (ficha == null) "Nueva ficha" else nombre.ifBlank { "Ficha" }, onCerrar) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollSafe()) {
            TIPOS_FICHA.forEach { (k, n) -> SoftChip(singularFicha(k), tipo == k) { tipo = k } }
        }
        CampoEscribir(nombre, { nombre = it }, "Nombre")
        CampoEscribir(alias, { alias = it }, "Otros nombres (separados por comas): así se reconoce en tus escenas")
        CampoEscribir(resumen, { resumen = it }, "En una frase", lineas = 2)
        EtiquetaEscribir("Datos")
        campos.forEachIndexed { i, (k, v) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CampoEscribir(k, { campos[i] = it to v }, "Dato", modifier = Modifier.weight(0.8f))
                CampoEscribir(v, { campos[i] = k to it }, "Valor", modifier = Modifier.weight(1.4f))
                IconPill(Icons.Rounded.Close, "Quitar") { campos.removeAt(i) }
            }
        }
        BotonEscribir("Añadir dato", suave = true, modifier = Modifier.align(Alignment.Start)) { campos.add("" to "") }
        CampoEscribir(texto, { texto = it }, "Notas largas (historia, personalidad, detalles…)", lineas = 8)
        EtiquetaEscribir("Color")
        ColoresEscribir(color) { color = it }
        EtiquetaEscribir("Tamaño en el árbol")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollSafe()) {
            listOf(0 to "Según el tipo", 1 to "Pequeña", 2 to "Mediana", 3 to "Grande").forEach { (k, n) -> SoftChip(n, tam == k) { tam = k } }
        }
        VinculosEscribir(e, pid, vin) { vin = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonEscribir("Guardar") {
                if (nombre.isBlank()) return@BotonEscribir
                val cambia: JSONObject.() -> Unit = {
                    put("tipo", tipo); put("nombre", nombre.trim()); put("alias", alias.trim()); put("resumen", resumen.trim()); put("texto", texto); put("color", color)
                    put("campos", JSONArray().also { a -> campos.filter { it.first.isNotBlank() || it.second.isNotBlank() }.forEach { a.put(JSONObject().put("k", it.first).put("v", it.second)) } })
                    ponVin(vin); if (tam > 0) put("tam", tam) else remove("tam")
                }
                if (ficha == null) onCreada(e.nuevo(Escritos.T_FICHA, pid, cambia)) else e.edita(ficha.id, cambia)
                onCerrar()
            }
            if (ficha != null) BotonEscribir("Borrar", peligro = true, suave = true) { aLaPapelera(e, ficha); onCerrar() }
        }
    }
}

/** Una fila de chips que se desliza si no cabe. */
@Composable
fun Modifier.horizontalScrollSafe(): Modifier = this.horizontalScroll(rememberScrollState())

// ---------------------------------------------------------------- Notas y línea temporal

@Composable
fun NotaDialogo(e: Escritos, pid: String, nota: Doc?, tipoNuevo: String = "nota", onCerrar: () -> Unit, vinNuevo: List<String> = emptyList()) {
    var titulo by remember { mutableStateOf(nota?.s("titulo").orEmpty()) }
    var texto by remember { mutableStateOf(nota?.s("texto").orEmpty()) }
    var tipo by remember { mutableStateOf(nota?.s("tipo")?.ifBlank { null } ?: tipoNuevo) }
    var color by remember { mutableStateOf(nota?.s("color")?.ifBlank { null } ?: COLORES_ESCRITO[2]) }
    var fija by remember { mutableStateOf(nota?.b("fija") == true) }
    var vin by remember { mutableStateOf(nota?.vin() ?: vinNuevo) }
    HojaEscribir(if (nota == null) "Nueva nota" else titulo.ifBlank { "Nota" }, onCerrar) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScrollSafe()) {
            TIPOS_NOTA.forEach { (k, n) -> SoftChip(n, tipo == k) { tipo = k } }
        }
        if (tipo == "plantilla") Text("Una plantilla es un texto que puedes tener al lado mientras escribes o insertar en una escena (una ficha de escena, una lista de comprobación…).", color = Muted, fontSize = 12.sp)
        CampoEscribir(titulo, { titulo = it }, "Título")
        CampoEscribir(texto, { texto = it }, "Texto", lineas = 10)
        EtiquetaEscribir("Color")
        ColoresEscribir(color) { color = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SoftChip(if (fija) "Fijada arriba" else "Fijar arriba", fija) { fija = !fija }
        }
        VinculosEscribir(e, pid, vin) { vin = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonEscribir("Guardar") {
                if (titulo.isBlank() && texto.isBlank()) return@BotonEscribir
                val cambia: JSONObject.() -> Unit = { put("tipo", tipo); put("titulo", titulo.trim()); put("texto", texto); put("color", color); put("fija", if (fija) 1 else 0); ponVin(vin) }
                if (nota == null) e.nuevo(Escritos.T_NOTA, pid, cambia) else e.edita(nota.id, cambia)
                onCerrar()
            }
            if (nota != null) BotonEscribir("Borrar", peligro = true, suave = true) { aLaPapelera(e, nota); onCerrar() }
        }
    }
}

/** Un suceso de la línea temporal: qué pasa, cuándo, en qué línea (la principal o una rama), la escena y una nota. */
@Composable
fun HitoDialogo(e: Escritos, pid: String, hito: Doc?, onCerrar: () -> Unit, ramaNueva: String = "", ordenNuevo: Double? = null) {
    var titulo by remember { mutableStateOf(hito?.s("titulo").orEmpty()) }
    var cuando by remember { mutableStateOf(hito?.s("cuando").orEmpty()) }
    var nota by remember { mutableStateOf(hito?.s("nota").orEmpty()) }
    var eid by remember { mutableStateOf(hito?.s("eid").orEmpty()) }
    var rama by remember { mutableStateOf(hito?.s("rama") ?: ramaNueva) }
    var color by remember { mutableStateOf(hito?.s("color").orEmpty()) }
    val escenas = remember(e.version) { listOf("" to "Ninguna") + e.escenasDe(pid).map { it.id to it.s("titulo").ifBlank { "Sin título" } } }
    val ramas = remember(e.version) { listOf("" to "La línea principal") + e.ramas(pid).map { it.id to "Rama · " + it.s("titulo").ifBlank { "Sin título" } } }
    HojaEscribir(if (hito == null) "Nuevo suceso" else "Editar el suceso", onCerrar) {
        CampoEscribir(titulo, { titulo = it }, "Qué pasa")
        CampoEscribir(cuando, { cuando = it }, "Cuándo (día 3, primavera del año 12, 1887…)")
        if (ramas.size > 1) SelectorEscribir("En qué línea", rama, ramas, { rama = it }, Modifier.fillMaxWidth())
        CampoEscribir(nota, { nota = it }, "Qué más", lineas = 5)
        SelectorEscribir("Se cuenta en la escena", eid, escenas, { eid = it }, Modifier.fillMaxWidth())
        EtiquetaEscribir("Color")
        ColoresEscribir(color) { color = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonEscribir("Guardar") {
                if (titulo.isBlank()) return@BotonEscribir
                val cambia: JSONObject.() -> Unit = {
                    put("titulo", titulo.trim()); put("cuando", cuando.trim()); put("nota", nota)
                    if (eid.isBlank()) remove("eid") else put("eid", eid)
                    if (rama.isBlank()) remove("rama") else put("rama", rama)
                    if (color.isBlank()) remove("color") else put("color", color)
                    if (hito == null && ordenNuevo != null) put("orden", ordenNuevo)
                }
                if (hito == null) e.nuevo(Escritos.T_HITO, pid, cambia) else e.edita(hito.id, cambia)
                onCerrar()
            }
            if (hito != null) BotonEscribir("Borrar", peligro = true, suave = true) { aLaPapelera(e, hito); onCerrar() }
        }
    }
}

/** Una rama de la línea: nombre, de qué va, de qué suceso de la principal nace, si vuelve (después de cuál) y su color. */
@Composable
fun RamaDialogo(e: Escritos, pid: String, rama: Doc?, onCerrar: () -> Unit, desdeNuevo: Double? = null, onCreada: (Doc) -> Unit = {}) {
    var titulo by remember { mutableStateOf(rama?.s("titulo").orEmpty()) }
    var texto by remember { mutableStateOf(rama?.s("texto").orEmpty()) }
    var color by remember { mutableStateOf(rama?.s("color")?.ifBlank { null } ?: COLORES_ESCRITO[(e.ramas(pid).size + 3) % COLORES_ESCRITO.size]) }
    val principal = remember(e.version) { e.hitos(pid).filter { it.s("rama").isEmpty() || e.get(it.s("rama")) == null } }
    fun clave(d: Double) = d.toString()
    var desde by remember { mutableStateOf(rama?.d("desde")?.let { clave(it) } ?: desdeNuevo?.let { clave(it) } ?: "") }
    var hasta by remember { mutableStateOf(rama?.o?.opt("hasta")?.toString()?.toDoubleOrNull()?.let { clave(it) } ?: "") }
    val opciones = principal.map { clave(it.orden) to it.s("titulo").ifBlank { "Sin título" } }
    var borrar by remember { mutableStateOf(false) }
    HojaEscribir(if (rama == null) "Nueva rama" else "Editar la rama", onCerrar) {
        CampoEscribir(titulo, { titulo = it }, "Nombre (la huida de Juan, el pasado de Ana…)")
        CampoEscribir(texto, { texto = it }, "De qué va", lineas = 3)
        SelectorEscribir("Nace después de", desde, listOf("" to "Desde el principio") + opciones, { desde = it }, Modifier.fillMaxWidth())
        SelectorEscribir("Vuelve a la principal después de", hasta, listOf("" to "No vuelve (acaba en su último suceso)") + opciones, { hasta = it }, Modifier.fillMaxWidth())
        EtiquetaEscribir("Color")
        ColoresEscribir(color) { color = it }
        BotonEscribir(if (rama == null) "Crear" else "Guardar") {
            val d = desde.toDoubleOrNull() ?: ((principal.firstOrNull()?.orden ?: 1000.0) - 1)
            val cambia: JSONObject.() -> Unit = {
                put("titulo", titulo.trim().ifBlank { "Rama" }); put("texto", texto); put("color", color); put("desde", d)
                val hh = hasta.toDoubleOrNull(); if (hh == null) remove("hasta") else put("hasta", hh)
            }
            if (rama == null) onCreada(e.nuevo(Escritos.T_RAMA, pid, cambia)) else e.edita(rama.id, cambia)
            onCerrar()
        }
        if (rama != null) {
            if (!borrar) BotonEscribir("Borrar la rama", peligro = true, suave = true) { borrar = true }
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Irá a la papelera con sus sucesos.", color = ColGeneral, fontSize = 13.sp, modifier = Modifier.weight(1f))
                BotonEscribir("Sí, a la papelera", peligro = true) { aLaPapelera(e, rama); onCerrar() }
            }
        }
    }
}

/** Una unión del árbol entre dos fichas: la palabra de la línea, todo lo que se quiera contar, flecha y color. */
@Composable
fun RelacionDialogo(e: Escritos, pid: String, rel: Doc?, onCerrar: () -> Unit, aNueva: String = "", bNueva: String = "") {
    val fichas = remember(e.version) { e.fichas(pid).map { it.id to it.s("nombre").ifBlank { "Sin nombre" } } }
    var a by remember { mutableStateOf(rel?.s("a") ?: aNueva) }
    var b by remember { mutableStateOf(rel?.s("b") ?: bNueva) }
    var titulo by remember { mutableStateOf(rel?.s("titulo").orEmpty()) }
    var texto by remember { mutableStateOf(rel?.s("texto").orEmpty()) }
    var flecha by remember { mutableIntStateOf(rel?.i("flecha") ?: 0) }
    var color by remember { mutableStateOf(rel?.s("color").orEmpty()) }
    fun nombre(id: String) = fichas.find { it.first == id }?.second ?: "…"
    HojaEscribir(if (rel == null) "Nueva unión" else "Editar la unión", onCerrar) {
        SelectorEscribir("Una", a, fichas, { a = it }, Modifier.fillMaxWidth())
        SelectorEscribir("Con", b, fichas, { b = it }, Modifier.fillMaxWidth())
        CampoEscribir(titulo, { titulo = it }, "La palabra de la línea (enemigos, amantes, hogar…)")
        CampoEscribir(texto, { texto = it }, "Qué son el uno para el otro (opcional)", lineas = 7)
        EtiquetaEscribir("Flecha")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftChip("Sin flecha", flecha == 0) { flecha = 0 }
            SoftChip("De ${nombre(a)} hacia ${nombre(b)}", flecha == 1) { flecha = 1 }
            SoftChip("De ${nombre(b)} hacia ${nombre(a)}", flecha == 2) { flecha = 2 }
        }
        EtiquetaEscribir("Color de la línea")
        ColoresEscribir(color) { color = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonEscribir(if (rel == null) "Unir" else "Guardar") {
                if (a.isBlank() || b.isBlank() || a == b || titulo.isBlank()) return@BotonEscribir
                // (la flecha se guarda siempre de «a» hacia «b»)
                val (x, y) = if (flecha == 2) b to a else a to b
                val cambia: JSONObject.() -> Unit = { put("a", x); put("b", y); put("flecha", if (flecha > 0) 1 else 0); put("titulo", titulo.trim()); put("texto", texto); if (color.isBlank()) remove("color") else put("color", color) }
                if (rel == null) e.nuevo(Escritos.T_REL, pid, cambia) else e.edita(rel.id, cambia)
                onCerrar()
            }
            if (rel != null) BotonEscribir("Borrar la unión", peligro = true, suave = true) { aLaPapelera(e, rel); onCerrar() }
        }
    }
}
