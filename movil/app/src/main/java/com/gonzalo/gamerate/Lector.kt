package com.gonzalo.gamerate

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.ActionMode
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.ByteArrayInputStream

/*
 * Libros en el móvil: a cada libro se le puede adjuntar su EPUB o PDF (también MOBI/AZW3, FB2 y CBZ) que ya tengas
 * en el teléfono. No se sube a ningún sitio: Atrio solo guarda el permiso para abrir ese archivo, y lo lee con su
 * propio lector (foliate-js y pdf.js, dentro de la app). Posición, ajustes, subrayados, notas y marcadores se
 * guardan aquí, por libro.
 */

/** Los archivos de libro adjuntos y lo que llevas leído (solo en este móvil). */
object BookFiles {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lector", Context.MODE_PRIVATE)

    fun state(ctx: Context, id: Long): JSONObject =
        prefs(ctx).getString("$id", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    fun put(ctx: Context, id: Long, o: JSONObject) { prefs(ctx).edit().putString("$id", o.toString()).apply() }

    /** El archivo adjunto (su dirección) o null. */
    fun get(ctx: Context, id: Long): String? = state(ctx, id).optString("uri").ifBlank { null }

    fun name(ctx: Context, id: Long): String = state(ctx, id).optString("name")

    /** Lo leído (0–1). */
    fun progress(ctx: Context, id: Long): Float = state(ctx, id).optDouble("fraction", 0.0).toFloat()

    fun attach(ctx: Context, id: Long, uri: Uri) {
        try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
        var name = "libro"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        // Un archivo distinto empieza de cero (los subrayados de otro archivo no encajarían)
        val old = state(ctx, id)
        val o = if (old.optString("uri") == uri.toString()) old else JSONObject()
        put(ctx, id, o.put("uri", uri.toString()).put("name", name).put("added", System.currentTimeMillis()))
    }

    fun detach(ctx: Context, id: Long) {
        val uri = get(ctx, id)
        if (uri != null) try { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
        prefs(ctx).edit().remove("$id").apply()
    }
}

/** El lector: una página web propia (assets/lector) que pinta el libro a pantalla completa. */
class ReaderActivity : ComponentActivity() {
    companion object {
        fun open(ctx: Context, id: Long, name: String) {
            ctx.startActivity(Intent(ctx, ReaderActivity::class.java).putExtra("id", id).putExtra("name", name)
                .addFlags(if (ctx is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        /** Tipo de archivo por la extensión (el WebView es estricto con los módulos de JavaScript). */
        fun mime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "pdf" -> "application/pdf"
            "epub" -> "application/epub+zip"
            else -> "application/octet-stream"
        }
    }

    private lateinit var web: WebView
    private var id = 0L
    private var uri: Uri? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getLongExtra("id", 0L)
        uri = BookFiles.get(this, id)?.let { Uri.parse(it) }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AColor.TRANSPARENT
        window.navigationBarColor = AColor.TRANSPARENT
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/") { path ->
                try { WebResourceResponse(mime(path), "utf-8", assets.open(path)) } catch (e: Exception) { null }
            }
            .addPathHandler("/libro/") { path ->
                val u = uri ?: return@addPathHandler WebResourceResponse("text/plain", "utf-8", 404, "No hay libro", null, ByteArrayInputStream(ByteArray(0)))
                try {
                    val input = contentResolver.openInputStream(u) ?: throw IllegalStateException()
                    WebResourceResponse(mime(path), null, 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"), input)
                } catch (e: Exception) {
                    WebResourceResponse("text/plain", "utf-8", 404, "No se puede leer", null, ByteArrayInputStream(ByteArray(0)))
                }
            }
            .build()
        web = object : WebView(this) {
            // Sin el menú de copiar/compartir de Android: el lector tiene el suyo (subrayar, nota, copiar…)
            override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? = null
            override fun startActionMode(callback: ActionMode.Callback?): ActionMode? = null
        }
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.setBackgroundColor(AColor.parseColor("#FBF8F1"))
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == "appassets.androidplatform.net") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) { }
                return true
            }
        }
        web.addJavascriptInterface(Bridge(), "Atrio")
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/lector/index.html")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.atrioBack ? atrioBack() : false") { r -> if (r != "true") finish() }
            }
        })
        bars(false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Volumen para pasar página
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { web.evaluateJavascript("window.atrioPage && atrioPage(1)", null); return true }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) { web.evaluateJavascript("window.atrioPage && atrioPage(-1)", null); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        (web.parent as? android.view.ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    private fun bars(show: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (show) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun edit(f: (JSONObject) -> Unit) {
        val o = BookFiles.state(this, id)
        f(o)
        BookFiles.put(this, id, o)
    }

    /** Lo que la página del lector puede pedirle a la app. */
    inner class Bridge {
        @JavascriptInterface
        fun state(): String {
            val o = BookFiles.state(this@ReaderActivity, id)
            val name = o.optString("name").ifBlank { "libro" }
            return JSONObject()
                .put("title", intent.getStringExtra("name") ?: "")
                .put("file", name)
                .put("location", o.optString("cfi").ifBlank { null } ?: JSONObject.NULL)
                .put("settings", o.optJSONObject("settings") ?: JSONObject(getSharedPreferences("lector", MODE_PRIVATE).getString("ajustes", "{}") ?: "{}"))
                .put("annotations", o.optJSONArray("notes") ?: org.json.JSONArray())
                .put("bookmarks", o.optJSONArray("marks") ?: org.json.JSONArray())
                .toString()
        }

        @JavascriptInterface
        fun saveProgress(cfi: String, fraction: Double, label: String) = edit { it.put("cfi", cfi).put("fraction", fraction).put("label", label).put("read", System.currentTimeMillis()) }

        /** Los ajustes valen para este libro y como punto de partida para los siguientes. */
        @JavascriptInterface
        fun saveSettings(json: String) {
            edit { it.put("settings", JSONObject(json)) }
            getSharedPreferences("lector", MODE_PRIVATE).edit().putString("ajustes", json).apply()
        }

        @JavascriptInterface
        fun saveAnnotations(json: String) = edit { it.put("notes", org.json.JSONArray(json)) }

        @JavascriptInterface
        fun saveBookmarks(json: String) = edit { it.put("marks", org.json.JSONArray(json)) }

        @JavascriptInterface
        fun setBrightness(v: Double) = runOnUiThread {
            window.attributes = window.attributes.apply { screenBrightness = if (v < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else v.toFloat().coerceIn(0.01f, 1f) }
        }

        @JavascriptInterface
        fun keepScreenOn(on: Boolean) = runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        @JavascriptInterface
        fun setTheme(t: String) = runOnUiThread {
            val bg = when (t) { "sepia" -> "#F1E6CF"; "gris" -> "#2B2D31"; "noche" -> "#121317"; "negro" -> "#000000"; else -> "#FBF8F1" }
            web.setBackgroundColor(AColor.parseColor(bg))
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = t == "papel" || t == "sepia"
        }

        @JavascriptInterface
        fun systemBars(show: Boolean) = runOnUiThread { bars(show) }

        @JavascriptInterface
        fun copy(text: String) = runOnUiThread {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Atrio", text))
        }

        @JavascriptInterface
        fun share(text: String) = runOnUiThread {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartir"))
        }

        @JavascriptInterface
        fun openUrl(url: String) = runOnUiThread { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { } }

        @JavascriptInterface
        fun close() = runOnUiThread { finish() }
    }
}

/** En la ficha del libro: «Leer» (con lo que llevas) o adjuntar su archivo. */
@Composable
fun BookFileBar(id: Long, name: String) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    // Al volver del lector se vuelve a mirar lo leído
    val life = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(life) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }
    val file = remember(tick) { BookFiles.get(ctx, id) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { BookFiles.attach(ctx, id, u); tick++; ReaderActivity.open(ctx, id, name) }
    }
    val types = arrayOf("application/epub+zip", "application/pdf", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook",
        "application/x-fictionbook+xml", "application/vnd.comicbook+zip", "application/zip", "application/octet-stream")
    var menu by remember { mutableStateOf(false) }
    Card6(Modifier.padding(vertical = 8.dp), padding = 14.dp) {
        if (file == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoStories, null, tint = ColGeneral, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Léelo aquí", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Adjunta su EPUB o PDF del móvil: no se sube a ningún sitio.", color = Muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            BigAction(Icons.Rounded.AttachFile, "Elegir archivo", Modifier.fillMaxWidth(), accent = false) { pick.launch(types) }
        } else {
            val st = remember(tick) { BookFiles.state(ctx, id) }
            val frac = st.optDouble("fraction", 0.0).toFloat()
            Row(verticalAlignment = Alignment.CenterVertically) {
                BigAction(Icons.Rounded.AutoStories, if (frac > 0.005f) "Seguir leyendo" else "Empezar a leer", Modifier.weight(1f)) { ReaderActivity.open(ctx, id, name) }
                Spacer(Modifier.width(8.dp))
                Box {
                    RoundIcon(Icons.Rounded.MoreVert, "Opciones del archivo") { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Cambiar de archivo") }, { menu = false; pick.launch(types) }, leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) })
                        DropdownMenuItem({ Text("Quitar el archivo") }, { menu = false; BookFiles.detach(ctx, id); tick++ }, leadingIcon = { Icon(Icons.Rounded.LinkOff, null) })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(pillShape()).background(Sand)) {
                Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(ColGeneral))
            }
            Spacer(Modifier.height(6.dp))
            val notes = st.optJSONArray("notes")?.length() ?: 0
            Text(
                listOfNotNull("${(frac * 100).toInt()} % leído", st.optString("label").ifBlank { null }, if (notes > 0) "$notes subrayados" else null, st.optString("name")).joinToString(" · "),
                color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
