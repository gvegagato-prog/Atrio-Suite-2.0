package com.gonzalo.gamerate

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/*
 * Tu nube (kDrive de Infomaniak): los libros, las fotos de portada y las capturas, en una carpeta «Atrio» de tu kDrive
 * que también ves y usas tú. El token se guarda solo en este dispositivo (nunca en la cuenta de Atrio: tu pareja puede
 * leer tus datos). Lo que sí viaja con la cuenta es la lista de lo subido ([NubeFile]): así tus otros dispositivos saben
 * qué bajar y tu pareja puede abrirlo con su enlace.
 */

/** Algo tuyo en la nube. [k]: qué es («libro:123», «foto:foto_1.jpg», «shot:abc»); [f]: su id en kDrive; [l]: su enlace. */
data class NubeFile(
    val k: String, val f: Long, val n: String, val s: Long, val l: String = "",
    /** Datos de una captura (juego, id de SteamGridDB, fecha, tamaño): para listarla sin bajarla. */
    val m: JSONObject? = null, val updated: Long = System.currentTimeMillis()
) {
    /** Id estable a partir de la clave (las listas de la cuenta se mezclan por id). */
    val id: Long get() = nubeId(k)
}

fun nubeId(k: String): Long {
    var h = -0x340d631b7bdddcdbL // FNV-1a de 64 bits
    for (b in k.toByteArray()) { h = h xor (b.toLong() and 0xff); h *= 0x100000001b3L }
    // 53 bits: el escritorio (JavaScript) lo lee como número sin perder precisión
    return (h and 0x1FFFFFFFFFFFFFL).coerceAtLeast(1L)
}

fun nubeToJson(x: NubeFile): JSONObject = JSONObject().put("id", x.id).put("k", x.k).put("f", x.f).put("n", x.n).put("s", x.s)
    .put("updated", x.updated).apply { if (x.l.isNotBlank()) put("l", x.l); if (x.m != null) put("m", x.m) }

fun nubeFromJson(o: JSONObject): NubeFile? {
    val k = o.optString("k").ifBlank { return null }
    return NubeFile(k, o.optLong("f"), o.optString("n"), o.optLong("s"), o.optString("l", ""), o.optJSONObject("m"), o.optLong("updated"))
}

/** La API de kDrive (la misma que usa el servidor MCP oficial de Infomaniak). */
object KDrive {
    /** Se cambia en las pruebas por un kDrive simulado. */
    var api = "https://api.infomaniak.com"

    class Fail(msg: String) : IOException(msg)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nube", Context.MODE_PRIVATE)
    fun token(ctx: Context) = prefs(ctx).getString("token", "").orEmpty()
    fun drive(ctx: Context) = prefs(ctx).getString("drive", "").orEmpty()
    fun on(ctx: Context) = token(ctx).isNotBlank() && drive(ctx).isNotBlank()
    /** Subir también las capturas que lleguen del PC (las sube el PC; esto vale si no lo hace). */
    fun shots(ctx: Context) = prefs(ctx).getBoolean("shots", true)
    fun setShots(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("shots", v).apply()
    var lastMsg: String = ""

    fun save(ctx: Context, token: String, drive: String) {
        // El id del kDrive puede venir con la dirección entera («…/app/drive/123456/…»)
        val d = Regex("""drive/(\d+)""").find(drive)?.groupValues?.get(1) ?: drive.filter { it.isDigit() }
        prefs(ctx).edit().putString("token", token.trim()).putString("drive", d).remove("dirs").remove("home").apply()
    }

    fun forget(ctx: Context) = prefs(ctx).edit().clear().apply()

    private fun call(ctx: Context, method: String, path: String, body: String? = null): JSONObject {
        val c = URL(api + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20000; c.readTimeout = 60000
        c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        return answer(c)
    }

    /** {"result":"success","data":…} o {"result":"error","error":{"description":…}} */
    private fun answer(c: HttpURLConnection): JSONObject {
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val o = runCatching { JSONObject(txt) }.getOrNull()
        if (code !in 200..299 || o?.optString("result") == "error") {
            val why = o?.optJSONObject("error")?.let { it.optString("description").ifBlank { it.optString("code") } }
            throw Fail(when (code) {
                401 -> "kDrive no acepta el token (revísalo o crea otro)"
                403 -> "El token no tiene permiso para este kDrive"
                404 -> "No encuentro ese kDrive (revisa su número)"
                else -> "kDrive: ${why ?: "error $code"}"
            })
        }
        return o ?: JSONObject()
    }

    /** Comprueba el token y el kDrive: busca tu carpeta privada («Mi carpeta»), que es donde va «Atrio». */
    suspend fun test(ctx: Context): String = withContext(Dispatchers.IO) {
        prefs(ctx).edit().remove("home").apply()
        home(ctx)
        "Conectado: Atrio guardará todo en «Mi carpeta/Atrio» de tu kDrive"
    }

    private class Item(val name: String, val id: Long, val dir: Boolean, val visibility: String)

    /** Lo que hay dentro de una carpeta. */
    private fun children(ctx: Context, dir: Long): List<Item> {
        val out = mutableListOf<Item>()
        var cursor = ""
        do {
            val q = if (cursor.isNotEmpty()) "?cursor=${URLEncoder.encode(cursor, "UTF-8")}" else ""
            val o = call(ctx, "GET", "/3/drive/${drive(ctx)}/files/$dir/files$q")
            val a = o.optJSONArray("data") ?: JSONArray()
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { out += Item(it.optString("name"), it.optLong("id"), it.optString("type") == "dir", it.optString("visibility")) }
            cursor = if (o.optBoolean("has_more")) o.optString("cursor") else ""
        } while (cursor.isNotEmpty() && out.size < 5000)
        return out
    }

    /**
     * Tu carpeta privada («Mi carpeta»): en la raíz del kDrive no se puede escribir. La app oficial de kDrive la
     * reconoce por su visibilidad «is_private_space»; es la única carpeta así.
     */
    fun home(ctx: Context): Long {
        prefs(ctx).getLong("home", 0L).takeIf { it > 0 }?.let { return it }
        val root = children(ctx, 1)
        val h = root.firstOrNull { it.dir && it.visibility == "is_private_space" }
            ?: throw Fail("No encuentro tu carpeta privada en el kDrive (hay: ${root.joinToString { it.name }.ifBlank { "nada" }})")
        prefs(ctx).edit().putLong("home", h.id).apply()
        return h.id
    }

    /** Archivos de más de 5 MB: a trozos (como la app oficial). */
    private const val CHUNKED = 5L * 1024 * 1024
    private const val PARALLEL = 4

    /**
     * Sube un archivo a «Mi carpeta/[path]/[name]» (kDrive crea las carpetas que falten). Si ya hay uno con ese
     * nombre, queda como versión nueva del mismo (nada de «libro (1).epub»). Devuelve su id.
     */
    fun upload(ctx: Context, path: String, name: String, size: Long, onProgress: (Long) -> Unit = {}, input: () -> InputStream): Long {
        val d = drive(ctx)
        val home = home(ctx)
        val enc = { v: String -> URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
        if (size <= CHUNKED) {
            val q = "directory_id=$home&directory_path=${enc(path)}&file_name=${enc(name)}&total_size=$size&conflict=version"
            val c = URL("$api/3/drive/$d/upload?$q").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 20000; c.readTimeout = 300000
            c.doOutput = true
            c.setFixedLengthStreamingMode(size)
            c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.outputStream.use { out -> input().use { it.copyTo(out) } }
            return answer(c).optJSONObject("data")?.optLong("id")?.takeIf { it > 0 }.also { onProgress(size) } ?: throw Fail("kDrive no ha devuelto el archivo subido")
        }
        // Trozos de al menos 1 MB, unos 200 por archivo (los mismos números que la app oficial)
        val chunk = maxOf(1L * 1024 * 1024, (size + 199) / 200)
        val total = ((size + chunk - 1) / chunk).toInt()
        val start = call(ctx, "POST", "/3/drive/$d/upload/session/start", JSONObject()
            .put("conflict", "version").put("directory_id", home).put("directory_path", path).put("file_name", name)
            .put("last_modified_at", System.currentTimeMillis() / 1000).put("total_chunks", total).put("total_size", size).toString())
            .optJSONObject("data") ?: throw Fail("kDrive no ha abierto la subida")
        val tok = start.optString("token").ifBlank { throw Fail("kDrive no ha abierto la subida") }
        val host = start.optString("upload_url").ifBlank { api }.trimEnd('/')
        // Cuatro trozos a la vez (como la app oficial): se aprovecha mucho mejor la subida que de uno en uno
        val pool = java.util.concurrent.Executors.newFixedThreadPool(PARALLEL)
        val slots = java.util.concurrent.Semaphore(PARALLEL)
        val sent = java.util.concurrent.atomic.AtomicLong(0)
        val failed = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        try {
            input().use { inp ->
                for (n in 1..total) {
                    val len = if (n == total) (size - chunk * (total - 1)).toInt() else chunk.toInt()
                    slots.acquire()
                    failed.get()?.let { throw it }
                    val buf = ByteArray(len)
                    var r = 0
                    while (r < len) { val k = inp.read(buf, r, len - r); if (k < 0) throw Fail("El archivo se ha acabado antes de tiempo"); r += k }
                    pool.execute {
                        try {
                            var tries = 0
                            while (true) {
                                try {
                                    val c = URL("$host/3/drive/$d/upload/session/$tok/chunk?chunk_number=$n&chunk_size=$len").openConnection() as HttpURLConnection
                                    c.requestMethod = "POST"
                                    c.connectTimeout = 20000; c.readTimeout = 300000
                                    c.doOutput = true
                                    c.setFixedLengthStreamingMode(len)
                                    c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
                                    c.setRequestProperty("Content-Type", "application/octet-stream")
                                    c.outputStream.use { it.write(buf) }
                                    answer(c)
                                    break
                                } catch (e: IOException) {
                                    // Un corte de red en un trozo: se reintenta ese trozo (no todo el archivo)
                                    if (e is Fail || ++tries >= 3) throw e
                                    Thread.sleep(1500L * tries)
                                }
                            }
                            onProgress(sent.addAndGet(len.toLong()))
                        } catch (e: Throwable) { failed.compareAndSet(null, e) } finally { slots.release() }
                    }
                }
            }
            slots.acquire(PARALLEL) // espera a que acaben todos
            failed.get()?.let { throw it }
        } finally { pool.shutdownNow() }
        val fin = call(ctx, "POST", "/3/drive/$d/upload/session/$tok/finish").optJSONObject("data")
        return fin?.optJSONObject("file")?.optLong("id")?.takeIf { it > 0 } ?: throw Fail("kDrive no ha cerrado la subida")
    }

    /** La web de los enlaces públicos (se cambia en las pruebas). */
    var share = "https://kdrive.infomaniak.com"

    /**
     * Baja un archivo desde su enlace público, sin cuenta (lo usa la app de tu pareja). El enlace es
     * «…/app/share/<kDrive>/<uuid>»; la descarga, la misma que usa la app oficial de kDrive para los enlaces.
     */
    fun downloadShared(link: String, file: Long, dest: File) {
        val m = Regex("""/app/share/(\d+)/([a-z0-9-]+)""").find(link) ?: throw Fail("Ese enlace no es de kDrive")
        val c = URL("$share/2/app/${m.groupValues[1]}/share/${m.groupValues[2]}/files/$file/download").openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 300000
        if (c.responseCode !in 200..299) throw Fail("El enlace ya no funciona (error ${c.responseCode})")
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, true); tmp.delete() }
    }

    /** Un enlace para abrirlo sin entrar en tu cuenta (para tu pareja). Si falla, sin enlace. */
    fun link(ctx: Context, file: Long): String = runCatching {
        val d = call(ctx, "POST", "/2/drive/${drive(ctx)}/files/$file/link", JSONObject().put("right", "public").put("can_download", true).toString()).opt("data")
        (d as? JSONObject)?.optString("url").orEmpty().ifBlank { (d as? String).orEmpty() }
    }.getOrDefault("")

    /** Baja un archivo a [dest] (primero a un temporal: un corte no deja un archivo a medias). */
    fun download(ctx: Context, file: Long, dest: File) {
        val c = URL("$api/2/drive/${drive(ctx)}/files/$file/download").openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 300000
        c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
        if (c.responseCode !in 200..299) { answer(c); throw Fail("kDrive: error ${c.responseCode}") }
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, true); tmp.delete() }
    }

    fun delete(ctx: Context, file: Long) { call(ctx, "DELETE", "/2/drive/${drive(ctx)}/files/$file") }
}

/** Qué subir y qué bajar: lo hace en segundo plano ([NubeWorker]) y a mano desde Ajustes. */
object NubeSync {
    var running by mutableStateOf(false)
    var status by mutableStateOf("")

    fun enqueue(ctx: Context) {
        if (!KDrive.on(ctx)) return
        val req = OneTimeWorkRequestBuilder<NubeWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        // KEEP: si ya está subiendo, que siga (antes se cortaba al abrir o cerrar la app y volvía a empezar)
        WorkManager.getInstance(ctx).enqueueUniqueWork("kdrive", ExistingWorkPolicy.KEEP, req)
    }

    /** Un nombre que vale para una carpeta o un archivo en cualquier sistema. */
    fun safe(s: String) = s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]+"""), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(80).trim().ifBlank { "Sin nombre" }

    /**
     * Dónde va cada cosa en tu kDrive, todo bajo «Mi carpeta/Atrio»:
     * Atrio/<Categoría>/<Ficha>/Libro · Capturas · Portadas, y Atrio/<Categoría>/Sagas/<Saga>/Portadas.
     */
    fun folder(kind: Kind, name: String, what: String) = "Atrio/${safe(kind.title)}/${safe(cleanTitle(name))}/$what"

    /** Las fotos tuyas que usan tus fichas y tus sagas (portadas y fondos), con la carpeta de su ficha. */
    private fun photosInUse(ctx: Context, store: Store): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val kinds = listOf(GAMES, BOOKS) + store.kinds
        val names = HashMap<Pair<Int, Long>, String>()
        for (k in kinds) {
            // Portada, fondo y logo de cada ficha
            val items = store.load(k.id).map { listOf(it.name, it.cover, it.hero, it.logo) to it.id } + store.loadPlays(k.id).map { listOf(it.name, it.cover, it.hero, it.logo) to it.id }
            for ((v, id) in items) {
                names[k.id to id] = v[0]
                for (u in v.drop(1)) Photos.nameOf(u)?.let { out.putIfAbsent(it, folder(k, v[0], "Portadas")) }
            }
        }
        // Las fotos que pusiste en las casillas de la revista de una ficha (se guardan en este dispositivo)
        for ((key, raw) in ctx.getSharedPreferences("maqueta", Context.MODE_PRIVATE).all) {
            val m = Regex("""^[pv]:(\d+):(\d+)$""").find(key) ?: continue
            val k = kinds.find { it.id == m.groupValues[1].toInt() } ?: continue
            val name = names[k.id to m.groupValues[2].toLong()] ?: continue
            Regex(""""i"\s*:\s*"(file:[^"]+)"""").findAll(raw as? String ?: continue).forEach { r ->
                Photos.nameOf(r.groupValues[1].replace("\\/", "/"))?.let { out.putIfAbsent(it, folder(k, name, "Imágenes")) }
            }
        }
        store.loadSagas().forEach { sg ->
            val k = kinds.find { it.id == sg.kind } ?: BOOKS
            Photos.nameOf(sg.cover)?.let { out.putIfAbsent(it, "Atrio/${safe(k.title)}/Sagas/${safe(sg.name)}/Portadas") }
        }
        return out
    }

    /** El nombre de la ficha de un juego (por su id de SteamGridDB), para que sus capturas vayan a su carpeta. */
    fun gameName(store: Store, sg: Long, fallback: String): String =
        (store.loadPlays(0).find { it.id == sg }?.name ?: store.load(0).find { it.id == sg }?.name)?.takeIf { sg > 0 } ?: fallback.ifBlank { "Escritorio" }

    /** Los libros con su archivo en este móvil: (id, dirección). */
    private fun localBooks(ctx: Context): List<Pair<Long, String>> =
        ctx.getSharedPreferences("lector", Context.MODE_PRIVATE).all.keys.mapNotNull { it.toLongOrNull() }
            .mapNotNull { id -> BookFiles.get(ctx, id)?.let { id to it } }

    private fun sizeOf(ctx: Context, uri: Uri): Long {
        if (uri.scheme == "file") return File(uri.path ?: "").length()
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0) }
        return ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    }

    /** Sube lo que falte y baja lo que falte aquí. Devuelve un resumen. */
    suspend fun run(ctx: Context, foreground: (suspend (String, Int) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        if (!KDrive.on(ctx)) return@withContext "Sin nube"
        val store = Store(ctx)
        running = true
        var up = 0; var down = 0; val errs = mutableListOf<String>()
        try {
            val have = store.loadNube().associateBy { it.k }
            // El progreso de cada subida, en Ajustes y en el aviso (cada 1 %)
            fun progress(what: String, size: Long): (Long) -> Unit {
                var last = -1
                return { done ->
                    val pct = if (size > 0) (done * 100 / size).toInt() else 100
                    if (pct != last) {
                        last = pct
                        status = "Subiendo $what… $pct %"
                        if (foreground != null) kotlinx.coroutines.runBlocking { runCatching { foreground(status, pct) } }
                    }
                }
            }
            val added = mutableListOf<NubeFile>()
            // Libros: el archivo que tengas adjunto, con su enlace para tu pareja
            val titles = (store.load(1).map { it.id to it.name } + store.loadPlays(1).map { it.id to it.name }).toMap()
            for ((id, u) in localBooks(ctx)) {
                if (have.containsKey("libro:$id")) continue
                try {
                    val uri = Uri.parse(u)
                    val size = sizeOf(ctx, uri)
                    if (size <= 0) continue
                    val file = BookFiles.name(ctx, id).ifBlank { "libro.epub" }
                    val ext = file.substringAfterLast('.', "epub").take(5)
                    val title = titles[id] ?: file.substringBeforeLast('.')
                    val name = "${safe(cleanTitle(title))}.$ext"
                    status = "Subiendo $name…"
                    foreground?.let { runCatching { it(status, 0) } }
                    val f = KDrive.upload(ctx, folder(BOOKS, title, "Libro"), name, size, progress(name, size)) { ctx.contentResolver.openInputStream(uri) ?: throw IOException("no se puede leer") }
                    added += NubeFile("libro:$id", f, name, size, KDrive.link(ctx, f))
                    up++
                } catch (e: Exception) { errs += "${titles[id] ?: "un libro"}: ${e.message}" }
            }
            // Fotos de portada: el original si está aquí
            for ((n, where) in photosInUse(ctx, store)) {
                if (have.containsKey("foto:$n")) continue
                val (f0, _) = Photos.best(ctx, n) ?: continue
                try {
                    status = "Subiendo la foto ${f0.name}…"
                    val f = KDrive.upload(ctx, where, f0.name, f0.length(), progress(f0.name, f0.length())) { f0.inputStream() }
                    added += NubeFile("foto:$n", f, f0.name, f0.length(), KDrive.link(ctx, f))
                    up++
                } catch (e: Exception) { errs += "${f0.name}: ${e.message}" }
            }
            // Capturas que tengas aquí y no estén (normalmente las sube el PC)
            if (KDrive.shots(ctx)) for (s in Suite.localShots) {
                if (have.containsKey("shot:${s.id}") || store.shotNoCloud(s.id)) continue
                val f0 = File(Uri.parse(s.uri).path ?: continue)
                if (!f0.exists()) continue
                try {
                    val g = gameName(store, s.sg, s.game)
                    val name = "${safe(cleanTitle(g))} ${java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.ROOT).format(java.util.Date(s.time))}.${f0.extension}"
                    status = "Subiendo la captura $name…"
                    val f = KDrive.upload(ctx, folder(GAMES, g, "Capturas"), name, f0.length(), progress(name, f0.length())) { f0.inputStream() }
                    added += NubeFile("shot:${s.id}", f, name, f0.length(), KDrive.link(ctx, f), shotMeta(s))
                    up++
                } catch (e: Exception) { errs += "${s.game}: ${e.message}" }
            }
            if (added.isNotEmpty()) store.addNube(added)
            // Bajar las fotos de portada que falten aquí (las elegiste en otro dispositivo)
            for (n in photosInUse(ctx, store).keys) {
                if (Photos.best(ctx, n) != null) continue
                val x = have["foto:$n"] ?: continue
                try {
                    status = "Bajando la foto ${x.n}…"
                    val tmp = File(ctx.cacheDir, "nube_${x.f}")
                    KDrive.download(ctx, x.f, tmp)
                    Photos.saveArrived(ctx, n, tmp.readBytes(), Photos.mimeOf(File(x.n)))
                    tmp.delete()
                    down++
                } catch (e: Exception) { errs += "${x.n}: ${e.message}" }
            }
        } catch (e: Exception) {
            errs += e.message ?: "error"
        } finally { running = false }
        val msg = listOfNotNull(
            if (up > 0) "$up subido${if (up == 1) "" else "s"}" else null,
            if (down > 0) "$down bajado${if (down == 1) "" else "s"}" else null,
            errs.firstOrNull()?.let { "falló: $it" + if (errs.size > 1) " (y ${errs.size - 1} más)" else "" }
        ).joinToString(" · ").ifBlank { "Todo al día" }
        status = msg
        KDrive.lastMsg = msg
        if (up > 0) runCatching { if (store.paired) Couple.push(store) }
        msg
    }

    fun shotMeta(s: Shot): JSONObject = JSONObject().put("game", s.game).put("sg", s.sg).put("t", s.time).put("w", s.w).put("h", s.h)
        .put("mime", s.mime).put("pc", s.pc).put("by", s.by)

    /** Baja un libro de la nube y lo deja adjunto. */
    suspend fun fetchBook(ctx: Context, id: Long): Boolean = withContext(Dispatchers.IO) {
        val x = Store(ctx).loadNube().find { it.k == "libro:$id" } ?: return@withContext false
        val dest = File(File(ctx.filesDir, "libros"), "$id.${x.n.substringAfterLast('.', "epub")}")
        KDrive.download(ctx, x.f, dest)
        BookFiles.attachFile(ctx, id, dest, x.n)
        true
    }

    /** Un libro de tu pareja (desde su enlace) a este móvil, adjunto a tu ficha de ese libro. */
    suspend fun copyFromPartner(ctx: Context, x: NubeFile, id: Long) = withContext(Dispatchers.IO) {
        val dest = File(File(ctx.filesDir, "libros"), "$id.${x.n.substringAfterLast('.', "epub")}")
        KDrive.downloadShared(x.l, x.f, dest)
        BookFiles.attachFile(ctx, id, dest, x.n)
    }

    /** Las capturas que están en tu nube y no en este móvil. */
    fun missingShots(store: Store): List<NubeFile> =
        store.loadNube().filter { it.k.startsWith("shot:") && Suite.shotById(it.k.removePrefix("shot:")) == null }

    /** Baja de la nube las capturas que falten en este móvil. */
    suspend fun fetchShots(ctx: Context, list: List<NubeFile>): Int = withContext(Dispatchers.IO) {
        var n = 0
        for (x in list) {
            try {
                status = "Bajando ${x.n}…"
                val tmp = File(ctx.cacheDir, "nube_${x.f}")
                KDrive.download(ctx, x.f, tmp)
                Suite.addFromCloud(x, tmp.readBytes())
                tmp.delete()
                n++
            } catch (e: Exception) { status = "No se pudo bajar ${x.n}: ${e.message}" }
        }
        n
    }

    /** Quita una captura de la nube (y no se vuelve a subir). */
    suspend fun removeShot(ctx: Context, shotId: String) = withContext(Dispatchers.IO) {
        val store = Store(ctx)
        store.setShotNoCloud(shotId)
        store.loadNube().find { it.k == "shot:$shotId" }?.let { x ->
            runCatching { KDrive.delete(ctx, x.f) }
            store.removeNube(x.k)
        }
    }
}

/** En la app de tu pareja: su nube (la pone la app al ver la suya). */
object NubeHub {
    var partner by mutableStateOf<List<NubeFile>>(emptyList())
    /** Copiar a tu Atrio un libro de tu pareja (lo pone la app). */
    var copyBook: ((Long) -> Unit)? = null
    var copying by mutableStateOf(0L)
}

/** En una ficha de tu pareja: su libro, para abrirlo (y bajarlo) desde su enlace. */
@Composable
fun PartnerFileLink(itemId: Long, kind: Kind, always: Boolean = false) {
    if ((SagaHub.on && !always) || !kind.isBook) return
    val x = NubeHub.partner.find { it.k == "libro:$itemId" && it.l.isNotBlank() } ?: return
    val ctx = LocalContext.current
    val copy = NubeHub.copyBook
    if (copy != null) MagLink(if (NubeHub.copying == itemId) "Copiando…" else "Copiar a mi Atrio", "${x.n} · con su archivo, para leerlo aquí", accent = true) {
        if (NubeHub.copying == 0L) copy(itemId)
    }
    MagLink("Abrir en su kDrive", "En el navegador") {
        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(x.l))) }
    }
}

class NubeWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    /**
     * Mientras sube, en primer plano con su aviso: así Android le deja pasar de los 10 minutos de un trabajo normal
     * (un libro de 700 MB). Si Android no lo deja (la app ya estaba de fondo), sigue como trabajo normal.
     */
    private suspend fun aviso(text: String, pct: Int) {
        val nm = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("nube") == null)
            nm.createNotificationChannel(android.app.NotificationChannel("nube", "Subidas a tu nube", android.app.NotificationManager.IMPORTANCE_LOW))
        val n = androidx.core.app.NotificationCompat.Builder(applicationContext, "nube")
            .setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Subiendo a tu nube").setContentText(text)
            .setProgress(100, pct, false).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).build()
        val info = if (android.os.Build.VERSION.SDK_INT >= 29) androidx.work.ForegroundInfo(4711, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else androidx.work.ForegroundInfo(4711, n)
        setForeground(info)
    }

    override suspend fun doWork(): Result {
        var fg = true
        val msg = NubeSync.run(applicationContext) { t, p -> if (fg) try { aviso(t, p) } catch (e: Exception) { fg = false } }
        return if (msg.startsWith("falló") && runAttemptCount < 3) Result.retry() else Result.success()
    }
}

/** Ajustes → Tu nube. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NubeSettings(say: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(KDrive.on(ctx)) }
    var token by remember { mutableStateOf("") }
    var drive by remember { mutableStateOf(KDrive.drive(ctx)) }
    var shots by remember { mutableStateOf(KDrive.shots(ctx)) }
    if (!on) {
        Text(
            "Tus libros, las fotos que pones de portada y las capturas, en una carpeta «Atrio» de tu kDrive (Infomaniak), " +
                "para tenerlos en todos tus dispositivos y que tu pareja pueda abrirlos.",
            color = UiSoft, fontSize = 13.sp
        )
        Plegado(
            "En el Manager de Infomaniak: Desarrollador → Tokens de API → Crear un token, con la aplicación «kDrive». " +
                "El número de tu kDrive es el que sale en la dirección al abrirlo en el navegador, después de «/drive/». " +
                "El token se guarda solo en este dispositivo: tu pareja no lo ve."
        )
        OutlinedTextField(token, { token = it }, label = { Text("Token de API") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(drive, { drive = it }, label = { Text("Número de tu kDrive (o su dirección)") }, singleLine = true, shape = shp(14.dp), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button({
            KDrive.save(ctx, token, drive)
            scope.launch {
                try { say(KDrive.test(ctx)); on = true; NubeSync.enqueue(ctx) }
                catch (e: Exception) { KDrive.forget(ctx); say(e.message ?: "No se pudo conectar") }
            }
        }, enabled = token.isNotBlank() && drive.isNotBlank(), shape = pillShape()) { Text("Conectar") }
    } else {
        Text("Conectada a tu kDrive ${KDrive.drive(ctx)}", fontWeight = FontWeight.SemiBold)
        val st = NubeSync.status.ifBlank { KDrive.lastMsg }
        if (st.isNotBlank()) Text(st, color = UiSoft, fontSize = 13.sp)
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Subir las capturas", modifier = Modifier.weight(1f))
            Switch(shots, { shots = it; KDrive.setShots(ctx, it) })
        }
        Plegado("Todas, salvo las que quites de la nube al verlas. Normalmente las sube el PC al hacerlas; desde aquí se suben las que falten.")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // En segundo plano (con su aviso): no se corta aunque salgas de aquí o cierres la app
            Button({ NubeSync.enqueue(ctx); say("Sincronizando con tu nube en segundo plano") }, enabled = !NubeSync.running, shape = pillShape()) { Text(if (NubeSync.running) "Sincronizando…" else "Sincronizar ahora") }
            OutlinedButton({ KDrive.forget(ctx); on = false; token = ""; say("Nube desconectada (lo subido sigue en tu kDrive)") }, shape = pillShape()) { Text("Desconectar") }
        }
    }
}
