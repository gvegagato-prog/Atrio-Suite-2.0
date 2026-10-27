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

/**
 * Actualizarse sola desde tu kDrive. Cada versión se sube a «Mi carpeta/versiones» (Atrio-<versión>.apk). Al abrir la app,
 * unos segundos después de verse la pantalla y en segundo plano (el arranque no cambia), se mira si hay internet y si la
 * última de la carpeta es más nueva que la instalada; si lo es, se baja a la caché (con wifi o con datos móviles) y se comprueba que
 * es Atrio y que está firmada con la misma clave. Se instala:
 *  - sin preguntar y al salir de la app, si Android lo permite (Android 12 o más, y solo si fue la propia Atrio quien
 *    instaló la versión actual: por eso la primera actualización necesita tu confirmación);
 *  - si no, con un aviso para actualizar ahora (Android pide su confirmación).
 */
object Actualizador {
    class Nueva(val nombre: String, val versionCode: Long, val archivo: File, val silenciosa: Boolean)

    /** La versión descargada y lista (la pantalla pregunta si no puede instalarse sola). */
    var nueva by mutableStateOf<Nueva?>(null)
    /** Falta el permiso de «instalar apps desconocidas» para Atrio. */
    var faltaPermiso by mutableStateOf(false)
    /** La app está delante (si no, un aviso en lugar de abrir pantallas). */
    @Volatile var primerPlano = false
    private var hecho = false

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
        return if (a.isNotEmpty() && a == n) nuevo.longVersionCode else null
    }

    private fun puedeSilenciosa(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31 || !ctx.packageManager.canRequestPackageInstalls()) return false
        return runCatching { ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName == ctx.packageName }.getOrDefault(false)
    }

    /** Se llama al abrir la app (en segundo plano). Una sola vez por arranque. */
    fun comprueba(ctx: Context) {
        if (hecho) return
        hecho = true
        if (!activo(ctx) || !KDrive.on(ctx) || !hayInternet(ctx)) return
        val ult = ultima(KDrive.versiones(ctx)) ?: return
        val actual = instalada(ctx).versionName?.split('.')?.mapNotNull { it.toIntOrNull() } ?: return
        val dir = File(ctx.cacheDir, "actualizaciones").apply { mkdirs() }
        dir.listFiles()?.filter { it.name != ult.first }?.forEach { it.delete() }
        if (!mayor(ult.third, actual)) { dir.listFiles()?.forEach { it.delete() }; return }
        val f = File(dir, ult.first)
        var code = if (f.exists()) valida(ctx, f) else null
        if (code == null) {
            f.delete()
            KDrive.download(ctx, ult.second, f)
            code = valida(ctx, f)
            if (code == null) { f.delete(); return }
        }
        if (!ctx.packageManager.canRequestPackageInstalls()) faltaPermiso = true
        nueva = Nueva(ult.first, code, f, puedeSilenciosa(ctx))
    }

    /** Al salir de la app: si se puede instalar sin preguntar, se instala ahora (no interrumpe lo que hacías). */
    fun alSalir(ctx: Context) { nueva?.takeIf { it.silenciosa }?.let { instala(ctx, it) } }

    fun instala(ctx: Context, n: Nueva) {
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
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
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
