package com.gonzalo.gamerate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una carpeta abierta en el explorador de tu nube: sus subcarpetas y sus imágenes (las de las subcarpetas sin imágenes propias, debajo). */
private class Vista(val carpetas: List<KDrive.Entry>, val grupos: List<Pair<String, List<KDrive.Entry>>>)

/**
 * Elegir una imagen de tu kDrive, a pantalla completa: se abre en la carpeta de la ficha (si ya tiene) o en «Mi carpeta/
 * Atrio», con miniaturas grandes, y se puede buscar por nombre en todo tu kDrive. La elegida se baja y queda como una
 * foto tuya de este móvil (sin volver a subir otra copia).
 */
@Composable
fun NubePicker(nombre: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    /** Dónde estás: (id, nombre) desde la carpeta en que se empezó. */
    var ruta by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var vista by remember { mutableStateOf<Vista?>(null) }
    var resultados by remember { mutableStateOf<List<KDrive.Entry>?>(null) }
    var buscar by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var bajando by remember { mutableStateOf(false) }

    suspend fun lee(id: Long): Vista = withContext(Dispatchers.IO) {
        val l = KDrive.list(ctx, id)
        val carpetas = l.filter { it.dir }
        val imgs = l.filter { it.image }
        val grupos = mutableListOf<Pair<String, List<KDrive.Entry>>>()
        if (imgs.isNotEmpty()) grupos += "" to imgs
        // Una ficha guarda sus imágenes en «Portadas» y «Capturas»: si aquí no hay ninguna, se enseñan las de dentro
        if (imgs.isEmpty()) for (c in carpetas.take(4)) {
            val dentro = runCatching { KDrive.list(ctx, c.id).filter { it.image } }.getOrDefault(emptyList())
            if (dentro.isNotEmpty()) grupos += c.name to dentro
        }
        Vista(carpetas, grupos)
    }

    fun abre(id: Long, nuevaRuta: List<Pair<Long, String>>) {
        vista = null; msg = ""; resultados = null
        scope.launch {
            try { vista = lee(id); ruta = nuevaRuta } catch (e: Exception) { msg = e.message ?: "No se pudo abrir"; vista = Vista(emptyList(), emptyList()) }
        }
    }

    fun busca() {
        val q = buscar.trim()
        if (q.isEmpty()) { resultados = null; return }
        resultados = emptyList(); msg = "Buscando «$q» en tu nube…"
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { KDrive.busca(ctx, q) }
                resultados = r
                msg = if (r.isEmpty()) "No hay ninguna imagen que se llame así en tu nube." else ""
            } catch (e: Exception) { msg = e.message ?: "No se pudo buscar"; resultados = null }
        }
    }

    fun usa(e: KDrive.Entry) {
        if (bajando) return
        bajando = true; msg = "Bajando ${e.name}…"
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { NubeSync.usarDeLaNube(ctx, e.id, e.name) } }
            bajando = false
            r.onSuccess { onPick(it) }.onFailure { msg = "No se pudo bajar: ${it.message}" }
        }
    }

    // Al abrir: la carpeta de la ficha si ya tiene; si no, «Mi carpeta/Atrio»
    LaunchedEffect(Unit) {
        try {
            val suya = withContext(Dispatchers.IO) { KDrive.carpetaDeFicha(ctx, nombre) }
            if (suya != null) { vista = lee(suya.first); ruta = listOf(suya.first to suya.second.substringAfterLast('/')) }
            else { val s = withContext(Dispatchers.IO) { KDrive.start(ctx) }; vista = lee(s); ruta = listOf(s to "Atrio") }
        } catch (e: Exception) { msg = e.message ?: "No se pudo abrir tu nube"; vista = Vista(emptyList(), emptyList()) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)) {
        // Atrás: primero sale de la búsqueda, luego sube de carpeta; al final, se cierra
        BackHandler {
            when {
                resultados != null -> { resultados = null; msg = "" }
                ruta.size > 1 -> { val r = ruta.dropLast(1); abre(r.last().first, r) }
                else -> onDismiss()
            }
        }
        Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onDismiss)
                OutlinedTextField(
                    buscar, { buscar = it; if (it.isBlank()) { resultados = null; msg = "" } }, Modifier.weight(1f).padding(horizontal = 6.dp),
                    singleLine = true, shape = shp(14.dp), label = { Text("Buscar imágenes en tu nube") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { busca() })
                )
                IconButton({ busca() }) { Icon(Icons.Rounded.Search, "Buscar") }
            }
            // Dónde estás (o qué buscas), y subir de carpeta
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val r = resultados
                if (r != null) Text("RESULTADOS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                else Text(ruta.joinToString(" / ") { it.second }.ifBlank { "Tu nube" }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (r != null) Text("VER CARPETA", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                    modifier = Modifier.clickable { resultados = null; msg = "" }.padding(8.dp))
                else if (ruta.size > 1) Text("← SUBIR", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                    modifier = Modifier.clickable { val x = ruta.dropLast(1); abre(x.last().first, x) }.padding(8.dp))
                else Text("↑ MI CARPETA", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                    modifier = Modifier.clickable { scope.launch { runCatching { withContext(Dispatchers.IO) { KDrive.home(ctx) } }.getOrNull()?.let { h -> abre(h, listOf(h to "Mi carpeta")) } } }.padding(8.dp))
            }
            if (msg.isNotEmpty()) Text(msg, color = Muted, fontSize = 13.sp, fontStyle = if (bajando) FontStyle.Italic else FontStyle.Normal, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            val r = resultados
            val v = vista
            when {
                r == null && v == null -> Text("Abriendo…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, modifier = Modifier.padding(18.dp))
                r == null && v != null && v.carpetas.isEmpty() && v.grupos.isEmpty() && msg.isEmpty() ->
                    Text("Aquí no hay carpetas ni imágenes. Prueba a buscar por el nombre de la imagen.", color = Muted, modifier = Modifier.padding(18.dp))
                else -> LazyVerticalGrid(GridCells.Adaptive(120.dp), Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (r != null) items(r, key = { "r${it.id}" }) { e -> Miniatura(e, e.path.substringAfter("Mi carpeta/").ifBlank { e.path }, bajando) { usa(e) } }
                    else if (v != null) {
                        items(v.carpetas, key = { "d${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { d ->
                            Text("▸  ${d.name}", fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { abre(d.id, ruta + (d.id to d.name)) }.padding(vertical = 10.dp, horizontal = 6.dp))
                        }
                        for ((titulo, imgs) in v.grupos) {
                            if (titulo.isNotEmpty()) item(key = "t$titulo", span = { GridItemSpan(maxLineSpan) }) {
                                Text(titulo.uppercase(), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
                            }
                            items(imgs, key = { "i${it.id}" }) { e -> Miniatura(e, e.name, bajando) { usa(e) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Miniatura(e: KDrive.Entry, pie: String, ocupado: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val req = remember(e.id) {
        coil.request.ImageRequest.Builder(ctx).data(KDrive.thumb(ctx, e.id)).addHeader("Authorization", KDrive.auth(ctx)).crossfade(true).build()
    }
    Column(Modifier.clickable(enabled = !ocupado, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Sand)) {
            coil.compose.AsyncImage(req, e.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(pie, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}
