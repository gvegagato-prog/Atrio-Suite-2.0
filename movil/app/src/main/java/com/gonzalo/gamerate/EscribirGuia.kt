@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Un paso de la guía: qué es, cómo, el botón de acción y si se marca solo (auto) al detectar lo hecho. */
class Paso(val id: Int, val titulo: String, val auto: Boolean, val accion: String, val que: String, val como: List<String>, val hecho: String)

/** Lo que la guía necesita saber de una obra (se junta igual en el PC y aquí). */
class DatosGuia(val obra: JSONObject, val personajes: Int, val resumenes: Int, val dias: Int, val palabras: Int, val ahora: Long)

/** El estado de un paso: [estado] es «hecho», «saltado», «curso» (el reposo empezado) o «pendiente». */
class EstadoPaso(val id: Int, val estado: String, val aMano: Boolean, val dias: Int = 0)

/**
 * La guía para escribir una novela paso a paso: los pasos, sus textos y cómo se sabe lo que ya llevas hecho. Es la misma
 * que escritorio/src/renderer/revista/js/escribir-guia.js (los casos de herramientas/casos/guia.json los prueban los dos).
 * En la obra se guarda gOn (1 = activada) y g1…g11 (1 = hecho a mano, 2 = saltado) y, en el reposo, g7t (cuándo empezó).
 */
object GuiaNovela {
    const val DIA = 86_400_000L
    const val FUENTES = "Basada en guías de Grammarly, MasterClass y Escritor Flexible, el método Snowflake de Randy Ingermanson y las clases de Brandon Sanderson. Las cantidades y plazos son orientativos."
    val PASOS = listOf(
        Paso(1, "Una idea pequeña", true, "Escribir mi frase",
            "No hace falta tener la historia entera. Basta una frase: quién quiere qué y qué se lo impide.",
            listOf("Escríbela en una sola frase, corta y sin nombres propios.",
                "Si solo tienes una imagen o una escena, anótala igual: de ahí sale la frase.",
                "No busques la idea perfecta: se puede cambiar más adelante."),
            "Tienes tu frase."),
        Paso(2, "Tus personajes", true, "Crear un personaje",
            "Para cada protagonista: qué quiere, qué se lo impide y qué pasa si fracasa.",
            listOf("Empieza solo por quien lleva la historia.",
                "Rellena «Quiere», «Se lo impide» y «Si fracasa» en su ficha.",
                "Los demás personajes pueden esperar a que los necesites."),
            "Ya tienes al menos un personaje."),
        Paso(3, "Un esquema ligero", true, "Crear los tres actos",
            "Una lista corta de lo que pasa, no un guion detallado. Lo habitual son tres actos: planteamiento, nudo y desenlace.",
            listOf("Atrio crea una nota «Esquema de la historia», fuera del manuscrito, con tres apartados: Planteamiento, Nudo y Desenlace.",
                "Escribe bajo cada uno una o dos líneas: qué pasa.",
                "Si planificar te agobia, haz un esquema mínimo y sigue: se puede ampliar mientras escribes."),
            "Tienes el esquema (al menos tres resúmenes)."),
        Paso(4, "Promesa, progreso y recompensa", false, "Anotar mis respuestas",
            "Una idea de las clases de Brandon Sanderson: el principio promete (tono, género, conflicto), el medio avanza y el final cumple lo prometido.",
            listOf("¿Qué le prometes al lector en las primeras páginas?",
                "¿Qué cambia en cada tramo para que el medio avance y no dé vueltas?",
                "¿Cómo cumple el final esas promesas?",
                "No es una plantilla rígida: es una brújula."),
            "Ya lo tienes pensado."),
        Paso(5, "Un hábito realista", true, "Poner mi meta diaria",
            "Una meta pequeña que cumplas todos los días pesa más que una grande que abandones.",
            listOf("Elige una cantidad que te parezca casi demasiado fácil (por ejemplo 300 palabras; es una sugerencia de Atrio, no de las guías).",
                "Si puedes, escribe a la misma hora.",
                "Un sprint de 10 o 25 minutos ayuda a empezar."),
            "Ya has escrito en tres días distintos."),
        Paso(6, "El primer borrador", true, "Seguir escribiendo",
            "Escribir hasta el final sin corregir. Será imperfecto, y está bien: se arregla después.",
            listOf("No releas ni corrijas mientras escribes: apunta una nota y sigue.",
                "Si te atascas, salta a una escena que sí sepas escribir.",
                "El objetivo es llegar al final, no que quede bonito."),
            "Has llegado a la meta de palabras."),
        Paso(7, "Dejarlo reposar", false, "Empezar el reposo",
            "Aparta el borrador unas semanas antes de releerlo, para leerlo casi como un lector (consejo de Atrio, sin una fuente concreta).",
            listOf("Pulsa «Empezar el reposo»: Atrio cuenta los días.",
                "Mientras tanto no lo abras: lee, descansa o piensa en otra historia.",
                "Cuando quieras volver, márcalo como hecho."),
            "Ya has descansado del borrador."),
        Paso(8, "Revisar lo grande", false, "Ver el manuscrito",
            "Primera pasada: lo que afecta a la historia. La gramática se deja para la siguiente.",
            listOf("Agujeros de trama: ¿algo no tiene sentido o se olvida por el camino?",
                "Personajes: ¿cambian y quieren cosas claras?",
                "Ritmo: ¿hay tramos que se arrastran o que van demasiado deprisa?",
                "Marca cada escena como «Revisión» (y luego «Final») según la repasas."),
            "Has revisado la historia."),
        Paso(9, "Revisar lo pequeño", false, "Ver el manuscrito",
            "Segunda pasada: frases, gramática y repeticiones.",
            listOf("Lee en voz alta: lo que suena mal, suele fallar.",
                "Cambia las palabras repetidas; el editor te da sinónimos en el menú del botón derecho (PC) o en «⋯» (móvil).",
                "Mira la ortografía."),
            "Has revisado el texto."),
        Paso(10, "Lectores de prueba", false, "Exportar el manuscrito",
            "Que alguien lea tu novela y te diga qué funciona: lectores beta o un editor profesional.",
            listOf("Exporta el manuscrito en Word o PDF.",
                "Pide a dos o tres personas que lo lean y te digan dónde se aburrieron o se perdieron.",
                "Pregunta y escucha: no te defiendas."),
            "Ya te han leído."),
        Paso(11, "Corrección final y publicar", false, "Exportar el manuscrito",
            "Última lectura para las erratas y decidir si buscas editorial o publicas tú.",
            listOf("Corrige las erratas con calma.",
                "El Word «manuscrito» sigue el formato que suelen pedir las editoriales, pero cada una tiene sus normas: léelas antes de enviar.",
                "Decide: editorial o autopublicación."),
            "Tu novela está lista.")
    )

    private fun num(o: JSONObject, k: String): Double = o.opt(k)?.toString()?.toDoubleOrNull() ?: 0.0

    /** El esquema vive en una nota de la obra (no en el manuscrito): tres apartados con su etiqueta. */
    const val TITULO_ESQUEMA = "Esquema de la historia"
    const val PLANTILLA_ESQUEMA = "Planteamiento:\n\n\nNudo:\n\n\nDesenlace:\n"
    private val ETIQUETA_ESQUEMA = Regex("^\\s*(Planteamiento|Nudo|Desenlace)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)

    /** Cuántos de los tres apartados tienen algo escrito debajo. */
    fun seccionesEsquema(texto: String): Int {
        val cuerpo = LinkedHashMap<String, String>()
        var actual: String? = null
        for (l in texto.split("\n")) {
            val m = ETIQUETA_ESQUEMA.matchEntire(l)
            if (m != null) { actual = m.groupValues[1].lowercase(); cuerpo[actual] = (cuerpo[actual] ?: "") + m.groupValues[2] }
            else if (actual != null) cuerpo[actual] = cuerpo[actual] + l
        }
        return cuerpo.values.count { it.isNotBlank() }
    }

    fun automatico(id: Int, d: DatosGuia): Boolean = when (id) {
        1 -> d.obra.optString("idea").trim().isNotEmpty()
        2 -> d.personajes >= 1
        3 -> d.resumenes >= 3
        5 -> d.dias >= 3
        6 -> num(d.obra, "meta") > 0 && d.palabras >= num(d.obra, "meta")
        else -> false
    }

    fun estados(d: DatosGuia): List<EstadoPaso> = PASOS.map { p ->
        val g = num(d.obra, "g${p.id}").toInt()
        when {
            g == 1 -> EstadoPaso(p.id, "hecho", true)
            g == 2 -> EstadoPaso(p.id, "saltado", true)
            automatico(p.id, d) -> EstadoPaso(p.id, "hecho", false)
            p.id == 7 && num(d.obra, "g7t") > 0 -> EstadoPaso(p.id, "curso", false, maxOf(0L, ((d.ahora - num(d.obra, "g7t").toLong()) / DIA)).toInt())
            else -> EstadoPaso(p.id, "pendiente", false)
        }
    }

    /** El primer paso que falta (el que toca ahora), o null si ya está todo. */
    fun siguiente(est: List<EstadoPaso>): Int? = est.firstOrNull { it.estado == "pendiente" || it.estado == "curso" }?.id
    fun hechos(est: List<EstadoPaso>): Int = est.count { it.estado == "hecho" }
    fun activa(obra: Doc): Boolean = obra.i("gOn") == 1
}

// ---------------------------------------------------------------- Las acciones de cada paso

/**
 * Lo que hace el botón del paso [id]: unos abren un diálogo y otros hacen algo y ya (crear los tres actos, empezar el
 * reposo, abrir la última escena…). Se pone en la pantalla de la obra mientras [id] no sea null y se llama a [onCerrar] al acabar.
 */
@Composable
fun AccionGuia(id: Int, e: Escritos, obra: Doc, abrir: (String) -> Unit, irManuscrito: () -> Unit, exportar: () -> Unit, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val pid = obra.id
    when (id) {
        1 -> {
            var frase by remember { mutableStateOf(obra.s("idea")) }
            HojaEscribir("Tu historia en una frase", onCerrar) {
                Text("Quién quiere qué, y qué se lo impide. Corta y sin nombres propios si puedes; no tiene que ser perfecta.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                CampoEscribir(frase, { frase = it }, "La frase", lineas = 3)
                Text("Ejemplo: Una aprendiz de hechicera sube a la torre donde nadie vuelve para salvar a su hermana, y descubre que lo que la espera arriba es ella misma.", color = Muted, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                BotonEscribir("Guardar") { if (frase.isNotBlank()) { e.edita(pid) { put("idea", frase.trim()) }; onCerrar() } }
            }
        }
        2 -> FichaDialogo(e, pid, null, "personaje", onCerrar, camposNuevos = listOf("Quiere", "Se lo impide", "Si fracasa", "Edad"))
        4 -> {
            var nota by remember { mutableStateOf<Doc?>(null) }
            LaunchedEffect(Unit) {
                nota = e.notas(pid).firstOrNull { it.s("titulo") == "Promesas de la historia" } ?: e.nuevo(Escritos.T_NOTA, pid) {
                    put("tipo", "idea"); put("titulo", "Promesas de la historia"); put("color", COLORES_ESCRITO[2])
                    put("texto", "Promesa: ¿qué le prometo al lector en las primeras páginas (tono, género, conflicto)?\n\nProgreso: ¿qué cambia en cada tramo para que el medio avance?\n\nRecompensa: ¿cómo cumple el final lo que prometí?\n")
                }
            }
            nota?.let { NotaDialogo(e, pid, e.get(it.id), onCerrar = onCerrar) }
        }
        3 -> {
            var nota by remember { mutableStateOf<Doc?>(null) }
            LaunchedEffect(Unit) {
                nota = e.notas(pid).firstOrNull { it.s("titulo") == GuiaNovela.TITULO_ESQUEMA } ?: e.nuevo(Escritos.T_NOTA, pid) {
                    put("tipo", "idea"); put("titulo", GuiaNovela.TITULO_ESQUEMA); put("color", COLORES_ESCRITO[1]); put("texto", GuiaNovela.PLANTILLA_ESQUEMA)
                }
            }
            nota?.let { NotaDialogo(e, pid, e.get(it.id), onCerrar = onCerrar) }
        }
        5 -> {
            var dia by remember { mutableStateOf(obra.i("diaria").takeIf { it > 0 }?.toString() ?: "300") }
            HojaEscribir("Tu meta de cada día", onCerrar) {
                Text("Elige una cantidad que te parezca casi demasiado fácil. Mejor cumplirla todos los días que abandonar una grande.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                Row(Modifier.horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("150", "300", "500", "1000").forEach { v -> SoftChip(v, dia == v) { dia = v } } }
                CampoEscribir(dia, { dia = it.filter(Char::isDigit).take(6) }, "Palabras al día", numero = true)
                Text("Para empezar, unas 300 (una sugerencia de Atrio). Siempre puedes subirla.", color = Muted, fontSize = 12.sp)
                BotonEscribir("Guardar") { e.edita(pid) { put("diaria", dia.toIntOrNull() ?: 0) }; onCerrar() }
            }
        }
        else -> LaunchedEffect(id) {
            when (id) {
                6 -> { val s = e.escenasDe(pid).maxByOrNull { it.u }; if (s != null) abrir(s.id) else irManuscrito() }
                7 -> e.edita(pid) { put("g7t", System.currentTimeMillis()) }
                8, 9 -> irManuscrito()
                10, 11 -> exportar()
            }
            onCerrar()
        }
    }
}

// ---------------------------------------------------------------- La tarjeta del manuscrito y la pestaña Guía

fun datosGuia(e: Escritos, pid: String): DatosGuia {
    val o = e.get(pid)?.o ?: JSONObject()
    val res = e.capitulos(pid).count { it.s("resumen").isNotBlank() } + e.escenasDe(pid).count { it.s("resumen").isNotBlank() } + e.hitos(pid).size +
        e.notas(pid).filter { it.s("titulo") == GuiaNovela.TITULO_ESQUEMA }.sumOf { GuiaNovela.seccionesEsquema(it.s("texto")) }
    return DatosGuia(o, e.fichas(pid).count { it.s("tipo") == "personaje" }, res, e.diasEscritos(pid), e.palabrasObra(pid), System.currentTimeMillis())
}

/** El paso que toca ahora, arriba del manuscrito (solo con la guía activada). */
@Composable
fun TarjetaGuia(e: Escritos, obra: Doc, ver: Int, hacer: (Int) -> Unit, verGuia: () -> Unit) {
    if (!GuiaNovela.activa(obra)) return
    val est = remember(ver, obra.u) { GuiaNovela.estados(datosGuia(e, obra.id)) }
    val id = GuiaNovela.siguiente(est) ?: return
    val p = GuiaNovela.PASOS.first { it.id == id }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(shp(16.dp)).background(Crudo).border(1.dp, ColGeneral, shp(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("GUÍA · PASO $id DE ${GuiaNovela.PASOS.size}", color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        FitTitle(p.titulo, 22.sp, 25.sp, color = Charcoal, conEdicion = false)
        Text(p.que, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            BotonEscribir(p.accion) { hacer(id) }
            BotonEscribir("Ver la guía", suave = true) { verGuia() }
        }
    }
}

/** La pestaña Guía: los 11 pasos, con el que toca abierto. */
@Composable
fun GuiaObra(e: Escritos, obra: Doc, ver: Int, hacer: (Int) -> Unit) {
    val pid = obra.id
    val d = remember(ver, obra.u) { datosGuia(e, pid) }
    val est = remember(d) { GuiaNovela.estados(d) }
    val sig = GuiaNovela.siguiente(est)
    val hechos = GuiaNovela.hechos(est)
    var abierto by rememberSaveable(pid) { mutableIntStateOf(sig ?: 0) }
    // El paso abierto sigue al que toca: al acabar uno, se abre el siguiente
    LaunchedEffect(sig) { abierto = sig ?: 0 }
    val color = colorEscrito(obra.s("color"))
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = dockSpace(40.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item("cab") {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$hechos de ${GuiaNovela.PASOS.size} pasos", color = Muted, fontSize = 13.sp)
                Bar(hechos.toFloat() / GuiaNovela.PASOS.size, color, height = 4.dp)
                if (obra.s("idea").isNotBlank()) Text(obra.s("idea"), fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 25.sp, color = Charcoal,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (GuiaNovela.activa(obra)) "La guía está activada: el paso que toca sale arriba del manuscrito." else "La guía está desactivada: no sale nada en el manuscrito.", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    SoftChip(if (GuiaNovela.activa(obra)) "Desactivar" else "Activar", false) { e.edita(pid) { put("gOn", if (GuiaNovela.activa(obra)) 0 else 1) } }
                }
            }
        }
        items(GuiaNovela.PASOS, key = { it.id }) { p ->
            val s = est[p.id - 1]
            val abre = abierto == p.id
            fun marca(v: Int) { abierto = if (p.id < GuiaNovela.PASOS.size) p.id + 1 else 0; e.edita(pid) { put("g${p.id}", v) } }
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Row(Modifier.fillMaxWidth().press(haptic = false) { abierto = if (abre) 0 else p.id }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val hecho = s.estado == "hecho"
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (hecho) Charcoal else Color.Transparent).border(1.5.dp, if (p.id == sig) ColGeneral else if (hecho) Charcoal else Muted, CircleShape), contentAlignment = Alignment.Center) {
                        Text(when (s.estado) { "hecho" -> "✓"; "saltado" -> "–"; "curso" -> "…"; else -> p.id.toString() }, color = if (hecho) Paper else if (p.id == sig) ColGeneral else Muted, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    Text(p.titulo, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (hecho) Muted else Charcoal, modifier = Modifier.weight(1f))
                    Text(when { s.estado == "hecho" -> "HECHO"; s.estado == "saltado" -> "SALTADO"; s.estado == "curso" -> "REPOSO: ${s.dias} ${if (s.dias == 1) "DÍA" else "DÍAS"}"; p.id == sig -> "TOCA AHORA"; else -> "" },
                        color = if (p.id == sig || s.estado == "curso") ColGeneral else Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                }
                if (abre) Column(Modifier.fillMaxWidth().padding(start = 40.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.que, color = Charcoal, fontSize = 15.sp, lineHeight = 21.sp)
                    p.como.forEach { Text("•  $it", color = Charcoal, fontSize = 14.sp, lineHeight = 20.sp) }
                    if (p.id == 6) {
                        val meta = obra.i("meta")
                        if (meta > 0) Bar((d.palabras.toFloat() / meta).coerceIn(0f, 1f), color, height = 4.dp)
                        Text("${cifraE(d.palabras)}${if (meta > 0) " de ${cifraE(meta)}" else ""} palabras", color = Muted, fontSize = 12.sp)
                    }
                    if (p.id == 5) Text("Has escrito en ${d.dias} ${if (d.dias == 1) "día" else "días"} distintos (con 3 se da por hecho).", color = Muted, fontSize = 12.sp)
                    if (s.estado == "hecho") Text(p.hecho, color = ColGeneral, fontSize = 14.sp, fontStyle = FontStyle.Italic)
                    if (s.estado == "curso") Text("Llevas ${s.dias} ${if (s.dias == 1) "día" else "días"} sin abrirlo (unas semanas suele ir bien).", color = Muted, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.estado != "curso") BotonEscribir(p.accion) { hacer(p.id) }
                        if (s.estado == "curso") { BotonEscribir("Ya he descansado") { marca(1) }; BotonEscribir("Empezar de nuevo", suave = true) { e.edita(pid) { put("g7t", System.currentTimeMillis()) } } }
                        if (s.estado == "pendiente") BotonEscribir(if (p.auto) "Marcar como hecho" else "Ya lo tengo", suave = true) { marca(1) }
                        if (s.estado == "pendiente") BotonEscribir("Saltar", suave = true) { marca(2) }
                        if (s.aMano) BotonEscribir("Quitar la marca", suave = true) { e.edita(pid) { put("g${p.id}", 0) } }
                    }
                }
            }
        }
        item("fuentes") { Text(GuiaNovela.FUENTES, color = Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 18.dp)) }
    }
}
