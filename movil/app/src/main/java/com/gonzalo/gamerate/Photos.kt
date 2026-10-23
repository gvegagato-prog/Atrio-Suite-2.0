package com.gonzalo.gamerate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer

/**
 * Fotos de tu galería usadas como portada: se copian (reducidas) dentro de la app para verlas rápido, y el
 * original, tal cual, aparte (covers/orig): es el que se manda a tus otros dispositivos (el PC, la tableta)
 * cuando lo piden con «Descargar de tu móvil».
 */
object Photos {
    private const val MAX = 1400

    private fun dir(ctx: Context) = File(ctx.filesDir, "covers").apply { mkdirs() }
    private fun origDir(ctx: Context) = File(dir(ctx), "orig").apply { mkdirs() }

    /** Copia la foto elegida y devuelve su dirección para usarla como portada. */
    suspend fun import(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val base = "foto_${System.currentTimeMillis()}"
        // El original, sin tocar (máxima calidad); la copia reducida sale de los mismos bytes
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes != null) try {
            val ext = extOf(ctx.contentResolver.getType(uri) ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()))
            File(origDir(ctx), "$base.$ext").writeBytes(bytes)
        } catch (e: Exception) { /* sin original: se queda la copia reducida */ }
        reduced(ctx, if (bytes != null) ImageDecoder.createSource(ByteBuffer.wrap(bytes)) else ImageDecoder.createSource(ctx.contentResolver, uri), base)
    }

    /** Guarda una imagen que viene de internet (sus bytes): el original y la copia reducida, como una foto elegida. */
    suspend fun importBytes(ctx: Context, bytes: ByteArray, mime: String?): String = withContext(Dispatchers.IO) {
        val base = "foto_${System.currentTimeMillis()}"
        try { File(origDir(ctx), "$base.${extOf(mime)}").writeBytes(bytes) } catch (e: Exception) { /* sin original */ }
        reduced(ctx, ImageDecoder.createSource(ByteBuffer.wrap(bytes)), base)
    }

    /** La copia reducida (la que se ve en la app) a partir de cualquier imagen. */
    private fun reduced(ctx: Context, src: ImageDecoder.Source, base: String): String {
        val bm = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val s = maxOf(w, h).toFloat() / MAX
            if (s > 1f) dec.setTargetSize((w / s).toInt().coerceAtLeast(1), (h / s).toInt().coerceAtLeast(1))
            // Software: así se puede guardar y dibujar en cualquier sitio.
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val f = File(dir(ctx), "$base.jpg")
        f.outputStream().use { bm.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return Uri.fromFile(f).toString()
    }

    private fun extOf(mime: String?) = when (mime?.lowercase()) {
        "image/png" -> "png"; "image/webp" -> "webp"; "image/heic" -> "heic"; "image/heif" -> "heif"; "image/gif" -> "gif"; "image/avif" -> "avif"
        else -> "jpg"
    }
    fun mimeOf(f: File) = when (f.extension.lowercase()) {
        "png" -> "image/png"; "webp" -> "image/webp"; "heic" -> "image/heic"; "heif" -> "image/heif"; "gif" -> "image/gif"; "avif" -> "image/avif"
        else -> "image/jpeg"
    }

    /** Es una foto tuya (no está en internet, tu pareja no la puede ver). */
    fun isLocal(url: String) = url.startsWith("file:")

    /** El nombre de la foto («foto_1700000000000.jpg») a partir de su dirección, o null si no es una foto tuya. */
    fun nameOf(url: String): String? {
        if (!isLocal(url)) return null
        val n = Uri.parse(url).lastPathSegment ?: return null
        return n.takeIf { Regex("^[A-Za-z0-9_.-]{1,80}$").matches(it) }
    }

    /** La foto está en este dispositivo (se puede ver aquí). */
    fun here(ctx: Context, url: String): Boolean = nameOf(url)?.let { File(dir(ctx), it).exists() } == true

    /** El mejor archivo que hay aquí de esa foto: el original si está; si no, la copia reducida. */
    fun best(ctx: Context, name: String): Pair<File, Boolean>? {
        // Con o sin extensión («foto_1.jpg» o «foto_1»): se busca por el nombre sin ella
        val base = if (Regex("\\.[A-Za-z0-9]{2,5}$").containsMatchIn(name)) name.substringBeforeLast('.') else name
        origDir(ctx).listFiles()?.firstOrNull { it.nameWithoutExtension == base }?.let { return it to true }
        return dir(ctx).listFiles()?.firstOrNull { it.isFile && it.nameWithoutExtension == base }?.let { it to false }
    }

    /** Sube cada vez que llegan fotos de otro dispositivo: las pantallas vuelven a pintar sus imágenes. */
    private val ver = androidx.compose.runtime.mutableIntStateOf(0)
    val version: Int get() = ver.intValue
    fun llegaron() { kotlinx.coroutines.MainScope().launch { ver.intValue++ } }

    /** Guarda una foto que llega de otro dispositivo: el original y la copia reducida con el mismo nombre. */
    suspend fun saveArrived(ctx: Context, name: String, bytes: ByteArray, mime: String) = withContext(Dispatchers.IO) {
        val base = name.substringBeforeLast('.')
        File(origDir(ctx), "$base.${extOf(mime)}").writeBytes(bytes)
        reduced(ctx, ImageDecoder.createSource(ByteBuffer.wrap(bytes)), base)
    }
}
