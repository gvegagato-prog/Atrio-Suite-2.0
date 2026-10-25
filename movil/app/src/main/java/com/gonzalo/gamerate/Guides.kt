package com.gonzalo.gamerate

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Una guía (web) de un juego. src: "pc" (guardada en Atrio del PC), "movil" (solo en este móvil) o "sug" (sugerida). */
data class Guide(val name: String, val url: String, val src: String) {
    val host: String get() = (Uri.parse(url).host ?: url).removePrefix("www.")
    val title: String get() = name.ifBlank { host }
}

object Guides {
    /** Lo pone la app: abre una guía dentro de Atrio (pantalla de web). */
    var opener: ((Guide) -> Unit)? = null

    /** Webs comprobadas para juegos online conocidos (las mismas que sugiere Atrio en el PC). */
    private val PRESETS: List<Pair<Regex, List<Pair<String, String>>>> = listOf(
        Regex("deadlock") to listOf(
            "Deadlock Wiki · héroes" to "https://deadlock.wiki/Heroes", "Statlocker" to "https://statlocker.gg",
            "Mobalytics · builds" to "https://mobalytics.gg/deadlock/build-stats", "Deadlock Labs" to "https://deadlocklabs.gg"
        ),
        Regex("league ?of ?legends|^lol$") to listOf(
            "OP.GG · campeones" to "https://op.gg/champions", "U.GG · campeones" to "https://u.gg/lol/champions",
            "Mobalytics · campeones" to "https://mobalytics.gg/lol/champions"
        ),
        Regex("^dota ?2$") to listOf("Dotabuff · héroes" to "https://www.dotabuff.com/heroes")
    )

    fun presets(name: String): List<Guide> {
        val n = normName(name).replace(Regex("[^a-z0-9 ]"), " ").trim()
        return PRESETS.firstOrNull { it.first.containsMatchIn(n) }?.second?.map { Guide(it.first, it.second, "sug") } ?: emptyList()
    }

    /** El juego del PC que corresponde a una ficha: por su id de SteamGridDB o por el nombre. */
    fun pcGameFor(id: Long, name: String): PcGame? {
        val all = Suite.libs.values.flatten()
        return all.firstOrNull { it.sg > 0 && it.sg == id } ?: all.firstOrNull { normName(it.name) == normName(name) }
    }

    // ---- Guías guardadas solo en el móvil (juegos que no están en ningún PC) ----
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE)
    fun local(ctx: Context, id: Long): List<Guide> = try {
        val a = JSONArray(prefs(ctx).getString("guides_$id", "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Guide(it.optString("n"), it.optString("u"), "movil") }
    } catch (e: Exception) { emptyList() }

    fun setLocal(ctx: Context, id: Long, list: List<Guide>) {
        prefs(ctx).edit().putString("guides_$id", JSONArray().apply { list.forEach { put(JSONObject().put("n", it.name).put("u", it.url)) } }.toString()).apply()
    }

    /** Todas las guías de un juego: las del PC, las del móvil y, si no hay ninguna, las sugeridas. */
    fun forGame(ctx: Context, id: Long, name: String): List<Guide> {
        val pc = pcGameFor(id, name)?.links?.map { (n, u) -> Guide(n, u, "pc") } ?: emptyList()
        val mine = local(ctx, id).filter { m -> pc.none { it.url == m.url } }
        val saved = pc + mine
        return saved + presets(name).filter { s -> saved.none { it.url == s.url } }
    }
}

/** Color propio de cada web (el mismo dominio, el mismo color). */
private fun hostHue(host: String) = ((host.hashCode() and 0x7fffffff) % 360).toFloat()

/** Tarjeta grande de una guía: icono de la web, nombre y dominio, sobre un degradado propio. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GuideCard(g: Guide, resume: String?, onOpen: () -> Unit, onLong: () -> Unit) {
    val hue = hostHue(g.host)
    val shape = shp(22.dp)
    Box(
        Modifier.width(216.dp).height(128.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.36f), Color.hsv((hue + 35f) % 360f, 0.65f, 0.16f))))
            .combinedClickable(onClick = onOpen, onLongClick = onLong)
    ) {
        Box(
            Modifier.padding(14.dp).size(44.dp).background(Color(0x33FFFFFF), shp(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage("https://www.google.com/s2/favicons?domain=${g.host}&sz=64", null, Modifier.size(28.dp).clip(shp(6.dp)), contentScale = ContentScale.Fit)
        }
        Text(
            when (g.src) { "pc" -> "PC"; "movil" -> "MÓVIL"; else -> "SUGERIDA" },
            color = Color(0xCCFFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).background(Color(0x33000000), pillShape()).padding(horizontal = 8.dp, vertical = 3.dp)
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(g.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (resume != null) "Seguías en: $resume" else g.host, color = Color(0xB3FFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Apartado «Guías» de la ficha de un juego: tarjetas grandes que abren la web dentro de Atrio
 * (para tenerla en el móvil mientras juegas en el PC). Mantener pulsada una tarjeta: más opciones.
 */
@Composable
fun GuidesSection(id: Long, name: String, modifier: Modifier = Modifier, inCell: Boolean = false) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    val libsKey = Suite.libs
    val list = remember(id, name, libsKey, tick) { Guides.forGame(ctx, id, name) }
    val pc = remember(id, name, libsKey) { Guides.pcGameFor(id, name) }
    var adding by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Guide?>(null) }
    var msg by remember { mutableStateOf("") }
    // Al volver de una guía se actualiza «Seguías en…»
    val life = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(life) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        life.lifecycle.addObserver(o)
        onDispose { life.lifecycle.removeObserver(o) }
    }
    val resume = remember(list, tick) {
        list.associate { g -> g.url to WebActivity.lastPage(ctx, g.url)?.let { (u, t) -> if (u.trimEnd('/') == g.url.trimEnd('/')) null else t.ifBlank { Uri.parse(u).lastPathSegment ?: u } } }
    }
    if (Skin.revista) Column(modifier.fillMaxWidth()) {
        // Revista: una lista de texto (sin tarjetas ni iconos), con «Seguías en…» debajo
        if (!inCell) SectionTitle("Guías", action = "Añadir", onAction = { adding = true })
        list.forEach { g ->
            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            Column(Modifier.fillMaxWidth().combinedClickable(onClick = { Guides.opener?.invoke(g) }, onLongClick = { menu = g }).padding(vertical = 6.dp)) {
                Text(g.title, fontFamily = Display, fontSize = 18.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    (resume[g.url]?.let { "Seguías en: $it" } ?: g.host).uppercase() + if (g.src == "sug") " · SUGERIDA" else "",
                    color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (list.isEmpty()) Text("Ninguna todavía.", color = Muted, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        if (inCell) Text("+ AÑADIR", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.clickable { adding = true }.padding(vertical = 4.dp))
        Text("BUSCAR GUÍAS\u00A0→", color = ColGeneral, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
            modifier = Modifier.clickable { Guides.opener?.invoke(Guide("Buscar guías", "https://duckduckgo.com/?q=" + Uri.encode("$name guía"), "sug")) }.padding(vertical = 4.dp))
        if (msg.isNotEmpty()) Text(msg, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    } else
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Language, null, tint = Charcoal, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Guías", fontFamily = Display, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton({ adding = true }) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Añadir") }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.url }) { g -> GuideCard(g, resume[g.url], { Guides.opener?.invoke(g) }, { menu = g }) }
            item {
                Box(
                    Modifier.width(128.dp).height(128.dp).clip(shp(22.dp)).border(1.dp, Line, shp(22.dp))
                        .clickable { Guides.opener?.invoke(Guide("Buscar guías", "https://duckduckgo.com/?q=" + Uri.encode("$name guía"), "sug")) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Language, null, tint = Muted, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("Buscar guías", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        if (msg.isNotEmpty()) Text(msg, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
    if (adding) {
        var url by remember { mutableStateOf("") }
        var nm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Añadir una guía", fontFamily = Display) },
            text = {
                Column {
                    OutlinedTextField(url, { url = it.trim() }, label = { Text("Dirección (https://…)") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(nm, { nm = it }, label = { Text("Nombre (opcional)") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(if (pc != null) "Se guarda también en el PC: la verás en la página de ${pc.name} en Atrio." else "Este juego no está en la biblioteca de ningún PC: la guía se guarda en este móvil.",
                        color = Muted, fontSize = 13.sp)
                    Text("Truco: desde el navegador, Compartir → Atrio también la guarda.", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton({
                    if (url.isBlank()) return@TextButton
                    val u = if (url.startsWith("http")) url else "https://$url"
                    adding = false
                    if (pc != null) scope.launch { val (_, m) = Suite.addLink(pc.key, u, nm.trim()); msg = m }
                    else { Guides.setLocal(ctx, id, Guides.local(ctx, id) + Guide(nm.trim(), u, "movil")); tick++ }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton({ adding = false }) { Text("Cancelar") } },
            shape = shp(24.dp)
        )
    }
    val m = menu
    if (m != null) {
        AlertDialog(
            onDismissRequest = { menu = null },
            title = { Text(m.title, fontFamily = Display) },
            text = { Text(m.url, color = Muted, fontSize = 13.sp) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton({ menu = null; Guides.opener?.invoke(m) }) { Text(if (resume[m.url] != null) "Seguir donde lo dejé" else "Abrir aquí") }
                    if (resume[m.url] != null) TextButton({ menu = null; WebActivity.forget(ctx, m.url); Guides.opener?.invoke(m) }) { Text("Empezar desde el inicio") }
                    if (pc != null && Suite.target() != null) TextButton({
                        menu = null
                        scope.launch { val (_, r) = Suite.send("guide", JSONObject().put("key", pc.key).put("url", m.url)); msg = r }
                    }) { Text("Abrir en el PC") }
                    if (m.src == "sug" && pc != null) TextButton({ menu = null; scope.launch { val (_, r) = Suite.addLink(pc.key, m.url, m.name); msg = r } }) { Text("Guardar en el PC") }
                    if (m.src == "movil") TextButton({ menu = null; Guides.setLocal(ctx, id, Guides.local(ctx, id).filter { it.url != m.url }); tick++ }) { Text("Quitar") }
                }
            },
            dismissButton = { TextButton({ menu = null }) { Text("Cerrar") } },
            shape = shp(24.dp)
        )
    }
}
