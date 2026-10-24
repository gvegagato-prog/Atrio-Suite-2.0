package com.gonzalo.gamerate

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** Buscar una imagen en internet (Google Imágenes dentro de la app) y quedarse con ella como foto tuya. */
object Internet {
    /** Lo pone la app: abre el buscador con una búsqueda inicial y devuelve la foto ya guardada en este móvil. */
    var pick: ((query: String, (String) -> Unit) -> Unit)? = null

    /** Una web de imágenes donde buscar: cómo se llama y cómo es su búsqueda. */
    class Fuente(val id: String, val nombre: String, val url: (String) -> String)

    val fuentes = listOf(
        Fuente("google", "Google Imágenes") { q -> "https://www.google.com/search?udm=2&hl=es&q=" + enc(q) },
        Fuente("artstation", "ArtStation") { q -> "https://www.artstation.com/search?sort_by=relevance&query=" + enc(q) },
        Fuente("pinterest", "Pinterest") { q -> "https://www.pinterest.com/search/pins/?q=" + enc(q) },
        Fuente("newgrounds", "Newgrounds") { q -> "https://www.newgrounds.com/search/conduct/art?terms=" + enc(q) }
    )
    private fun enc(q: String) = URLEncoder.encode(q.trim(), "UTF-8")
    fun fuente(id: String?) = fuentes.firstOrNull { it.id == id } ?: fuentes[0]

    fun searchUrl(q: String, f: Fuente = fuentes[0]) = f.url(q)

    /**
     * Las direcciones a probar para una imagen, de mejor a peor calidad: las webs enseñan miniaturas y guardan la grande con
     * otro tamaño en la dirección (Pinterest: /236x/ → /originals/). La última es siempre la que se vio.
     */
    fun candidatas(url: String): List<String> {
        val out = ArrayList<String>()
        if (url.contains("pinimg.com/")) {
            val m = Regex("(pinimg\\.com)/(\\d+x)/").find(url)
            if (m != null) { out += url.replace("/${m.groupValues[2]}/", "/originals/"); if (m.groupValues[2] != "736x") out += url.replace("/${m.groupValues[2]}/", "/736x/") }
        }
        if (url.contains("artstation.com/")) {
            val r = Regex("/(micro_square|smaller_square|small_square|small|medium)/")
            if (r.containsMatchIn(url)) out += r.replace(url, "/large/")
        }
        out += url
        return out.distinct()
    }

    /** De un enlace de Google Imágenes (/imgres?imgurl=…), la dirección de la imagen. */
    fun deImgres(href: String): String? {
        val m = Regex("[?&]imgurl=([^&]+)").find(href) ?: return null
        return runCatching { URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrNull()?.takeIf { it.startsWith("http") }
    }

    /** Una miniatura de Google (de la cuadrícula de resultados), no la imagen de verdad. */
    fun esMiniatura(u: String) = u.startsWith("data:") || u.contains("encrypted-tbn") || u.contains("gstatic.com/")

    /** Los bytes de una imagen (dirección http/https o data:) y su tipo. */
    fun descargar(url: String, ua: String): Pair<ByteArray, String?> {
        if (url.startsWith("data:")) {
            val head = url.substringBefore(',')
            val mime = head.removePrefix("data:").substringBefore(';').ifBlank { null }
            val body = url.substringAfter(',')
            val bytes = if (head.contains("base64")) android.util.Base64.decode(body, android.util.Base64.DEFAULT) else URLDecoder.decode(body, "ISO-8859-1").toByteArray(Charsets.ISO_8859_1)
            return bytes to mime
        }
        var last: Exception? = null
        // Algunas webs no dejan sacar sus imágenes sin venir de una página: se prueba también así
        for (u in candidatas(url)) for (referer in listOf<String?>(null, "https://www.google.com/")) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", ua)
                c.setRequestProperty("Accept", "image/*,*/*;q=0.8")
                if (referer != null) c.setRequestProperty("Referer", referer)
                c.connectTimeout = 15000; c.readTimeout = 25000
                if (c.responseCode !in 200..299) error("la web respondió ${c.responseCode}")
                val len = c.contentLengthLong
                if (len > MAX) error("la imagen es demasiado grande")
                val bytes = c.inputStream.use { it.readBytes() }
                if (bytes.size > MAX) error("la imagen es demasiado grande")
                if (bytes.size < 200) error("no es una imagen")
                return bytes to c.contentType?.substringBefore(';')?.trim()
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("no se pudo bajar")
    }
    private const val MAX = 40L * 1024 * 1024

    /**
     * Qué imagen se está viendo en la página: la más grande de las visibles, mejor si es la de verdad (http) y no la
     * miniatura de Google. Devuelve {"u": dirección, "w": ancho, "low": true si es solo la miniatura}.
     */
    const val JS_ELEGIDA = """(function(){try{
var vis=function(e){var r=e.getBoundingClientRect();return r.width>=90&&r.height>=90&&r.bottom>0&&r.top<innerHeight&&r.right>0&&r.left<innerWidth;};
var tbn=function(s){return /encrypted-tbn|gstatic\.com\/|^data:/.test(s);};
var best=null,bestLow=null;
var all=[].slice.call(document.images).filter(vis);
all.forEach(function(i){var s=i.currentSrc||i.src||'';if(!s)return;var r=i.getBoundingClientRect();var a=r.width*r.height;
 var a2=i.closest&&i.closest('a');var h=a2?a2.href:'';var m=/[?&]imgurl=([^&]+)/.exec(h);
 var full=m?decodeURIComponent(m[1]):null;
 var o={u:full||s,a:a,w:i.naturalWidth||0,low:!full&&tbn(s)};
 if(o.low){if(!bestLow||a>bestLow.a)bestLow=o;}else{if(!best||a>best.a)best=o;}});
var r=best||bestLow;return JSON.stringify(r?{u:r.u,w:r.w,low:!!r.low}:{});}catch(e){return JSON.stringify({err:String(e)});}})()"""
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InternetPicker(initialQuery: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    val prefs = remember { ctx.getSharedPreferences("internet", Context.MODE_PRIVATE) }
    var fuente by remember { mutableStateOf(Internet.fuente(prefs.getString("fuente", null))) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun buscar() { msg = ""; web?.loadUrl(Internet.searchUrl(query, fuente)) }

    fun usar(raw: String) {
        if (busy) return
        busy = true; msg = "Bajando la imagen…"
        scope.launch {
            try {
                val ua = web?.settings?.userAgentString ?: "Mozilla/5.0"
                val (bytes, mime) = withContext(Dispatchers.IO) { Internet.descargar(raw, ua) }
                val foto = Photos.importBytes(ctx, bytes, mime)
                busy = false
                onPick(foto)
            } catch (e: Exception) {
                busy = false
                msg = "No se ha podido usar esa imagen (${e.message ?: "error"}). Prueba con otra."
            }
        }
    }

    // Una miniatura (la de la cuadrícula de Google, pequeña): se avisa, y si vuelves a pulsar, se usa igual
    var miniatura by remember { mutableStateOf("") }
    fun usarOMiniatura(u: String, low: Boolean) {
        if (!low || miniatura == u) { miniatura = ""; usar(u); return }
        miniatura = u
        msg = "Eso es la miniatura (se vería borrosa): tócala, espera a que se vea grande y pulsa «Usar esta imagen». Si aun así la quieres, pulsa otra vez."
    }

    fun usarLaAbierta() {
        val w = web ?: return
        w.evaluateJavascript(Internet.JS_ELEGIDA) { r ->
            val txt = runCatching { JSONTokener(r).nextValue() as? String }.getOrNull()
            val o = runCatching { JSONObject(txt ?: "{}") }.getOrDefault(JSONObject())
            val u = o.optString("u")
            when {
                u.isBlank() -> msg = "No veo ninguna imagen abierta: toca una de los resultados y espera a que se vea grande."
                else -> usarOMiniatura(u, o.optBoolean("low"))
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)) {
        // Atrás: primero por las páginas que has abierto dentro; luego se cierra
        BackHandler { val w = web; if (w != null && w.canGoBack()) w.goBack() else onDismiss() }
        Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
            // De dónde: tres webs de imágenes
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Internet.fuentes.forEach { f ->
                    SoftChip(f.nombre, f.id == fuente.id) { fuente = f; prefs.edit().putString("fuente", f.id).apply(); web?.loadUrl(Internet.searchUrl(query, f)); msg = "" }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onDismiss)
                OutlinedTextField(
                    query, { query = it }, Modifier.weight(1f).padding(horizontal = 6.dp),
                    singleLine = true, shape = shp(14.dp), label = { Text("Buscar en ${fuente.nombre}") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { buscar() })
                )
                IconButton({ buscar() }) { Icon(Icons.Rounded.Search, "Buscar") }
            }
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        // Como el Chrome del móvil (con «wv», algunas webs dan una versión recortada)
                        settings.userAgentString = settings.userAgentString.replace("; wv)", ")")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                val u = r.url.toString()
                                return !(u.startsWith("http://") || u.startsWith("https://"))
                            }
                        }
                        // Mantener pulsada una imagen: esa es la que quieres
                        setOnLongClickListener {
                            val h = hitTestResult
                            val extra = h.extra.orEmpty()
                            val u = when (h.type) {
                                WebView.HitTestResult.IMAGE_TYPE -> extra
                                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE, WebView.HitTestResult.SRC_ANCHOR_TYPE -> Internet.deImgres(extra) ?: extra.takeIf { h.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE }
                                else -> null
                            }
                            if (!u.isNullOrBlank() && (u.startsWith("http") || u.startsWith("data:"))) { usarOMiniatura(u, Internet.esMiniatura(u) && Internet.deImgres(extra) == null); true } else false
                        }
                        loadUrl(Internet.searchUrl(initialQuery, fuente))
                        web = this
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onRelease = { it.stopLoading(); it.destroy(); web = null }
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(msg.ifBlank { "Toca una imagen para verla grande y pulsa «Usar esta imagen». También vale mantener pulsada una imagen." + if (fuente.id == "google") "" else " Se intenta bajar en su tamaño original." }, color = Muted, fontSize = 12.sp)
                Button({ usarLaAbierta() }, enabled = !busy, shape = pillShape(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Bajando…" else "Usar esta imagen") }
            }
        }
    }
}
