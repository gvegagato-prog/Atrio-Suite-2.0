package com.gonzalo.gamerate

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Widgets de la pantalla de inicio, con la estética de la revista: papel y tinta, titulares grandes en Fraunces,
 * portadas recortadas y esquinas redondas. Cada widget se dibuja entero (así la letra y las portadas quedan como en
 * la app) y encima lleva zonas transparentes para tocar. Los libros con archivo se abren directamente en el lector.
 */

/** Los widgets que hay. */
enum class WTipo { LEYENDO, ESTANTE, CALENDARIO, PORTADA, ANO, CITA, EFEMERIDE, MARCA, MARCA_PAPEL, SIGUIENTE, ACTIVIDAD }

abstract class WBase(private val tipo: WTipo) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.pintar(ctx, tipo, ids, goAsync())
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) =
        Widgets.pintar(ctx, tipo, intArrayOf(id), goAsync())
    override fun onDeleted(ctx: Context, ids: IntArray) = Widgets.olvidar(ctx, ids)
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Widgets.ACCION) {
            val id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            Widgets.accion(ctx, id, i.getStringExtra("op").orEmpty())
            Widgets.pintar(ctx, tipo, intArrayOf(id), goAsync())
        } else super.onReceive(ctx, i)
    }
}

class WLeyendo : WBase(WTipo.LEYENDO)
class WEstante : WBase(WTipo.ESTANTE)
class WCalendario : WBase(WTipo.CALENDARIO)
class WPortada : WBase(WTipo.PORTADA)
class WAno : WBase(WTipo.ANO)
class WCita : WBase(WTipo.CITA)
class WEfemeride : WBase(WTipo.EFEMERIDE)
class WMarca : WBase(WTipo.MARCA)
class WMarcaPapel : WBase(WTipo.MARCA_PAPEL)
class WSiguiente : WBase(WTipo.SIGUIENTE)
class WActividad : WBase(WTipo.ACTIVIDAD)

/** Una zona que se puede tocar (en dp, desde la esquina de arriba a la izquierda); con [celdas], una rejilla. */
class Zona(
    val x: Float, val y: Float, val w: Float, val h: Float, val toque: PendingIntent?, val desc: String = "",
    val celdas: List<List<Pair<PendingIntent?, String>>>? = null
) {
    fun vista(ctx: Context, ancho: Float, alto: Float, d: Float): RemoteViews {
        fun px(v: Float) = (max(0f, v) * d).roundToInt()
        val (l, t, r, b) = listOf(px(x), px(y), px(ancho - x - w), px(alto - y - h))
        val rejilla = celdas
        if (rejilla == null) {
            val v = RemoteViews(ctx.packageName, R.layout.w_zona)
            v.setViewPadding(R.id.w_hueco, l, t, r, b)
            toque?.let { v.setOnClickPendingIntent(R.id.w_toque, it) }
            if (desc.isNotEmpty()) v.setContentDescription(R.id.w_toque, desc)
            return v
        }
        val v = RemoteViews(ctx.packageName, R.layout.w_rejilla)
        v.setViewPadding(R.id.w_hueco, l, t, r, b)
        rejilla.forEach { fila ->
            val f = RemoteViews(ctx.packageName, R.layout.w_fila)
            fila.forEach { (pi, ds) ->
                val c = RemoteViews(ctx.packageName, R.layout.w_celda)
                pi?.let { c.setOnClickPendingIntent(R.id.w_celda, it) }
                if (ds.isNotEmpty()) c.setContentDescription(R.id.w_celda, ds)
                f.addView(R.id.w_fila, c)
            }
            v.addView(R.id.w_filas, f)
        }
        return v
    }
}

/** Un widget listo para dibujar: sus zonas y cómo se pinta (igual en claro que en oscuro). */
class Pagina(val zonas: List<Zona>, val dibujo: Hoja.() -> Unit) {
    /** Sin papel ni esquinas: solo lo dibujado, sobre el fondo de pantalla. */
    var sinFondo = false
    fun transparente() = apply { sinFondo = true }
    fun bitmap(ctx: Context, w: Float, h: Float, noche: Boolean, s: Float): Bitmap = Hoja(ctx, w, h, noche, s).apply { dibujo(); if (!sinFondo) terminar() }.bm
}

private val ES = Locale("es", "ES")

/** El lienzo de un widget: se dibuja en dp, con los colores y las letras de la revista. */
class Hoja(val ctx: Context, val w: Float, val h: Float, val noche: Boolean, s: Float) {
    val bm: Bitmap = Bitmap.createBitmap(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bm).apply { scale(bm.width / w, bm.height / h) }

    // El papel de la revista (Skin.kt, paleta «revista»)
    val papel = (if (noche) 0xFF141210 else 0xFFF3EEE4).toInt()
    val crudo = (if (noche) 0xFF1C1916 else 0xFFFAF6EE).toInt()
    val tinta = (if (noche) 0xFFF1EBE0 else 0xFF15120F).toInt()
    val gris = (if (noche) 0xFF9C9387 else 0xFF7C746A).toInt()
    val linea = (if (noche) 0xFF2E2924 else 0xFFD9D0C1).toInt()
    val arena = (if (noche) 0xFF221E1A else 0xFFE9E2D5).toInt()
    val acento = Widgets.acento(ctx, noche)
    /** El acento sobre una foto oscura (el claro se lee mejor). */
    val acentoFoto = Widgets.acento(ctx, true)
    val radio = 22f

    val fTitular: Typeface get() = Widgets.fuente(ctx, R.font.display_semibold)
    val fCursiva: Typeface get() = Widgets.fuente(ctx, R.font.display_italic)
    val fNegrita: Typeface get() = Widgets.fuente(ctx, R.font.body_bold)
    val fTexto: Typeface get() = Widgets.fuente(ctx, R.font.body_medium)

    /** Recorta las esquinas (suaves, con antialias). */
    fun terminar() {
        val p = Path().apply { addRoundRect(RectF(0f, 0f, w, h), radio, radio, Path.Direction.CW); fillType = Path.FillType.INVERSE_WINDING }
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
    }

    fun fondo(color: Int = papel) = c.drawColor(color)
    fun lleno(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    fun tp(f: Typeface, size: Float, color: Int, track: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = f; textSize = size; this.color = color; letterSpacing = track; textLocale = ES
        }

    fun capa(t: CharSequence, p: TextPaint, ancho: Float, lineas: Int = Int.MAX_VALUE, inter: Float = 1f, al: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, p, max(1, ancho.toInt())).setAlignment(al).setMaxLines(lineas)
            .setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, inter).setIncludePad(false).build()

    /** Pinta un texto ya compuesto; devuelve su alto. */
    fun pon(l: Layout, x: Float, y: Float): Float { c.save(); c.translate(x, y); l.draw(c); c.restore(); return l.height.toFloat() }

    /** Un titular tan grande como quepa (sin partir palabras) en [lineas] y [alto]. */
    fun titular(
        t: String, ancho: Float, alto: Float, maxS: Float, minS: Float, lineas: Int, color: Int = tinta,
        f: Typeface = fTitular, inter: Float = 0.95f, al: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        var s = maxS
        while (true) {
            val p = tp(f, s, color, -0.01f)
            val palabra = t.split(' ').maxOfOrNull { p.measureText(it) } ?: 0f
            val l = capa(t, p, ancho, Int.MAX_VALUE, inter, al)
            if (l.lineCount <= lineas && l.height <= alto && palabra <= ancho) return l
            if (s <= minS) return capa(t, p, ancho, max(1, min(lineas, (alto / (s * 1.15f)).toInt())), inter, al)
            s = max(minS, s - max(0.5f, s * 0.06f))
        }
    }

    /** Antetítulo: mayúsculas pequeñas, negras y espaciadas. Devuelve el alto. */
    fun ante(t: String, x: Float, y: Float, ancho: Float, color: Int = tinta, size: Float = 9f, al: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): Float =
        pon(capa(t.uppercase(ES), tp(fNegrita, size, color, 0.16f), ancho, 1, 1f, al), x, y)

    fun anchoAnte(t: String, size: Float = 9f) = tp(fNegrita, size, tinta, 0.16f).measureText(t.uppercase(ES))

    /** Cabecera de sección: el cuadradito de acento y el antetítulo (como «LA CITA» en la revista). */
    fun seccion(t: String, x: Float, y: Float, ancho: Float, color: Int = tinta, cuadro: Int = acento) {
        c.drawRect(x, y + 1.6f, x + 7.4f, y + 9f, lleno(cuadro))
        ante(t, x + 12f, y, ancho - 12f, color)
    }

    /** Una portada recortada (centrada) con esquinas redondas; sin imagen, una portada de letra. */
    fun portada(img: Bitmap?, r: RectF, nombre: String, rad: Float = 9f, filo: Boolean = true) {
        if (img == null || img.width <= 0) { tapa(r, nombre, rad); return }
        val s = max(r.width() / img.width, r.height() / img.height)
        val m = Matrix().apply { setScale(s, s); postTranslate(r.left + (r.width() - img.width * s) / 2, r.top + (r.height() - img.height * s) / 2) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(img, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) } }
        c.drawRoundRect(r, rad, rad, p)
        if (filo) c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = 0x26000000 })
    }

    /** Portada de letra (la de las fichas sin imagen): la inicial grande en acento y el nombre. */
    fun tapa(r: RectF, nombre: String, rad: Float = 9f) {
        c.drawRoundRect(r, rad, rad, lleno(arena))
        val ini = nombre.trim().firstOrNull()?.uppercase() ?: "·"
        val big = capa(ini, tp(fTitular, r.height() * 0.42f, acento), r.width(), 1, 1f, Layout.Alignment.ALIGN_CENTER)
        val conNombre = r.width() >= 56f && r.height() >= 70f
        pon(big, r.left, r.top + (r.height() - big.height) / 2 - if (conNombre) r.height() * 0.1f else 0f)
        if (conNombre) {
            val n = capa(nombre, tp(fTitular, min(11f, r.width() / 6.5f), tinta), r.width() - 12f, 2, 0.95f, Layout.Alignment.ALIGN_CENTER)
            pon(n, r.left + 6f, r.bottom - n.height - 8f)
        }
    }

    /** Barra de progreso fina. */
    fun barra(x: Float, y: Float, ancho: Float, f: Float, grosor: Float = 3f, fondo: Int = linea, color: Int = acento) {
        c.drawRoundRect(x, y, x + ancho, y + grosor, grosor / 2, grosor / 2, lleno(fondo))
        val v = f.coerceIn(0f, 1f)
        if (v > 0f) c.drawRoundRect(x, y, x + max(grosor, ancho * v), y + grosor, grosor / 2, grosor / 2, lleno(color))
    }

    /** Degradado vertical (para leer encima de una foto). */
    fun velo(top: Float, bottom: Float, de: Int, a: Int, medio: Int? = null) {
        val cs = if (medio != null) intArrayOf(de, medio, a) else intArrayOf(de, a)
        val p = Paint().apply { shader = LinearGradient(0f, top, 0f, bottom, cs, null, Shader.TileMode.CLAMP) }
        c.drawRect(0f, top, w, bottom, p)
    }

    /** «2/5 ›»: hay más, y tocando aquí se pasa al siguiente. */
    fun paginas(i: Int, n: Int, xDer: Float, y: Float, color: Int = gris) {
        if (n < 2) return
        ante("${i + 1}/$n  ›", xDer - 80f, y, 80f, color, 9f, Layout.Alignment.ALIGN_OPPOSITE)
    }
}

/** Lo que hay guardado en la app (lo de cada categoría, como lo ve la revista). */
class WDatos(val ctx: Context) {
    val store = Store(ctx)
    val kinds: List<Kind> = listOf(GAMES, BOOKS) + runCatching { store.kinds }.getOrDefault(emptyList())
    private val cache = HashMap<Int, List<ShellEntry>>()
    fun de(k: Kind): List<ShellEntry> = cache.getOrPut(k.id) {
        store.loadPlays(k.id).map { ShellEntry(k, it, null) } + store.load(k.id).map { ShellEntry(k, null, it) }
    }
    val todas: List<ShellEntry> get() = kinds.flatMap { de(it) }

    /** Cuándo se leyó por última vez (el lector lo apunta) o se tocó la ficha. */
    fun reciente(e: ShellEntry): Long = max(e.activity, if (e.kind.isBook) BookFiles.state(ctx, e.id).optLong("read", 0L) else 0L)
    fun enCurso(): List<ShellEntry> = todas.filter { it.play?.status == 1 }.sortedByDescending { reciente(it) }
    fun conArchivo(e: ShellEntry) = e.kind.isBook && BookFiles.get(ctx, e.id) != null

    /** Lo leído o jugado (0–1), si se sabe. */
    fun avance(e: ShellEntry): Float? {
        if (conArchivo(e)) return BookFiles.progress(ctx, e.id)
        return e.play?.percent()?.let { it / 100f }
    }

    /** «LEYENDO · 43 %», «JUGANDO · 12 H», «PASADO · NOTA 87»… */
    fun estado(e: ShellEntry): String {
        val extra = when {
            e.play == null -> e.score?.let { "nota $it" }
            avance(e) != null && (e.kind.isBook || e.play.parts.isNotEmpty()) -> "${((avance(e) ?: 0f) * 100).roundToInt()} %"
            e.kind.isGame && e.minutes >= 60 -> "${e.minutes / 60} h"
            else -> avance(e)?.let { "${(it * 100).roundToInt()} %" }
        }
        return listOfNotNull(e.status, extra).joinToString(" · ")
    }
}

object Widgets {
    const val ACCION = "com.gonzalo.gamerate.WIDGET"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var pendiente: Job? = null
    private val fuentes = HashMap<Int, Typeface>()

    fun clase(t: WTipo): Class<out AppWidgetProvider> = when (t) {
        WTipo.LEYENDO -> WLeyendo::class.java
        WTipo.ESTANTE -> WEstante::class.java
        WTipo.CALENDARIO -> WCalendario::class.java
        WTipo.PORTADA -> WPortada::class.java
        WTipo.ANO -> WAno::class.java
        WTipo.CITA -> WCita::class.java
        WTipo.EFEMERIDE -> WEfemeride::class.java
        WTipo.MARCA -> WMarca::class.java
        WTipo.MARCA_PAPEL -> WMarcaPapel::class.java
        WTipo.SIGUIENTE -> WSiguiente::class.java
        WTipo.ACTIVIDAD -> WActividad::class.java
    }

    @Synchronized fun fuente(ctx: Context, id: Int): Typeface =
        fuentes.getOrPut(id) { runCatching { ctx.resources.getFont(id) }.getOrDefault(Typeface.SERIF) }

    /** El acento elegido en Estilo (en oscuro, su versión clara). */
    fun acento(ctx: Context, noche: Boolean): Int {
        val id = ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE).getString("skin_accent_id", "rojo")
        val a = ACCENT_COLORS.firstOrNull { it.id == id } ?: ACCENT_COLORS.first()
        return (if (noche) a.dark else a.light).toInt()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private fun estado(ctx: Context, id: Int, k: String, def: Int) = prefs(ctx).getInt("${id}_$k", def)

    /** Lo que se toca dentro del widget: el siguiente, otro mes, otra categoría. */
    fun accion(ctx: Context, id: Int, op: String) {
        val p = prefs(ctx)
        val ahora = mesDe(LocalDate.now())
        val ed = p.edit()
        when (op) {
            "sig" -> ed.putInt("${id}_i", p.getInt("${id}_i", 0) + 1)
            "cat" -> ed.putInt("${id}_k", p.getInt("${id}_k", 0) + 1)
            "mes-" -> ed.putInt("${id}_m", p.getInt("${id}_m", ahora) - 1)
            "mes+" -> ed.putInt("${id}_m", p.getInt("${id}_m", ahora) + 1)
            "hoy" -> ed.remove("${id}_m")
        }
        ed.apply()
    }

    fun olvidar(ctx: Context, ids: IntArray) {
        val ed = prefs(ctx).edit()
        ids.forEach { id -> listOf("i", "k", "m").forEach { ed.remove("${id}_$it") } }
        ed.apply()
    }

    private fun mesDe(d: LocalDate) = d.year * 12 + d.monthValue - 1

    /** Repinta esos widgets (en segundo plano: las portadas pueden venir de internet). */
    fun pintar(ctx: Context, tipo: WTipo, ids: IntArray, res: BroadcastReceiver.PendingResult?) {
        val app = ctx.applicationContext
        scope.launch {
            try {
                withTimeoutOrNull(9_000) { ids.forEach { id -> try { pintarUno(app, tipo, id) } catch (e: CancellationException) { throw e } catch (e: Exception) { } } }
            } finally { res?.finish() }
        }
    }

    /** Algo cambió en la app (una ficha, lo leído, el estilo): se repintan los widgets que haya, sin prisa. */
    fun pedir(ctx: Context) {
        val app = ctx.applicationContext
        val mgr = runCatching { AppWidgetManager.getInstance(app) }.getOrNull() ?: return
        val hay = WTipo.entries.mapNotNull { t ->
            runCatching { mgr.getAppWidgetIds(ComponentName(app, clase(t))) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { t to it }
        }
        if (hay.isEmpty()) return
        pendiente?.cancel()
        pendiente = scope.launch {
            delay(1_200)
            hay.forEach { (t, ids) -> ids.forEach { id -> try { pintarUno(app, t, id) } catch (e: CancellationException) { throw e } catch (e: Exception) { } } }
        }
    }

    suspend fun pintarUno(ctx: Context, tipo: WTipo, id: Int) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val (w, h) = tamano(ctx, mgr.getAppWidgetOptions(id), tipo)
        mgr.updateAppWidget(id, vistas(ctx, tipo, id, w, h))
    }

    /** Claro, oscuro, o null = como el sistema (se mandan los dos y el sistema elige). */
    fun noche(ctx: Context): Boolean? = when (Store(ctx).themeMode) { 1 -> false; 2 -> true; else -> null }

    /** El tamaño del widget en dp (en vertical: el ancho mínimo y el alto máximo, como hace Android). */
    fun tamano(ctx: Context, o: Bundle?, tipo: WTipo): Pair<Float, Float> {
        val vertical = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val w = o?.getInt(if (vertical) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
        val h = o?.getInt(if (vertical) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val (dw, dh) = porDefecto(tipo)
        return (if (w > 40) w.toFloat() else dw) to (if (h > 40) h.toFloat() else dh)
    }

    fun porDefecto(t: WTipo): Pair<Float, Float> = when (t) {
        WTipo.CALENDARIO -> 330f to 340f
        WTipo.ANO -> 165f to 165f
        WTipo.PORTADA -> 330f to 250f
        WTipo.MARCA -> 330f to 80f
        WTipo.MARCA_PAPEL -> 330f to 165f
        else -> 330f to 165f
    }

    /** Píxeles por dp del dibujo: los de la pantalla, sin pasarse de memoria (Android limita lo que pesa un widget). */
    fun escala(ctx: Context, w: Float, h: Float, copias: Int): Float {
        val d = ctx.resources.displayMetrics.density
        val tope = if (copias == 2) 1_100_000f else 1_800_000f
        return min(d, sqrt(tope / (w * h)))
    }

    suspend fun vistas(ctx: Context, tipo: WTipo, id: Int, w: Float, h: Float): RemoteViews {
        val pag = componer(ctx, tipo, id, w, h)
        val rv = RemoteViews(ctx.packageName, R.layout.w_marco)
        when (val n = noche(ctx)) {
            null -> {
                val s = escala(ctx, w, h, 2)
                rv.setImageViewBitmap(R.id.w_dia, pag.bitmap(ctx, w, h, false, s))
                rv.setImageViewBitmap(R.id.w_noche, pag.bitmap(ctx, w, h, true, s))
            }
            else -> rv.setImageViewBitmap(R.id.w_img, pag.bitmap(ctx, w, h, n, escala(ctx, w, h, 1)))
        }
        val d = ctx.resources.displayMetrics.density
        pag.zonas.forEach { rv.addView(R.id.w_zonas, it.vista(ctx, w, h, d)) }
        return rv
    }

    suspend fun componer(ctx: Context, tipo: WTipo, id: Int, w: Float, h: Float): Pagina {
        val d = WDatos(ctx)
        return when (tipo) {
            WTipo.LEYENDO -> leyendo(d, id, w, h)
            WTipo.ESTANTE -> estante(d, w, h)
            WTipo.CALENDARIO -> calendario(d, id, w, h)
            WTipo.PORTADA -> enPortada(d, id, w, h)
            WTipo.ANO -> ano(d, w, h)
            WTipo.CITA -> cita(d, id, w, h)
            WTipo.EFEMERIDE -> efemeride(d, id, w, h)
            WTipo.MARCA -> marca(d, w, h)
            WTipo.MARCA_PAPEL -> marcaPapel(d, w, h)
            WTipo.SIGUIENTE -> siguiente(d, id, w, h)
            WTipo.ACTIVIDAD -> actividad(d, w, h)
        }
    }

    // ------------------------------------------------------------------ toques

    private const val PI = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun op(ctx: Context, tipo: WTipo, id: Int, op: String): PendingIntent {
        val i = Intent(ctx, clase(tipo)).setAction(ACCION).setData(Uri.parse("atrio://w/$id/$op"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("op", op)
        return PendingIntent.getBroadcast(ctx, id, i, PI)
    }

    /** Abre la app: "item:<categoría>:<id>" (una ficha), "diario:<categoría>", "add_book", "stats_game"… */
    fun abrir(ctx: Context, go: String): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("atrio://abrir/" + Uri.encode(go)))
            .putExtra("go", go).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 0, i, PI)
    }

    /** Abre el libro directamente en el lector (en [goto], si es un subrayado). */
    fun leer(ctx: Context, id: Long, nombre: String, goto: String? = null): PendingIntent {
        val i = Intent(ctx, ReaderActivity::class.java).setData(Uri.parse("atrio://leer/$id/" + Uri.encode(goto.orEmpty())))
            .putExtra("id", id).putExtra("name", nombre).putExtra("goto", goto)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 0, i, PI)
    }

    /** Un libro con archivo se abre para leerlo; lo demás, en su ficha. */
    fun tocar(d: WDatos, e: ShellEntry): PendingIntent =
        if (d.conArchivo(e)) leer(d.ctx, e.id, e.name) else abrir(d.ctx, "item:${e.kind.id}:${e.id}")

    // ------------------------------------------------------------------ imágenes

    /** Una portada a unos [lado] píxeles (de la app o de internet; si no llega a tiempo, null). */
    suspend fun imagen(ctx: Context, url: String, lado: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        try {
            if (url.startsWith("file:") || url.startsWith("/")) {
                val path = (if (url.startsWith("/")) url else Uri.parse(url).path) ?: return@withContext null
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, o)
                if (o.outWidth <= 0) return@withContext null
                var s = 1
                while (min(o.outWidth, o.outHeight) / (s * 2) >= lado) s *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
            } else withTimeoutOrNull(4_500) {
                val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).size(lado).build())
                ((r as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
    }

    private suspend fun imagenes(ctx: Context, urls: List<String>, lado: Int): List<Bitmap?> =
        coroutineScope { urls.map { u -> async { imagen(ctx, u, lado) } }.awaitAll() }

    private fun px(ctx: Context, dp: Float) = (dp * ctx.resources.displayMetrics.density).roundToInt().coerceIn(32, 1400)

    // ------------------------------------------------------------------ Leyendo

    private suspend fun leyendo(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val libros = d.kinds.filter { it.isBook }.flatMap { d.de(it) }.filter { it.play?.status == 1 }.sortedByDescending { d.reciente(it) }
        if (libros.isEmpty()) return vacio(ctx, "Leyendo", "Nada en la mesilla", "Empieza un libro y lo tendrás aquí, a un toque de seguir leyendo.", abrir(ctx, "add_book"), w, h)
        val i = Math.floorMod(estado(ctx, id, "i", 0), libros.size)
        val e = libros[i]
        val compacto = w < 200f && h < 200f
        val img = imagen(ctx, e.cover, px(ctx, if (compacto) max(w, h) else h * 0.9f))
        val av = d.avance(e)
        val archivo = d.conArchivo(e)
        val accion = if (archivo) "Leer  →" else "Abrir ficha  →"
        val zonas = mutableListOf(Zona(0f, 0f, w, h, tocar(d, e), (if (archivo) "Seguir leyendo " else "Abrir ") + e.name))
        if (libros.size > 1) zonas += Zona(w - 72f, 0f, 72f, 42f, op(ctx, WTipo.LEYENDO, id, "sig"), "Otro libro")
        return Pagina(zonas) {
            val pad = 14f
            when {
                compacto -> {
                    // La portada a sangre y el título encima
                    portada(img, RectF(0f, 0f, w, h), e.name, 0f, false)
                    velo(h * 0.2f, h, 0x00000000, 0xF0000000.toInt(), 0x88000000.toInt())
                    velo(0f, 52f, 0xB0000000.toInt(), 0x00000000)
                    seccion("Leyendo", pad, pad, w - 2 * pad, 0xFFFFFFFF.toInt(), acentoFoto)
                    paginas(i, libros.size, w - pad, pad, 0xCCFFFFFF.toInt())
                    val bottom = h - pad - 4f
                    barra(pad, bottom, w - 2 * pad, av ?: 0f, 3f, 0x55FFFFFF, acentoFoto)
                    val l = titular(e.name, w - 2 * pad, h * 0.42f, 24f, 12f, 3, 0xFFFFFFFF.toInt())
                    pon(l, pad, bottom - 8f - l.height)
                }
                h > w * 1.15f -> {
                    // Alto: portada arriba a la izquierda y lo leído en grande al lado
                    fondo()
                    seccion("Leyendo", pad, pad, w - 2 * pad - 50f)
                    paginas(i, libros.size, w - pad, pad)
                    val top = pad + 20f
                    val ch = h * 0.5f - top
                    val cw = min(ch * 0.66f, w * 0.55f)
                    portada(img, RectF(pad, top, pad + cw, top + ch), e.name)
                    val x0 = pad + cw + 10f
                    if (av != null) {
                        val n = capa("${(av * 100).roundToInt()}%", tp(fTitular, min(40f, (w - x0 - pad) / 2.2f), acento), w - x0 - pad + 4f, 1)
                        pon(n, x0, top + ch - n.height)
                    }
                    var y = top + ch + 12f
                    val autor = if (e.creator.isNotBlank()) 16f else 0f
                    val l = titular(e.name, w - 2 * pad, h - y - pad - 26f - autor, 30f, 13f, 4)
                    y += pon(l, pad, y) + 3f
                    if (e.creator.isNotBlank()) pon(capa(e.creator, tp(fCursiva, 12f, gris), w - 2 * pad, 1), pad, y)
                    ante(accion, pad, h - pad - 18f, w - 2 * pad, acento)
                    barra(pad, h - pad - 3f, w - 2 * pad, av ?: 0f)
                }
                else -> {
                    // Ancho: portada a la izquierda, titular grande a la derecha
                    fondo()
                    val ch = h - 2 * pad
                    val cw = min(ch * 0.66f, w * 0.36f)
                    portada(img, RectF(pad, pad, pad + cw, pad + ch), e.name)
                    val x0 = pad + cw + 14f
                    val rw = w - x0 - pad
                    seccion("Leyendo", x0, pad + 1f, rw)
                    paginas(i, libros.size, w - pad, pad + 1f)
                    val pie = 30f
                    val autor = if (e.creator.isNotBlank() && h >= 130f) 17f else 0f
                    val y0 = pad + 20f
                    val l = titular(e.name, rw, h - y0 - pad - pie - autor, (h * 0.19f).coerceIn(30f, 46f), 13f, 3)
                    pon(l, x0, y0)
                    if (autor > 0f) pon(capa(e.creator, tp(fCursiva, 12.5f, gris), rw, 1), x0, y0 + l.height + 4f)
                    val pct = av?.let { "${(it * 100).roundToInt()} %" }.orEmpty()
                    ante(accion, x0, h - pad - 17f, rw, acento)
                    if (pct.isNotEmpty()) ante(pct, x0, h - pad - 17f, rw, gris, 9f, Layout.Alignment.ALIGN_OPPOSITE)
                    barra(x0, h - pad - 3f, rw, av ?: 0f)
                }
            }
        }
    }

    /** Un widget sin nada que enseñar todavía: lo dice con la misma letra y lleva a donde empezar. */
    private fun vacio(ctx: Context, sec: String, titulo: String, texto: String, toque: PendingIntent, w: Float, h: Float): Pagina =
        Pagina(listOf(Zona(0f, 0f, w, h, toque, titulo))) {
            fondo()
            val pad = 16f
            seccion(sec, pad, pad, w - 2 * pad)
            val l = titular(titulo, w - 2 * pad, h * 0.42f, 28f, 13f, 3)
            pon(l, pad, pad + 22f)
            val t = capa(texto, tp(fTexto, 11.5f, gris), w - 2 * pad, max(1, ((h - pad * 2 - 30f - l.height) / 15f).toInt()), 1.05f)
            pon(t, pad, pad + 28f + l.height)
        }

    // ------------------------------------------------------------------ En curso (estantería)

    private suspend fun estante(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val pad = 14f
        val todas = d.enCurso()
        if (todas.isEmpty()) return vacio(ctx, "En curso", "Nada empezado", "Lo que juegues, leas o veas aparecerá aquí en fila, como en una estantería.", abrir(ctx, "home"), w, h)
        val bajo = h < 150f
        val n = ((w - 2 * pad) / (if (bajo) 140f else 78f)).toInt().coerceIn(1, 6).coerceAtMost(todas.size)
        val items = todas.take(n)
        val slot = (w - 2 * pad) / n
        val top = pad + 22f
        val textos = if (bajo) 0f else 44f
        val ch = h - top - pad - textos
        val imgs = imagenes(ctx, items.map { it.cover }, px(ctx, ch))
        val zonas = listOf(
            Zona(0f, 0f, w, top, abrir(ctx, "home"), "Abrir Atrio"),
            Zona(pad, top, w - 2 * pad, h - top, null, "", listOf(items.map { tocar(d, it) to it.name }))
        )
        return Pagina(zonas) {
            fondo()
            seccion("En curso", pad, pad, w - 2 * pad)
            pon(capa("Atrio", tp(fTitular, 13f, tinta), 80f, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE), w - pad - 80f, pad - 3f)
            c.drawRect(pad, pad + 14.5f, w - pad, pad + 15.2f, lleno(linea))
            items.forEachIndexed { k, e ->
                val gap = 5f
                val x = pad + slot * k + gap
                val cw = min(slot - 2 * gap, ch * 0.68f)
                portada(imgs[k], RectF(x, top, x + cw, top + ch), e.name, 8f)
                if (bajo) {
                    val tx = x + cw + 8f
                    val tw = slot - 2 * gap - cw - 8f
                    val est = capa(d.estado(e).uppercase(ES), tp(fNegrita, 7.5f, acento, 0.16f), tw, 2)
                    val l = titular(e.name, tw, ch - est.height - 4f, 15f, 10f, 3)
                    pon(l, tx, top)
                    pon(est, tx, top + ch - est.height)
                } else {
                    val tw = slot - 2 * gap
                    val l = capa(e.name, tp(fTitular, 12f, tinta, -0.01f), tw, 2, 0.95f)
                    pon(l, x, top + ch + 5f)
                    ante(d.estado(e), x, top + ch + 7f + l.height, tw, acento, 7.5f)
                }
            }
        }
    }

    // ------------------------------------------------------------------ Calendario

    private class Dia(val kind: Kind, val item: CalItem)

    private suspend fun calendario(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val cats: List<Kind?> = listOf<Kind?>(null) + d.kinds
        val cat = cats[Math.floorMod(estado(ctx, id, "k", 0), cats.size)]
        val hoy = LocalDate.now()
        val m = estado(ctx, id, "m", mesDe(hoy))
        val primero = LocalDate.of(m / 12, m % 12 + 1, 1)
        val dias = primero.lengthOfMonth()
        val hueco = primero.dayOfWeek.value - 1 // lunes primero
        val filas = (hueco + dias + 6) / 7
        val ks = if (cat == null) d.kinds else listOf(cat)
        val items = ks.flatMap { k -> calItems(d.store.load(k.id), d.store.loadPlays(k.id)).map { Dia(k, it) } }
        val e0 = primero.toEpochDay()
        val e1 = e0 + dias - 1
        val terminados = items.filter { it.item.realEnd in e0..e1 }.groupBy { it.item.realEnd }
        val durante = items.filter { it.item.s <= e1 && it.item.e >= e0 }

        val pad = 12f
        val cab = 34f
        val sem = 16f
        val y0 = pad + cab + 6f + sem
        val cw = (w - 2 * pad) / 7
        val chh = (h - pad - y0) / filas
        val lado = px(ctx, max(cw, chh))
        val portadas = terminados.mapValues { (_, l) -> l.maxByOrNull { it.item.score }!! }
        val imgs = imagenes(ctx, portadas.values.map { it.item.cover }, lado).let { b -> portadas.keys.zip(b).toMap() }

        fun entrada(x: Dia): ShellEntry? = if (x.item.isPlay) d.store.loadPlays(x.kind.id).find { it.id == x.item.id }?.let { ShellEntry(x.kind, it, null) }
            else d.store.load(x.kind.id).find { it.id == x.item.id }?.let { ShellEntry(x.kind, null, it) }
        val diario = abrir(ctx, "diario:${cat?.id ?: 0}")
        val rejilla = (0 until filas).map { f ->
            (0 until 7).map { c ->
                val dia = f * 7 + c - hueco + 1
                if (dia !in 1..dias) null to ""
                else {
                    val ep = e0 + dia - 1
                    val top = portadas[ep]?.let { entrada(it) }
                    if (top != null) tocar(d, top) to "$dia: ${top.name}" else diario to ""
                }
            }
        }
        val mesNombre = primero.month.getDisplayName(TextStyle.FULL_STANDALONE, ES).replaceFirstChar { it.titlecase(ES) }
        val catNombre = (cat?.title ?: "Todo").uppercase(ES)
        val zonas = listOf(
            Zona(0f, 0f, w, h, diario, "Abrir el Diario"),
            Zona(0f, 0f, pad + 34f, pad + cab, op(ctx, WTipo.CALENDARIO, id, "mes-"), "Mes anterior"),
            Zona(pad + 34f, 0f, w * 0.5f - pad - 34f, pad + cab, op(ctx, WTipo.CALENDARIO, id, "hoy"), "Volver a este mes"),
            Zona(w * 0.5f, 0f, w * 0.5f - pad - 34f, pad + cab, op(ctx, WTipo.CALENDARIO, id, "cat"), "Cambiar de categoría ($catNombre)"),
            Zona(w - pad - 34f, 0f, pad + 34f, pad + cab, op(ctx, WTipo.CALENDARIO, id, "mes+"), "Mes siguiente"),
            Zona(pad, y0, w - 2 * pad, h - pad - y0, null, "", rejilla)
        )
        return Pagina(zonas) {
            fondo()
            // Cabecera: ‹ Octubre 2026 · [CATEGORÍA] ›
            fun flecha(cx: Float, t: String) {
                c.drawCircle(cx, pad + cab / 2, 12f, lleno(arena))
                val l = capa(t, tp(fTitular, 17f, tinta), 24f, 1, 1f, Layout.Alignment.ALIGN_CENTER)
                pon(l, cx - 12f, pad + cab / 2 - l.height / 2 - 1f)
            }
            flecha(pad + 12f, "‹")
            flecha(w - pad - 12f, "›")
            val pillW = min(w * 0.4f, anchoAnte(catNombre, 8.5f) + 22f)
            val px0 = w - pad - 32f - pillW
            val pr = RectF(px0, pad + cab / 2 - 11f, px0 + pillW, pad + cab / 2 + 11f)
            c.drawRoundRect(pr, 11f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = acento })
            val pl = capa(catNombre, tp(fNegrita, 8.5f, acento, 0.16f), pillW - 14f, 1, 1f, Layout.Alignment.ALIGN_CENTER)
            pon(pl, px0 + 7f, pr.centerY() - pl.height / 2)
            val tx = pad + 32f
            val tw = px0 - tx - 6f
            val t = titular("$mesNombre ${primero.year}", tw, cab, 24f, 13f, 1)
            pon(t, tx, pad + cab / 2 - t.height / 2)
            // Días de la semana
            listOf("L", "M", "X", "J", "V", "S", "D").forEachIndexed { k, s ->
                ante(s, pad + cw * k, pad + cab + 6f, cw, if (k >= 5) acento else gris, 8.5f, Layout.Alignment.ALIGN_CENTER)
            }
            val g = 1.6f
            for (f in 0 until filas) for (k in 0 until 7) {
                val dia = f * 7 + k - hueco + 1
                if (dia !in 1..dias) continue
                val ep = e0 + dia - 1
                val r = RectF(pad + cw * k + g, y0 + chh * f + g, pad + cw * (k + 1) - g, y0 + chh * (f + 1) - g)
                val esHoy = ep == hoy.toEpochDay()
                val p = portadas[ep]
                if (p != null) {
                    portada(imgs[ep], r, p.item.name, 6f, false)
                    // El número en una etiqueta de papel
                    val nl = capa("$dia", tp(fNegrita, 8f, if (esHoy) papel else tinta), 20f, 1, 1f, Layout.Alignment.ALIGN_CENTER)
                    val et = RectF(r.left + 2.5f, r.top + 2.5f, r.left + 2.5f + 15f, r.top + 2.5f + 11.5f)
                    c.drawRoundRect(et, 4f, 4f, lleno(if (esHoy) acento else papel))
                    pon(nl, et.left - 2.5f, et.centerY() - nl.height / 2)
                    val mas = (terminados[ep]?.size ?: 1) - 1
                    if (mas > 0) {
                        val ml = capa("+$mas", tp(fNegrita, 7.5f, 0xFFFFFFFF.toInt()), 20f, 1, 1f, Layout.Alignment.ALIGN_CENTER)
                        val mr = RectF(r.right - 19f, r.bottom - 13f, r.right - 2.5f, r.bottom - 2.5f)
                        c.drawRoundRect(mr, 4f, 4f, lleno(acento))
                        pon(ml, mr.left - 1.75f, mr.centerY() - ml.height / 2)
                    }
                    if (esHoy) c.drawRoundRect(r, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = acento })
                } else {
                    val n = durante.count { it.item.s <= ep && it.item.e >= ep }
                    if (esHoy) c.drawCircle(r.centerX(), r.top + min(r.height(), 26f) / 2 + 1f, min(11f, r.width() / 2.4f), lleno(acento))
                    val nl = capa("$dia", tp(fTitular, min(13f, r.height() * 0.42f), if (esHoy) papel else if (n > 0) tinta else gris), r.width(), 1, 1f, Layout.Alignment.ALIGN_CENTER)
                    pon(nl, r.left, r.top + min(r.height(), 26f) / 2 - nl.height / 2 + 1f)
                    // Lo que tenías empezado ese día: rayitas de acento
                    if (n > 0) {
                        val bw = min(r.width() * 0.5f, 14f)
                        repeat(min(n, 3)) { j ->
                            val y = min(r.bottom - 4f - j * 3.2f, r.top + 26f + j * 3.2f)
                            c.drawRoundRect(r.centerX() - bw / 2, y, r.centerX() + bw / 2, y + 1.8f, 1f, 1f, lleno(if (j == 0) acento else linea))
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Portada (la de la revista)

    private suspend fun enPortada(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val hace = LocalDate.now().toEpochDay() - 90
        val items = (d.enCurso() + d.todas.filter { (it.game?.end ?: 0L) >= hace }.sortedByDescending { it.game?.end ?: 0L }).distinctBy { it.kind.id to it.id }
        if (items.isEmpty()) return vacio(ctx, "En portada", "Tu revista empieza aquí", "Valora o empieza algo y saldrá en la portada.", abrir(ctx, "home"), w, h)
        val i = Math.floorMod(estado(ctx, id, "i", 0), items.size)
        val e = items[i]
        val img = imagen(ctx, e.hero.ifBlank { e.cover }, px(ctx, max(w, h)))
            ?: imagen(ctx, e.cover, px(ctx, max(w, h)))
        val zonas = mutableListOf(Zona(0f, 0f, w, h, tocar(d, e), e.name))
        if (items.size > 1) zonas += Zona(w - 80f, 0f, 80f, 52f, op(ctx, WTipo.PORTADA, id, "sig"), "Siguiente")
        val fecha = LocalDate.now().let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')} ${it.year}" }
        return Pagina(zonas) {
            val pad = 16f
            val blanco = 0xFFFFFFFF.toInt()
            if (img != null) {
                portada(img, RectF(0f, 0f, w, h), e.name, 0f, false)
                velo(0f, min(96f, h * 0.42f), 0xC8000000.toInt(), 0x00000000, 0x70000000)
                velo(h * 0.18f, h, 0x00000000, 0xF2000000.toInt(), 0x99000000.toInt())
            } else { fondo(0xFF15120F.toInt()) }
            // Cabecera de la revista
            val mast = capa("Atrio", tp(fTitular, if (h < 180f) 22f else 28f, blanco, -0.02f), w * 0.6f, 1)
            pon(mast, pad, pad - 4f)
            val fw = w - 2 * pad - mast.getLineWidth(0) - 8f
            ante(if (fw < anchoAnte(fecha, 8.5f)) fecha.substringBeforeLast(' ') else fecha, w - pad - fw, pad, fw, 0xDDFFFFFF.toInt(), 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
            paginas(i, items.size, w - pad, pad + 13f, 0xBBFFFFFF.toInt())
            c.drawRect(pad, pad + mast.height + 1f, w - pad, pad + mast.height + 1.8f, lleno(0x66FFFFFF))
            // El titular
            val meta = e.creator.takeIf { it.isNotBlank() && h >= 170f }
            var y = h - pad
            if (meta != null) { val ml = capa(meta, tp(fCursiva, 12.5f, 0xCCFFFFFF.toInt()), w - 2 * pad, 1); y -= ml.height; pon(ml, pad, y); y -= 4f }
            val l = titular(e.name, w - 2 * pad, h * 0.46f, if (w > 280f) 40f else 30f, 14f, 3, blanco, inter = 0.92f)
            y -= l.height
            pon(l, pad, y)
            // El estado, en una etiqueta de acento (se lee sobre cualquier foto)
            val est = d.estado(e)
            val ew = min(w - 2 * pad, anchoAnte(est, 8.5f) + 12f)
            c.drawRoundRect(pad, y - 19f, pad + ew, y - 4f, 4f, 4f, lleno(acento))
            ante(est, pad + 6f, y - 16.5f, ew - 10f, 0xFFFFFFFF.toInt(), 8.5f)
        }
    }

    // ------------------------------------------------------------------ Tu año

    private suspend fun ano(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val y = LocalDate.now().year
        fun delAno(k: Kind) = d.store.load(k.id).filter { it.end > 0L && LocalDate.ofEpochDay(it.end).year == y }
        val filas = d.kinds.map { k -> Triple(k, delAno(k).size, if (k.id <= 1) d.store.goal(k.id, y) else 0) }
            .filter { it.second > 0 || it.third > 0 || it.first.id <= 1 }
        val ancho = w >= 250f
        val ultimos = if (ancho) d.kinds.flatMap { k -> delAno(k).map { ShellEntry(k, null, it) } }.sortedByDescending { it.game!!.end }.take(3) else emptyList()
        val imgs = imagenes(ctx, ultimos.map { it.cover }, px(ctx, h * 0.7f))
        return Pagina(listOf(Zona(0f, 0f, w, h, abrir(ctx, "stats_game"), "Tus cifras de $y"))) {
            fondo()
            val pad = 14f
            val colW = if (ancho) w * 0.56f - pad else w - 2 * pad
            seccion("Tu año", pad, pad, colW)
            val big = capa("$y", tp(fTitular, if (h < 150f) 28f else 36f, acento, -0.02f), colW, 1)
            pon(big, pad, pad + 13f)
            var yy = pad + 15f + big.height + 6f
            val cabe = ((h - yy - pad + 4f) / 34f).toInt().coerceAtLeast(1)
            val muestra = filas.sortedByDescending { it.second + it.third }.take(cabe)
            muestra.forEach { (k, n, goal) ->
                val num = capa("$n", tp(fTitular, 24f, tinta), 60f, 1)
                pon(num, pad, yy)
                var x1 = pad + num.getLineWidth(0) + 2f
                if (goal > 0) { val g = capa("/$goal", tp(fTitular, 12f, gris), 50f, 1); pon(g, x1, yy + num.height - g.height - 3f); x1 += g.getLineWidth(0) }
                x1 = max(pad + 30f, x1 + 8f)
                ante(k.title, x1, yy + 3f, colW - (x1 - pad), gris, 8.5f)
                if (goal > 0) barra(x1, yy + 17f, colW - (x1 - pad), n / goal.toFloat(), 3f)
                else c.drawRect(x1, yy + 18f, pad + colW, yy + 18.6f, lleno(linea))
                yy += 34f
            }
            if (ancho) {
                val x0 = w * 0.56f + 6f
                val rw = w - pad - x0
                if (ultimos.isNotEmpty()) {
                    val ch = h - 2 * pad - 22f
                    val cw = min(ch * 0.68f, rw * 0.62f)
                    // Las tres últimas, una encima de otra, como revistas sobre la mesa
                    ultimos.indices.reversed().forEach { k ->
                        val off = (rw - cw) / 2f * k
                        val r = RectF(x0 + off, pad + k * 6f, x0 + off + cw, pad + k * 6f + ch - 12f)
                        c.drawRoundRect(RectF(r.left - 1.5f, r.top - 1.5f, r.right + 1.5f, r.bottom + 1.5f), 9f, 9f, lleno(papel))
                        portada(imgs[k], r, ultimos[k].name, 8f)
                    }
                }
                if (ultimos.isEmpty()) ante("Nada terminado aún", x0, h - pad - 11f, rw, gris, 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
            }
        }
    }

    // ------------------------------------------------------------------ La cita

    private fun cita(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val todas = citas(ctx, d.kinds.filter { it.isBook }.flatMap { d.de(it) })
        if (todas.isEmpty()) return vacio(ctx, "La cita", "Subraya y saldrá aquí", "Las frases que subrayes en el lector se irán turnando aquí, una cada día.", abrir(ctx, "diario:1"), w, h)
        // Una distinta cada día (y la que quieras tocando «Otra»)
        val i = Math.floorMod(LocalDate.now().toEpochDay() + estado(ctx, id, "i", 0), todas.size.toLong()).toInt()
        val q = todas[i]
        val zonas = mutableListOf(Zona(0f, 0f, w, h, leer(ctx, q.bookId, q.bookName, q.note.value), "Abrir ${q.bookName} en esta cita"))
        if (todas.size > 1) zonas += Zona(w - 84f, 0f, 84f, 44f, op(ctx, WTipo.CITA, id, "sig"), "Otra cita")
        return Pagina(zonas) {
            fondo(crudo)
            val pad = 16f
            seccion("La cita", pad, pad, w - 2 * pad)
            if (todas.size > 1) ante("Otra  ›", w - pad - 80f, pad, 80f, acento, 9f, Layout.Alignment.ALIGN_OPPOSITE)
            val comilla = capa("«", tp(fTitular, 46f, acento), 40f, 1)
            val firma = ("— " + listOf(q.bookName, q.author).filter { it.isNotBlank() }.joinToString(", ")).uppercase(ES)
            val fl = capa(firma, tp(fNegrita, 8.5f, gris, 0.14f), w - 2 * pad, 2)
            val y0 = pad + 18f
            val x0 = pad + 26f
            val hueco = h - y0 - pad - fl.height - 8f
            val l = titular(q.note.text, w - x0 - pad, hueco, 34f, 10.5f, 12, tinta, fCursiva, 1.02f)
            val yq = y0 + max(0f, (hueco - l.height) / 2.5f)
            pon(l, x0, yq)
            pon(comilla, pad - 2f, yq - 8f)
            pon(fl, x0, min(h - pad - fl.height, yq + l.height + 8f))
        }
    }

    // ------------------------------------------------------------------ Tal día como hoy

    private suspend fun efemeride(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val hoy = LocalDate.now()
        val todas = d.todas
        val exactas = efemerides(todas, hoy)
        val mes = hoy.month.getDisplayName(TextStyle.FULL_STANDALONE, ES)
        // Si hoy no hay, lo de este mes en otros años; y si tampoco, lo último que terminaste
        val (titulo, lista) = when {
            exactas.isNotEmpty() -> "Tal día como hoy" to exactas.map { it.first to "${it.second}" to if (it.second == 1) "año" else "años" }
            else -> {
                val mesAntes = todas.mapNotNull { e -> e.game?.end?.takeIf { it > 0L }?.let { LocalDate.ofEpochDay(it) }?.takeIf { it.monthValue == hoy.monthValue && it.year < hoy.year }?.let { e to hoy.year - it.year } }
                    .sortedBy { it.second }
                if (mesAntes.isNotEmpty()) "Un $mes como este" to mesAntes.map { it.first to "${it.second}" to if (it.second == 1) "año" else "años" }
                else todas.filter { (it.game?.end ?: 0L) > 0L }.sortedByDescending { it.game!!.end }.take(5).map { e ->
                    val dias = (hoy.toEpochDay() - e.game!!.end).toInt().coerceAtLeast(0)
                    e to "$dias" to if (dias == 1) "día" else "días"
                }.let { "Lo último que terminaste" to it }
            }
        }
        if (lista.isEmpty()) return vacio(ctx, "Tal día como hoy", "Aún sin recuerdos", "Cuando termines algo, aquí te recordaré qué pasabas, leías o veías tal día como hoy.", abrir(ctx, "home"), w, h)
        val i = Math.floorMod(estado(ctx, id, "i", 0), lista.size)
        val (par, unidad) = lista[i]
        val (e, num) = par
        val conPortada = w >= 220f
        val img = if (conPortada) imagen(ctx, e.cover, px(ctx, h)) else null
        val zonas = mutableListOf(Zona(0f, 0f, w, h, tocar(d, e), e.name))
        if (lista.size > 1) zonas += Zona(w - 72f, 0f, 72f, 42f, op(ctx, WTipo.EFEMERIDE, id, "sig"), "Otro recuerdo")
        val verbo = when { e.kind.isGame -> "Lo pasaste"; e.kind.isBook -> "Lo leíste"; e.kind.isMovie || e.kind.isTv -> "La viste"; else -> "Lo terminaste" }
        return Pagina(zonas) {
            fondo()
            val pad = 14f
            var x0 = pad
            if (conPortada) {
                val ch = h - 2 * pad
                val cw = min(ch * 0.68f, w * 0.34f)
                portada(img, RectF(pad, pad, pad + cw, pad + ch), e.name)
                x0 = pad + cw + 14f
            }
            val rw = w - x0 - pad
            val corto = when { titulo.startsWith("Tal") -> "Hoy, hace"; titulo.startsWith("Un ") -> "Este mes, hace"; else -> "Terminado hace" }
            seccion(if (anchoAnte(titulo) + 12f > rw - (if (lista.size > 1) 44f else 0f)) corto else titulo, x0, pad + 1f, rw - 34f)
            paginas(i, lista.size, w - pad, pad + 1f)
            val n = capa(num, tp(fTitular, if (h < 150f) 34f else 46f, acento, -0.02f), rw, 1)
            val yN = pad + 16f
            pon(n, x0, yN)
            val nw = n.getLineWidth(0)
            ante(if (unidad == "días") "días" else unidad, x0 + nw + 6f, yN + n.height - 15f, rw - nw - 6f, gris, 9f)
            val pie = 16f
            val yT = yN + n.height + 2f
            val l = titular(e.name, rw, h - yT - pad - pie, 24f, 12f, 3)
            pon(l, x0, yT)
            ante(listOfNotNull(verbo, e.score?.let { "nota $it" }).joinToString(" · "), x0, h - pad - 11f, rw, acento, 8.5f)
        }
    }

    // ------------------------------------------------------------------ ATRIO (la cabecera de la revista)

    private fun marca(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val hoy = LocalDate.now()
        val fecha = "${hoy.dayOfWeek.getDisplayName(TextStyle.FULL, ES)} ${hoy.dayOfMonth} de ${hoy.month.getDisplayName(TextStyle.FULL, ES)}"
        val nombre = runCatching { d.store.myName }.getOrDefault("").trim()
        return Pagina(listOf(Zona(0f, 0f, w, h, abrir(ctx, "home"), "Abrir la revista"))) {
            // Solo la palabra, sobre el fondo de pantalla (con una sombra suave para que se lea en cualquiera)
            val color = if (noche) 0xFFF6F1E7.toInt() else tinta
            val sombra = if (noche) 0x99000000.toInt() else 0x55FFFFFF
            val pie = if (h >= 84f) 16f else 0f
            val p = tp(fTitular, 10f, color, -0.03f)
            // El tamaño que llena el ancho (y cabe en alto)
            val ancho = p.measureText("ATRIO")
            val cuadro = 0.16f
            var s = min((w - 8f) / (ancho / 10f * (1f + cuadro)), (h - pie - 4f) / 0.95f)
            s = s.coerceAtLeast(10f)
            val tpa = tp(fTitular, s, color, -0.03f).apply { setShadowLayer(s * 0.06f, 0f, s * 0.02f, sombra) }
            val fm = tpa.fontMetrics
            val tw = tpa.measureText("ATRIO")
            val total = tw + s * cuadro
            val x = (w - total) / 2
            val capH = s * 0.72f
            val base = (h - pie) / 2 + capH / 2
            c.drawText("ATRIO", x, base, tpa)
            // El punto de la revista: un cuadradito de acento
            val q = s * 0.12f
            c.drawRect(x + tw + s * 0.03f, base - q, x + tw + s * 0.03f + q, base, lleno(acento).apply { setShadowLayer(s * 0.04f, 0f, s * 0.015f, sombra) })
            if (pie > 0f) {
                val texto = (if (nombre.isNotEmpty()) "La revista de $nombre · " else "") + fecha
                val l = capa(texto.uppercase(ES), tp(fNegrita, 8.5f, color, 0.2f).apply { setShadowLayer(2f, 0f, 0.6f, sombra) }, w - 8f, 1, 1f, Layout.Alignment.ALIGN_CENTER)
                pon(l, 4f, min(h - l.height - 2f, base + fm.descent * 0.4f + 2f))
            }
        }.transparente()
    }

    /** ATRIO en papel: la cabecera de la portada de la revista (el nombre, el número del día y la fecha). */
    private fun marcaPapel(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val hoy = LocalDate.now()
        val fecha = "${hoy.dayOfWeek.getDisplayName(TextStyle.FULL, ES)} ${hoy.dayOfMonth} de ${hoy.month.getDisplayName(TextStyle.FULL, ES)}"
        val nombre = runCatching { d.store.myName }.getOrDefault("").trim()
        return Pagina(listOf(Zona(0f, 0f, w, h, abrir(ctx, "home"), "Abrir la revista"))) {
            fondo()
            val pad = if (h < 90f) 12f else 16f
            // Lo de debajo del nombre, según el sitio: la línea del número y la fecha; y la entradilla si cabe
            val conLinea = h >= 70f
            val conEntradilla = h >= 140f && nombre.isNotEmpty()
            val bajo = (if (conLinea) 22f else 0f) + (if (conEntradilla) 24f else 0f)
            val alto = h - 2 * pad - bajo
            val p = tp(fTitular, 10f, tinta, -0.03f)
            val s = min((w - 2 * pad) / (p.measureText("ATRIO") / 10f), alto / 0.78f).coerceAtLeast(10f)
            val tpa = tp(fTitular, s, tinta, -0.03f)
            val capH = s * 0.72f
            // Todo el bloque centrado en el widget (el nombre se mide por sus mayúsculas, no por la caja de la letra)
            val bloque = capH + s * 0.08f + 4f + bajo
            val base = max(pad, (h - bloque) / 2) + capH
            val tw = tpa.measureText("ATRIO")
            c.drawText("ATRIO", (w - tw) / 2, base, tpa)
            var y = base + s * 0.08f + 4f
            if (conLinea) {
                c.drawRect(pad, y, w - pad, y + 1.6f, lleno(tinta))
                y += 5f
                val iz = "Nº ${hoy.dayOfYear}"
                ante(iz, pad, y, w / 2, tinta, 8.5f)
                val sitio = w - 2 * pad - anchoAnte(iz, 8.5f) - 12f
                val corta = "${hoy.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')} ${hoy.dayOfMonth} ${hoy.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')}"
                ante(if (anchoAnte(fecha, 8.5f) > sitio) corta else fecha, pad + anchoAnte(iz, 8.5f) + 12f, y, w - 2 * pad - anchoAnte(iz, 8.5f) - 12f, tinta, 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
                y += 14f
                c.drawRect(pad, y, w - pad, y + 0.6f, lleno(tinta))
                y += 6f
            }
            if (conEntradilla) {
                val pe = tp(fCursiva, 13f, gris)
                val larga = "La colección de $nombre, al día"
                pon(capa(if (pe.measureText(larga) > w - 2 * pad) "La colección de $nombre" else larga, pe, w - 2 * pad, 1), pad, y)
            }
        }
    }

    // ------------------------------------------------------------------ ¿Y ahora qué? (algo de tu lista)

    private suspend fun siguiente(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val lista = d.todas.filter { it.play?.status == 0 }.sortedBy { (it.id * 2654435761L xor it.kind.id.toLong()) and 0xFFFFFF }
        if (lista.isEmpty()) return vacio(ctx, "¿Y ahora qué?", "Tu lista está vacía", "Apunta lo que quieras jugar, leer o ver y aquí te propondré qué empezar.", abrir(ctx, "home"), w, h)
        // Una propuesta distinta cada día (y la que quieras con «Otra»)
        val i = Math.floorMod(LocalDate.now().toEpochDay() + estado(ctx, id, "i", 0), lista.size.toLong()).toInt()
        val e = lista[i]
        val compacto = w < 200f && h < 200f
        val img = imagen(ctx, e.cover, px(ctx, if (compacto) max(w, h) else h))
        val pad = 14f
        val zonas = mutableListOf(
            Zona(0f, 0f, w, h, abrir(ctx, "item:${e.kind.id}:${e.id}"), e.name),
            Zona(0f, h - 40f, w * 0.55f, 40f, abrir(ctx, "empezar:${e.kind.id}:${e.id}"), "Empezar ${e.name}")
        )
        if (lista.size > 1) zonas += Zona(w - 86f, h - 40f, 86f, 40f, op(ctx, WTipo.SIGUIENTE, id, "sig"), "Otra propuesta")
        val empezar = when { e.kind.isBook -> "Empezar a leer"; e.kind.isGame -> "Empezar a jugar"; e.kind.isMovie || e.kind.isTv -> "Ver ahora"; else -> "Empezar" }
        return Pagina(zonas) {
            if (compacto) {
                portada(img, RectF(0f, 0f, w, h), e.name, 0f, false)
                velo(h * 0.15f, h, 0x00000000, 0xF0000000.toInt(), 0x99000000.toInt())
                velo(0f, 46f, 0xA0000000.toInt(), 0x00000000)
                seccion("¿Y ahora qué?", pad, pad, w - 2 * pad, 0xFFFFFFFF.toInt(), acentoFoto)
                val l = titular(e.name, w - 2 * pad, h * 0.42f, 24f, 12f, 3, 0xFFFFFFFF.toInt())
                pon(l, pad, h - pad - 22f - l.height)
                ante("Empezar  →", pad, h - pad - 11f, w * 0.6f, acentoFoto, 8.5f)
                if (lista.size > 1) ante("Otra  ›", w - pad - 60f, h - pad - 11f, 60f, 0xCCFFFFFF.toInt(), 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
                return@Pagina
            }
            fondo()
            val ch = h - 2 * pad
            val cw = min(ch * 0.68f, w * 0.34f)
            portada(img, RectF(pad, pad, pad + cw, pad + ch), e.name)
            val x0 = pad + cw + 14f
            val rw = w - x0 - pad
            seccion("¿Y ahora qué?", x0, pad + 1f, rw)
            val meta = listOf(e.kind.title, e.creator).filter { it.isNotBlank() }.joinToString(" · ")
            val y0 = pad + 20f
            val l = titular(e.name, rw, h - y0 - pad - 30f - 16f, (h * 0.19f).coerceIn(30f, 46f), 13f, 3)
            pon(l, x0, y0)
            pon(capa(meta, tp(fCursiva, 12f, gris), rw, 1), x0, y0 + l.height + 4f)
            c.drawRect(x0, h - pad - 22f, w - pad, h - pad - 21.4f, lleno(linea))
            ante("$empezar  →", x0, h - pad - 12f, rw * 0.7f, acento)
            if (lista.size > 1) ante("Otra  ›", w - pad - 70f, h - pad - 12f, 70f, gris, 9f, Layout.Alignment.ALIGN_OPPOSITE)
        }
    }

    // ------------------------------------------------------------------ Tu actividad (racha y mapa de días)

    /** Minutos de cada día: lectura (lo que cuenta el lector), apuntes y lo que terminaste. */
    fun minutosPorDia(d: WDatos): Map<LocalDate, Int> {
        val m = HashMap<LocalDate, Int>()
        val zona = ZoneId.systemDefault()
        fun dia(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zona).toLocalDate()
        d.kinds.forEach { k ->
            d.store.loadPlays(k.id).forEach { p ->
                p.entries.forEach { en -> if (en.time > 0) m.merge(dia(en.time), 5, Int::plus) }
            }
            d.store.load(k.id).forEach { g -> if (g.end > 0L) m.merge(LocalDate.ofEpochDay(g.end), 30, Int::plus) }
            if (k.isBook) d.de(k).forEach { e ->
                val st = BookFiles.state(d.ctx, e.id).optJSONObject("stats")?.optJSONObject("days")
                st?.keys()?.forEach { key ->
                    runCatching { LocalDate.parse(key) }.getOrNull()?.let { m.merge(it, (st.optLong(key) / 60_000L).toInt().coerceAtLeast(1), Int::plus) }
                }
            }
        }
        return m
    }

    /** Días seguidos con algo (si hoy aún no, cuenta desde ayer). */
    fun racha(m: Map<LocalDate, Int>, hoy: LocalDate = LocalDate.now()): Int {
        var dia = if ((m[hoy] ?: 0) > 0) hoy else hoy.minusDays(1)
        var n = 0
        while ((m[dia] ?: 0) > 0) { n++; dia = dia.minusDays(1) }
        return n
    }

    private fun actividad(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val m = minutosPorDia(d)
        val hoy = LocalDate.now()
        val r = racha(m, hoy)
        val lunes = hoy.minusDays((hoy.dayOfWeek.value - 1).toLong())
        val semana = (0..6).sumOf { m[lunes.plusDays(it.toLong())] ?: 0 }
        return Pagina(listOf(Zona(0f, 0f, w, h, abrir(ctx, "diario:0"), "Tu actividad: $r días seguidos"))) {
            fondo()
            val pad = 14f
            val ancho = w >= 250f
            val colW = if (ancho) 104f else w - 2 * pad
            seccion("Tu actividad", pad, pad, colW)
            val big = capa("$r", tp(fTitular, if (h < 150f) 40f else (h * 0.3f).coerceIn(52f, 80f), if (r > 0) acento else gris, -0.02f), colW, 1)
            pon(big, pad, pad + 14f)
            ante(if (r == 1) "día seguido" else "días seguidos", pad, pad + 16f + big.height, colW, tinta, 8.5f)
            val sem = if (semana >= 60) "${semana / 60} h esta semana" else "$semana min esta semana"
            if (ancho) ante(sem, pad, h - pad - 11f, colW, gris, 8f)
            fun color(v: Int) = when {
                v <= 0 -> linea
                v < 20 -> (acento and 0x00FFFFFF) or 0x55000000
                v < 60 -> (acento and 0x00FFFFFF) or 0x99000000.toInt()
                v < 120 -> (acento and 0x00FFFFFF) or 0xCC000000.toInt()
                else -> acento
            }
            if (!ancho) {
                // Pequeño: las dos últimas semanas, de lunes a domingo
                val cel = min((w - 2 * pad) / 7f, 16f)
                val y0 = h - pad - cel * 2
                for (f in 0..1) for (k in 0..6) {
                    val dia = lunes.minusWeeks((1 - f).toLong()).plusDays(k.toLong())
                    val x = pad + k * cel
                    val y = y0 + f * cel
                    val g = cel * 0.16f
                    if (dia.isAfter(hoy)) { c.drawRoundRect(x + g, y + g, x + cel - g, y + cel - g, cel * 0.22f, cel * 0.22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f; color = linea }); continue }
                    c.drawRoundRect(x + g, y + g, x + cel - g, y + cel - g, cel * 0.22f, cel * 0.22f, lleno(color(m[dia] ?: 0)))
                    if (dia == hoy) c.drawRoundRect(x + g, y + g, x + cel - g, y + cel - g, cel * 0.22f, cel * 0.22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; this.color = tinta })
                }
                return@Pagina
            }
            // El mapa: una columna por semana, de lunes a domingo, como un cuaderno de cuadrícula
            val gx = if (ancho) pad + colW + 12f else pad
            val gy = pad + 2f
            val gw = w - pad - gx
            val gh = h - pad - 14f - gy
            if (gh < 20f) return@Pagina
            val celda = min(gh / 7f, 26f)
            val semanas = (gw / celda).toInt().coerceIn(4, 53)
            val cel = min(celda, gw / semanas)
            val g = max(1.2f, cel * 0.16f)
            val x1 = w - pad - semanas * cel
            val inicio = lunes.minusWeeks((semanas - 1).toLong())
            for (sm in 0 until semanas) for (dd in 0 until 7) {
                val dia = inicio.plusDays((sm * 7 + dd).toLong())
                if (dia.isAfter(hoy)) continue
                val v = m[dia] ?: 0
                val tono = color(v)
                val x = x1 + sm * cel
                val y = gy + dd * cel
                c.drawRoundRect(x + g / 2, y + g / 2, x + cel - g / 2, y + cel - g / 2, cel * 0.22f, cel * 0.22f, lleno(tono))
                if (dia == hoy) c.drawRoundRect(x + g / 2, y + g / 2, x + cel - g / 2, y + cel - g / 2, cel * 0.22f, cel * 0.22f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = tinta })
            }
            if (ancho) {
                val mes = inicio.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')
                ante(mes, x1, h - pad - 10f, 60f, gris, 7.5f)
                ante("hoy", w - pad - 40f, h - pad - 10f, 40f, gris, 7.5f, Layout.Alignment.ALIGN_OPPOSITE)
            }
        }
    }
}
