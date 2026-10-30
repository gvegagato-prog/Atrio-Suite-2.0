package com.gonzalo.gamerate

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            val h = any?.st
            if ((h != null && h.macs.isNotEmpty()) || Tuya.configured(ctx)) waking = true
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
    if (waking) WakeDialog(any?.name ?: "el PC", any?.st) { waking = false }
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

/** Encender el PC: por red (Wake-on-LAN, en casa) o con el enchufe de Tuya (estés donde estés). */
@Composable
fun WakeDialog(pcName: String, h: StreamHost?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    val tuya = remember(setup) { Tuya.configured(ctx) }
    val wol = h != null && h.macs.isNotEmpty()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.PowerSettingsNew, null) },
        title = { Text("¿Encender $pcName?", fontFamily = Display) },
        text = {
            Column {
                if (tuya) Text("Con el enchufe: Atrio corta la corriente unos segundos y la vuelve a dar; con «Restore on AC power loss» en «Power On» en la BIOS, el PC arranca solo. Funciona también con datos. Si el PC estuviera encendido, se apagaría de golpe.")
                else Text("Con un enchufe inteligente de Tuya (Smart Life) puedes encenderlo desde cualquier sitio. Configúralo abajo.")
                if (wol) { Spacer(Modifier.height(8.dp)); Text("Por red (Wake-on-LAN): solo con el móvil en la wifi de casa y la opción activada en la BIOS.", color = Muted, fontSize = 13.sp) }
                if (msg.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(msg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                TextButton({ setup = true }, enabled = !busy) { Text(if (tuya) "Ajustes del enchufe" else "Configurar el enchufe (Tuya)") }
            }
        },
        confirmButton = {
            Row {
                if (wol) TextButton({
                    scope.launch {
                        msg = if (Stream.wake(h!!)) "Señal enviada. Cuando arranque y se abra Atrio, vuelve a tocar «Streaming»." else "No se ha podido mandar la señal."
                    }
                }, enabled = !busy) { Text("Por red") }
                if (tuya) TextButton({
                    busy = true
                    scope.launch {
                        val r = Tuya.powerCycle(ctx) { msg = it }
                        busy = false
                        msg = r.fold({ "Corriente dada: el PC debería estar arrancando. Cuando se abra Atrio, vuelve a tocar «Streaming»." }, { it.message ?: "No se ha podido." })
                    }
                }, enabled = !busy) { Text(if (busy) "Un momento…" else "Con el enchufe") }
            }
        },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cerrar") } },
        shape = shp(24.dp)
    )
    if (setup) TuyaDialog { setup = false }
}

/** Ajustes del enchufe de Tuya (se guardan solo en este móvil). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TuyaDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c0 = remember { Tuya.load(ctx) }
    var region by remember { mutableStateOf(c0.region) }
    var host by remember { mutableStateOf(c0.host) }
    var id by remember { mutableStateOf(c0.id) }
    var secret by remember { mutableStateOf(c0.secret) }
    var device by remember { mutableStateOf(c0.device) }
    var wait by remember { mutableStateOf(c0.waitSec) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun save() = Tuya.save(ctx, Tuya.Cfg(region, host, id, secret, device, wait))
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.PowerSettingsNew, null) },
        title = { Text("Enchufe del PC (Tuya)", fontFamily = Display) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "1. En la BIOS del PC: «Restore on AC power loss» (o «AC Back», «Power On after power failure») en «Power On».\n" +
                        "2. En iot.tuya.com crea un proyecto de «Cloud» (Smart Home, el centro de datos de tu cuenta) y enlaza tu cuenta de Smart Life o Tuya Smart (Devices → Link App Account, con el QR).\n" +
                        "3. Copia aquí el Access ID y el Access Secret del proyecto y el Device ID del enchufe (en Devices).\n" +
                        "La prueba gratuita de Tuya caduca cada cierto tiempo: se renueva en el proyecto (IoT Core → ampliar prueba).",
                    fontSize = 13.sp, color = Muted
                )
                Spacer(Modifier.height(10.dp))
                Text("Centro de datos", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tuya.REGIONS.forEach { (k, v) -> SoftChip(v.first, region == k) { region = k; host = v.second } }
                }
                OutlinedTextField(host, { host = it.trim() }, label = { Text("Dirección de la API") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(id, { id = it.trim() }, label = { Text("Access ID") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(secret, { secret = it.trim() }, label = { Text("Access Secret") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                OutlinedTextField(device, { device = it.trim() }, label = { Text("Device ID del enchufe") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Segundos sin corriente", Modifier.weight(1f), fontSize = 14.sp)
                    SoftChip("−", false) { wait = (wait - 2).coerceAtLeast(4) }
                    Text("  $wait  ", fontWeight = FontWeight.Bold)
                    SoftChip("+", false) { wait = (wait + 2).coerceAtMost(30) }
                }
                if (msg.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(msg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton({
                save(); busy = true; msg = "Preguntando a Tuya…"
                scope.launch {
                    val r = Tuya.isOn(ctx)
                    busy = false
                    msg = r.fold({ if (it) "Funciona: el enchufe está dando corriente." else "Funciona: el enchufe está apagado." }, { it.message ?: "No ha funcionado." })
                }
            }, enabled = !busy && id.isNotBlank() && secret.isNotBlank() && device.isNotBlank()) { Text("Guardar y probar") }
        },
        dismissButton = { TextButton({ save(); onDismiss() }, enabled = !busy) { Text("Listo") } },
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

/** Panel «Streaming» de la pantalla del PC: Steam Link en este móvil, Steam en el PC, emparejar y encender el PC. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StreamPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pc = Suite.target()
    val any = Stream.host()
    val h = (pc ?: any)?.st
    var link by remember { mutableStateOf(Stream.installed(ctx)) }
    var waking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var falta by remember { mutableStateOf(false) }
    var tuyaSetup by remember { mutableStateOf(false) }
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
            if (pc == null) EnlaceRevista("Encender ${any?.name ?: "el PC"}") { waking = true }
            EnlaceRevista("Enchufe del PC") { tuyaSetup = true }
        }
        Spacer(Modifier.height(6.dp))
        Text("La primera vez: en Steam Link elige tu PC y te enseña un PIN; escríbelo en Steam del PC (sale una ventana, o en Ajustes → Remote Play → Emparejar Steam Link). Steam Link también puede conectarse fuera de casa, por internet.",
            color = Muted, fontSize = 13.sp)
    }
    if (tuyaSetup) TuyaDialog { tuyaSetup = false }
    if (falta) SteamLinkDialog { falta = false }
    if (waking) WakeDialog(any?.name ?: "el PC", any?.st) { waking = false }
}

/** Los botones de jugar en la ficha de un juego, si ese juego está en la biblioteca de algún PC. */
@Composable
fun FichaPlayButtons(id: Long, name: String, modifier: Modifier = Modifier) {
    val libs = Suite.libs
    val g = remember(id, name, libs) { Guides.pcGameFor(id, name) } ?: return
    PlayButtons(g.key, g.name, modifier)
}
