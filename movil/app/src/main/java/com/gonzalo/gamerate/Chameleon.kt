package com.gonzalo.gamerate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Camaleón: al abrir la ficha de un juego, libro o película, la app se tiñe con el color de su portada
 * (el fondo, las tarjetas y el color principal de las notas), y el cambio se abre en círculo desde donde tocaste.
 * Al salir de la ficha vuelve tu paleta. Se apaga en Estilo → Fondo.
 */
object Chameleon {
    private val cache = HashMap<String, Int?>()

    /** Color con más carácter de la portada (el más vivo y abundante), o null si no se puede leer. */
    suspend fun colorOf(ctx: Context, url: String): Int? {
        if (url.isBlank()) return null
        synchronized(cache) { if (cache.containsKey(url)) return cache[url] }
        val c = withContext(Dispatchers.IO) {
            try {
                val r = ImageRequest.Builder(ctx).data(url).allowHardware(false).size(48).build()
                val bmp = (ImageLoader(ctx).execute(r).drawable as? BitmapDrawable)?.bitmap ?: return@withContext null
                pick(bmp)
            } catch (e: Exception) { null }
        }
        synchronized(cache) { cache[url] = c }
        return c
    }

    /** Agrupa los píxeles por tono (12 grupos) y se queda con el grupo de más peso (abundancia × viveza). */
    private fun pick(src: Bitmap): Int? {
        val b = Bitmap.createScaledBitmap(src, 32, 32, true)
        val weight = DoubleArray(12)
        val sumH = DoubleArray(12); val sumS = DoubleArray(12); val sumV = DoubleArray(12)
        val hsv = FloatArray(3)
        for (y in 0 until b.height) for (x in 0 until b.width) {
            android.graphics.Color.colorToHSV(b.getPixel(x, y), hsv)
            val s = hsv[1]; val v = hsv[2]
            if (s < 0.18f || v < 0.18f) continue
            val w = (s * v).toDouble().let { it * it }
            val bin = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            weight[bin] += w; sumH[bin] += hsv[0] * w; sumS[bin] += s * w; sumV[bin] += v * w
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: return null
        if (weight[best] < 1.0) return null
        val w = weight[best]
        return android.graphics.Color.HSVToColor(floatArrayOf((sumH[best] / w).toFloat(), (sumS[best] / w).toFloat(), (sumV[best] / w).toFloat()))
    }

    /** Tonos de la paleta [base] teñidos con [c]: el color principal pasa a ser el de la portada, ajustado para leerse. */
    fun tint(base: Tones, c: Int, dark: Boolean): Tones {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        // En oscuro, un color claro y vivo; en claro, uno más hondo
        val accent = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1].coerceIn(0.45f, 0.85f), if (dark) 0.88f else 0.62f))
        val wash = android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1].coerceAtMost(0.7f), if (dark) 0.45f else 0.92f))
        fun L(col: Color) = col.toArgb().toLong() and 0xFFFFFFFFL
        val paper = lerp(base.cPaper, Color(wash), if (dark) 0.22f else 0.30f)
        val crudo = lerp(base.cCrudo, Color(wash), if (dark) 0.16f else 0.22f)
        return base.copy(paper = L(paper), crudo = L(crudo), general = L(Color(accent)), sand = 0, line = 0, edge = 0, muted = 0)
    }
}

/** Pone el camaleón mientras se ve esta ficha (con la portada [url]) y lo quita al salir. */
@Composable
fun ChameleonEffect(url: String) {
    val ctx = LocalContext.current
    val on = Skin.chameleon
    val dark = Mode.dark
    val pal = Skin.palette
    LaunchedEffect(url, on, dark, pal) {
        if (!on || url.isBlank()) { Skin.override = null; return@LaunchedEffect }
        val c = Chameleon.colorOf(ctx, url) ?: return@LaunchedEffect
        val t = Chameleon.tint(Skin.base, c, dark)
        // La primera vez, el color se abre en círculo desde la portada que tocaste
        if (Skin.override == null) withReveal { Skin.override = t } else Skin.override = t
    }
    DisposableEffect(Unit) { onDispose { Skin.override = null } }
}
