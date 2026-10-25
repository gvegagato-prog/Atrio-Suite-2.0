package com.gonzalo.gamerate

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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

/** Polaris en un PC (lo publica Atrio del PC en su presencia, «st»). */
data class StreamHost(
    val on: Boolean, val running: Boolean, val name: String, val uuid: String, val app: String,
    /** UUID de esa app en Polaris (su apps.json). */
    val appUuid: String,
    val lan: List<String>, val macs: List<String>, val tsIp: String, val tsName: String, val tsInstalled: Boolean,
    val creds: Boolean, val allowed: Boolean
) {
    companion object {
        private fun list(a: JSONArray?) = a?.let { x -> (0 until x.length()).map { x.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
        fun from(o: JSONObject) = StreamHost(
            o.optBoolean("on"), o.optBoolean("run"), o.optString("name"), o.optString("uuid"), o.optString("app", "Desktop").ifBlank { "Desktop" },
            o.optString("appUuid"),
            list(o.optJSONArray("lan")), list(o.optJSONArray("mac")), o.optString("ts"), o.optString("tsn"), o.optBoolean("tsi"),
            o.optBoolean("creds"), o.optBoolean("ok", true)
        )
    }
}

/**
 * Jugar en streaming: el PC (Atrio) arranca Polaris y lanza el juego; aquí se abre Nova directamente en ese PC.
 * Nova se abre por su «atajo» (ShortcutTrampoline, como sus accesos directos): UUID del PC + aplicación.
 * Para emparejar, Nova acepta enlaces art://equipo:puerto?pin=…&passphrase=… con un código de un solo uso de Polaris.
 */
object Stream {
    private const val TRAMPOLINE = "com.papi.nova.ShortcutTrampoline"
    private val KNOWN = listOf("com.papi.nova", "com.papi.nova.root", "com.papi.nova.pre", "com.papi.nova.debug")
    const val NOVA_RELEASES = "https://github.com/papi-ux/nova/releases/latest"

    /** Nova instalado: su paquete (la versión normal, root, beta o de pruebas). */
    fun nova(ctx: Context): String? {
        val pm = ctx.packageManager
        val launchers = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }
        } catch (e: Exception) { emptyList() }
        return (KNOWN + launchers.filter { it.startsWith("com.papi.nova") }).distinct().firstOrNull { p ->
            try { pm.getActivityInfo(ComponentName(p, TRAMPOLINE), 0); true } catch (e: Exception) { false }
        }
    }

    /** Nova no está en Google Play: se baja de su página de GitHub (APK). */
    fun installNova(ctx: Context) = openUrl(ctx, NOVA_RELEASES)
    fun installTailscale(ctx: Context) = store(ctx, "com.tailscale.ipn")

    private fun store(ctx: Context, pkg: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { openUrl(ctx, "https://play.google.com/store/apps/details?id=$pkg") }
    }

    /** Nova en su pantalla de inicio. */
    fun openNova(ctx: Context): Boolean {
        val pkg = nova(ctx) ?: return false
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Nova directo al PC y a la aplicación de Polaris. */
    fun launch(ctx: Context, uuid: String, name: String, app: String, appUuid: String = ""): Boolean {
        val pkg = nova(ctx) ?: return false
        val i = Intent().setComponent(ComponentName(pkg, TRAMPOLINE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (uuid.isNotBlank()) i.putExtra("UUID", uuid)
        if (name.isNotBlank()) i.putExtra("Name", name)
        // Con el UUID de la app, Nova la abre directamente. Solo con el nombre, Nova la busca en su lista guardada
        // de apps de ese PC, que no existe hasta abrir ese PC en Nova una vez («error parsing applist cache»).
        // Sin UUID no se pasa la app: Nova abre la lista de apps de ese PC (y la guarda para la próxima).
        val validUuid = appUuid.isNotBlank() && runCatching { java.util.UUID.fromString(appUuid) }.isSuccess
        if (validUuid) {
            i.putExtra("AppUUID", appUuid)
            i.putExtra("AppName", app.ifBlank { "Desktop" })
        }
        return try { ctx.startActivity(i); true } catch (e: Exception) { false }
    }

    /**
     * Emparejar con un toque: el PC pide a Polaris un código de un solo uso (vale 3 minutos) y Nova lo recibe en un
     * enlace art:// con la dirección del PC (la de casa, o la de Tailscale para fuera). Nova pide confirmar y empareja.
     */
    suspend fun pairAuto(ctx: Context, viaTailscale: Boolean = false): Pair<Boolean, String> {
        val pkg = nova(ctx) ?: return false to "NOVA"
        val pc = Suite.target() ?: return false to "El PC no está conectado."
        val r = Suite.sendRaw("pair", JSONObject().put("auto", true).put("name", android.os.Build.MODEL ?: "Móvil"), pc.dev, 60_000)
        if (!r.optBoolean("ok")) return false to r.optString("msg")
        val ip = if (viaTailscale) pc.st?.tsIp.orEmpty() else r.optString("ip").ifBlank { pc.st?.lan?.firstOrNull().orEmpty() }
        if (ip.isBlank()) return false to "No sé la dirección del PC."
        val port = r.optInt("port", 47989)
        val uri = Uri.Builder().scheme("art").encodedAuthority("$ip:$port")
            .appendQueryParameter("pin", r.optString("otp")).appendQueryParameter("passphrase", r.optString("passphrase"))
            .appendQueryParameter("name", r.optString("name").ifBlank { pc.name }).build()
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true to "En Nova, confirma para emparejar con ${pc.name}"
        } catch (e: Exception) { false to "Nova no ha abierto el enlace de emparejamiento (actualiza Nova)." }
    }

    /** El PC al que se manda el streaming: el conectado, o (para encenderlo) el último que tuvo Polaris. */
    fun host(): LiveDev? = Suite.target() ?: Suite.pcs().filter { it.st?.on == true }.maxByOrNull { it.at }

    /**
     * Prepara el PC (Polaris y, si se da, el juego) y abre Nova. Devuelve un mensaje si algo falla.
     */
    suspend fun play(ctx: Context, key: String?): String? {
        if (nova(ctx) == null) return "NOVA"
        val pc = Suite.target() ?: return "El PC no está conectado. Enciéndelo (o abre Atrio en él) y vuelve a probar."
        val st = pc.st
        if (st != null && !st.on) return "${pc.name} no tiene Polaris. En Atrio del PC: Ajustes → Streaming."
        val r = Suite.sendRaw("stream", JSONObject().apply { if (key != null) put("key", key) }, pc.dev, 75_000)
        if (!r.optBoolean("ok")) return r.optString("msg")
        val uuid = r.optString("uuid").ifBlank { st?.uuid ?: "" }
        val name = r.optString("name").ifBlank { st?.name ?: pc.name }
        // Un momento para que el juego tome la pantalla antes de conectar
        if (key != null) delay(1500)
        val appUuid = r.optString("appUuid").ifBlank { st?.appUuid ?: "" }
        return if (launch(ctx, uuid, name, r.optString("app").ifBlank { st?.app ?: "Desktop" }, appUuid)) null else "No se ha podido abrir Nova."
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

    suspend fun pair(pin: String, name: String): Pair<Boolean, String> =
        Suite.send("pair", JSONObject().put("pin", pin).put("name", name), Suite.target()?.dev, 150_000)
}

private fun toast(ctx: Context, m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()

/** Un botón grande de la pareja «Jugar en el PC / Jugar en streaming». */
@Composable
private fun BigPlay(icon: ImageVector, title: String, sub: String, brush: Brush, busy: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = shp(22.dp)
    // Icono arriba y texto debajo: cabe entero aunque los dos botones vayan lado a lado en un móvil estrecho
    Column(
        modifier.height(104.dp).clip(shape).background(brush).clickable(enabled = enabled && !busy) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.size(36.dp).background(Color(0x2EFFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
            else Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Color(0xCCFFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Los dos botones grandes de un juego del PC: abrirlo en el PC o jugarlo en este móvil en streaming.
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
    var needNova by remember { mutableStateOf(false) }
    var waking by remember { mutableStateOf(false) }
    val running = pc?.game?.key == key
    val launch: () -> Unit = {
        busyPc = true
        scope.launch { val (_, m) = Suite.send("launch", JSONObject().put("key", key), pc?.dev); busyPc = false; toast(ctx, m); onLaunched(m) }
    }
    val stream: () -> Unit = stream@{
        if (Stream.nova(ctx) == null) { needNova = true; return@stream }
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
            if (err == "NOVA") needNova = true else if (err != null) toast(ctx, err)
        }
    }
    if (Skin.revista) {
        // Revista: dos líneas de texto en lugar de botones de color
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val canPc = pc != null && !running
            Column(Modifier.fillMaxWidth().clickable(enabled = canPc && !busyPc) { launch() }) {
                Text(if (busyPc) "Abriendo…" else if (running) "En marcha" else "Jugar en el PC\u00A0→", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = if (canPc || running) Charcoal else Muted)
                Text((pc?.let { if (running) "Ya en ${it.name}" else "En ${it.name}" } ?: "PC apagado").uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Column(Modifier.fillMaxWidth().clickable(enabled = !busySt) { stream() }) {
                Text(if (busySt) "Conectando…" else "Streaming\u00A0→", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColGeneral)
                Text((if (running) "Seguir en el móvil" else "En este móvil").uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    } else
    Column(modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigPlay(
                Icons.Rounded.PlayArrow, if (running) "En marcha" else "Jugar en el PC",
                pc?.let { if (running) "Ya en ${it.name}" else "En ${it.name}" } ?: "PC apagado",
                Brush.linearGradient(listOf(Color(0xFF2B2B31), Color(0xFF17171A))), busyPc, pc != null && !running, Modifier.weight(1f)
            ) { launch() }
            BigPlay(
                Icons.Rounded.Cast, "Streaming", if (running) "Seguir en el móvil" else "En este móvil",
                Brush.linearGradient(listOf(Color(0xFF1F8A8A), Color(0xFF3B3FA8))), busySt, true, Modifier.weight(1f)
            ) { stream() }
        }
    }
    if (needNova) NovaDialog { needNova = false }
    if (waking) WakeDialog(any?.name ?: "el PC", any?.st) { waking = false }
}

@Composable
fun NovaDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Cast, null) },
        title = { Text("Falta Nova", fontFamily = Display) },
        text = { Text("El streaming va con Nova, gratis y de código abierto (no está en Google Play: se instala con su APK de GitHub). Instálalo y en Atrio, PC → Streaming → «Emparejar con Nova»: se empareja con un toque.") },
        confirmButton = { TextButton({ onDismiss(); Stream.installNova(ctx) }) { Text("Descargar Nova") } },
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

/** Panel «Streaming» de la pantalla del PC: estado, emparejar con Nova, fuera de casa, encender. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StreamPanel(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val pc = Suite.target()
    val any = Stream.host()
    val h = (pc ?: any)?.st
    val nv = remember { mutableStateOf(Stream.nova(ctx)) }
    var pairing by remember { mutableStateOf(false) }
    var waking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var need by remember { mutableStateOf(false) }
    var tuyaSetup by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { nv.value = Stream.nova(ctx); delay(4000) } }
    fun pairNow(ts: Boolean) {
        if (nv.value == null) { need = true; return }
        busy = true
        scope.launch {
            val (ok, m) = Stream.pairAuto(ctx, ts)
            busy = false
            if (m == "NOVA") need = true else if (!ok) toast(ctx, m) else toast(ctx, m)
        }
    }
    Column((if (Skin.revista) modifier.fillMaxWidth().surface(20.dp, Crudo).padding(top = 14.dp, bottom = 12.dp)
        else modifier.fillMaxWidth().clip(shp(20.dp))
        .background(Brush.linearGradient(listOf(Color(0x221F8A8A), Color(0x223B3FA8))))
        .border(1.dp, Line, shp(20.dp)).padding(16.dp)).animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CastConnected, null, tint = Charcoal)
            Spacer(Modifier.width(8.dp))
            Text("Streaming", fontFamily = Display, fontSize = 22.sp, modifier = Modifier.weight(1f))
        }
        Text("Juega a los juegos del PC en este móvil con Nova, en casa o con datos.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        StatusLine(nv.value != null, if (nv.value != null) "Nova instalado" else "Nova no está instalado", Icons.Rounded.Cast)
        StatusLine(h?.on == true, when {
            h == null -> "Atrio del PC aún no ha dicho nada de Polaris"
            !h.on -> "El PC no tiene Polaris (Atrio del PC: Ajustes → Streaming)"
            h.running -> "Polaris en marcha en ${h.name}"
            else -> "Polaris instalado (se arranca solo al jugar)"
        }, Icons.Rounded.Wifi)
        StatusLine(h?.tsIp?.isNotBlank() == true, when {
            h?.tsIp?.isNotBlank() == true -> "Fuera de casa: Tailscale ${h.tsIp}"
            h?.tsInstalled == true -> "Tailscale instalado en el PC pero desconectado"
            else -> "Para jugar fuera de casa: Tailscale (opcional)"
        }, Icons.Rounded.Public)
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (nv.value == null) FilledTonalButton({ Stream.installNova(ctx) }, shape = pillShape()) { Text("Descargar Nova") }
            if (pc != null && h?.on == true) FilledTonalButton({ pairNow(false) }, shape = pillShape(), enabled = !busy) {
                Icon(Icons.Rounded.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (busy) "Un momento…" else "Emparejar con Nova")
            }
            if (nv.value != null) OutlinedButton({ Stream.openNova(ctx) }, shape = pillShape()) { Icon(Icons.Rounded.Cast, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Abrir Nova") }
            if (pc != null && h?.on == true && !h.running) OutlinedButton({
                busy = true
                scope.launch { val (_, m) = Suite.send("polaris", JSONObject(), pc.dev, 60_000); busy = false; toast(ctx, m) }
            }, enabled = !busy, shape = pillShape()) { Text(if (busy) "Arrancando…" else "Arrancar Polaris") }
            if (pc != null && h?.on == true) OutlinedButton({
                busy = true
                scope.launch { val err = Stream.play(ctx, null); busy = false; if (err == "NOVA") need = true else if (err != null) toast(ctx, err) }
            }, enabled = !busy, shape = pillShape()) { Text("Ver el escritorio") }
            if (pc != null && h?.on == true) TextButton({ pairing = true }) { Text("Con PIN") }
            TextButton({ tuyaSetup = true }) { Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Enchufe del PC") }
            if (pc == null) OutlinedButton({ waking = true }, shape = pillShape()) { Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Encender ${any?.name ?: "el PC"}") }
        }
        Spacer(Modifier.height(12.dp))
        QualityGuide()
        if (h != null && h.on && !h.creds) {
            Spacer(Modifier.height(8.dp))
            Text("Para emparejar desde aquí, guarda tu usuario de Polaris en Atrio del PC (Ajustes → Streaming).", color = ColGeneral, fontSize = 13.sp)
        }
        if (h?.tsIp?.isNotBlank() == true) {
            Spacer(Modifier.height(12.dp))
            Text("Fuera de casa", fontWeight = FontWeight.SemiBold)
            Text("Con Tailscale en el móvil (misma cuenta que en el PC), empareja Nova con la dirección de Tailscale: Nova la guarda y te conecta estés donde estés.", color = Muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.tsIp, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                IconButton({ clip.setText(AnnotatedString(h.tsIp)); toast(ctx, "Copiada") }) { Icon(Icons.Rounded.ContentCopy, "Copiar") }
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pc != null) OutlinedButton({ pairNow(true) }, shape = pillShape(), enabled = !busy) { Text("Emparejar por Tailscale") }
                TextButton({ Stream.installTailscale(ctx) }) { Icon(Icons.Rounded.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tailscale para el móvil") }
            }
        }
    }
    if (pairing) PairDialog(h?.creds == true) { pairing = false }
    if (tuyaSetup) TuyaDialog { tuyaSetup = false }
    if (need) NovaDialog { need = false }
    if (waking) WakeDialog(any?.name ?: "el PC", any?.st) { waking = false }
}

/** Emparejar a mano: Nova enseña un PIN de 4 números al tocar el PC y aquí se escribe. */
@Composable
fun PairDialog(creds: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.Key, null) },
        title = { Text("Emparejar con PIN", fontFamily = Display) },
        text = {
            Column {
                Text("1. Abre Nova con el móvil en la wifi de casa y toca tu PC.\n2. Te enseña un PIN de 4 números: escríbelo aquí.", fontSize = 14.sp)
                if (!creds) { Spacer(Modifier.height(8.dp)); Text("Antes, en Atrio del PC (Ajustes → Streaming), guarda tu usuario de Polaris.", color = ColGeneral, fontSize = 13.sp) }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(4) }, label = { Text("PIN") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), shape = shp(14.dp))
                if (msg.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(msg, color = Muted, fontSize = 13.sp) }
                Spacer(Modifier.height(4.dp))
                TextButton({ if (!Stream.openNova(ctx)) Stream.installNova(ctx) }) { Text("Abrir Nova") }
            }
        },
        confirmButton = {
            TextButton({
                busy = true; msg = "Mandando el PIN al PC…"
                scope.launch {
                    val (ok, m) = Stream.pair(pin, android.os.Build.MODEL ?: "Móvil")
                    busy = false; msg = m
                    if (ok) { toast(ctx, m); onDismiss() }
                }
            }, enabled = pin.length == 4 && !busy) { Text(if (busy) "Un momento…" else "Emparejar") }
        },
        dismissButton = { TextButton({ onDismiss() }, enabled = !busy) { Text("Cerrar") } },
        shape = shp(24.dp)
    )
}

/** Los botones grandes en la ficha de un juego, si ese juego está en la biblioteca de algún PC. */
@Composable
fun FichaPlayButtons(id: Long, name: String, modifier: Modifier = Modifier) {
    val libs = Suite.libs
    val g = remember(id, name, libs) { Guides.pcGameFor(id, name) } ?: return
    PlayButtons(g.key, g.name, modifier)
}

/** ¿El móvil va ahora con datos (red medida)? Es lo mismo que mira Nova para usar su bitrate de datos. */
fun onMobileData(ctx: Context): Boolean {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
    return cm.isActiveNetworkMetered
}

/**
 * Calidad en casa y con datos. Nova no deja que otra app le cambie la resolución ni los FPS al abrir un juego
 * (los saca de sus propios ajustes), pero sí tiene un bitrate aparte para redes medidas (datos), que usa solo.
 * Aquí se enseña qué poner, con la pantalla de este móvil.
 */
@Composable
private fun QualityGuide() {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val data = remember { onMobileData(ctx) }
    val (w, hgt, hz) = remember {
        val dm = ctx.resources.displayMetrics
        val disp = (ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val mode = disp?.mode
        val ww = maxOf(mode?.physicalWidth ?: dm.widthPixels, mode?.physicalHeight ?: dm.heightPixels)
        val hh = minOf(mode?.physicalWidth ?: dm.widthPixels, mode?.physicalHeight ?: dm.heightPixels)
        val maxHz = disp?.supportedModes?.maxOfOrNull { it.refreshRate } ?: (mode?.refreshRate ?: 60f)
        Triple(ww, hh, Math.round(maxHz))
    }
    val fps = if (hz >= 90) 90 else 60
    Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Wifi, null, tint = Muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Calidad en casa y con datos", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(if (data) "Ahora: datos" else "Ahora: wifi", color = Muted, fontSize = 12.sp)
    }
    if (open) {
        Text(
            "Nova elige la calidad con sus propios ajustes (Atrio no puede cambiársela al abrirlo). Así queda como pides:\n" +
                "• En casa (wifi), en Nova → Ajustes: resolución nativa ${w}×${hgt} y $fps FPS" + (if (hz < 90) " (tu pantalla llega a $hz Hz)" else "") +
                ", bitrate alto (40–80 Mbps).\n" +
                "• Con datos: en Nova → Ajustes → «Video bitrate on metered networks», 8–12 Mbps (unos 3,5–5,5 GB por hora). " +
                "Nova lo usa solo cuando la red es de datos.\n" +
                "• Bajar también a 60 FPS o a menos resolución con datos: en Nova, crea un perfil «Datos» (1080p, 60 FPS) y actívalo antes de salir de casa.",
            color = Muted, fontSize = 13.sp
        )
    }
}
