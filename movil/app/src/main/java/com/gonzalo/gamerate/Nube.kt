package com.gonzalo.gamerate

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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

    /** Una carpeta o un archivo de tu kDrive, para el explorador ([path]: dónde está, en las búsquedas). */
    class Entry(val id: Long, val name: String, val dir: Boolean, val image: Boolean, val path: String = "")

    private val IMG = Regex("""(?i)\.(jpe?g|png|webp|gif|heic|heif|avif|bmp)$""")

    /** Lo que hay en una carpeta de tu kDrive: primero las carpetas y luego las imágenes (lo demás no sirve aquí). */
    fun list(ctx: Context, dir: Long): List<Entry> {
        val out = mutableListOf<Entry>()
        var cursor = ""
        do {
            val q = if (cursor.isNotEmpty()) "?cursor=${URLEncoder.encode(cursor, "UTF-8")}" else ""
            val o = call(ctx, "GET", "/3/drive/${drive(ctx)}/files/$dir/files$q")
            val a = o.optJSONArray("data") ?: JSONArray()
            for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                val dirx = it.optString("type") == "dir"
                val img = !dirx && (it.optString("extension_type") == "image" || IMG.containsMatchIn(it.optString("name")))
                if (dirx || img) out += Entry(it.optLong("id"), it.optString("name"), dirx, img)
            }
            cursor = if (o.optBoolean("has_more")) o.optString("cursor") else ""
        } while (cursor.isNotEmpty() && out.size < 5000)
        return out.sortedWith(compareByDescending<Entry> { it.dir }.thenBy { it.name.lowercase() })
    }

    /** Dónde empieza el explorador: «Mi carpeta/Atrio» si ya existe; si no, «Mi carpeta». */
    fun start(ctx: Context): Long {
        val h = home(ctx)
        return list(ctx, h).firstOrNull { it.dir && it.name == "Atrio" }?.id ?: h
    }

    /**
     * Busca por nombre en todo tu kDrive (como la app oficial: /3/drive/<id>/files/search). [tipo]: "image" o "dir".
     * Devuelve como mucho [max], con su carpeta (la ruta empieza por «Mi carpeta» o por el espacio común).
     */
    fun busca(ctx: Context, texto: String, tipo: String = "image", max: Int = 300): List<Entry> {
        val q = texto.trim()
        if (q.isEmpty()) return emptyList()
        val enc = { v: String -> URLEncoder.encode(v, "UTF-8").replace("+", "%20") }
        val out = mutableListOf<Entry>()
        var cursor = ""
        do {
            val c = if (cursor.isNotEmpty()) "&cursor=${enc(cursor)}" else ""
            val o = call(ctx, "GET", "/3/drive/${drive(ctx)}/files/search?query=${enc(q)}&types[]=$tipo&with=path$c")
            val a = o.optJSONArray("data") ?: JSONArray()
            for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                val dirx = it.optString("type") == "dir"
                val img = !dirx && (it.optString("extension_type") == "image" || IMG.containsMatchIn(it.optString("name")))
                if (dirx || img) out += Entry(it.optLong("id"), it.optString("name"), dirx, img, rutaLegible(it.optString("path")))
            }
            cursor = if (o.optBoolean("has_more")) o.optString("cursor") else ""
        } while (cursor.isNotEmpty() && out.size < max)
        return out.take(max)
    }

    /** «/Private/Atrio/Juegos/Hades/Portadas/x.jpg» → «Mi carpeta/Atrio/Juegos/Hades/Portadas» (la carpeta). */
    fun rutaLegible(p: String): String = p.trim('/').split('/').dropLast(1).let { ps ->
        if (ps.firstOrNull() == "Private") (listOf("Mi carpeta") + ps.drop(1)).joinToString("/") else ps.joinToString("/")
    }

    /** La carpeta de una ficha («Mi carpeta/Atrio/<Categoría>/<Ficha>») si ya existe en tu nube, o null. */
    fun carpetaDeFicha(ctx: Context, nombre: String): Pair<Long, String>? {
        val n = NubeSync.safe(cleanTitle(nombre))
        if (nombre.isBlank()) return null
        return runCatching { busca(ctx, n, tipo = "dir", max = 60) }.getOrDefault(emptyList())
            .firstOrNull { it.dir && it.name == n && it.path.startsWith("Mi carpeta/Atrio/") }?.let { it.id to "${it.path}/${it.name}" }
    }

    /** La miniatura de una imagen (la misma dirección que usa la app oficial). */
    fun thumb(ctx: Context, file: Long) = "$api/2/drive/${drive(ctx)}/files/$file/thumbnail"
    fun auth(ctx: Context) = "Bearer ${token(ctx)}"

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

    /** Los archivos de «Mi carpeta/versiones» (nombre, id): ahí se sube cada versión de la app para que se actualice sola. */
    fun versiones(ctx: Context): List<Pair<String, Long>> {
        // El id de la carpeta se recuerda: así cada comprobación es una sola consulta (y si ya no vale, se busca de nuevo)
        prefs(ctx).getLong("versDir", 0L).takeIf { it > 0 }?.let { id ->
            runCatching { return children(ctx, id).filter { !it.dir }.map { it.name to it.id } }
            prefs(ctx).edit().remove("versDir").apply()
        }
        val dir = children(ctx, home(ctx)).firstOrNull { it.dir && it.name == "versiones" } ?: return emptyList()
        prefs(ctx).edit().putLong("versDir", dir.id).apply()
        return children(ctx, dir.id).filter { !it.dir }.map { it.name to it.id }
    }

    /**
     * Baja un archivo a [dest] (primero a un temporal: un corte no deja un archivo a medias). Los grandes se bajan en
     * cuatro trozos a la vez (kDrive acepta rangos) y es mucho más rápido; si no, de una vez.
     */
    fun download(ctx: Context, file: Long, dest: File) {
        if (runCatching { descargaEnTrozos(ctx, file, dest) }.getOrDefault(false)) return
        descargaSimple(ctx, file, dest)
    }

    private const val TROZOS = 4
    private const val MINIMO_EN_TROZOS = 4L * 1024 * 1024

    /** Devuelve false si no vale la pena (pequeño) o el servidor no entiende rangos: entonces se baja de una vez. */
    private fun descargaEnTrozos(ctx: Context, file: Long, dest: File): Boolean {
        val url = "$api/2/drive/${drive(ctx)}/files/$file/download"
        fun abre(desde: Long, hasta: Long): HttpURLConnection {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 20000; c.readTimeout = 300000
            c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
            c.setRequestProperty("Range", "bytes=$desde-$hasta")
            return c
        }
        // Primero un pedacito: dice cuánto pesa y si entiende rangos
        val sonda = abre(0, 0)
        if (sonda.responseCode != 206) { sonda.disconnect(); return false }
        val total = sonda.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: run { sonda.disconnect(); return false }
        sonda.inputStream.use { it.readBytes() }
        if (total < MINIMO_EN_TROZOS) return false
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        java.io.RandomAccessFile(tmp, "rw").use { it.setLength(total) }
        val trozo = (total + TROZOS - 1) / TROZOS
        val fallo = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val hilos = (0 until TROZOS).map { i ->
            Thread {
                try {
                    val a = i * trozo
                    val b = minOf(total - 1, a + trozo - 1)
                    if (a > b) return@Thread
                    var intentos = 0
                    while (true) {
                        try {
                            val c = abre(a, b)
                            if (c.responseCode != 206) throw Fail("kDrive: error ${c.responseCode}")
                            c.inputStream.use { inp ->
                                java.io.RandomAccessFile(tmp, "rw").use { f ->
                                    f.seek(a)
                                    val buf = ByteArray(64 * 1024); var pos = a
                                    while (true) { val n = inp.read(buf); if (n < 0) break; f.write(buf, 0, n); pos += n }
                                    if (pos != b + 1) throw IOException("trozo incompleto")
                                }
                            }
                            break
                        } catch (e: IOException) {
                            if (e is Fail || ++intentos >= 3) throw e
                            Thread.sleep(1000L * intentos)
                        }
                    }
                } catch (e: Throwable) { fallo.compareAndSet(null, e) }
            }.also { it.start() }
        }
        hilos.forEach { it.join() }
        fallo.get()?.let { tmp.delete(); throw it }
        if (tmp.length() != total) { tmp.delete(); throw Fail("kDrive: la descarga ha quedado incompleta") }
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, true); tmp.delete() }
        return true
    }

    private fun descargaSimple(ctx: Context, file: Long, dest: File) {
        val c = URL("$api/2/drive/${drive(ctx)}/files/$file/download").openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 300000
        c.setRequestProperty("Authorization", "Bearer ${token(ctx)}")
        if (c.responseCode !in 200..299) { answer(c); throw Fail("kDrive: error ${c.responseCode}") }
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, true); tmp.delete() }
    }

    /**
     * ¿Sigue ese archivo en tu kDrive, en su carpeta? (false si lo borraste, lo mandaste a la papelera o ya no está;
     * null si no se puede saber —sin red, otro error— y entonces se da por bueno.) No se fía de cómo marque kDrive lo
     * borrado: pregunta por el archivo, y mira si su carpeta lo lista.
     */
    fun exists(ctx: Context, file: Long): Boolean? = try {
        val d = call(ctx, "GET", "/3/drive/${drive(ctx)}/files/$file").optJSONObject("data")
        val parent = d?.optLong("parent_id", 0L) ?: 0L
        if (d == null) null else if (parent <= 0L) true else children(ctx, parent).any { it.id == file }
    } catch (e: Fail) {
        if (e.message?.startsWith("No encuentro") == true || e.message?.contains("not_found", true) == true) false else null
    } catch (e: Exception) { null }

    /** ¿Responde tu kDrive con este token? (lista su raíz; falla si el número o el token no valen). */
    fun reach(ctx: Context) { children(ctx, 1) }

    fun delete(ctx: Context, file: Long) { call(ctx, "DELETE", "/2/drive/${drive(ctx)}/files/$file") }
}

/** Qué subir y qué bajar: lo hace en segundo plano ([NubeWorker]) y a mano desde Ajustes. */
object NubeSync {
    var running by mutableStateOf(false)
    var status by mutableStateOf("")

    fun enqueue(ctx: Context, verify: Boolean = false) {
        // Sin kDrive aquí, solo si hay algo tuyo que bajar por su enlace (tus portadas)
        if (!KDrive.on(ctx) && Store(ctx).loadNube().none { it.k.startsWith("foto:") && it.l.isNotBlank() }) return
        val req = OneTimeWorkRequestBuilder<NubeWorker>()
            .setInputData(androidx.work.workDataOf("verify" to verify))
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
    /** Tus fotos (portadas, fondos, imágenes de casillas) que usan tus fichas y faltan aquí: se bajan. Cuántas. */
    suspend fun bajarFotos(ctx: Context, store: Store = Store(ctx), errs: MutableList<String> = mutableListOf()): Int = withContext(Dispatchers.IO) {
        val have = store.loadNube().associateBy { it.k }
        var n0 = 0
        for (n in photosInUse(ctx, store).keys) {
            if (Photos.best(ctx, n) != null) continue
            val x = have["foto:$n"] ?: continue
            try {
                status = "Bajando la foto ${x.n}…"
                val tmp = File(ctx.cacheDir, "nube_${x.f}")
                bajar(ctx, x, tmp)
                Photos.saveArrived(ctx, n, tmp.readBytes(), Photos.mimeOf(File(x.n)))
                tmp.delete()
                n0++
            } catch (e: Exception) { errs += "${x.n}: ${e.message}" }
        }
        if (n0 > 0) Photos.llegaron()
        n0
    }

    /**
     * Una imagen que eliges de tu nube para una portada: se baja y se usa, y se apunta ese mismo archivo de tu
     * kDrive para ella (no se vuelve a subir otra copia). Devuelve su dirección.
     */
    suspend fun usarDeLaNube(ctx: Context, file: Long, name: String): String = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "nube_pick_${file}_${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
        KDrive.download(ctx, file, tmp)
        val size = tmp.length()
        val u = Photos.import(ctx, Uri.fromFile(tmp))
        tmp.delete()
        Photos.nameOf(u)?.let { n -> Store(ctx).addNube(listOf(NubeFile("foto:$n", file, name, size, KDrive.link(ctx, file)))) }
        u
    }

    /** ¿Hay fotos tuyas en uso aquí que aún no están en tu nube? (para subirlas enseguida) */
    fun fotosPorSubir(ctx: Context, store: Store = Store(ctx)): Boolean {
        if (!KDrive.on(ctx)) return false
        val have = store.loadNube().map { it.k }.toSet()
        return photosInUse(ctx, store).keys.any { "foto:$it" !in have && Photos.best(ctx, it) != null }
    }

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
        // Las fotos de tu diario
        store.loadDiario().forEach { n -> Photos.nameOf(n.foto)?.let { out.putIfAbsent(it, "Atrio/Diario/${java.time.LocalDate.ofEpochDay(n.dia).year}") } }
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
    suspend fun run(ctx: Context, verify: Boolean = false, foreground: (suspend (String, Int) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        // Sin tu kDrive en este dispositivo no se sube nada, pero lo tuyo se baja igual por sus enlaces
        val on = KDrive.on(ctx)
        val store = Store(ctx)
        if (!on && store.loadNube().none { it.l.isNotBlank() }) return@withContext "Sin nube"
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
            if (on) {
            // Libros: el archivo que tengas adjunto, con su enlace para tu pareja
            val titles = (store.load(1).map { it.id to it.name } + store.loadPlays(1).map { it.id to it.name }).toMap()
            // Comprobar que lo subido sigue en tu kDrive (a mano, o cada 6 h): si lo borraste de allí, se vuelve a subir
            val np = ctx.getSharedPreferences("nube", Context.MODE_PRIVATE)
            val comprobar = verify || System.currentTimeMillis() - np.getLong("verificadoEn", 0L) > 6 * 3600_000L
            if (comprobar) np.edit().putLong("verificadoEn", System.currentTimeMillis()).apply()
            for ((id, u) in localBooks(ctx)) {
                val rec = have["libro:$id"]
                if (rec != null) {
                    val tam = runCatching { sizeOf(ctx, Uri.parse(u)) }.getOrDefault(0L)
                    // Otro archivo (su tamaño ha cambiado) o ya no está en tu kDrive: el apunte viejo se quita y se sube de nuevo
                    val sobra = (tam > 0 && tam != rec.s) || (comprobar && KDrive.exists(ctx, rec.f) == false)
                    if (!sobra) continue
                    store.removeNube("libro:$id")
                }
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
            }
            if (added.isNotEmpty()) store.addNube(added)
            // Bajar las fotos de portada que falten aquí (las elegiste en otro dispositivo)
            down += bajarFotos(ctx, store, errs)
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
        // Lo subido queda apuntado en tu cuenta (para que tus otros dispositivos lo encuentren): sube ya
        if (up > 0) Couple.pushIfPending(store)
        msg
    }

    fun shotMeta(s: Shot): JSONObject = JSONObject().put("game", s.game).put("sg", s.sg).put("t", s.time).put("w", s.w).put("h", s.h)
        .put("mime", s.mime).put("pc", s.pc).put("by", s.by)

    /**
     * Baja un archivo de tu nube. Primero con tu cuenta; si falla (este dispositivo conectado a otro kDrive, un
     * token sin permiso…), por el enlace del archivo, que no depende de la cuenta. Si tampoco, el porqué de verdad.
     */
    fun bajar(ctx: Context, x: NubeFile, dest: File) {
        val first = if (KDrive.on(ctx)) try { KDrive.download(ctx, x.f, dest); return } catch (e: Exception) { e } else null
        var shared: Exception? = null
        if (x.l.isNotBlank()) try { KDrive.downloadShared(x.l, x.f, dest); return } catch (e: Exception) { shared = e }
        throw KDrive.Fail(if (first != null) porQue(ctx, x, first) else "No se pudo bajar por su enlace (${shared?.message ?: "no tiene"}); conecta tu kDrive en Ajustes → Tu nube")
    }

    /** Por qué no se pudo bajar: otro kDrive en este dispositivo, el archivo ya no está, o el error tal cual. */
    internal fun porQue(ctx: Context, x: NubeFile, e: Exception): String {
        val suyo = Regex("""/app/share/(\d+)/""").find(x.l)?.groupValues?.get(1)
        val aqui = KDrive.drive(ctx)
        if (suyo != null && aqui.isNotBlank() && suyo != aqui)
            return "Este dispositivo está conectado a otro kDrive (nº $aqui) y el archivo está en el nº $suyo: cámbialo en Ajustes → Tu nube"
        if (e is KDrive.Fail && e.message?.startsWith("No encuentro") == true)
            return if (runCatching { KDrive.reach(ctx) }.isSuccess) "Ese archivo ya no está en tu kDrive (¿lo borraste o lo moviste?): vuelve a adjuntarlo desde el dispositivo donde lo tengas"
            else "No encuentro tu kDrive (nº $aqui) con este token: revísalo en Ajustes → Tu nube"
        return e.message ?: "error"
    }

    /** Baja un libro de la nube y lo deja adjunto. */
    suspend fun fetchBook(ctx: Context, id: Long): Boolean = withContext(Dispatchers.IO) {
        val x = Store(ctx).loadNube().find { it.k == "libro:$id" } ?: return@withContext false
        val dest = File(File(ctx.filesDir, "libros"), "$id.${x.n.substringAfterLast('.', "epub")}")
        try { bajar(ctx, x, dest) } catch (e: Exception) {
            // Si ya no está en tu kDrive, el apunte sobra: deja de ofrecerse «Bajar de tu nube» y el libro se puede volver a subir
            if (e.message?.contains("ya no está") == true) Store(ctx).removeNube("libro:$id")
            throw e
        }
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
                bajar(ctx, x, tmp)
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
    /**
     * Elegir una imagen de tu kDrive (lo pone la app; null sin nube): la devuelve ya como foto tuya de este móvil. El
     * primer dato es el nombre de la ficha o la saga (para abrir su carpeta); puede ir vacío.
     */
    var pickFromCloud: ((String, (String) -> Unit) -> Unit)? = null
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
        var msg = NubeSync.run(applicationContext, inputData.getBoolean("verify", false)) { t, p -> if (fg) try { aviso(t, p) } catch (e: Exception) { fg = false } }
        // Si mientras subía pusiste otra foto, otra pasada (hasta dos más)
        repeat(2) { if (NubeSync.fotosPorSubir(applicationContext)) msg = NubeSync.run(applicationContext) { t, p -> if (fg) try { aviso(t, p) } catch (e: Exception) { fg = false } } }
        // Lo subido, a tu cuenta enseguida (aunque la app esté cerrada): así tus otros dispositivos lo ven ya
        Couple.pushIfPending(Store(applicationContext))
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
            Button({ NubeSync.enqueue(ctx, verify = true); say("Sincronizando con tu nube en segundo plano") }, enabled = !NubeSync.running, shape = pillShape()) { Text(if (NubeSync.running) "Sincronizando…" else "Sincronizar ahora") }
            OutlinedButton({ KDrive.forget(ctx); on = false; token = ""; say("Nube desconectada (lo subido sigue en tu kDrive)") }, shape = pillShape()) { Text("Desconectar") }
        }
    }
}
