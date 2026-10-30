package com.gonzalo.gamerate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Steam en un PC (lo publica Atrio del PC en su presencia, «st»): para jugar en streaming con Steam Link. */
data class StreamHost(
    /** Steam instalado en ese PC. */
    val on: Boolean,
    /** Steam abierto ahora (si no, Atrio del PC lo abre al jugar). */
    val running: Boolean,
    val name: String,
    val lan: List<String>, val macs: List<String>
) {
    companion object {
        private fun list(a: JSONArray?) = a?.let { x -> (0 until x.length()).map { x.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
        fun from(o: JSONObject) = StreamHost(
            o.optBoolean("on"), o.optBoolean("run"), o.optString("name"),
            list(o.optJSONArray("lan")), list(o.optJSONArray("mac"))
        )
    }
}

/**
 * Jugar en streaming con Steam Link (la app gratuita de Valve, en Google Play): Atrio del PC abre Steam (Steam Link no
 * conecta con un PC sin Steam abierto) y el juego, y aquí se abre Steam Link. Valve no documenta cómo decirle a
 * Steam Link a qué PC o a qué juego ir: se abre en su pantalla de inicio, con el PC que tenga emparejado.
 */
object Stream {
    const val STEAM_LINK = "com.valvesoftware.steamlink"
    /** Lo que devuelve [play] cuando falta Steam Link en este móvil. */
    const val FALTA = "STEAM_LINK"

    /** Steam Link instalado en este móvil. */
    fun installed(ctx: Context): Boolean = ctx.packageManager.getLaunchIntentForPackage(STEAM_LINK) != null

    fun install(ctx: Context) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$STEAM_LINK")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { openUrl(ctx, "https://play.google.com/store/apps/details?id=$STEAM_LINK") }
    }

    /** Abre Steam Link (en su pantalla de inicio). */
    fun open(ctx: Context): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(STEAM_LINK) ?: return false
        return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }

    /** El PC al que se manda el streaming: el conectado, o (para encenderlo) el último que tuvo Steam. */
    fun host(): LiveDev? = Suite.target() ?: Suite.pcs().filter { it.st?.on == true }.maxByOrNull { it.at }

    /**
     * Prepara el PC (Steam abierto y, si se da, el juego lanzado) y abre Steam Link. Devuelve un mensaje si algo falla
     * ([FALTA] si este móvil no tiene Steam Link).
     */
    suspend fun play(ctx: Context, key: String?): String? {
        if (!installed(ctx)) return FALTA
        val pc = Suite.target() ?: return "El PC no está conectado. Enciéndelo (o abre Atrio en él) y vuelve a probar."
        val st = pc.st
        if (st != null && !st.on) return "${pc.name} no tiene Steam: Steam Link necesita Steam abierto en el PC."
        val r = Suite.sendRaw("stream", JSONObject().apply { if (key != null) put("key", key) }, pc.dev, 75_000)
        if (!r.optBoolean("ok")) return r.optString("msg")
        // Un momento para que el juego tome la pantalla antes de conectar
        if (key != null) delay(1500)
        return if (open(ctx)) null else "No se ha podido abrir Steam Link."
    }

    /** Encender el PC por red (Wake-on-LAN): solo en la misma red de casa y con la opción activada en la BIOS. */
    suspend fun wake(h: StreamHost): Boolean = withContext(Dispatchers.IO) {
        val macs = h.macs.mapNotNull { m -> m.split(':', '-').takeIf { it.size == 6 }?.map { it.toInt(16).toByte() } }
        if (macs.isEmpty()) return@withContext false
        val targets = (listOf("255.255.255.255") + h.lan.mapNotNull { ip -> ip.split('.').takeIf { it.size == 4 }?.let { "${it[0]}.${it[1]}.${it[2]}.255" } }).distinct()
        var sent = false
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                for (mac in macs) {
                    val pkt = ByteArray(6) { 0xFF.toByte() } + (0 until 16).flatMap { mac }.toByteArray()
                    for (t in targets) for (port in listOf(9, 7)) {
                        try { s.send(DatagramPacket(pkt, pkt.size, InetAddress.getByName(t), port)); sent = true } catch (e: Exception) { }
                    }
                }
            }
        } catch (e: Exception) { }
        sent
    }
}

private fun toast(ctx: Context, m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()

/**
 * Los dos botones de un juego del PC: abrirlo en el PC o jugarlo en este móvil en streaming (Steam Link).
 * [key]: clave del juego en la biblioteca del PC.
 */
@Composable
fun PlayButtons(key: String, name: String, modifier: Modifier = Modifier, onLaunched: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pc = Suite.target()
    val any = Stream.host()
    // El PC apagado se puede encender por red si Atrio publicó su MAC
    val wol = any?.st?.takeIf { it.macs.isNotEmpty() }
    var busyPc by remember { mutableStateOf(false) }
    var busySt by remember { mutableStateOf(false) }
    var falta by remember { mutableStateOf(false) }
    var waking by remember { mutableStateOf(false) }
    val running = pc?.game?.key == key
    val launch: () -> Unit = {
        busyPc = true
        scope.launch { val (_, m) = Suite.send("launch", JSONObject().put("key", key), pc?.dev); busyPc = false; toast(ctx, m); onLaunched(m) }
    }
    val stream: () -> Unit = stream@{
        if (!Stream.installed(ctx)) { falta = true; return@stream }
        if (pc == null) {
            if (wol != null) waking = true
            else toast(ctx, "El PC no está conectado: enciéndelo o abre Atrio en él.")
            return@stream
        }
        busySt = true
        scope.launch {
            val err = Stream.play(ctx, if (running) null else key)
            busySt = false
            if (err == Stream.FALTA) falta = true else if (err != null) toast(ctx, err)
        }
    }
    // Dos líneas de texto, como el resto de la revista
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val canPc = pc != null && !running
        Column(Modifier.fillMaxWidth().clickable(enabled = canPc && !busyPc) { launch() }) {
            Text(if (busyPc) "Abriendo…" else if (running) "En marcha" else "Jugar en el PC\u00A0→", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                color = if (canPc || running) Charcoal else Muted)
            Text((pc?.let { if (running) "Ya en ${it.name}" else "En ${it.name}" } ?: "PC apagado").uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Column(Modifier.fillMaxWidth().clickable(enabled = !busySt) { stream() }) {
            Text(if (busySt) "Conectando…" else "Streaming\u00A0→", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColGeneral)
            Text((if (running) "Seguir en el móvil, con Steam Link" else "En este móvil, con Steam Link").uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
    if (falta) SteamLinkDialog { falta = false }
    if (waking && wol != null) WakeDialog(any?.name ?: "el PC", wol) { waking = false }
}

/** Falta Steam Link en este móvil: qué es y dónde se instala. */
@Composable
fun SteamLinkDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Cast, null) },
        title = { Text("Falta Steam Link", fontFamily = Display) },
        text = { Text("El streaming va con Steam Link, la app gratuita de Valve (está en Google Play). Instálala, ábrela y elige tu PC: te enseña un PIN que se escribe en Steam del PC. Desde entonces, «Streaming» en cualquier juego.") },
        confirmButton = { TextButton({ onDismiss(); Stream.install(ctx) }) { Text("Instalar Steam Link") } },
        dismissButton = { TextButton(onDismiss) { Text("Ahora no") } },
        shape = shp(24.dp)
    )
}

/** Encender el PC por red (Wake-on-LAN): con el móvil en la wifi de casa y la opción activada en la BIOS. */
@Composable
fun WakeDialog(pcName: String, h: StreamHost, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.PowerSettingsNew, null) },
        title = { Text("¿Encender $pcName?", fontFamily = Display) },
        text = {
            Column {
                Text("Por red (Wake-on-LAN): solo con el móvil en la wifi de casa y la opción activada en la BIOS.")
                if (msg.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(msg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton({
                scope.launch {
                    msg = if (Stream.wake(h)) "Señal enviada. Cuando arranque y se abra Atrio, vuelve a tocar «Streaming»." else "No se ha podido mandar la señal."
                }
            }) { Text("Encender") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cerrar") } },
        shape = shp(24.dp)
    )
}

@Composable
private fun StatusLine(ok: Boolean, text: String, icon: ImageVector) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(if (ok) ColMusic else Line, CircleShape))
        Spacer(Modifier.width(10.dp))
        Icon(icon, null, tint = Muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 14.sp)
    }
}

/** Panel «Streaming» de la pantalla del PC: Steam Link en este móvil, Steam en el PC, emparejar y encender el PC por red. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StreamPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pc = Suite.target()
    val any = Stream.host()
    val h = (pc ?: any)?.st
    val wol = any?.st?.takeIf { it.macs.isNotEmpty() }
    var link by remember { mutableStateOf(Stream.installed(ctx)) }
    var waking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var falta by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { link = Stream.installed(ctx); delay(4000) } }
    // Abrir Steam Link; con el PC conectado y con Steam, antes se abre Steam en él (si no, Steam Link no lo encuentra)
    fun abrir() {
        if (!link) { falta = true; return }
        if (pc == null || h?.on != true) { Stream.open(ctx); return }
        busy = true
        scope.launch { val err = Stream.play(ctx, null); busy = false; if (err == Stream.FALTA) falta = true else if (err != null) toast(ctx, err) }
    }
    Column(modifier.fillMaxWidth().surface(20.dp, Crudo).padding(top = 14.dp, bottom = 12.dp).animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CastConnected, null, tint = Charcoal)
            Spacer(Modifier.width(8.dp))
            Text("Streaming", fontFamily = Display, fontSize = 22.sp, modifier = Modifier.weight(1f))
        }
        Text("Juega a los juegos del PC en este móvil con Steam Link, la app gratuita de Valve.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        StatusLine(link, if (link) "Steam Link instalado" else "Steam Link no está instalado", Icons.Rounded.Cast)
        StatusLine(h?.on == true, when {
            h == null -> "Atrio del PC aún no ha dicho nada de Steam"
            !h.on -> "El PC no tiene Steam instalado"
            h.running -> "Steam abierto en ${h.name}"
            else -> "Steam cerrado en ${h.name} (Atrio lo abre al jugar)"
        }, Icons.Rounded.Wifi)
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (!link) EnlaceRevista("Instalar Steam Link", acento = true) { Stream.install(ctx) }
            else EnlaceRevista(if (busy) "Abriendo…" else "Abrir Steam Link", acento = true) { if (!busy) abrir() }
            if (pc == null && wol != null) EnlaceRevista("Encender ${any?.name ?: "el PC"}") { waking = true }
        }
        Spacer(Modifier.height(6.dp))
        Text("La primera vez: en Steam Link elige tu PC y te enseña un PIN; escríbelo en Steam del PC (sale una ventana, o en Ajustes → Remote Play → Emparejar Steam Link). Steam Link también puede conectarse fuera de casa, por internet.",
            color = Muted, fontSize = 13.sp)
    }
    if (falta) SteamLinkDialog { falta = false }
    if (waking && wol != null) WakeDialog(any?.name ?: "el PC", wol) { waking = false }
}

/** Los botones de jugar en la ficha de un juego, si ese juego está en la biblioteca de algún PC. */
@Composable
fun FichaPlayButtons(id: Long, name: String, modifier: Modifier = Modifier) {
    val libs = Suite.libs
    val g = remember(id, name, libs) { Guides.pcGameFor(id, name) } ?: return
    PlayButtons(g.key, g.name, modifier)
}
