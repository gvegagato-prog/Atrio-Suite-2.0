package com.gonzalo.gamerate

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Base64

/**
 * La suite: Ronrón y Atrio (el lanzador del PC) conectados en tiempo real por tu cuenta.
 *
 *   /users/<uid>/suite/live/<disp>       quién está conectado y a qué juega (cada dispositivo escribe el suyo)
 *   /users/<uid>/suite/lib/<disp>        la biblioteca de cada PC
 *   /users/<uid>/suite/cmd/<disp>/<id>   órdenes para un PC ({ op, a, t, by }); el PC responde en «res»
 *   /users/<uid>/suite/shots/<id>        capturas (los trozos en /users/<uid>/shotdata/<id>/<n>)
 *   /users/<uid>/stamps                  se escucha para mezclar al momento lo que cambie el PC
 *
 * Se escucha con la API REST de Firebase en streaming (EventSource): cada cambio llega al instante.
 */

/** Un dispositivo de tu cuenta conectado (un PC con Atrio, otro móvil…). */
data class LiveDev(
    val dev: String, val app: String, val name: String, val os: String, val online: Boolean, val at: Long,
    val game: LiveGame?, val canShot: Boolean,
    /** Minutos jugados hoy en ese PC. */
    val today: Int = 0,
    /** Su Polaris (streaming), si lo tiene instalado. */
    val st: StreamHost? = null
)

/** Lo que se está jugando en un PC. */
data class LiveGame(val key: String, val name: String, val sg: Long, val start: Long, val cover: String, val hero: String, val logo: String)

/** Un juego de la biblioteca de un PC. */
data class PcGame(
    val key: String, val name: String, val cover: String, val sg: Long, val fav: Boolean, val lastPlayed: Long, val src: String, val minutes: Int,
    /** Webs guardadas para el juego en Atrio del PC (guías, builds…): nombre y dirección. */
    val links: List<Pair<String, String>> = emptyList()
)

/** Una captura (en la nube o ya en el móvil). */
data class Shot(
    val id: String, val pc: String, val w: Int, val h: Int, val mime: String, val bytes: Long, val time: Long,
    val game: String, val sg: Long, val uri: String = "", val by: String = ""
) {
    val local: Boolean get() = uri.isNotEmpty()
}

/** Lee un sitio de la base de datos en streaming y mantiene una copia del árbol. */
class FbStream(
    private val name: String,
    private val url: (Boolean) -> String?,
    private val onTree: (JSONObject?, String) -> Unit,
    private val onState: (String) -> Unit = {}
) {
    @Volatile private var conn: HttpURLConnection? = null
    private var job: Job? = null
    private var tree: Any? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            var retry = 0
            while (isActive) {
                val u = try { url(retry > 0) } catch (e: Exception) { null }
                if (u == null) { onState("sin sesión"); delay(15_000); retry++; continue }
                try {
                    val c = URL(u).openConnection() as HttpURLConnection
                    conn = c
                    c.setRequestProperty("Accept", "text/event-stream")
                    c.instanceFollowRedirects = true
                    c.connectTimeout = 15_000
                    // Firebase manda un «keep-alive» cada medio minuto: si en 75 s no llega nada, se reconecta
                    c.readTimeout = 75_000
                    val code = c.responseCode
                    if (code == 401 || code == 403) { onState("sin permiso"); c.disconnect(); delay(30_000); retry++; continue }
                    if (code !in 200..299) throw java.io.IOException("respuesta $code")
                    retry = 0
                    onState("conectado")
                    val reader = c.inputStream.bufferedReader(Charsets.UTF_8)
                    var event = ""
                    val data = StringBuilder()
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        when {
                            line.startsWith("event:") -> event = line.substring(6).trim()
                            line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.substring(5).trimStart()) }
                            line.isEmpty() -> {
                                handle(event, data.toString())
                                if (event == "auth_revoked" || event == "cancel") break
                                event = ""
                                data.setLength(0)
                            }
                        }
                    }
                    c.disconnect()
                    onState("reconectando")
                    delay(1000)
                } catch (e: SocketTimeoutException) {
                    onState("reconectando")
                } catch (e: Exception) {
                    if (!isActive) break
                    onState("sin conexión")
                    retry++
                    delay(minOf(60_000L, 1000L shl minOf(retry, 6)))
                }
            }
        }
    }

    private fun handle(event: String, raw: String) {
        if (event != "put" && event != "patch") return
        val o = try { JSONObject(raw) } catch (e: Exception) { return }
        val path = o.optString("path", "/")
        val data = o.opt("data")
        tree = setIn(tree, path.split('/').filter { it.isNotEmpty() }, data, event == "patch")
        onTree(tree as? JSONObject, path)
    }

    private fun setIn(root: Any?, ps: List<String>, v: Any?, merge: Boolean): Any? {
        if (ps.isEmpty()) {
            if (merge && root is JSONObject && v is JSONObject) {
                val out = JSONObject(root.toString())
                v.keys().forEach { k -> if (v.isNull(k)) out.remove(k) else out.put(k, v.get(k)) }
                return out
            }
            return if (v == JSONObject.NULL) null else v
        }
        val out = if (root is JSONObject) root else JSONObject()
        val next = setIn(out.opt(ps[0]), ps.drop(1), v, merge)
        if (next == null || (next is JSONObject && next.length() == 0)) out.remove(ps[0]) else out.put(ps[0], next)
        return out
    }

    fun stop() {
        job?.cancel()
        job = null
        val c = conn
        conn = null
        Thread { try { c?.disconnect() } catch (e: Exception) { } }.start()
        tree = null
    }
}

object Suite {
    /** Tiempo sin latido para dar un dispositivo por desconectado. */
    private const val ONLINE_MS = 3 * 60_000L

    var devices by mutableStateOf<List<LiveDev>>(emptyList()); private set
    var libs by mutableStateOf<Map<String, List<PcGame>>>(emptyMap()); private set
    var cloudShots by mutableStateOf<List<Shot>>(emptyList()); private set
    var localShots by mutableStateOf<List<Shot>>(emptyList()); private set
    var state by mutableStateOf("desconectado"); private set
    /** Capturas bajándose ahora: id -> progreso (0..1). */
    val downloading = mutableStateMapOf<String, Float>()
    /** Aviso para la interfaz (se borra al enseñarlo). */
    var notice by mutableStateOf<String?>(null)
    /** Captura recién llegada (la interfaz la puede abrir). */
    var arrived by mutableStateOf<Shot?>(null)
    /** Para que la interfaz se redibuje cada minuto (cronómetro de la partida). */
    var tick by mutableStateOf(0L)

    private var scope: CoroutineScope? = null
    private var suiteStream: FbStream? = null
    private var stampsStream: FbStream? = null
    private var uid = ""
    private var tree: JSONObject? = null
    private val waits = HashMap<String, CompletableDeferred<JSONObject>>()
    private val busy = HashSet<String>()
    private lateinit var appCtx: Context
    private lateinit var store: Store

    fun online(): List<LiveDev> = devices.filter { it.online && it.app == "atrio" }
    fun pcs(): List<LiveDev> = devices.filter { it.app == "atrio" }

    /** El PC al que se mandan las órdenes: el elegido si está conectado, si no el que esté jugando o el primero. */
    fun target(): LiveDev? {
        val on = online()
        return on.firstOrNull { it.dev == store.suitePc } ?: on.firstOrNull { it.game != null } ?: on.firstOrNull()
    }

    private fun base() = "/users/${store.accUid}"

    private fun authUrl(path: String, force: Boolean): String? {
        if (!store.paired) return null
        // Fuerza un pase nuevo si el anterior dio problemas
        if (force) try { Account.db(store, "GET", "/users/${store.accUid}/suite/live/${store.accDevice}/at", null) } catch (e: Exception) { }
        return store.dbUrl + path + ".json?auth=" + Account.token(store, false)
    }

    /** Empieza a escuchar (al abrir la app con la cuenta iniciada). */
    fun start(ctx: Context, st: Store) {
        appCtx = ctx.applicationContext
        store = st
        loadLocal()
        if (!store.paired) { stop(); return }
        if (scope != null && uid == store.accUid) return
        stop()
        uid = store.accUid
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = sc
        suiteStream = FbStream("suite", { f -> authUrl("/users/$uid/suite", f) }, { t, _ -> onSuite(t) }, { s -> state = s }).also { it.start(sc) }
        var first = true
        stampsStream = FbStream("stamps", { f -> authUrl("/users/$uid/stamps", f) }, { _, path ->
            if (first) { first = false; return@FbStream }
            if (path.contains(store.accDevice)) return@FbStream // mi propia subida
            sc.launch { try { Couple.syncOwn(store) } catch (e: Exception) { } }
        }).also { it.start(sc) }
        // Presencia: este móvil está conectado (el PC lo enseña) y latido para el cronómetro
        sc.launch {
            while (isActive) {
                presence(true)
                repeat(6) { delay(10_000); tick = System.currentTimeMillis() }
            }
        }
    }

    /** Deja de escuchar (al salir de la app). */
    fun stop() {
        val sc = scope
        if (sc != null && store.paired) {
            CoroutineScope(Dispatchers.IO).launch { presence(false) }
        }
        suiteStream?.stop(); suiteStream = null
        stampsStream?.stop(); stampsStream = null
        sc?.coroutineContext?.get(Job)?.cancel()
        scope = null
        tree = null
        state = "desconectado"
    }

    private fun presence(on: Boolean) {
        try {
            // «dn»: el nombre del aparato (el de Ajustes del sistema: «OPPO Pad Mini»), para distinguir el móvil de la tableta
            val dn = try { android.provider.Settings.Global.getString(appCtx.contentResolver, "device_name") } catch (e: Exception) { null }
            val o = JSONObject().put("app", "ronron").put("name", store.myName.ifBlank { Build.MODEL ?: "Móvil" })
                .put("dn", dn?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Móvil")
                .put("os", "android").put("on", on).put("at", JSONObject().put(".sv", "timestamp"))
            Account.db(store, "PUT", base() + "/suite/live/${store.accDevice}", o.toString(), "print=silent", ok404 = false)
        } catch (e: Exception) { }
    }

    private fun onSuite(t: JSONObject?) {
        tree = t
        val now = System.currentTimeMillis()
        val live = t?.optJSONObject("live") ?: JSONObject()
        devices = live.keys().asSequence().filter { it != store.accDevice }.mapNotNull { d ->
            val o = live.optJSONObject(d) ?: return@mapNotNull null
            val g = o.optJSONObject("game")
            LiveDev(
                d, o.optString("app"), o.optString("dn").ifBlank { o.optString("name", "?") }, o.optString("os"),
                o.optBoolean("on") && now - o.optLong("at") < ONLINE_MS, o.optLong("at"),
                g?.let { LiveGame(it.optString("key"), it.optString("name"), it.optLong("sg"), it.optLong("s"), it.optString("cover"), it.optString("hero"), it.optString("logo")) },
                o.optBoolean("shot", true), o.optInt("today", 0),
                o.optJSONObject("st")?.let { StreamHost.from(it) }
            )
        }.sortedByDescending { it.at }.toList()
        val lib = t?.optJSONObject("lib") ?: JSONObject()
        libs = lib.keys().asSequence().associateWith { d ->
            val arr = lib.optJSONObject(d)?.optJSONArray("games") ?: JSONArray()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
                PcGame(it.optString("k"), it.optString("n"), it.optString("c"), it.optLong("sg"), it.optInt("f") == 1, it.optLong("lp"), it.optString("s"), it.optInt("m"),
                    (it.optJSONArray("w") ?: JSONArray()).let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { l -> l.optString("n") to l.optString("u") } }.filter { p -> p.second.startsWith("http") } })
            }
        }
        // Respuestas a mis órdenes
        val cmds = t?.optJSONObject("cmd") ?: JSONObject()
        synchronized(waits) {
            for ((id, w) in waits.toMap()) {
                cmds.keys().forEach { d ->
                    val res = cmds.optJSONObject(d)?.optJSONObject(id)?.optJSONObject("res")
                    if (res != null) { w.complete(res); waits.remove(id) }
                }
            }
        }
        // Órdenes para este dispositivo (otro dispositivo pide una foto tuya)
        cmds.optJSONObject(store.accDevice)?.let { mine ->
            for (id in mine.keys().asSequence().toList()) {
                val c = mine.optJSONObject(id) ?: continue
                if (c.has("res") || id in busy) continue
                if (System.currentTimeMillis() - c.optLong("t") > 10 * 60_000L) continue
                busy.add(id)
                scope?.launch { answer(id, c) }
            }
        }
        // Capturas: las nuevas se bajan solas
        val shots = t?.optJSONObject("shots") ?: JSONObject()
        val list = shots.keys().asSequence().mapNotNull { id -> shots.optJSONObject(id)?.let { id to it } }.toList()
        cloudShots = list.filter { it.second.optBoolean("done") && !it.second.optBoolean("gone") }.map { (id, o) -> shotOf(id, o) }.sortedByDescending { it.time }
        for ((id, o) in list) {
            val mine = o.optJSONObject("got")?.has(store.accDevice) == true
            if (o.optBoolean("done") && !o.optBoolean("gone") && !mine && localShots.none { it.id == id } && id !in busy) {
                busy.add(id)
                scope?.launch { download(id, o) }
            } else if (!o.optBoolean("done") && o.optInt("n") > 0) {
                downloading[id] = o.optInt("up") * 0.5f / o.optInt("n")
            }
        }
    }

    private fun shotOf(id: String, o: JSONObject, uri: String = ""): Shot {
        val g = o.optJSONObject("game")
        return Shot(id, o.optString("pc"), o.optInt("w"), o.optInt("h"), o.optString("mime", "image/png"), o.optLong("bytes"),
            o.optLong("t"), g?.optString("n") ?: "", g?.optLong("sg") ?: 0L, uri, o.optString("by"))
    }

    /** Baja una captura trozo a trozo, la guarda tal cual (sin recomprimir) y avisa al PC de que ya la tiene. */
    private suspend fun download(id: String, o: JSONObject) {
        val n = o.optInt("n")
        try {
            downloading[id] = 0.5f
            val out = java.io.ByteArrayOutputStream(o.optInt("bytes").coerceAtLeast(1024))
            val dec = Base64.getDecoder()
            // Cada trozo tiene un número de caracteres múltiplo de 4: se decodifica por separado
            for (i in 0 until n) {
                val raw = Account.db(store, "GET", base() + "/shotdata/$id/$i", null) ?: throw java.io.IOException("falta un trozo")
                val part = JSONTokener(raw).nextValue() as? String ?: throw java.io.IOException("trozo dañado")
                out.write(dec.decode(part))
                downloading[id] = 0.5f + 0.5f * (i + 1) / n
            }
            val bytes = out.toByteArray()
            if (bytes.size.toLong() != o.optLong("bytes")) throw java.io.IOException("tamaño distinto")
            val shot = shotOf(id, o)
            val uri = save(shot, bytes)
            val done = shot.copy(uri = uri)
            addLocal(done)
            Account.db(store, "PATCH", base() + "/suite/shots/$id/got", JSONObject().put(store.accDevice, JSONObject().put(".sv", "timestamp")).toString(), "print=silent", ok404 = false)
            arrived = done
        } catch (e: Exception) {
            notice = "No se ha podido bajar la captura: ${e.message}"
            busy.remove(id)
        } finally {
            downloading.remove(id)
        }
    }

    /** Guarda en la app (siempre) y en la galería (si se quiere), con su formato y tamaño originales. */
    private fun save(s: Shot, bytes: ByteArray): String {
        val ext = if (s.mime == "image/jpeg") "jpg" else "png"
        val dir = File(appCtx.filesDir, "capturas").apply { mkdirs() }
        val f = File(dir, "${s.id}.$ext")
        f.writeBytes(bytes)
        if (store.shotsToGallery) {
            try {
                val safe = s.game.ifBlank { "Escritorio" }.replace(Regex("[\\\\/:*?\"<>|]+"), " ").trim().take(60)
                val v = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "${safe} ${java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.ROOT).format(java.util.Date(s.time))}.$ext")
                    put(MediaStore.Images.Media.MIME_TYPE, s.mime)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Atrio/$safe")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val r = appCtx.contentResolver
                val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
                if (uri != null) {
                    r.openOutputStream(uri)?.use { it.write(bytes) }
                    r.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                }
            } catch (e: Exception) { /* sin galería: queda en la app */ }
        }
        return Uri.fromFile(f).toString()
    }

    private fun loadLocal() {
        localShots = try {
            val a = JSONArray(store.shotsRaw)
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                Shot(it.optString("id"), it.optString("pc"), it.optInt("w"), it.optInt("h"), it.optString("mime"), it.optLong("bytes"),
                    it.optLong("t"), it.optString("game"), it.optLong("sg"), it.optString("uri"), it.optString("by"))
            }.filter { s -> File(Uri.parse(s.uri).path ?: "").exists() }.sortedByDescending { it.time }
        } catch (e: Exception) { emptyList() }
    }

    private fun persist() {
        store.shotsRaw = JSONArray().apply {
            localShots.forEach {
                put(JSONObject().put("id", it.id).put("pc", it.pc).put("w", it.w).put("h", it.h).put("mime", it.mime).put("bytes", it.bytes)
                    .put("t", it.time).put("game", it.game).put("sg", it.sg).put("uri", it.uri).put("by", it.by))
            }
        }.toString()
    }

    private fun addLocal(s: Shot) {
        localShots = (listOf(s) + localShots.filter { it.id != s.id }).sortedByDescending { it.time }
        persist()
    }

    /** Borra una captura de este móvil (la de la galería se queda). */
    fun deleteLocal(s: Shot) {
        try { File(Uri.parse(s.uri).path ?: "").delete() } catch (e: Exception) { }
        localShots = localShots.filter { it.id != s.id }
        persist()
    }

    fun shotsFor(sg: Long): List<Shot> = if (sg <= 0L) emptyList() else localShots.filter { it.sg == sg }
    fun shotById(id: String): Shot? = localShots.find { it.id == id }

    /**
     * Manda una orden al PC y espera su respuesta. Devuelve (bien, mensaje).
     * op: "shot", "launch" (a.key), "power" (a.action), "focus", "page" (a.key), "toast" (a.text), "ping".
     */
    suspend fun send(op: String, args: JSONObject = JSONObject(), dev: String? = null, timeoutMs: Long = 45_000): Pair<Boolean, String> {
        val r = sendRaw(op, args, dev, timeoutMs)
        return r.optBoolean("ok") to r.optString("msg")
    }

    /** Igual que [send], pero con toda la respuesta del PC. */
    suspend fun sendRaw(op: String, args: JSONObject = JSONObject(), dev: String? = null, timeoutMs: Long = 45_000): JSONObject = withContext(Dispatchers.IO) {
        fun fail(m: String) = JSONObject().put("ok", false).put("msg", m)
        val to = dev ?: target()?.dev ?: return@withContext fail("No hay ningún PC con Atrio conectado.")
        val body = JSONObject().put("op", op).put("a", args).put("t", JSONObject().put(".sv", "timestamp"))
            .put("by", store.myName.ifBlank { store.accUser.ifBlank { "Móvil" } })
        val r = try {
            Account.db(store, "POST", base() + "/suite/cmd/$to", body.toString(), ok404 = false)
        } catch (e: Exception) { return@withContext fail(e.message ?: "sin conexión") }
        val id = try { JSONObject(r ?: "{}").optString("name") } catch (e: Exception) { "" }
        if (id.isBlank()) return@withContext fail("Firebase no ha aceptado la orden.")
        val w = CompletableDeferred<JSONObject>()
        synchronized(waits) { waits[id] = w }
        // Por si ya estaba en el árbol (respuesta rapidísima)
        tree?.let { onSuite(it) }
        val res = withTimeoutOrNull(timeoutMs) { w.await() }
        synchronized(waits) { waits.remove(id) }
        if (res != null) return@withContext res
        // Apagar/reiniciar: el PC la recibió («ack») pero se apagó antes de contestar
        val acked = tree?.optJSONObject("cmd")?.optJSONObject(to)?.optJSONObject(id)?.optBoolean("ack") == true
        if (acked) JSONObject().put("ok", true).put("msg", "${target()?.name ?: "El PC"} ha dejado de contestar: se está apagando")
        else fail("El PC no responde (¿Atrio está abierto y con tu cuenta?).")
    }

    // ---------------------------------------------------------------- Fotos entre dispositivos
    // Una foto de tu galería puesta de portada (o de fondo) solo está en el móvil donde la elegiste. Otro
    // dispositivo tuyo (el PC, la tableta) se la pide: este la sube por trozos, tal cual (el original, sin
    // recomprimir), a /users/<uid>/fotodata/<id>, y el otro la baja y la borra.
    private const val CHUNK = 512 * 1024

    /** Los otros móviles y tabletas con la app abierta ahora mismo. */
    fun phones(): List<LiveDev> = devices.filter { it.online && it.app == "ronron" }

    private suspend fun answer(id: String, c: JSONObject) {
        val res = try {
            when (c.optString("op")) {
                "foto" -> sendPhoto(id, c.optJSONObject("a") ?: JSONObject())
                "ping" -> JSONObject().put("ok", true).put("msg", "${store.myName.ifBlank { Build.MODEL ?: "Móvil" }} conectado")
                else -> JSONObject().put("ok", false).put("msg", "Orden desconocida: actualiza la app del móvil.")
            }
        } catch (e: Exception) { JSONObject().put("ok", false).put("msg", e.message ?: "error") }
        try {
            Account.db(store, "PUT", base() + "/suite/cmd/${store.accDevice}/$id/res", res.put("t", JSONObject().put(".sv", "timestamp")).toString(), "print=silent", ok404 = false)
        } catch (e: Exception) { }
    }

    private fun sendPhoto(id: String, a: JSONObject): JSONObject {
        val name = a.optString("name")
        if (!Regex("^[A-Za-z0-9_.-]{1,80}$").matches(name)) throw java.io.IOException("Nombre de foto no válido")
        val (f, orig) = Photos.best(appCtx, name) ?: return JSONObject().put("ok", false).put("msg", "Esa foto no está en este dispositivo")
        val bytes = f.readBytes()
        val b64 = Base64.getEncoder().encodeToString(bytes)
        val n = (b64.length + CHUNK - 1) / CHUNK
        // Trozo a trozo con PUT (PATCH no lo admiten todas las conexiones HTTP de Java)
        for (j in 0 until n) {
            Account.db(store, "PUT", base() + "/fotodata/$id/$j", JSONObject.quote(b64.substring(j * CHUNK, minOf(b64.length, (j + 1) * CHUNK))), "print=silent", ok404 = false)
        }
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.path, o)
        return JSONObject().put("ok", true).put("data", id).put("n", n).put("bytes", bytes.size).put("mime", Photos.mimeOf(f))
            .put("w", o.outWidth).put("h", o.outHeight).put("orig", orig)
            .put("msg", if (orig) "Foto original (${o.outWidth}×${o.outHeight})" else "Foto (${o.outWidth}×${o.outHeight}): de antes de guardar el original")
    }

    /**
     * Pide a otro dispositivo una foto tuya (por su dirección «file:…») y la guarda en este, con el mismo nombre:
     * la portada que ya tenía la ficha se ve aquí también. Devuelve (bien, mensaje).
     */
    suspend fun fetchPhoto(dev: String, url: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val name = Photos.nameOf(url) ?: return@withContext false to "Esa imagen no es una foto de un dispositivo"
        val r = sendRaw("foto", JSONObject().put("name", name), dev, 120_000)
        if (!r.optBoolean("ok")) return@withContext false to r.optString("msg").ifBlank { "No ha contestado" }
        val data = r.optString("data")
        try {
            val out = java.io.ByteArrayOutputStream(r.optInt("bytes").coerceAtLeast(1024))
            val dec = Base64.getDecoder()
            for (i in 0 until r.optInt("n")) {
                val raw = Account.db(store, "GET", base() + "/fotodata/$data/$i", null) ?: throw java.io.IOException("falta un trozo")
                out.write(dec.decode(JSONTokener(raw).nextValue() as? String ?: throw java.io.IOException("trozo dañado")))
            }
            val bytes = out.toByteArray()
            if (bytes.size != r.optInt("bytes")) throw java.io.IOException("ha llegado incompleta")
            Photos.saveArrived(appCtx, name, bytes, r.optString("mime", "image/jpeg"))
            true to r.optString("msg").ifBlank { "Foto descargada" }
        } catch (e: Exception) {
            false to "No se ha podido descargar: ${e.message}"
        } finally {
            try { Account.db(store, "DELETE", base() + "/fotodata/$data", null) } catch (e: Exception) { }
        }
    }

    /** El juego del PC con esa clave (de la biblioteca de cualquier PC). */
    fun pcGame(key: String): PcGame? = libs.values.firstNotNullOfOrNull { l -> l.find { it.key == key } }

    /**
     * Adjunta una web (guía, build…) a un juego de la biblioteca del PC. Va al buzón del PC: le llega aunque
     * esté apagado ahora mismo y Atrio la guarda en ese juego en cuanto se abra.
     */
    suspend fun addLink(key: String, url: String, name: String, dev: String? = null): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val to = dev ?: libs.entries.firstOrNull { e -> e.value.any { it.key == key } }?.key ?: target()?.dev
            ?: return@withContext false to "No encuentro ese juego en ningún PC."
        val body = JSONObject().put("op", "link").put("key", key).put("u", url).put("n", name)
            .put("t", JSONObject().put(".sv", "timestamp")).put("by", store.myName.ifBlank { store.accUser.ifBlank { "Móvil" } })
        try {
            Account.db(store, "POST", base() + "/suite/inbox/$to", body.toString(), ok404 = false)
            true to (if (devices.any { it.dev == to && it.online }) "Web añadida" else "Web guardada: llegará al PC cuando abras Atrio")
        } catch (e: Exception) { false to (e.message ?: "sin conexión") }
    }

    /** Pide una captura al PC. Devuelve el id de la captura (o null) y el mensaje. */
    suspend fun shoot(dev: String? = null): Pair<String?, String> {
        val r = sendRaw("shot", JSONObject(), dev, 60_000)
        if (!r.optBoolean("ok")) return null to r.optString("msg")
        return r.optString("shot").ifBlank { null } to r.optString("msg")
    }

    /** Espera a que una captura esté ya en el móvil (hasta [ms]). */
    suspend fun awaitLocal(id: String, ms: Long = 120_000): Shot? = withTimeoutOrNull(ms) {
        var s: Shot? = null
        while (s == null) { s = shotById(id); if (s == null) delay(250) }
        s
    }
}
