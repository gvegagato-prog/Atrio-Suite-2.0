package com.gonzalo.gamerate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.launch

/**
 * Actualizarse sola desde tu kDrive. Cada versión se sube a «Mi carpeta/versiones» (Atrio-<versión>.apk). Se mira si hay
 * una más nueva al abrir la app, cada minuto mientras se ve (cualquier pantalla suya, también el lector) y, con la app
 * cerrada, cada pocas horas en segundo plano. Si la hay, se baja (con wifi o con datos móviles) y se comprueba que es
 * Atrio y que está firmada con la misma clave. Se instala:
 *  - sin preguntar, cuando no la estás usando (al salir de la app, o en segundo plano): Android lo permite a una app que
 *    se actualiza a sí misma (Android 12 o más, con el permiso de instalar apps y apuntando a una versión reciente);
 *  - si Android no lo deja, con un aviso para actualizar ahora (Android pide su confirmación), y al terminar se vuelve a
 *    abrir sola (o te avisa para abrirla).
 */
object Actualizador {
    class Nueva(val nombre: String, val versionCode: Long, val archivo: File, val silenciosa: Boolean)

    /** La versión descargada y lista (la pantalla pregunta si no puede instalarse sola). */
    var nueva by mutableStateOf<Nueva?>(null)
    /** Falta el permiso de «instalar apps desconocidas» para Atrio. */
    var faltaPermiso by mutableStateOf(false)
    /** La app está delante (si no, un aviso en lugar de abrir pantallas). */
    @Volatile var primerPlano = false
    /** Qué pasó en la última comprobación (se enseña en Perfil, para saber por qué no se actualiza si no lo hace). */
    var estado by mutableStateOf("")
        private set
    private fun anota(ctx: Context, t: String) {
        val v = java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) } + " · " + t
        estado = v
        prefs(ctx).edit().putString("estado", v).apply()
    }
    fun estadoGuardado(ctx: Context): String = prefs(ctx).getString("estado", "").orEmpty().also { if (estado.isBlank()) estado = it }

    /** Ya hay una comprobación o una descarga en marcha. */
    private val ocupado = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("actualizador", Context.MODE_PRIVATE)
    fun activo(ctx: Context) = prefs(ctx).getBoolean("on", true)
    fun ponActivo(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("on", v).apply()

    /** «Atrio-6.67.apk» → [6, 67]. */
    fun versionDe(nombre: String): List<Int>? =
        Regex("""^Atrio-(\d+(?:\.\d+)*)\.apk$""").find(nombre)?.groupValues?.get(1)?.split('.')?.map { it.toInt() }

    /** ¿[a] es más nueva que [b]? («6.67» > «6.9» > «6.10» como números, no como texto). */
    fun mayor(a: List<Int>, b: List<Int>): Boolean {
        for (i in 0 until maxOf(a.size, b.size)) { val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }; if (x != y) return x > y }
        return false
    }

    /**
     * Con la 1.0 la numeración empezó de nuevo. Los archivos «Atrio-6.x.apk» que siguen en la carpeta son de antes: si no se
     * ignoran, el 6.93 parecería más nuevo que el 1.0.1 y se bajaría una y otra vez. (Una 6.93 instalada ve un «Atrio-6.94.apk»
     * como más nuevo: esa es la 1.0 con nombre de la serie antigua, para que las que ya estaban instaladas den el salto solas.)
     */
    fun deLaSerieNueva(archivos: List<Pair<String, Long>>): List<Pair<String, Long>> =
        archivos.filter { (versionDe(it.first)?.firstOrNull() ?: 0) < SERIE_ANTIGUA }
    private const val SERIE_ANTIGUA = 6

    /** La más nueva de los archivos de la carpeta. */
    fun ultima(archivos: List<Pair<String, Long>>): Triple<String, Long, List<Int>>? =
        archivos.mapNotNull { (n, id) -> versionDe(n)?.let { Triple(n, id, it) } }.fold(null as Triple<String, Long, List<Int>>?) { m, x -> if (m == null || mayor(x.third, m.third)) x else m }

    /** Hay internet de verdad (wifi o datos móviles). */
    private fun hayInternet(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val c = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    @Suppress("DEPRECATION")
    private fun instalada(ctx: Context) = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)

    /** Que el archivo bajado sea de verdad una versión más nueva de Atrio, firmada con la misma clave que la instalada. */
    @Suppress("DEPRECATION")
    fun valida(ctx: Context, f: File): Long? {
        val pm = ctx.packageManager
        val nuevo = pm.getPackageArchiveInfo(f.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return null
        val actual = instalada(ctx)
        if (nuevo.packageName != ctx.packageName || nuevo.longVersionCode <= actual.longVersionCode) return null
        val a = actual.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val n = nuevo.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        // Si Android no deja leer la firma del archivo, se da por buena: al instalar, rechaza una firma distinta
        return if (n.isEmpty() || (a.isNotEmpty() && a == n)) nuevo.longVersionCode else null
    }

    /**
     * ¿Puede instalarse sin preguntar? (developer.android.com, SessionParams.setRequireUserAction): una app que se actualiza
     * a sí misma, con el permiso de instalar apps, en Android 12 o más y apuntando como mínimo a la versión que pide cada
     * Android (29 en el 12, 30 en el 13, 31 en el 14, 33 en el 15, 34 en el 16, 35 en el 17). No hace falta haber sido
     * quien la instaló la primera vez. Si aun así Android pidiera confirmar, se pregunta (ActualizadorReceiver).
     */
    internal fun puedeSilenciosa(ctx: Context, sdk: Int = Build.VERSION.SDK_INT, target: Int = ctx.applicationInfo.targetSdkVersion): Boolean {
        if (sdk < 31 || !ctx.packageManager.canRequestPackageInstalls()) return false
        val minimo = when { sdk >= 37 -> 35; sdk >= 36 -> 34; sdk >= 35 -> 33; sdk >= 34 -> 31; sdk >= 33 -> 30; else -> 29 }
        return target >= minimo
    }

    // ---- Cuándo se mira ----
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var bucle: kotlinx.coroutines.Job? = null
    /** Lo que se espera tras dejar de verse la app antes de instalar (por si vuelves enseguida). */
    private const val DESPUES_DE_SALIR = 45_000L
    @Volatile private var iniciado = false

    /**
     * Desde el arranque de la app: mientras se vea alguna pantalla suya (también el lector) se mira cada minuto; al dejar
     * de verse ninguna, se instala lo que esté listo; y con la app cerrada, cada pocas horas (ActualizadorWorker).
     */
    fun init(ctx: Context) {
        if (iniciado) return
        iniciado = true
        val app = ctx.applicationContext
        runCatching { programa(app) }
        val pon = Runnable {
            runCatching {
                androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, ev ->
                    when (ev) {
                        androidx.lifecycle.Lifecycle.Event.ON_START -> {
                            primerPlano = true
                            runCatching { androidx.work.WorkManager.getInstance(app).cancelUniqueWork("actualizar-al-salir") }
                            quitaAviso(app)
                            bucle?.cancel()
                            bucle = scope.launch { while (true) { runCatching { comprueba(app) }; kotlinx.coroutines.delay(60_000) } }
                        }
                        androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                            primerPlano = false
                            bucle?.cancel(); bucle = null
                            // Se espera un poco: si solo has ido a la galería o al selector de archivos (otra app encima),
                            // al volver se cancela; instalar ahí cerraría Atrio a mitad de elegir una foto. (Con WorkManager y
                            // no con una espera aquí: Android congela las apps que no se ven, y su tarea llega igual.)
                            if (nueva?.silenciosa == true) programaAlSalir(app)
                        }
                        else -> {}
                    }
                })
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) pon.run() else android.os.Handler(android.os.Looper.getMainLooper()).post(pon)
    }

    /** La instalación sin preguntar, un poco después de dejar de verse la app (se cancela si vuelves antes). */
    private fun programaAlSalir(ctx: Context) = runCatching {
        val req = androidx.work.OneTimeWorkRequestBuilder<ActualizadorWorker>()
            .setInitialDelay(DESPUES_DE_SALIR, java.util.concurrent.TimeUnit.MILLISECONDS)
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .build()
        androidx.work.WorkManager.getInstance(ctx).enqueueUniqueWork("actualizar-al-salir", androidx.work.ExistingWorkPolicy.REPLACE, req)
    }

    /** Con la app cerrada: cada 4 horas (con red) se mira y, si hay una nueva, se baja y se instala sola. */
    fun programa(ctx: Context) {
        val req = androidx.work.PeriodicWorkRequestBuilder<ActualizadorWorker>(4, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .build()
        androidx.work.WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("actualizar", androidx.work.ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Se llama nada más abrir la app y luego cada minuto mientras está abierta (siempre en segundo plano). */
    fun comprueba(ctx: Context, manual: Boolean = false) {
        if (!activo(ctx)) { if (manual) anota(ctx, "apagada (interruptor de arriba)"); return }
        if (!KDrive.on(ctx)) { anota(ctx, "kDrive sin conectar en Atrio"); return }
        if (!hayInternet(ctx)) { anota(ctx, "sin internet"); return }
        if (!ocupado.compareAndSet(false, true)) return
        try { compruebaYa(ctx) } catch (e: Throwable) { anota(ctx, "error: ${e.message ?: e.javaClass.simpleName}") } finally { ocupado.set(false) }
    }

    private fun compruebaYa(ctx: Context) {
        val lista = deLaSerieNueva(KDrive.versiones(ctx))
        val ult = ultima(lista) ?: run { anota(ctx, if (lista.isEmpty()) "no encuentro la carpeta «versiones» (o está vacía) en tu kDrive" else "en «versiones» no hay ningún Atrio-<versión>.apk"); return }
        val actual = instalada(ctx).versionName?.split('.')?.mapNotNull { it.toIntOrNull() } ?: return
        val dir = File(ctx.cacheDir, "actualizaciones").apply { mkdirs() }
        dir.listFiles()?.filter { it.name != ult.first }?.forEach { it.delete() }
        if (!mayor(ult.third, actual)) { dir.listFiles()?.forEach { it.delete() }; anota(ctx, "al día (la última de tu kDrive es la ${ult.third.joinToString(".")})"); return }
        if (nueva?.nombre == ult.first) return      // ya está descargada y lista
        val f = File(dir, ult.first)
        var code = if (f.exists()) valida(ctx, f) else null
        if (code == null) {
            f.delete()
            KDrive.download(ctx, ult.second, f)
            code = valida(ctx, f)
            if (code == null) { f.delete(); anota(ctx, "bajada la ${ult.first}, pero Android no la da por válida (otra firma o archivo roto)"); return }
        }
        if (nueva?.nombre == ult.first) return
        if (!ctx.packageManager.canRequestPackageInstalls()) faltaPermiso = true
        val silenciosa = puedeSilenciosa(ctx)
        nueva = Nueva(ult.first, code, f, silenciosa)
        // Si mientras se bajaba dejaste la app, se instala en cuanto pase un momento
        if (silenciosa && !primerPlano && iniciado) programaAlSalir(ctx)
        anota(ctx, "lista la ${ult.third.joinToString(".")}" + if (silenciosa) " (se instala al salir de la app)" else " (te pregunto para instalarla)")
    }

    /** La instalación no salió bien (la silenciosa, sobre todo): se anota y se pasa a preguntar con el aviso de siempre. */
    fun fallo(ctx: Context, msg: String) {
        anota(ctx, "la instalación no se completó: $msg")
        nueva?.let { nueva = Nueva(it.nombre, it.versionCode, it.archivo, false) }
    }

    /** Al salir de la app (y en segundo plano): si se puede instalar sin preguntar, se instala ahora (no interrumpe nada). */
    fun alSalir(ctx: Context) { if (!primerPlano) nueva?.takeIf { it.silenciosa }?.let { instala(ctx, it) } }

    /** Si instalas con la app delante, al terminar se vuelve a abrir (Android cierra la app al actualizarla). */
    private fun marcaReabrir(ctx: Context, si: Boolean) {
        ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE).edit().putLong("act_reabrir", if (si) System.currentTimeMillis() else 0L).apply()
    }

    /** Lo llama el receptor de «app actualizada»: reabre Atrio si la actualización se hizo con ella delante. */
    fun reabre(ctx: Context) {
        val sp = ctx.getSharedPreferences("gamerate", Context.MODE_PRIVATE)
        val t = sp.getLong("act_reabrir", 0L)
        sp.edit().putLong("act_reabrir", 0L).apply()
        if (t == 0L || System.currentTimeMillis() - t > 15 * 60_000L) return
        val abrir = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        // Con el permiso «Mostrar sobre otras apps», Android deja abrirla directamente; sin él (o si el móvil lo bloquea), queda el aviso
        if (puedeReabrirSola(ctx)) runCatching { ctx.startActivity(abrir) }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("reabrir", "Atrio actualizada", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(ctx, 2006, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(AVISO_REABRIR, Notification.Builder(ctx, "reabrir").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Atrio se ha actualizado").setContentText("Toca para abrirla").setContentIntent(pi).setAutoCancel(true).build())
    }

    const val AVISO_REABRIR = 2006
    fun puedeReabrirSola(ctx: Context) = android.provider.Settings.canDrawOverlays(ctx)
    /** Ya abierta: el aviso de «toca para abrirla» sobra. */
    fun quitaAviso(ctx: Context) { runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(AVISO_REABRIR) } }

    fun instala(ctx: Context, n: Nueva) {
        marcaReabrir(ctx, primerPlano)
        val pi = ctx.packageManager.packageInstaller
        val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(p)
        pi.openSession(id).use { s ->
            n.archivo.inputStream().use { inp -> s.openWrite("atrio.apk", 0, n.archivo.length()).use { out -> inp.copyTo(out); s.fsync(out) } }
            val i = Intent(ctx, ActualizadorReceiver::class.java).setPackage(ctx.packageName)
            s.commit(PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender)
        }
    }
}

/** El resultado de la instalación: si Android pide confirmar, se abre su pantalla (o un aviso, si la app no está delante). */
class ActualizadorReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        if (estado == PackageInstaller.STATUS_SUCCESS) return
        if (estado != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Falló (o se canceló): se anota y la próxima vez se pregunta en pantalla en lugar de intentarlo en silencio
            Actualizador.fallo(ctx, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "código $estado")
            return
        }
        @Suppress("DEPRECATION") val conf = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
        conf.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Actualizador.primerPlano) { runCatching { ctx.startActivity(conf) }; return }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("actualizar", "Actualizaciones de Atrio", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(ctx, 2005, conf, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(2005, Notification.Builder(ctx, "actualizar").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Atrio tiene una versión nueva").setContentText("Toca para actualizar").setContentIntent(pi).setAutoCancel(true).build())
    }
}

/** Android cierra la app al actualizarla; este receptor la vuelve a abrir (si la actualización se hizo con ella delante). */
class ActualizadorReabrir : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Actualizador.reabre(ctx)
    }
}

/** Con la app cerrada: mira si hay una versión nueva en tu kDrive y, si puede, la instala sola (sin pantallas ni avisos). */
class ActualizadorWorker(ctx: Context, p: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        if (!Actualizador.activo(c) || !KDrive.on(c)) return Result.success()
        // (si ya está bajada y lista, no hace falta volver a mirar)
        if (Actualizador.nueva == null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { Actualizador.comprueba(c) } }
        // (si justo la estás usando, espera a que salgas)
        runCatching { Actualizador.alSalir(c) }
        return Result.success()
    }
}
