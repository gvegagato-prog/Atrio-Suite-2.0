package com.gonzalo.gamerate

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.hypot

sealed interface Screen {
    object Home : Screen
    object Settings : Screen
    class Search(val forPlay: Boolean, val manual: Boolean = false, val name: String = "") : Screen
    class Art(val game: Game, val hero: Boolean, val isNew: Boolean, val chain: Boolean, val play: Play?, val home: Boolean = false) : Screen
    /** editing: abrir directamente en edición (si no, las ya valoradas se abren en modo lectura). */
    class Edit(val game: Game, val isNew: Boolean, val editing: Boolean = false) : Screen
    /** editing: se abre ya en modo edición (recién creada, o al volver de cambiar la portada). */
    class PlayDetail(val play: Play, val editing: Boolean = false) : Screen
    class Note(val play: Play, val entry: Entry?) : Screen
    /** Mando a distancia del PC (la suite con Atrio). */
    object Suite : Screen
    /** Una captura a pantalla completa; [back] es adonde se vuelve. */
    class ShotView(val shot: Shot, val back: Screen) : Screen
    /** Ficha de tu pareja abierta desde el menú o desde su calendario. */
    class PartnerItem(val kind: Int, val id: Long) : Screen
    /** Atrio 6: perfil (estilo, pareja, objetivos, PC y ajustes). */
    object Profile : Screen
    /** Estadísticas de la categoría abierta. */
    object Stats : Screen
    /** «Siguiente»: cartas para deslizar (juegos). */
    /** Descubrir con preguntas: 1 libros, 2 pelis y series. */
    class Quiz(val domain: Int) : Screen
    /** Una saga: sus libros, en orden. */
    class Saga(val id: Long) : Screen
    /** Cambiar notas en masa (desde Perfil o Ajustes); [back] es adonde se vuelve. */
    class MassScore(val back: Screen) : Screen
    /** Ordenar los libros por duelos y repartir sus notas; [back] es adonde se vuelve. */
    class Duelos(val back: Screen) : Screen
}

class MainActivity : ComponentActivity() {
    private var openId by mutableStateOf(0L)
    private var openPartner by mutableStateOf(false)
    /** Acceso directo del icono: "add_game", "add_book", "next_game", "next_book", "stats_game", "stats_book". */
    private var openGo by mutableStateOf("")
    private var shareUrl by mutableStateOf("")
    private var shareTitle by mutableStateOf("")

    private fun read(i: Intent?) {
        // Al volver desde Recientes, Android repite la intent original: no hay que repetir el atajo.
        if (i != null && (i.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        openId = i?.getLongExtra("play_id", 0L) ?: 0L
        openPartner = i?.getBooleanExtra("partner", false) ?: false
        openGo = i?.getStringExtra("go") ?: ""
        // Compartir → Atrio desde el navegador: una web para guardarla como guía de un juego del PC
        if (i?.action == Intent.ACTION_SEND) {
            val txt = i.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val url = Regex("https?://\\S+").find(txt)?.value ?: ""
            if (url.isNotEmpty()) { shareUrl = url; shareTitle = (i.getStringExtra(Intent.EXTRA_SUBJECT) ?: txt.replace(url, "")).trim() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Desde el icono con Atrio ya abierto (por ejemplo con una guía delante): se vuelve a lo que había, sin otra pantalla encima
        if (!isTaskRoot && intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) { finish(); return }
        if (savedInstanceState == null) read(intent)
        maxRefresh()
        val store = Store(this)
        Mode.themeMode = store.themeMode
        Skin.load(this)
        store.forgetGemini()
        Limpieza.antiguas(this)
        // Solo para la prueba automática en el emulador: Firebase de mentira en el ordenador que lo ejecuta (10.0.2.2).
        intent?.getStringExtra("fbtest")?.takeIf { it.startsWith("http://10.0.2.2:") }?.let {
            store.authBase = it
            store.dbUrl = it
            store.webKey = "prueba"
        }
        // Para la prueba automática: abrir con una interfaz concreta (0 = la del dock)
        intent?.getIntExtra("shell", -1)?.takeIf { it == 0 || it == 2 }?.let { if (it == 2) Skin.applyShell(2, this) else { Skin.shell = 0; Skin.save(this) } }
        // Portadas: aparecen con un fundido suave en lugar de saltar.
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(this).crossfade(180).build())
        setContent {
            val sys = isSystemInDarkTheme()
            val own = when (Mode.themeMode) { 1 -> false; 2 -> true; else -> sys }
            // Lo de tu pareja va con el tema contrario al tuyo.
            val target = if (Mode.partnerShown) !own else own
            remember { Mode.dark = target; true }
            var overlay by remember { mutableStateOf<ImageBitmap?>(null) }
            var center by remember { mutableStateOf(Offset.Zero) }
            val radius = remember { Animatable(0f) }
            var busy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            fun maxRadius(): Float {
                val v = window.decorView
                val w = v.width.toFloat()
                val h = v.height.toFloat()
                return hypot(maxOf(center.x, w - center.x), maxOf(center.y, h - center.y))
            }
            fun touchCenter(): Offset {
                val v = window.decorView
                return Mode.touch.takeIf { it.isSpecified } ?: Offset(v.width / 2f, v.height / 3f)
            }

            // Cambio pedido desde la app: primero la foto de la pantalla, luego el cambio debajo y el círculo se abre.
            SideEffect {
                Mode.reveal = { change ->
                    if (busy) change() else {
                        busy = true
                        scope.launch {
                            try {
                                val shot = capture()
                                center = touchCenter()
                                radius.snapTo(0f)
                                overlay = shot?.asImageBitmap()
                                change()
                                // Unos fotogramas para que lo nuevo (y su tema) se pinte debajo de la foto.
                                repeat(4) { withFrameNanos { } }
                                if (overlay != null) radius.animateTo(maxRadius(), tween(480, easing = FastOutSlowInEasing))
                            } finally {
                                overlay = null
                                busy = false
                            }
                        }
                    }
                }
            }

            LaunchedEffect(target) {
                val bars = if (target) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                if (Mode.dark == target) return@LaunchedEffect
                // Si ya hay una animación en marcha, el cambio queda tapado por su foto.
                if (busy) { Mode.dark = target; return@LaunchedEffect }
                // Cambios que no pasan por la app (volver atrás, tema del sistema): foto de lo que hay y círculo.
                val shot = capture()
                center = touchCenter()
                overlay = shot?.asImageBitmap()
                radius.snapTo(0f)
                Mode.dark = target
                try {
                    if (overlay != null) radius.animateTo(maxRadius(), tween(480, easing = FastOutSlowInEasing))
                } finally {
                    if (!busy) overlay = null
                }
            }
            Box(Modifier.fillMaxSize()) {
                // Tamaño del texto elegido en Ajustes → Estilo (encima del que tenga el sistema)
                val dens = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, dens.fontScale * Skin.textScale)
                ) {
                    MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
                        App(store, openId, openPartner, openGo, shareUrl, shareTitle, { shareUrl = "" }) { openId = 0L; openPartner = false; openGo = "" }
                        // Fondo propio de la forma (líneas de TV, rejilla, grano, trama): no se puede tocar
                        FormOverlay()
                        // Ajustes → Estilo: encima de todo
                        if (Skin.open) androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = Paper, contentColor = Charcoal) {
                            StyleScreen(Mode.themeMode, { t -> store.themeMode = t; withReveal { Mode.themeMode = t } }, { l -> Mode.look = l; store.look = l }) { Skin.open = false }
                        }
                        if (Skin.open) FormOverlay()
                    }
                }
                val img = overlay
                if (img != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        val hole = Path().apply { addOval(Rect(center, radius.value)) }
                        clipPath(hole, ClipOp.Difference) { drawImage(img) }
                    }
                }
            }
        }
    }

    /** Foto de lo que se ve ahora mismo (null si no se puede). */
    private suspend fun capture(): Bitmap? = suspendCancellableCoroutine { cont ->
        val v = window.decorView
        if (v.width <= 0 || v.height <= 0) { cont.resume(null); return@suspendCancellableCoroutine }
        val bm = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(window, bm, { r -> if (cont.isActive) cont.resume(if (r == PixelCopy.SUCCESS) bm else null) },
                Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /**
     * La pantalla a su frecuencia más alta (90/120 Hz si la tiene) mientras se usa la app: más fluida aunque gaste
     * algo más. Muchos móviles dejan las apps a 60 Hz si no lo piden. (El lector no lo pide: allí decide Android y
     * baja la frecuencia cuando el texto está quieto.)
     */
    private fun maxRefresh() {
        val d = (if (android.os.Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay) ?: return
        val cur = d.mode
        val best = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId; it.preferredRefreshRate = best.refreshRate }
    }

    // Al salir de la app, los widgets se ponen al día (lo que hayas cambiado, el día de hoy)
    override fun onStop() {
        super.onStop()
        Widgets.pedir(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }
}
