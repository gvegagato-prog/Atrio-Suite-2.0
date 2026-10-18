@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.gonzalo.gamerate

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Color propio de la suite: el «en directo» (mismo en Atrio). */
val LiveColor: Color get() = if (Mode.dark) Color(0xFFFF6B5E) else Color(0xFFD9443A)

/** "1 h 05 min" desde un momento hasta ahora. */
fun sinceText(start: Long, now: Long = System.currentTimeMillis()): String {
    val m = ((now - start) / 60_000L).toInt().coerceAtLeast(0)
    return if (m < 1) "ahora mismo" else hoursText(m)
}

/** Punto rojo que late: algo está pasando en directo. */
@Composable
fun LiveDot(size: Int = 8) {
    val t = rememberInfiniteTransition(label = "latido")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size.dp).graphicsLayer { alpha = a }.background(LiveColor, CircleShape))
}

/** Botón redondo grande con icono y texto debajo (acciones del mando a distancia). */
@Composable
fun RemoteButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier, light: Boolean = false, busy: Boolean = false, onClick: () -> Unit) {
    Column(modifier.pop(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).background(if (light) Color(0x33FFFFFF) else Sand, CircleShape)
                .border(1.dp, if (light) Color(0x55FFFFFF) else Line, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = if (light) Color.White else Charcoal)
            else Icon(icon, text, tint = if (light) Color.White else Charcoal, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (light) Color.White else Charcoal, maxLines = 1)
    }
}

/**
 * Tarjeta «Ahora en el PC» del menú: lo que se está jugando en directo, con captura, apunte y valorar
 * a un toque. Si el PC está encendido pero sin jugar, una versión pequeña; si no hay ninguno, nada.
 */
@Composable
fun LiveCard(
    plays: List<Play>, onShot: () -> Unit, shooting: Boolean, onMemo: (LiveGame) -> Unit,
    onOpenPlay: (Play) -> Unit, onRemote: () -> Unit,
    /** Guías del juego en marcha (si tiene webs guardadas en el PC). */
    onGuides: (PcGame) -> Unit = {}
) {
    val pc = Suite.target()
    if (pc == null) {
        // Hay Atrio en la cuenta pero está apagado: una línea discreta
        val known = Suite.pcs().maxByOrNull { it.at }
        if (known != null) {
            Row(
                Modifier.fillMaxWidth().clip(shp(16.dp)).background(Sand)
                    .clickable { onRemote() }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Computer, null, tint = Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${known.name} · desconectado", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(if (known.at > 0) "Visto por última vez ${ago(known.at)}" else "Abre Atrio con tu cuenta", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            SuiteIntro("Conecta tu PC", "Instala Atrio 1.0 en el PC y entra con esta misma cuenta (en el PC: Ajustes → Cuenta). Al abrirlo aparecerá aquí en directo: capturas, apuntes y el mando del PC.", onRemote)
        }
        return
    }
    val g = pc.game
    @Suppress("UNUSED_VARIABLE") val tick = Suite.tick
    val shape = shp(24.dp)
    if (g == null) {
        Row(
            Modifier.fillMaxWidth().surface(20.dp, Crudo).clip(shp(20.dp)).clickable { onRemote() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).background(ColMusic, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Overline("Atrio · ${pc.name}")
                Text("Conectado, sin jugar", fontFamily = Display, fontSize = 20.sp)
                if (pc.today > 0) Text("Hoy llevas ${hoursText(pc.today)} jugando", color = Muted, fontSize = 12.sp)
            }
            IconPill(Icons.Rounded.PhotoCamera, "Captura") { onShot() }
            Spacer(Modifier.width(8.dp))
            IconPill(Icons.Rounded.SportsEsports, "Mando") { onRemote() }
        }
        return
    }
    val play = plays.find { it.id == g.sg }
    // Revista: sin arte ni logo, un recuadro de tinta con el nombre
    val hero = if (Skin.revista) "" else play?.hero?.ifBlank { null } ?: g.hero.ifBlank { g.cover }
    Box(Modifier.fillMaxWidth().height(236.dp).clip(shape).background(Color(0xFF15151A)).clickable { onRemote() }) {
        if (hero.isNotBlank()) AsyncImage(hero, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (!Skin.revista) {
            // Sin arte: un degradado con un tono propio de cada juego (el mismo nombre, el mismo color)
            val hue = ((g.name.hashCode() and 0x7fffffff) % 360).toFloat()
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
                Color.hsv(hue, 0.55f, 0.42f), Color.hsv((hue + 40f) % 360f, 0.6f, 0.18f)
            ))))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x55000000), 0.45f to Color(0x22000000), 1f to Color(0xEE0B0B0E))))
        Row(Modifier.padding(14.dp).background(Color(0x66000000), pillShape()).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveDot()
            Spacer(Modifier.width(6.dp))
            Text("EN DIRECTO · ${pc.name.uppercase()}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
            val logo = if (Skin.revista) "" else play?.logo?.ifBlank { null } ?: g.logo
            if (logo.isNotBlank()) {
                AsyncImage(logo, g.name, Modifier.heightIn(max = 54.dp).widthIn(max = 230.dp), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
            } else {
                Text(g.name, color = Color.White, fontFamily = Display, fontSize = 26.sp, lineHeight = 30.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Jugando desde hace ${sinceText(g.start)}" + (play?.let { " · ${hoursText(it.minutes)} en total" } ?: ""),
                color = Color(0xDDFFFFFF), fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                RemoteButton(Icons.Rounded.PhotoCamera, "Captura", light = true, busy = shooting) { onShot() }
                RemoteButton(Icons.Rounded.EditNote, "Apunte", light = true) { onMemo(g) }
                if (play != null) RemoteButton(Icons.Rounded.Star, "Ficha", light = true) { onOpenPlay(play) }
                val pg = Suite.pcGame(g.key)
                if (pg != null && pg.links.isNotEmpty()) RemoteButton(Icons.Rounded.Language, "Guías", light = true) { onGuides(pg) }
                if (pc.st?.on == true) {
                    val ctx = LocalContext.current
                    val scope = rememberCoroutineScope()
                    var busy by remember { mutableStateOf(false) }
                    var need by remember { mutableStateOf(false) }
                    // Seguir la partida en el móvil: Nova directo a ese PC
                    RemoteButton(Icons.Rounded.Cast, "Streaming", light = true, busy = busy) {
                        busy = true
                        scope.launch {
                            val err = Stream.play(ctx, null)
                            busy = false
                            if (err == "NOVA") need = true else if (err != null) android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    if (need) NovaDialog { need = false }
                } else RemoteButton(Icons.Rounded.SportsEsports, "Mando", light = true) { onRemote() }
            }
        }
    }
}

/** Tarjeta de la suite cuando aún no hay ningún PC (o no has entrado en tu cuenta). */
@Composable
fun SuiteIntro(title: String, text: String, onClick: () -> Unit) {
    val shape = shp(20.dp)
    Row(
        (if (Skin.revista) Modifier.fillMaxWidth().surface(20.dp, Crudo).clickable { onClick() }.padding(top = 14.dp, bottom = 12.dp)
        else Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(LiveColor.copy(alpha = 0.18f), Color.Transparent)))
            .clickable { onClick() }.padding(16.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Computer, null, tint = if (Skin.revista) Charcoal else LiveColor, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Overline("Novedad · Atrio en el PC")
            Text(title, fontFamily = Display, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(text, color = Muted, fontSize = 13.sp)
        }
    }
}

/** "hace 5 min", "hace 3 h", "ayer"… */
fun ago(t: Long): String {
    val m = ((System.currentTimeMillis() - t) / 60_000L).coerceAtLeast(0)
    return when {
        m < 1 -> "ahora mismo"
        m < 60 -> "hace $m min"
        m < 24 * 60 -> "hace ${m / 60} h"
        m < 48 * 60 -> "ayer"
        else -> "hace ${m / (24 * 60)} días"
    }
}

/** Tira de las últimas capturas (menú y fichas). */
@Composable
fun ShotStrip(shots: List<Shot>, title: String = "Capturas", onOpen: (Shot) -> Unit) {
    val pending = Suite.downloading
    if (shots.isEmpty() && pending.isEmpty()) return
    Column {
        Overline(title)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pending.entries.toList(), key = { "d" + it.key }) { (_, f) ->
                Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(shp(12.dp)).background(Sand), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { f }, Modifier.size(28.dp), strokeWidth = 3.dp, color = Charcoal)
                }
            }
            items(shots, key = { it.id }) { s ->
                AsyncImage(
                    s.uri, s.game, Modifier.width(150.dp).aspectRatio(16f / 9f).clip(shp(12.dp)).background(Sand).pop { onOpen(s) },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/** Visor de una captura a pantalla completa, con zoom (pellizco o doble toque) y su resolución original. */
@Composable
fun ShotViewer(shot: Shot, onBack: () -> Unit, onDelete: () -> Unit, onMemo: (() -> Unit)?, onUncloud: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var chrome by remember { mutableStateOf(true) }
    var ask by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val req = remember(shot.uri) {
            coil.request.ImageRequest.Builder(ctx).data(shot.uri).size(coil.size.Size.ORIGINAL).crossfade(150).build()
        }
        AsyncImage(
            req, shot.game,
            Modifier.fillMaxSize()
                .pointerInputZoom(
                    onTransform = { c, z, p ->
                        val ns = (scale * z).coerceIn(1f, 8f)
                        off = if (ns <= 1f) Offset.Zero else off * (ns / scale) + p
                        scale = ns
                    },
                    onDouble = { if (scale > 1f) { scale = 1f; off = Offset.Zero } else scale = 2.5f },
                    onTap = { chrome = !chrome }
                )
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y },
            contentScale = ContentScale.Fit
        )
        AnimatedVisibility(chrome) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color(0xAA000000), 1f to Color.Transparent)).statusBarsPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(Color(0x55000000), CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(shot.game.ifBlank { "Escritorio" }, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${shot.w}×${shot.h} · ${if (shot.mime == "image/jpeg") "JPG" else "PNG original"} · ${shot.bytes / 1024} KB · ${fmt(shot.time)}",
                            color = Color(0xCCFFFFFF), fontSize = 12.sp)
                    }
                }
            }
        }
        AnimatedVisibility(chrome, Modifier.align(Alignment.BottomCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xAA000000))).navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RemoteButton(Icons.Rounded.Share, "Compartir", light = true) { shareShot(ctx, shot) }
                if (onMemo != null) RemoteButton(Icons.Rounded.EditNote, "Apunte", light = true) { onMemo() }
                RemoteButton(Icons.AutoMirrored.Rounded.OpenInNew, "Abrir", light = true) { openShot(ctx, shot) }
                // Está en tu nube: quitarla de allí (y que no se vuelva a subir)
                if (onUncloud != null) RemoteButton(Icons.Rounded.CloudOff, "Quitar de la nube", light = true) { onUncloud() }
                RemoteButton(Icons.Rounded.Delete, "Borrar", light = true) { ask = true }
            }
        }
    }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("¿Borrar la captura?", fontFamily = Display) },
            text = { Text("Se borra de Atrio. Si se guardó en la galería, allí se queda; y en el PC sigue en Imágenes/Atrio.") },
            confirmButton = { TextButton({ ask = false; onDelete() }) { Text("Borrar") } },
            dismissButton = { TextButton({ ask = false }) { Text("Cancelar") } },
            shape = shp(24.dp)
        )
    }
}

/** Gestos del visor: pellizcar y arrastrar, doble toque para ampliar, un toque para enseñar/ocultar botones. */
private fun Modifier.pointerInputZoom(onTransform: (Offset, Float, Offset) -> Unit, onDouble: () -> Unit, onTap: () -> Unit): Modifier =
    this.then(Modifier.pointerInput(Unit) { detectTransformGestures { c, pan, zoom, _ -> onTransform(c, zoom, pan) } })
        .then(Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDouble() }, onTap = { onTap() }) })

private fun fileUri(ctx: Context, s: Shot): Uri {
    val f = java.io.File(Uri.parse(s.uri).path ?: "")
    return androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".archivos", f)
}

fun shareShot(ctx: Context, s: Shot) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = s.mime.ifBlank { "image/png" }
            putExtra(Intent.EXTRA_STREAM, fileUri(ctx, s))
            putExtra(Intent.EXTRA_TEXT, s.game)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Compartir captura").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { }
}

fun openShot(ctx: Context, s: Shot) {
    try {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri(ctx, s), s.mime.ifBlank { "image/png" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(view)
    } catch (e: Exception) { }
}

/** Apunte rápido: texto y, si el PC está conectado, con una captura de ese momento. */
@Composable
fun MemoDialog(title: String, canShot: Boolean, presetShot: Shot? = null, onSave: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var withShot by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = Display) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, placeholder = { Text("Lo que está pasando, una idea, algo que no quieres olvidar…") },
                    shape = shp(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
                if (presetShot != null) {
                    Spacer(Modifier.height(10.dp))
                    AsyncImage(presetShot.uri, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shp(10.dp)), contentScale = ContentScale.Crop)
                } else if (canShot) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.clip(shp(10.dp)).clickable { withShot = !withShot }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(withShot, { withShot = it })
                        Text("Con una captura del PC de este momento")
                    }
                }
            }
        },
        confirmButton = { TextButton({ if (text.isNotBlank() || withShot || presetShot != null) onSave(text.trim(), withShot) }) { Text("Guardar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
        shape = shp(24.dp)
    )
}

/** Qué son los apuntes rápidos en cada categoría (en los libros no se juega). */
fun memoHint(kind: Kind): String = when {
    kind.isGame -> "Ideas sueltas mientras juegas (también desde Atrio)"
    kind.isBook -> "Ideas sueltas mientras lees"
    kind.isMovie -> "Ideas sueltas mientras la ves"
    kind.isTv -> "Ideas sueltas mientras la ves (de cada capítulo)"
    else -> "Ideas sueltas sobre la marcha"
}

/** En la ficha de algo en curso: apuntes rápidos y capturas. */
@Composable
fun PlaySuitePanel(
    p: Play, kind: Kind,
    onMemo: () -> Unit, onDeleteMemo: (Memo) -> Unit, onOpenShot: (Shot) -> Unit
) {
    val memos = p.liveMemos.sortedByDescending { it.t }
    Column(Modifier.fillMaxWidth().surface(20.dp, Crudo).padding(16.dp).animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("Apuntes rápidos")
                Text(if (memos.isEmpty()) memoHint(kind) else "${memos.size}", color = Muted, fontSize = 14.sp)
            }
            IconPill(Icons.Rounded.EditNote, "Apunte rápido") { onMemo() }
        }
        memos.take(20).forEach { m ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(fmt(m.t), color = Muted, fontSize = 12.sp)
                    if (m.x.isNotBlank()) Text(m.x, fontSize = 15.sp)
                    val sh = if (m.sh.isNotBlank()) Suite.shotById(m.sh) else null
                    if (sh != null) {
                        Spacer(Modifier.height(6.dp))
                        AsyncImage(sh.uri, null, Modifier.fillMaxWidth(0.7f).aspectRatio(16f / 9f).clip(shp(10.dp)).pop { onOpenShot(sh) }, contentScale = ContentScale.Crop)
                    } else if (m.sh.isNotBlank()) {
                        Text("📷 captura en el PC", color = Muted, fontSize = 12.sp)
                    }
                }
                IconButton({ onDeleteMemo(m) }) { Icon(Icons.Rounded.Close, "Borrar apunte", tint = Muted, modifier = Modifier.size(16.dp)) }
            }
        }
    }
    val shots = Suite.shotsFor(p.id)
    if (shots.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth()) { ShotStrip(shots, "Capturas de ${p.name}", onOpenShot) }
    }
}

/** Mando a distancia del PC: lo que se juega, la biblioteca, las capturas y la energía. */
@Composable
fun SuiteScreen(
    plays: List<Play>, store: Store, onBack: () -> Unit, onOpenShot: (Shot) -> Unit, onMemo: (LiveGame) -> Unit,
    onOpenPlay: (Play) -> Unit, say: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var shooting by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var query by remember { mutableStateOf("") }
    var busyKey by remember { mutableStateOf<String?>(null) }
    var pcPick by remember { mutableStateOf(store.suitePc) }
    var sheet by remember { mutableStateOf<PcGame?>(null) }
    val pc = Suite.target()
    fun act(op: String, args: JSONObject = JSONObject(), done: (Boolean, String) -> Unit = { _, m -> say(m) }) {
        scope.launch { val (ok, m) = Suite.send(op, args, pc?.dev, if (op == "power") 25_000 else 45_000); done(ok, m) }
    }
    fun shot() {
        if (shooting) return
        shooting = true
        scope.launch {
            val (id, m) = Suite.shoot(pc?.dev)
            if (id == null) say(m) else Suite.awaitLocal(id)?.let { onOpenShot(it) }
            shooting = false
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            TopBar("PC", onBack, pc?.name ?: "Tu PC") {
                Box(Modifier.padding(end = 4.dp)) {
                    if (pc != null) Row(verticalAlignment = Alignment.CenterVertically) { LiveDot(); Spacer(Modifier.width(6.dp)); Text("Conectado", fontSize = 12.sp, color = Muted) }
                    else Text(Suite.state, fontSize = 12.sp, color = Muted)
                }
            }
            if (Suite.pcs().size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Suite.pcs().forEach { d ->
                        SoftChip(d.name + if (d.online) "" else " (apagado)", (pc?.dev ?: pcPick) == d.dev) { pcPick = d.dev; store.suitePc = d.dev }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            TabRow(tab, containerColor = Color.Transparent, contentColor = Charcoal, divider = { HorizontalDivider(color = Line) }) {
                listOf("Ahora", "Biblioteca", "Capturas", "Equipo").forEachIndexed { i, t ->
                    Tab(tab == i, { tab = i }, text = { Text(t, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, softWrap = false) })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (pc == null) {
                        EmptySuite()
                    } else {
                        LiveCard(plays, { shot() }, shooting, onMemo, onOpenPlay, onRemote = { }, onGuides = { sheet = it })
                        Spacer(Modifier.height(18.dp))
                        Text("Las capturas llegan a su resolución original (PNG, sin pérdida) y se guardan en Imágenes/Atrio y, en el PC, en Imágenes/Atrio.",
                            color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(18.dp))
                        ShotStrip(Suite.localShots.take(12), "Últimas capturas", onOpenShot)
                        Spacer(Modifier.height(18.dp))
                        val recent = (Suite.libs[pc.dev] ?: emptyList()).sortedByDescending { it.lastPlayed }.take(6)
                        if (recent.isNotEmpty()) {
                            Overline("Seguir jugando")
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(recent, key = { it.key }) { g ->
                                    PcGameTile(g, busyKey == g.key, Modifier.width(104.dp)) {
                                        busyKey = g.key
                                        act("launch", JSONObject().put("key", g.key)) { _, m -> say(m); busyKey = null }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val lib = (Suite.libs[pc?.dev ?: pcPick] ?: Suite.libs.values.firstOrNull() ?: emptyList())
                        .filter { query.isBlank() || normName(it.name).contains(normName(query)) }
                        .sortedWith(compareByDescending<PcGame> { it.fav }.thenByDescending { it.lastPlayed }.thenBy { it.name.lowercase() })
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(12.dp))
                        SearchPill(query, "Buscar en la biblioteca del PC") { query = it }
                        Spacer(Modifier.height(12.dp))
                        if (lib.isEmpty()) Text(if (Suite.libs.isEmpty()) "Abre Atrio en el PC con tu cuenta para ver aquí su biblioteca." else "Nada con ese nombre.", color = Muted)
                        LazyVerticalGrid(GridCells.Adaptive(104.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(lib, key = { it.key }) { g ->
                                PcGameTile(g, busyKey == g.key) { sheet = g }
                            }
                        }
                    }
                }
                2 -> {
                    val shots = Suite.localShots
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${shots.size} capturas en este móvil", color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            if (pc != null) ActionPill(Icons.Rounded.PhotoCamera, if (shooting) "Capturando…" else "Capturar", dark = true) { shot() }
                        }
                        // Las que están en tu nube y no aquí (hechas antes, o mientras este móvil estaba apagado)
                        val nctx = LocalContext.current
                        val nscope = rememberCoroutineScope()
                        val faltan = remember(shots) { if (KDrive.on(nctx)) NubeSync.missingShots(Store(nctx)) else emptyList() }
                        if (faltan.isNotEmpty()) {
                            Text(if (NubeSync.running) NubeSync.status else "Traer ${faltan.size} de tu nube →", color = ColGeneral, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp).clickable(enabled = !NubeSync.running) {
                                    nscope.launch { NubeSync.running = true; try { NubeSync.fetchShots(nctx, faltan) } finally { NubeSync.running = false } }
                                })
                        }
                        Spacer(Modifier.height(12.dp))
                        if (Suite.downloading.isNotEmpty()) { ShotStrip(emptyList(), "Llegando") { }; Spacer(Modifier.height(12.dp)) }
                        LazyVerticalGrid(GridCells.Adaptive(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(shots, key = { it.id }) { s ->
                                Column(Modifier.pop { onOpenShot(s) }) {
                                    AsyncImage(s.uri, s.game, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shp(10.dp)).background(Sand), contentScale = ContentScale.Crop)
                                    Text(s.game.ifBlank { "Escritorio" }, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                    Text(fmt(s.time), fontSize = 11.sp, color = Muted)
                                }
                            }
                        }
                    }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (pc == null) { EmptySuite(); if (Stream.host() != null) { Spacer(Modifier.height(20.dp)); StreamPanel() } } else {
                        Overline("Atrio")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            RemoteButton(Icons.Rounded.Tv, "Traer Atrio") { act("focus") }
                            RemoteButton(Icons.Rounded.PhotoCamera, "Captura", busy = shooting) { shot() }
                            RemoteButton(Icons.Rounded.Search, "Rebuscar") { act("rescan") }
                        }
                        Spacer(Modifier.height(24.dp))
                        StreamPanel()
                        Spacer(Modifier.height(24.dp))
                        Overline("Energía")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            RemoteButton(Icons.Rounded.Bedtime, "Suspender") { confirm = "suspender" to "¿Suspender ${pc.name}?" }
                            RemoteButton(Icons.Rounded.RestartAlt, "Reiniciar") { confirm = "reiniciar" to "¿Reiniciar ${pc.name}?" }
                            RemoteButton(Icons.Rounded.PowerSettingsNew, "Apagar") { confirm = "apagar" to "¿Apagar ${pc.name}?" }
                        }
                        Spacer(Modifier.height(24.dp))
                        Overline("Mensaje en la pantalla")
                        Spacer(Modifier.height(8.dp))
                        var msg by remember { mutableStateOf("") }
                        OutlinedTextField(msg, { msg = it }, placeholder = { Text("Sale como aviso en Atrio") }, singleLine = true,
                            shape = shp(14.dp), modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { IconButton({ if (msg.isNotBlank()) { act("toast", JSONObject().put("text", msg)); msg = "" } }) { Icon(Icons.Rounded.PlayArrow, "Enviar") } })
                        Spacer(Modifier.height(24.dp))
                        Overline("Dispositivos de tu cuenta")
                        Spacer(Modifier.height(6.dp))
                        Suite.devices.forEach { d ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(if (d.online) ColMusic else Line, CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text("${d.name} · ${if (d.app == "atrio") "PC" else "móvil"}" + if (d.online) "" else " · ${ago(d.at)}", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    val sh = sheet
    if (sh != null) {
        PcGameSheet(sh, pc != null, onPlay = {
            busyKey = sh.key
            act("launch", JSONObject().put("key", sh.key)) { _, m -> say(m); busyKey = null }
        }, onDismiss = { sheet = null }, say = say)
    }
    val c = confirm
    if (c != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.second, fontFamily = Display) },
            text = { Text(if (pc?.game != null) "Ahora mismo se está jugando a ${pc.game.name}: guarda la partida antes." else "Atrio guarda tus horas antes de hacerlo.") },
            confirmButton = { TextButton({ confirm = null; act("power", JSONObject().put("action", c.first)) }) { Text(c.first.replaceFirstChar { it.uppercase() }) } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancelar") } },
            shape = shp(24.dp)
        )
    }
}

@Composable
private fun EmptySuite() {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Computer, null, tint = Muted, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Ningún PC conectado", fontFamily = Display, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text("Abre Atrio en el PC y entra con tu cuenta (en el PC: Ajustes → Cuenta). En cuanto esté abierto aparecerá aquí, en directo.",
            color = Muted, textAlign = TextAlign.Center, fontSize = 14.sp)
    }
}

@Composable
private fun PcGameTile(g: PcGame, busy: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.pop(onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shp(10.dp)).background(Sand), contentAlignment = Alignment.Center) {
            Cover(g.cover, g.name, Modifier.fillMaxSize())
            if (busy) Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
            }
            if (g.fav) Icon(Icons.Rounded.Star, null, tint = Gold, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp))
            if (g.links.isNotEmpty()) Text("${g.links.size} guía" + if (g.links.size > 1) "s" else "", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).background(Color(0x99000000), pillShape()).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(g.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        if (g.minutes > 0) Text(hoursText(g.minutes), fontSize = 11.sp, color = Muted)
    }
}

/** Abre una web en el navegador del móvil. */
fun openUrl(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
}

/**
 * Un juego de la biblioteca del PC: jugarlo allí, sus guías (abrirlas aquí o en la página del juego en el PC)
 * y adjuntarle una web nueva.
 */
@Composable
fun PcGameSheet(g: PcGame, pcOnline: Boolean, onPlay: () -> Unit, onDismiss: () -> Unit, say: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(g.name, fontFamily = Display) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!adding) {
                    PlayButtons(g.key, g.name, Modifier.padding(bottom = 16.dp)) { onDismiss() }
                    Overline("Guías y webs")
                    Spacer(Modifier.height(6.dp))
                    if (g.links.isEmpty()) Text("Aún no tiene ninguna. Añade una aquí, compártela desde el navegador (Compartir → Atrio) o guárdala en el PC desde Guías.", color = Muted, fontSize = 13.sp)
                    g.links.forEach { (n, u) ->
                        Row(Modifier.fillMaxWidth().clip(shp(10.dp)).clickable {
                            val op = Guides.opener
                            if (op != null) { onDismiss(); op(Guide(n, u, "pc")) } else openUrl(ctx, u)
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(n.ifBlank { Uri.parse(u).host ?: u }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Uri.parse(u).host ?: u, color = Muted, fontSize = 12.sp, maxLines = 1)
                            }
                            if (pcOnline) TextButton({
                                scope.launch { val (_, m) = Suite.send("guide", JSONObject().put("key", g.key).put("url", u)); say(m) }
                            }) { Text("En el PC") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton({ adding = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Añadir una web") }
                } else {
                    OutlinedTextField(url, { url = it.trim() }, label = { Text("Dirección (https://…)") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre (opcional)") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (adding) TextButton({
                val u = if (url.startsWith("http")) url else "https://$url"
                if (url.isBlank()) return@TextButton
                scope.launch { val (_, m) = Suite.addLink(g.key, u, name.trim()); say(m); onDismiss() }
            }) { Text("Guardar") }

        },
        dismissButton = { TextButton({ if (adding) adding = false else onDismiss() }) { Text(if (adding) "Atrás" else "Cerrar") } },
        shape = shp(24.dp)
    )
}

/** Una web compartida desde otra app (Compartir → Atrio): a qué juego del PC se adjunta. */
@Composable
fun ShareToGameDialog(url: String, title: String, onDismiss: () -> Unit, say: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    val all = Suite.libs.values.flatten().distinctBy { it.key }
    val list = all.filter { q.isBlank() || normName(it.name).contains(normName(q)) }
        .sortedWith(compareByDescending<PcGame> { it.links.isNotEmpty() }.thenByDescending { it.lastPlayed })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar como guía", fontFamily = Display) },
        text = {
            Column {
                Text(title.ifBlank { Uri.parse(url).host ?: url }, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Uri.parse(url).host ?: url, color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                if (all.isEmpty()) Text("No veo la biblioteca de ningún PC: abre Atrio en el PC con tu cuenta y vuelve a compartirla.", color = Muted, fontSize = 13.sp)
                else {
                    SearchPill(q, "¿De qué juego?") { q = it }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        list.take(60).forEach { g ->
                            Row(Modifier.fillMaxWidth().clip(shp(10.dp)).clickable {
                                scope.launch { val (_, m) = Suite.addLink(g.key, url, title.take(60)); say("${g.name}: $m"); onDismiss() }
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                MiniCover(g.cover, 30.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(g.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (g.links.isNotEmpty()) Text("${g.links.size}", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
        shape = shp(24.dp)
    )
}
