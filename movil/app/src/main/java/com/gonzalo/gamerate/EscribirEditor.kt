@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gonzalo.gamerate

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ES = Locale("es", "ES")

/**
 * El editor de una escena: una página de libro donde escribir con el formato a la vista, con concentración, máquina de
 * escribir, deshacer, buscar y reemplazar, y un panel con la escena, las fichas, las notas, los borradores y el historial.
 * Se guarda solo (y sube con tu cuenta en cuanto hay red; sin conexión se queda en el móvil).
 */
@Composable
fun EscribirEditor(eid: String, onBack: () -> Unit, abrirEscena: (String) -> Unit) {
    val ctx = LocalContext.current
    val e = remember { Escritos.get(ctx) }
    val ver = e.version
    val escena = e.get(eid)
    if (escena == null) { LaunchedEffect(Unit) { onBack() }; return }
    val pid = escena.pid
    val obra = e.get(pid)
    val prefs = remember { ctx.getSharedPreferences("escribir", android.content.Context.MODE_PRIVATE) }
    var tam by remember { mutableFloatStateOf(prefs.getFloat("tam", 19f)) }
    var fuente by remember { mutableIntStateOf(prefs.getInt("fuente", 0)) }
    var maquina by remember { mutableStateOf(prefs.getBoolean("maquina", false)) }
    var tipografia by remember { mutableStateOf(prefs.getBoolean("tipografia", true)) }
    var sprint by remember { mutableStateOf(false) }
    var sinonimos by remember { mutableStateOf<Pair<IntRange, List<List<String>>>?>(null) }
    var avisoEd by remember { mutableStateOf("") }
    LaunchedEffect(avisoEd) { if (avisoEd.isNotEmpty()) { kotlinx.coroutines.delay(2500); avisoEd = "" } }
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Sinonimos.carga(ctx) } }
    var foco by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(false) }
    var buscar by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    var tf by remember(eid) { mutableStateOf(TextFieldValue(escena.s("texto"), TextRange(escena.s("texto").length))) }
    val historial = remember(eid) { HistorialTexto(tf) }
    var guardado by remember(eid) { mutableStateOf(escena.s("texto")) }
    val inicio = remember(eid) { Escritos.palabras(escena.s("texto")) }
    val ultimo by rememberUpdatedState(tf.text)
    var titulo by remember(eid) { mutableStateOf(escena.s("titulo")) }

    DisposableEffect(eid) {
        EscribirHub.editores++
        onDispose {
            EscribirHub.editores = (EscribirHub.editores - 1).coerceAtLeast(0)
            e.escribe(eid, ultimo)
            e.instantanea(eid, ultimo)
            Escritos.pronto(ctx)
        }
    }
    LaunchedEffect(eid) { e.instantanea(eid, escena.s("texto"), hueco = 3_600_000L) }
    // Se guarda a los 0,6 s de dejar de escribir; una instantánea (historial) cada 5 min
    LaunchedEffect(tf.text) { if (tf.text != guardado) { kotlinx.coroutines.delay(600); e.escribe(eid, tf.text); guardado = tf.text } }
    LaunchedEffect(eid) { while (true) { kotlinx.coroutines.delay(300_000); e.instantanea(eid, ultimo) } }
    LaunchedEffect(titulo) { if (titulo != escena.s("titulo")) { kotlinx.coroutines.delay(500); e.edita(eid) { put("titulo", titulo) } } }
    // Si otro dispositivo cambia esta escena y aquí no hay cambios sin guardar, se ve al momento
    LaunchedEffect(ver) {
        val r = e.get(eid)?.s("texto") ?: return@LaunchedEffect
        if (r != guardado && tf.text == guardado) {
            tf = TextFieldValue(r, TextRange(minOf(tf.selection.start, r.length)))
            guardado = r; historial.apunta(tf)
        }
    }

    fun cambia(v: TextFieldValue) { tf = v; historial.apunta(v) }
    fun pon(texto: String) { cambia(TextFieldValue(texto, TextRange(texto.length))) }
    // Lo que llega del teclado: si es un solo carácter, la tipografía automática (— « » …), que se deshace aparte
    fun teclea(v: TextFieldValue) {
        val antes = tf
        val c = v.selection.start
        if (tipografia && v.selection.collapsed && c >= 1 && v.text.length == antes.text.length + 1 && v.composition.let { it == null || it.end <= c - 3 } &&
            v.text.regionMatches(0, antes.text, 0, c - 1) && v.text.regionMatches(c, antes.text, c - 1, v.text.length - c)) {
            Tipografia.aplica(v.text, c, v.text[c - 1])?.let { (t, k) ->
                historial.apunta(v); historial.corte()
                val r = TextFieldValue(t, TextRange(k))
                tf = r; historial.apunta(r); historial.corte()
                return
            }
        }
        cambia(v)
    }
    fun abreSinonimos() {
        val r = Sinonimos.palabraEn(tf.text, tf.selection.start, tf.selection.end)
        if (r == null) { avisoEd = "Pon el cursor en una palabra"; return }
        val g = Sinonimos.busca(tf.text.substring(r.first, r.last + 1))
        if (g.isEmpty()) avisoEd = "Sin sinónimos para «${tf.text.substring(r.first, r.last + 1)}»" else sinonimos = r to g
    }

    // ---- Buscar y reemplazar
    var consulta by remember { mutableStateOf("") }
    var reemplazo by remember { mutableStateOf("") }
    var mayus by remember { mutableStateOf(false) }
    val rx = remember(consulta, mayus) { if (consulta.isEmpty()) null else Regex(Regex.escape(consulta), if (mayus) emptySet() else setOf(RegexOption.IGNORE_CASE)) }
    val coincidencias = remember(rx, tf.text) { rx?.findAll(tf.text)?.map { it.range }?.toList().orEmpty() }
    var actual by remember(consulta) { mutableIntStateOf(-1) }
    val foco1 = remember { FocusRequester() }
    fun salta(i: Int) {
        if (coincidencias.isEmpty()) return
        val k = ((i % coincidencias.size) + coincidencias.size) % coincidencias.size
        actual = k
        tf = tf.copy(selection = TextRange(coincidencias[k].first, coincidencias[k].last + 1))
        runCatching { foco1.requestFocus() }
    }

    val fichasObra = remember(ver, pid) { e.fichas(pid) }
    val nombres = remember(fichasObra) {
        val ns = fichasObra.flatMap { f -> listOf(f.s("nombre")) + f.s("alias").split(',') }.map { it.trim() }.filter { it.length >= 2 }.distinctBy { it.lowercase() }.sortedByDescending { it.length }
        if (ns.isEmpty()) null else Regex("(?<![\\p{L}\\p{N}])(" + ns.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }
    val familia = when (fuente) { 0 -> Display; 1 -> Body; else -> FontFamily.Monospace }
    val aspecto = AspectoTexto(Charcoal, Muted.copy(alpha = 0.6f), ColGeneral, Display, tam)
    val formato = remember(aspecto.tinta, aspecto.tenue, aspecto.acento, tam, nombres, rx) { FormatoMarkdown(aspecto, nombres, rx) }
    val palabras = remember(tf.text) { Escritos.palabras(tf.text) }
    val selPalabras = if (tf.selection.collapsed) 0 else Escritos.palabras(tf.text.substring(minOf(tf.selection.start, tf.selection.end), maxOf(tf.selection.start, tf.selection.end)))
    val hoy = e.escritas(Escritos.hoy(), pid)
    val diaria = obra?.i("diaria") ?: 0
    val metaEscena = escena.i("meta")

    BackHandler {
        when {
            panel -> panel = false
            buscar -> buscar = false
            foco -> foco = false
            else -> onBack()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Paper)) {
        val ancha = maxWidth >= 900.dp
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().statusBarsPadding().navigationBarsPadding().imePadding()) {
                if (!foco) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BackButton(onBack)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(obra?.s("titulo").orEmpty().uppercase(), color = ColGeneral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, maxLines = 1)
                            BasicTextField(
                                titulo, { titulo = it.replace("\n", "") }, singleLine = true,
                                textStyle = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Charcoal),
                                cursorBrush = SolidColor(ColGeneral),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                decorationBox = { inner -> Box { if (titulo.isEmpty()) Text("Sin título", color = Muted, fontFamily = Display, fontSize = 20.sp, fontStyle = FontStyle.Italic); inner() } }
                            )
                        }
                        IconPill(Icons.Rounded.ViewSidebar, "Panel de la escena", active = panel) { panel = !panel }
                        Spacer(Modifier.width(6.dp))
                        IconPill(Icons.Rounded.CenterFocusStrong, "Concentración") { foco = true }
                        Spacer(Modifier.width(6.dp))
                        Box {
                            IconPill(Icons.Rounded.MoreVert, "Más") { menu = true }
                            DropdownMenu(menu, { menu = false }, containerColor = Paper) {
                                DropdownMenuItem({ Text("Buscar y reemplazar") }, { menu = false; buscar = true })
                                DropdownMenuItem({ Text(if (SprintEscribir.activo()) "Terminar el sprint" else "Sprint de escritura…") }, { menu = false; if (SprintEscribir.activo()) SprintEscribir.termina(ctx, e, ya = true) else sprint = true })
                                DropdownMenuItem({ Text("Sinónimos de la palabra") }, { menu = false; abreSinonimos() })
                                DropdownMenuItem({ Text(if (maquina) "Máquina de escribir: sí" else "Máquina de escribir: no") }, { menu = false; maquina = !maquina; prefs.edit().putBoolean("maquina", maquina).apply() })
                                DropdownMenuItem({ Text(if (tipografia) "Tipografía automática (— « » …): sí" else "Tipografía automática (— « » …): no") }, { menu = false; tipografia = !tipografia; prefs.edit().putBoolean("tipografia", tipografia).apply() })
                                DropdownMenuItem({ Text("Letra más grande") }, { tam = (tam + 1f).coerceAtMost(30f); prefs.edit().putFloat("tam", tam).apply() })
                                DropdownMenuItem({ Text("Letra más pequeña") }, { tam = (tam - 1f).coerceAtLeast(14f); prefs.edit().putFloat("tam", tam).apply() })
                                DropdownMenuItem({ Text("Tipo de letra: " + listOf("con serifa", "de palo seco", "máquina")[fuente]) }, { fuente = (fuente + 1) % 3; prefs.edit().putInt("fuente", fuente).apply() })
                                DropdownMenuItem({ Text("Guardar una versión ahora") }, { menu = false; e.instantanea(eid, tf.text, fuerza = true) })
                                DropdownMenuItem({ Text("Compartir el texto") }, {
                                    menu = false
                                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, tf.text), "Compartir"))
                                })
                            }
                        }
                    }
                    // Hoy: lo escrito frente a la meta del día
                    if (diaria > 0) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Bar((hoy.toFloat() / diaria).coerceIn(0f, 1f), if (hoy >= diaria) Color5() else ColGeneral, height = 3.dp)
                    }
                    if (buscar) BarraBuscar(
                        consulta, { consulta = it }, reemplazo, { reemplazo = it }, mayus, { mayus = it }, coincidencias.size, actual,
                        onSig = { salta(actual + 1) }, onAnt = { salta(actual - 1) },
                        onEsta = {
                            if (actual in coincidencias.indices) {
                                val r = coincidencias[actual]
                                val v = TextFieldValue(tf.text.replaceRange(r.first, r.last + 1, reemplazo), TextRange(r.first + reemplazo.length))
                                cambia(v)
                            }
                        },
                        onEscena = { if (rx != null) cambia(TextFieldValue(rx.replace(tf.text, Regex.escapeReplacement(reemplazo)), TextRange(0))) },
                        onObra = { e.escribe(eid, tf.text); guardado = tf.text; e.reemplaza(pid, consulta, reemplazo, mayusculas = mayus); e.get(eid)?.s("texto")?.let { r -> tf = TextFieldValue(r, TextRange(0)); guardado = r } },
                        onCierra = { buscar = false; consulta = "" }
                    )
                }

                // ---- La página
                val scroll = rememberScrollState()
                var vista by remember { mutableIntStateOf(0) }
                var diseno by remember { mutableStateOf<TextLayoutResult?>(null) }
                val densidad = LocalDensity.current
                val margenSup = 18.dp
                // Máquina de escribir: la línea donde escribes se queda a un 40 % de la altura
                LaunchedEffect(tf.selection, diseno, maquina, foco) {
                    val d = diseno ?: return@LaunchedEffect
                    if (!maquina || vista == 0 || tf.selection.start > d.layoutInput.text.length) return@LaunchedEffect
                    val r = d.getCursorRect(tf.selection.start.coerceIn(0, d.layoutInput.text.length))
                    val destino = (r.top + with(densidad) { margenSup.toPx() } - vista * 0.4f).toInt().coerceAtLeast(0)
                    scroll.animateScrollTo(destino.coerceAtMost(scroll.maxValue))
                }
                Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { vista = it.height }) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.widthIn(max = 700.dp).fillMaxWidth().padding(horizontal = if (foco) 28.dp else 22.dp).padding(top = margenSup)) {
                            BasicTextField(
                                tf, { teclea(it) },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = (vista / densidad.density * 0.7f).dp).focusRequester(foco1)
                                    // Con teclado físico: Ctrl+B, Ctrl+I, Ctrl+Z, Ctrl+Y (o Ctrl+Mayús+Z), Ctrl+F, Ctrl+S (guarda una versión), Esc
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        if (ev.key == Key.Escape) { when { buscar -> { buscar = false; consulta = "" }; foco -> foco = false; else -> return@onPreviewKeyEvent false }; return@onPreviewKeyEvent true }
                                        if (!ev.isCtrlPressed && !ev.isMetaPressed) return@onPreviewKeyEvent false
                                        when (ev.key) {
                                            Key.B -> cambia(EdicionTexto.envuelve(tf, "**"))
                                            Key.I -> cambia(EdicionTexto.envuelve(tf, "*"))
                                            Key.Z -> (if (ev.isShiftPressed) historial.rehace() else historial.deshace())?.let { tf = it }
                                            Key.Y -> historial.rehace()?.let { tf = it }
                                            Key.F -> buscar = true
                                            Key.S -> { e.escribe(eid, tf.text); guardado = tf.text; e.instantanea(eid, tf.text, fuerza = true); avisoEd = "Versión guardada" }
                                            else -> return@onPreviewKeyEvent false
                                        }
                                        true
                                    },
                                textStyle = TextStyle(fontFamily = familia, fontSize = tam.sp, lineHeight = (tam * 1.62f).sp, color = Charcoal),
                                cursorBrush = SolidColor(ColGeneral),
                                visualTransformation = formato,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                onTextLayout = { diseno = it },
                                decorationBox = { inner -> Box { if (tf.text.isEmpty()) Text("Empieza aquí…", color = Muted, fontFamily = familia, fontSize = tam.sp, fontStyle = FontStyle.Italic); inner() } }
                            )
                            Spacer(Modifier.height(if (maquina) (vista / densidad.density * 0.55f).dp else 120.dp))
                        }
                    }
                    if (foco) Text("SALIR", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).press(haptic = false) { foco = false }.padding(6.dp))
                }
                if (!foco) {
                    // Contadores
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val sesion = palabras - inicio
                        Text(if (selPalabras > 0) "$selPalabras de $palabras palabras" else "$palabras palabras", color = Muted, fontSize = 12.sp)
                        Text("${maxOf(1, (palabras + 219) / 220)} min", color = Muted, fontSize = 12.sp)
                        if (sesion != 0) Text((if (sesion > 0) "+" else "") + "$sesion en esta sesión", color = if (sesion > 0) ColGeneral else Muted, fontSize = 12.sp)
                        if (metaEscena > 0) Text("meta $metaEscena", color = if (palabras >= metaEscena) Color5() else Muted, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        if (diaria > 0) Text("hoy $hoy/$diaria", color = if (hoy >= diaria) Color5() else Muted, fontSize = 12.sp)
                    }
                    if (SprintEscribir.activo(pid) || avisoEd.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (SprintEscribir.activo(pid)) TiraSprint(e) { SprintEscribir.termina(ctx, e, ya = true) }
                        Spacer(Modifier.weight(1f))
                        if (avisoEd.isNotEmpty()) Text(avisoEd, color = ColGeneral, fontSize = 12.sp)
                    }
                    BarraFormato(
                        puedeDeshacer = historial.puedeDeshacer, puedeRehacer = historial.puedeRehacer,
                        onDeshacer = { historial.deshace()?.let { tf = it } }, onRehacer = { historial.rehace()?.let { tf = it } },
                        onNegrita = { cambia(EdicionTexto.envuelve(tf, "**")) }, onCursiva = { cambia(EdicionTexto.envuelve(tf, "*")) },
                        onTitulo = { cambia(EdicionTexto.prefijoLinea(tf, "## ")) }, onCita = { cambia(EdicionTexto.prefijoLinea(tf, "> ")) },
                        onSalto = { cambia(EdicionTexto.salto(tf)) }, onComillas = { cambia(EdicionTexto.comillas(tf)) },
                        onRaya = { cambia(EdicionTexto.inserta(tf, "—")) }, onBuscar = { buscar = !buscar }, onSinonimos = { abreSinonimos() }
                    )
                }
            }
            if (ancha && panel && !foco) Column(Modifier.width(360.dp).fillMaxHeight().background(Crudo).statusBarsPadding().navigationBarsPadding()) {
                PanelEscena(e, escena, ver, onCierra = { panel = false }, inserta = { x -> cambia(EdicionTexto.inserta(tf, x)) }, pon = { pon(it) }, textoActual = { tf.text }, abrirEscena = abrirEscena)
            }
        }
        if (sprint) SprintDialogo(e, pid) { sprint = false }
        sinonimos?.let { (r, grupos) ->
            val palabra = tf.text.substring(r.first, (r.last + 1).coerceAtMost(tf.text.length))
            ModalBottomSheet({ sinonimos = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Crudo, shape = shp(28.dp)) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sinónimos de «$palabra»", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Charcoal)
                    grupos.take(12).forEach { g ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            g.forEach { s -> SoftChip(s, false) {
                                val fin = (r.last + 1).coerceAtMost(tf.text.length)
                                if (tf.text.substring(r.first, fin) == palabra) cambia(TextFieldValue(tf.text.replaceRange(r.first, fin, s), TextRange(r.first + s.length)))
                                sinonimos = null
                            } }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    }
                    Text("Del diccionario libre de sinónimos RLA-ES.", color = Muted, fontSize = 11.sp)
                }
            }
        }
        if (!ancha && panel && !foco) ModalBottomSheet({ panel = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Crudo, shape = shp(28.dp)) {
            Column(Modifier.fillMaxHeight(0.92f)) {
                PanelEscena(e, escena, ver, onCierra = { panel = false }, inserta = { x -> cambia(EdicionTexto.inserta(tf, x)); panel = false }, pon = { pon(it); panel = false }, textoActual = { tf.text }, abrirEscena = abrirEscena)
            }
        }
    }
}

@Composable
private fun Color5() = androidx.compose.ui.graphics.Color(0xFF5B8C5A)

@Composable
private fun BarraFormato(
    puedeDeshacer: Boolean, puedeRehacer: Boolean, onDeshacer: () -> Unit, onRehacer: () -> Unit,
    onNegrita: () -> Unit, onCursiva: () -> Unit, onTitulo: () -> Unit, onCita: () -> Unit,
    onSalto: () -> Unit, onComillas: () -> Unit, onRaya: () -> Unit, onBuscar: () -> Unit, onSinonimos: () -> Unit
) {
    Row(Modifier.fillMaxWidth().background(Crudo).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun Tecla(icono: androidx.compose.ui.graphics.vector.ImageVector, desc: String, on: Boolean = true, f: () -> Unit) =
            Box(Modifier.size(44.dp).clip(CircleShape).then(if (on) Modifier.press(haptic = false, onClick = f) else Modifier), contentAlignment = Alignment.Center) {
                Icon(icono, desc, tint = if (on) Charcoal else Line, modifier = Modifier.size(22.dp))
            }
        @Composable fun Letra(t: String, desc: String, f: () -> Unit) =
            Box(Modifier.size(44.dp).clip(CircleShape).press(haptic = false, onClick = f).semantics(mergeDescendants = true) { contentDescription = desc }, contentAlignment = Alignment.Center) {
                Text(t, color = Charcoal, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Display, modifier = Modifier.clearAndSetSemantics { })
            }
        Tecla(Icons.AutoMirrored.Rounded.Undo, "Deshacer", puedeDeshacer, onDeshacer)
        Tecla(Icons.AutoMirrored.Rounded.Redo, "Rehacer", puedeRehacer, onRehacer)
        Box(Modifier.width(1.dp).height(24.dp).background(Line))
        Tecla(Icons.Rounded.FormatBold, "Negrita", f = onNegrita)
        Tecla(Icons.Rounded.FormatItalic, "Cursiva", f = onCursiva)
        Tecla(Icons.Rounded.Title, "Título", f = onTitulo)
        Tecla(Icons.Rounded.FormatQuote, "Cita", f = onCita)
        Tecla(Icons.Rounded.HorizontalRule, "Salto de escena", f = onSalto)
        Letra("«»", "Comillas", onComillas)
        Letra("—", "Raya", onRaya)
        Letra("≈", "Sinónimos", onSinonimos)
        Box(Modifier.width(1.dp).height(24.dp).background(Line))
        Tecla(Icons.Rounded.Search, "Buscar", f = onBuscar)
    }
}

@Composable
private fun BarraBuscar(
    consulta: String, onConsulta: (String) -> Unit, reemplazo: String, onReemplazo: (String) -> Unit, mayus: Boolean, onMayus: (Boolean) -> Unit,
    total: Int, actual: Int, onSig: () -> Unit, onAnt: () -> Unit, onEsta: () -> Unit, onEscena: () -> Unit, onObra: () -> Unit, onCierra: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Crudo).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CampoEscribir(consulta, onConsulta, "Buscar", modifier = Modifier.weight(1f), mayus = false)
            Text(if (consulta.isEmpty()) "" else if (total == 0) "0" else "${actual + 1}/$total".replace("0/", "–/"), color = Muted, fontSize = 12.sp)
            IconPill(Icons.Rounded.KeyboardArrowUp, "Anterior", onClick = onAnt)
            IconPill(Icons.Rounded.KeyboardArrowDown, "Siguiente", onClick = onSig)
            IconPill(Icons.Rounded.Close, "Cerrar", onClick = onCierra)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CampoEscribir(reemplazo, onReemplazo, "Cambiar por", modifier = Modifier.weight(1f), mayus = false)
            SoftChip("Aa", mayus) { onMayus(!mayus) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SoftChip("Este", false, onClick = onEsta)
            SoftChip("Todos en la escena", false, onClick = onEscena)
            SoftChip("Todos en la obra", false, onClick = onObra)
        }
    }
}

// ---------------------------------------------------------------- El panel de la escena

@Composable
private fun PanelEscena(
    e: Escritos, escena: Doc, ver: Int, onCierra: () -> Unit, inserta: (String) -> Unit, pon: (String) -> Unit, textoActual: () -> String, abrirEscena: (String) -> Unit
) {
    var pestana by remember { mutableIntStateOf(0) }
    val pid = escena.pid
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Escena", "Fichas", "Notas", "Borradores", "Historial").forEachIndexed { i, n -> SoftChip(n, pestana == i) { pestana = i } }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (pestana) {
                0 -> PestanaEscena(e, escena)
                1 -> PestanaFichas(e, pid, textoActual(), inserta, abrirEscena)
                2 -> PestanaNotas(e, pid, inserta)
                3 -> PestanaBorradores(e, escena, textoActual(), pon)
                else -> PestanaHistorial(e, escena, textoActual(), pon)
            }
        }
    }
}

@Composable
private fun PestanaEscena(e: Escritos, escena: Doc) {
    val fichas = e.fichas(escena.pid)
    var resumen by remember(escena.id) { mutableStateOf(escena.s("resumen")) }
    var cuando by remember(escena.id) { mutableStateOf(escena.s("cuando")) }
    var etq by remember(escena.id) { mutableStateOf(escena.lista("etq").joinToString(", ")) }
    var meta by remember(escena.id) { mutableStateOf(escena.i("meta").takeIf { it > 0 }?.toString().orEmpty()) }
    LaunchedEffect(resumen, cuando, etq, meta) {
        kotlinx.coroutines.delay(500)
        val etiquetas = etq.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (resumen != escena.s("resumen") || cuando != escena.s("cuando") || etiquetas != escena.lista("etq") || (meta.toIntOrNull() ?: 0) != escena.i("meta"))
            e.edita(escena.id) { put("resumen", resumen); put("cuando", cuando); put("etq", org.json.JSONArray(etiquetas)); put("meta", meta.toIntOrNull() ?: 0) }
    }
    EtiquetaEscribir("Estado")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ESTADOS_ESCENA.forEachIndexed { i, n -> SoftChip(n, escena.i("estado") == i) { e.edita(escena.id) { put("estado", i) } } }
    }
    CampoEscribir(resumen, { resumen = it }, "Resumen de la escena", lineas = 4)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectorEscribir("Punto de vista", escena.s("pov"), listOf("" to "—") + fichas.filter { it.s("tipo") == "personaje" }.map { it.id to it.s("nombre") }, { v -> e.edita(escena.id) { if (v.isBlank()) remove("pov") else put("pov", v) } }, Modifier.weight(1f))
        SelectorEscribir("Lugar", escena.s("lugar"), listOf("" to "—") + fichas.filter { it.s("tipo") == "lugar" }.map { it.id to it.s("nombre") }, { v -> e.edita(escena.id) { if (v.isBlank()) remove("lugar") else put("lugar", v) } }, Modifier.weight(1f))
    }
    CampoEscribir(cuando, { cuando = it }, "Cuándo pasa (para la línea temporal)")
    CampoEscribir(etq, { etq = it }, "Etiquetas (separadas por comas)")
    CampoEscribir(meta, { meta = it.filter(Char::isDigit).take(6) }, "Meta de palabras de esta escena", numero = true)
}

@Composable
private fun PestanaFichas(e: Escritos, pid: String, texto: String, inserta: (String) -> Unit, abrirEscena: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var abierta by remember { mutableStateOf<String?>(null) }
    var editar by remember { mutableStateOf<Doc?>(null) }
    var nueva by remember { mutableStateOf(false) }
    val aqui = remember(texto) { e.fichasEn(pid, texto).map { it.id }.toSet() }
    val todas = e.fichas(pid).filter { q.isBlank() || it.s("nombre").contains(q, true) || it.s("alias").contains(q, true) }
    CampoEscribir(q, { q = it }, "Buscar una ficha", mayus = false)
    val lista = todas.sortedByDescending { it.id in aqui }
    if (lista.isEmpty()) Text(if (e.fichas(pid).isEmpty()) "Aún no hay fichas. Crea la de tu protagonista: quedará subrayada en el texto cada vez que la nombres." else "Nada con «$q».", color = Muted, fontSize = 13.sp)
    lista.forEach { f ->
        val on = abierta == f.id
        Column(Modifier.fillMaxWidth().animateContentSize().clip(shp(14.dp)).border(1.dp, Line, shp(14.dp)).padding(12.dp)) {
            Row(Modifier.fillMaxWidth().press(haptic = false) { abierta = if (on) null else f.id }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(colorEscrito(f.s("color"), ColGeneral)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.s("nombre"), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Charcoal)
                    Text((singularFicha(f.s("tipo"))) + if (f.id in aqui) " · sale aquí" else "", color = Muted, fontSize = 12.sp)
                }
            }
            if (on) {
                if (f.s("resumen").isNotBlank()) Text(f.s("resumen"), color = Charcoal, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                f.o.optJSONArray("campos")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { c -> if (c.optString("v").isNotBlank()) Text("${c.optString("k")}: ${c.optString("v")}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)) } }
                if (f.s("texto").isNotBlank()) Text(f.s("texto"), color = Charcoal, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    SoftChip("Poner el nombre", false) { inserta(f.s("nombre")) }
                    SoftChip("Editar", false) { editar = f }
                }
            }
        }
    }
    BotonEscribir("Nueva ficha", suave = true) { nueva = true }
    if (nueva) FichaDialogo(e, pid, null, onCerrar = { nueva = false })
    editar?.let { f -> FichaDialogo(e, pid, e.get(f.id), onCerrar = { editar = null }, abrirEscena = { abrirEscena(it.id) }) }
}

@Composable
private fun PestanaNotas(e: Escritos, pid: String, inserta: (String) -> Unit) {
    var abierta by remember { mutableStateOf<String?>(null) }
    var editar by remember { mutableStateOf<Doc?>(null) }
    var nueva by remember { mutableStateOf(false) }
    val notas = e.notas(pid).sortedWith(compareByDescending<Doc> { it.b("fija") }.thenByDescending { it.s("tipo") == "plantilla" }.thenByDescending { it.u })
    if (notas.isEmpty()) Text("Notas, ideas, investigación y plantillas de la obra: aquí las tienes al lado mientras escribes.", color = Muted, fontSize = 13.sp)
    notas.forEach { n ->
        val on = abierta == n.id
        Column(Modifier.fillMaxWidth().animateContentSize().clip(shp(14.dp)).border(1.dp, Line, shp(14.dp)).padding(12.dp)) {
            Row(Modifier.fillMaxWidth().press(haptic = false) { abierta = if (on) null else n.id }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(colorEscrito(n.s("color"), ColGeneral)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.s("titulo").ifBlank { "Sin título" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Charcoal, maxLines = 1)
                    Text((TIPOS_NOTA.find { it.first == n.s("tipo") }?.second?.removeSuffix("s") ?: "Nota") + if (n.b("fija")) " · fijada" else "", color = Muted, fontSize = 12.sp)
                }
            }
            if (on) {
                Text(n.s("texto"), color = Charcoal, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    if (n.s("texto").isNotBlank()) SoftChip(if (n.s("tipo") == "plantilla") "Insertar la plantilla" else "Insertar el texto", false) { inserta(n.s("texto")) }
                    SoftChip("Editar", false) { editar = n }
                }
            }
        }
    }
    BotonEscribir("Nueva nota", suave = true) { nueva = true }
    if (nueva) NotaDialogo(e, pid, null, onCerrar = { nueva = false })
    editar?.let { n -> NotaDialogo(e, pid, e.get(n.id), onCerrar = { editar = null }) }
}

private fun fechaHora(t: Long) = SimpleDateFormat("d MMM · HH:mm", ES).format(Date(t))

@Composable
private fun PestanaBorradores(e: Escritos, escena: Doc, actual: String, pon: (String) -> Unit) {
    var comparar by remember { mutableStateOf<Doc?>(null) }
    val bs = e.borradores(escena.id).reversed()
    Text("Un borrador es otra versión de esta escena: pruébala sin perder la buena, compárala y mezcla lo mejor de cada una.", color = Muted, fontSize = 13.sp)
    BotonEscribir("Guardar el texto actual como borrador", suave = true) {
        e.nuevo(Escritos.T_BORRADOR, escena.pid) { put("eid", escena.id); put("nombre", "Borrador del " + fechaHora(System.currentTimeMillis())); put("texto", actual) }
    }
    bs.forEach { b ->
        Column(Modifier.fillMaxWidth().clip(shp(14.dp)).border(1.dp, if (b.b("conflicto")) ColGeneral else Line, shp(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(b.s("nombre").ifBlank { "Borrador" }, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Charcoal)
            Text("${Escritos.palabras(b.s("texto"))} palabras" + if (b.b("conflicto")) " · se guardó al juntar dos cambios a la vez" else "", color = if (b.b("conflicto")) ColGeneral else Muted, fontSize = 12.sp)
            Text(b.s("texto").take(220).replace('\n', ' ') + if (b.s("texto").length > 220) "…" else "", color = Charcoal, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SoftChip("Comparar y mezclar", false) { comparar = b }
                SoftChip("Usar este", false) {
                    e.nuevo(Escritos.T_BORRADOR, escena.pid) { put("eid", escena.id); put("nombre", "Antes de usar «${b.s("nombre")}»"); put("texto", actual) }
                    pon(b.s("texto"))
                }
                SoftChip("Borrar", false) { aLaPapelera(e, b) }
            }
        }
    }
    comparar?.let { b -> ComparaDialogo(actual, b.s("nombre"), b.s("texto"), onCerrar = { comparar = null }, onAplica = { t -> e.instantanea(escena.id, actual, fuerza = true); pon(t); comparar = null }) }
}

@Composable
private fun PestanaHistorial(e: Escritos, escena: Doc, actual: String, pon: (String) -> Unit) {
    var comparar by remember { mutableStateOf<Version?>(null) }
    val vs = remember(escena.u, actual.length / 50) { e.versiones(escena.id).reversed() }
    Text("Cada cierto tiempo (y al salir) se guarda una versión de la escena en este dispositivo: las últimas ${Escritos.VERSIONES}.", color = Muted, fontSize = 13.sp)
    if (vs.isEmpty()) Text("Todavía no hay versiones: se guardan solas mientras escribes.", color = Muted, fontSize = 13.sp)
    vs.forEach { v ->
        Column(Modifier.fillMaxWidth().clip(shp(14.dp)).border(1.dp, Line, shp(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(fechaHora(v.u) + " · ${v.palabras} palabras", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Charcoal)
            Text(v.texto.take(160).replace('\n', ' ') + if (v.texto.length > 160) "…" else "", color = Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftChip("Comparar", false) { comparar = v }
                SoftChip("Restaurar", false) { e.instantanea(escena.id, actual, fuerza = true); pon(v.texto) }
            }
        }
    }
    comparar?.let { v -> ComparaDialogo(actual, fechaHora(v.u), v.texto, onCerrar = { comparar = null }, onAplica = { t -> e.instantanea(escena.id, actual, fuerza = true); pon(t); comparar = null }) }
}

/** Las diferencias entre el texto de ahora y otra versión, párrafo a párrafo, para quedarte con lo que quieras de cada una. */
@Composable
fun ComparaDialogo(actual: String, nombre: String, otro: String, onCerrar: () -> Unit, onAplica: (String) -> Unit) {
    val partes = remember(actual, otro) { EscribirDiff.compara(actual, otro) }
    val cambios = partes.filterIsInstance<Parte.Cambio>()
    val elige = remember(partes) { mutableStateListOf<Boolean>().also { l -> repeat(cambios.size) { l.add(false) } } }
    HojaEscribir("Comparar con «$nombre»", onCerrar) {
        if (cambios.isEmpty()) Text("Son iguales.", color = Muted, fontSize = 15.sp)
        else Text("${cambios.size} ${if (cambios.size == 1) "diferencia" else "diferencias"}. Toca cada una para elegir qué texto te quedas.", color = Muted, fontSize = 13.sp)
        var k = 0
        partes.forEach { p ->
            when (p) {
                is Parte.Igual -> Text(p.parrafos.joinToString("\n\n").let { if (it.length > 180) it.take(90) + " … " + it.takeLast(70) else it }, color = Muted, fontSize = 13.sp, fontFamily = Display)
                is Parte.Cambio -> {
                    val i = k++
                    val otroOn = elige.getOrElse(i) { false }
                    Column(Modifier.fillMaxWidth().clip(shp(14.dp)).border(2.dp, ColGeneral.copy(alpha = 0.5f), shp(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.fillMaxWidth().clip(shp(10.dp)).background(if (!otroOn) ColGeneral.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent).press(haptic = false) { if (i < elige.size) elige[i] = false }.padding(10.dp)) {
                            Text(if (!otroOn) "✓ EL ACTUAL" else "EL ACTUAL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                            Text(p.aqui.joinToString("\n\n").ifBlank { "(nada)" }, color = Charcoal, fontFamily = Display, fontSize = 15.sp)
                        }
                        Column(Modifier.fillMaxWidth().clip(shp(10.dp)).background(if (otroOn) ColGeneral.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent).press(haptic = false) { if (i < elige.size) elige[i] = true }.padding(10.dp)) {
                            Text(if (otroOn) "✓ ESTE" else "ESTE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                            Text(p.otro.joinToString("\n\n").ifBlank { "(nada)" }, color = Charcoal, fontFamily = Display, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
        if (cambios.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonEscribir("Aplicar la mezcla") { onAplica(EscribirDiff.mezcla(partes, elige.toList())) }
            BotonEscribir("Quedarme con este", suave = true) { onAplica(otro) }
        }
    }
}
