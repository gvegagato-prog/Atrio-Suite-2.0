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
import androidx.compose.foundation.clickable
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
        /** [goto]: abrir directamente en ese punto (el de un subrayado). */
        fun open(ctx: Context, id: Long, name: String, goto: String? = null) {
            ctx.startActivity(Intent(ctx, ReaderActivity::class.java).putExtra("id", id).putExtra("name", name).putExtra("goto", goto)
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

    // Leer en voz alta: la voz de Android. Lo que se pide antes de que esté lista, espera en [pending].
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false
    private var pending: (() -> Unit)? = null
    // La última frase pedida mientras la voz arrancaba (si no arranca, se le contesta con error a esa)
    private var pendingUid: String? = null

    private fun speakNow(text: String, uid: String, rate: Float, lang: String) {
        val t = tts
        if (t == null || !ttsReady) {
            pending = { speakNow(text, uid, rate, lang) }
            pendingUid = uid
            // El aviso de «lista» se atiende en el siguiente paso del hilo principal: si no hay motor de voz llega
            // dentro del propio constructor, antes de que [tts] tenga la voz nueva
            if (t == null) tts = android.speech.tts.TextToSpeech(this) { status -> android.os.Handler(mainLooper).post { ttsInit(status) } }
            return
        }
        // El idioma del libro, si el móvil tiene esa voz (si no, la que tenga por defecto)
        val loc = java.util.Locale.forLanguageTag(lang.ifBlank { "es" })
        if (t.isLanguageAvailable(loc) >= android.speech.tts.TextToSpeech.LANG_AVAILABLE && t.voice?.locale?.language != loc.language) t.language = loc
        t.setSpeechRate(rate.coerceIn(0.5f, 2.5f))
        // Android no lee de una vez más de getMaxSpeechInputLength() letras: una frase larguísima va en trozos
        // (solo el último lleva el id que espera el lector; los de antes no los mira)
        val max = (android.speech.tts.TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(100)
        val parts = if (text.length <= max) listOf(text) else text.chunked(max)
        var ok = true
        parts.forEachIndexed { i, part ->
            val last = i == parts.lastIndex
            val r = t.speak(part, if (i == 0) android.speech.tts.TextToSpeech.QUEUE_FLUSH else android.speech.tts.TextToSpeech.QUEUE_ADD,
                null, if (last) uid else "$uid.$i")
            if (r != android.speech.tts.TextToSpeech.SUCCESS) ok = false
        }
        // Si no se ha podido ni poner en cola, no llegará ningún aviso: se contesta ya con error
        if (!ok) spoken(uid, true)
    }

    private fun ttsInit(status: Int) {
        if (tts == null) return // ya se ha cerrado el lector
        ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { spoken(utteranceId, false) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { spoken(utteranceId, true) }
                override fun onError(utteranceId: String?, errorCode: Int) { spoken(utteranceId, true) }
            })
            val p = pending; pending = null; pendingUid = null
            p?.invoke()
        } else {
            // No hay motor de voz (o no arranca): se suelta para volver a intentarlo la próxima vez
            val u = pendingUid
            pending = null; pendingUid = null
            tts?.shutdown(); tts = null
            spoken(u, true)
        }
    }

    private fun spoken(uid: String?, error: Boolean) = runOnUiThread {
        if (::web.isInitialized && uid != null) web.evaluateJavascript("window.atrioSpoken && atrioSpoken(${JSONObject.quote(uid)}, $error)", null)
    }

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
            // El menú de copiar/compartir de Android se abre vacío: el lector tiene el suyo (subrayar, nota, copiar…).
            // Ojo: devolver null aquí hace que el WebView cancele la selección al momento; hay que dejarlo abrir.
            override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? =
                super.startActionMode(emptyMenu(callback), type)
            override fun startActionMode(callback: ActionMode.Callback?): ActionMode? =
                super.startActionMode(emptyMenu(callback))
        }
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.setBackgroundColor(AColor.parseColor("#FBF8F1"))
        web.webChromeClient = object : WebChromeClient() {
            // La consola del lector va al registro de Android (adb logcat -s AtrioLector)
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.i("AtrioLector", "${m.messageLevel()} ${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                return true
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
            override fun onPageFinished(view: WebView, url: String) { pushInsets() }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                // Los capítulos se cargan en iframes con direcciones blob:/data:/about: — eso no se toca.
                // Solo los enlaces web de verdad, en la página principal, se abren fuera (en el navegador).
                if (!request.isForMainFrame || u.scheme !in setOf("http", "https") || u.host == "appassets.androidplatform.net") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) { }
                return true
            }
        }
        web.addJavascriptInterface(Bridge(), "Atrio")
        setContentView(web)
        // El lector necesita un WebView reciente (se actualiza solo desde Google Play)
        val wv = androidx.webkit.WebViewCompat.getCurrentWebViewPackage(this)
        val major = wv?.versionName?.substringBefore('.')?.toIntOrNull() ?: 0
        if (major in 1..99) android.widget.Toast.makeText(
            this, "Tu WebView es antiguo (versión $major): actualiza «Android System WebView» en Google Play si el libro no se abre.",
            android.widget.Toast.LENGTH_LONG
        ).show()
        web.loadUrl("https://appassets.androidplatform.net/assets/lector/index.html")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.atrioBack ? atrioBack() : false") { r -> if (r != "true") finish() }
            }
        })
        // El WebView no le dice al CSS cuánto ocupan la barra de estado y la de navegación: se lo decimos nosotros,
        // para que las barras del lector no queden debajo de la hora ni de los botones de Android.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(web) { _, ins ->
            val d = resources.displayMetrics.density
            val sys = ins.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            safeTop = sys.top / d; safeBottom = sys.bottom / d
            pushInsets()
            ins
        }
        bars(false)
    }

    /** El menú de selección de Android, pero sin opciones (y sin barra visible): la selección se mantiene. */
    private fun emptyMenu(cb: ActionMode.Callback?): ActionMode.Callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: android.view.Menu): Boolean { cb?.onCreateActionMode(mode, menu); menu.clear(); return true }
        override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu): Boolean { cb?.onPrepareActionMode(mode, menu); menu.clear(); return true }
        override fun onActionItemClicked(mode: ActionMode, item: android.view.MenuItem): Boolean = false
        override fun onDestroyActionMode(mode: ActionMode) { cb?.onDestroyActionMode(mode) }
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
            if (cb is ActionMode.Callback2) cb.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
        }
    }

    private var safeTop = 0f
    private var safeBottom = 0f

    private fun pushInsets() {
        if (!::web.isInitialized) return
        // (antes de que cargue la página aún no hay documento: entonces no se hace nada y llega en onPageFinished)
        web.evaluateJavascript(
            "if (document.documentElement) {" +
                "document.documentElement.style.setProperty('--safe-top','${safeTop}px');" +
                "document.documentElement.style.setProperty('--safe-bottom','${safeBottom}px');" +
                "window.atrioInsets && atrioInsets() }", null
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Volumen para pasar página
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { web.evaluateJavascript("window.atrioPage && atrioPage(1)", null); return true }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) { web.evaluateJavascript("window.atrioPage && atrioPage(-1)", null); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown(); tts = null
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
                .put("goto", intent.getStringExtra("goto") ?: JSONObject.NULL)
                .put("stats", o.optJSONObject("stats") ?: JSONObject())
                .put("accent", accentJson())
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

        /** Lo que llevas leído de este libro (tiempo total y por día). */
        @JavascriptInterface
        fun saveStats(json: String) = edit { it.put("stats", JSONObject(json)) }

        /** Lee una frase en voz alta; al acabar contesta en window.atrioSpoken(id, error). */
        @JavascriptInterface
        fun speak(text: String, id: String, rate: Double, lang: String) = runOnUiThread { speakNow(text, id, rate.toFloat(), lang) }

        @JavascriptInterface
        fun stopSpeaking() = runOnUiThread { pending = null; tts?.stop() }

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

        /** Un toque de vibración al coger una palabra. */
        @JavascriptInterface
        fun buzz() = runOnUiThread { web.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) }

        /** Significado de una palabra (y en qué idioma está su frase). Contesta en window.atrioResult(id, …). */
        @JavascriptInterface
        fun lookup(word: String, sentence: String, id: String) {
            Thread {
                val o = JSONObject()
                val lang = Diccionario.identify(sentence.ifBlank { word })
                o.put("lang", lang)
                if (lang != "und") o.put("langName", Diccionario.langName(lang))
                try {
                    val (found, defs) = Diccionario.define(word, if (lang == "es" || lang == "und") "Español" else Diccionario.langName(lang))
                    o.put("defs", org.json.JSONArray(defs))
                    if (defs.isNotEmpty() && found.lowercase() != word.lowercase()) o.put("lemma", found)
                    if (defs.isEmpty()) o.put("msg", "El Wikcionario no tiene esta palabra. Prueba con la RAE.")
                } catch (e: Exception) {
                    o.put("msg", "No se ha podido consultar el Wikcionario (¿sin conexión?).")
                }
                answer(id, o)
            }.start()
        }

        /** Traduce la palabra y su frase al español (el idioma se baja la primera vez, unos 30 MB). */
        @JavascriptInterface
        fun translate(word: String, sentence: String, lang: String, id: String) {
            Thread {
                val o = JSONObject()
                try {
                    val from = if (lang == "und") Diccionario.identify(sentence.ifBlank { word }) else lang
                    o.put("word", Diccionario.translate(word, from))
                    if (sentence.isNotBlank() && sentence != word) o.put("sentence", Diccionario.translate(sentence, from))
                } catch (e: Exception) {
                    o.put("msg", "No se ha podido traducir: ${e.message ?: "sin conexión para bajar el idioma"}")
                }
                answer(id, o)
            }.start()
        }
    }

    /** El acento elegido en Apariencia (claro y oscuro), para que el lector vaya a juego con la app. */
    private fun accentJson(): JSONObject {
        val idA = getSharedPreferences("gamerate", MODE_PRIVATE).getString("skin_accent_id", "rojo")
        val a = ACCENT_COLORS.firstOrNull { it.id == idA } ?: ACCENT_COLORS.first()
        fun hex(c: Long) = String.format("#%06X", (c and 0xFFFFFF))
        return JSONObject().put("light", hex(a.light)).put("dark", hex(a.dark))
    }

    private fun answer(id: String, o: JSONObject) = runOnUiThread {
        if (::web.isInitialized) web.evaluateJavascript("window.atrioResult && atrioResult(${JSONObject.quote(id)}, $o)", null)
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
            val readMin = (st.optJSONObject("stats")?.optLong("total") ?: 0L) / 60000L
            val readTxt = when {
                readMin < 1 -> null
                readMin < 60 -> "$readMin min leyendo"
                else -> "${readMin / 60} h${if (readMin % 60 > 0) " ${readMin % 60} min" else ""} leyendo"
            }
            Text(
                listOfNotNull("${(frac * 100).toInt()} % leído", readTxt, st.optString("label").ifBlank { null }, if (notes > 0) "$notes subrayados" else null, st.optString("name")).joinToString(" · "),
                color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Un subrayado del lector (con su nota, si tiene). */
data class BookNote(val value: String, val text: String, val note: String, val label: String, val created: Long, val index: Int)

fun bookNotes(ctx: Context, id: Long): List<BookNote> {
    val a = BookFiles.state(ctx, id).optJSONArray("notes") ?: return emptyList()
    return (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        BookNote(o.optString("value"), o.optString("text"), o.optString("note"), o.optString("label"), o.optLong("created"), o.optInt("index"))
    }.sortedWith(compareBy({ it.index }, { it.created }))
}

/** La casilla «Subrayados y notas» de la ficha de un libro: tocar uno abre el libro ahí. */
@Composable
fun BookNotesBox(id: Long, name: String) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val life = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(life) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }
    val file = remember(tick) { BookFiles.get(ctx, id) }
    val notes = remember(tick) { bookNotes(ctx, id) }
    var all by remember { mutableStateOf(false) }
    val italic = androidx.compose.ui.text.font.FontStyle.Italic
    if (file == null) {
        Text("Adjunta el libro (casilla Lectura) para leerlo aquí; lo que subrayes y anotes aparecerá en esta casilla.",
            color = Muted, fontFamily = Display, fontStyle = italic, fontSize = 15.sp, lineHeight = 20.sp)
        return
    }
    if (notes.isEmpty()) {
        Text("Aún no has subrayado nada. En el lector, mantén pulsada una palabra y toca Subrayar o Nota.",
            color = Muted, fontFamily = Display, fontStyle = italic, fontSize = 15.sp, lineHeight = 20.sp)
        return
    }
    val withNote = notes.count { it.note.isNotBlank() }
    Text(listOfNotNull("${notes.size} subrayado${if (notes.size == 1) "" else "s"}", if (withNote > 0) "$withNote con nota" else null).joinToString(" · ").uppercase(),
        color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Spacer(Modifier.height(6.dp))
    val shown = if (all) notes else notes.take(4)
    shown.forEachIndexed { i, n ->
        if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Charcoal.copy(alpha = 0.15f)))
        Column(Modifier.fillMaxWidth().clickable { ReaderActivity.open(ctx, id, name, n.value) }.padding(vertical = 2.dp)) {
            Text("«${n.text}»", fontFamily = Display, fontSize = 17.sp, lineHeight = 23.sp, maxLines = if (all) 12 else 5, overflow = TextOverflow.Ellipsis)
            if (n.note.isNotBlank()) Text(n.note, fontStyle = italic, fontSize = 14.sp, lineHeight = 19.sp, color = ColGeneral, modifier = Modifier.padding(top = 4.dp))
            Text(listOfNotNull(n.label.ifBlank { null }, if (n.created > 0) fmtDay(n.created / 86_400_000L) else null).joinToString(" · ").uppercase(),
                color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (notes.size > 4) Text(if (all) "VER MENOS" else "VER LOS ${notes.size} →", color = ColGeneral, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
        modifier = Modifier.clickable { all = !all }.padding(top = 10.dp, bottom = 4.dp))
}
