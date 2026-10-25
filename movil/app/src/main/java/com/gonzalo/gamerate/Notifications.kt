package com.gonzalo.gamerate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime


/** Comprueba cada media hora (aprox.) si tu pareja tiene novedades. */
object PartnerWatch {
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<PartnerWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("partner", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork("partner")
    }
}

class PartnerWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        val store = Store(app)
        if (!store.paired || !store.notifyPartner) return Result.success()
        val p = try { Couple.pull(store) } catch (e: Exception) { return Result.retry() } ?: return Result.success()
        val news = Couple.news(store, p)
        Couple.markSeen(store, p)
        if (news.isEmpty()) return Result.success()

        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("partner", "Novedades de tu pareja", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("partner", true)
        }
        val pi = PendingIntent.getActivity(app, 2002, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (news.size == 1) "♥ ${p.name}" else "♥ ${p.name} · ${news.size} novedades"
        val style = Notification.InboxStyle()
        news.take(6).forEach { style.addLine(it) }
        val n = Notification.Builder(app, "partner")
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle(title)
            .setContentText(news.first())
            .setStyle(style)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(2002, n)
        return Result.success()
    }
}

/**
 * Subida a la nube en segundo plano: se programa al salir de la app para que lo último quede guardado
 * aunque la cierres al momento o no tengas conexión (Android la hace en cuanto haya internet).
 */
object CloudSave {
    fun enqueue(ctx: Context) {
        val req = androidx.work.OneTimeWorkRequestBuilder<CloudWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("nube", androidx.work.ExistingWorkPolicy.REPLACE, req)
    }
}

class CloudWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        var retry = false
        if (store.paired) {
            try { Couple.push(store) } catch (e: Exception) { retry = true }
        }
        if (store.driveUri.isNotBlank()) DriveBackup.save(applicationContext, store)
        return if (retry && runAttemptCount < 5) Result.retry() else Result.success()
    }
}

/**
 * Lo que quedó de versiones antiguas (el aviso de las sesiones y el de las partidas para valorar, ya quitados):
 * sus tareas programadas y sus canales de notificación se borran del móvil.
 */
object Limpieza {
    fun antiguas(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelUniqueWork("session")
        wm.cancelUniqueWork("suite")
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("session")
        nm.deleteNotificationChannel("partes")
    }
}

/**
 * El objetivo de lectura del día: a la hora que elijas, si aún no has leído los minutos que te propusiste, un aviso
 * con lo que llevas y el libro por el que vas (tocarlo lo abre en el lector, en tu página).
 */
object LecturaAviso {
    fun programa(ctx: Context) { runCatching { programaYa(ctx) } }

    private fun programaYa(ctx: Context) {
        val st = Store(ctx)
        val h = st.avisoLectura
        val wm = WorkManager.getInstance(ctx)
        if (h !in 0..23 || st.metaLectura <= 0) { wm.cancelUniqueWork("lectura"); return }
        val ahora = LocalDateTime.now()
        var sig = ahora.toLocalDate().atTime(h, 0)
        if (!sig.isAfter(ahora)) sig = sig.plusDays(1)
        val req = PeriodicWorkRequestBuilder<LecturaWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(ahora, sig).toMinutes().coerceAtLeast(1), TimeUnit.MINUTES)
            .addTag("h$h")
            .build()
        wm.enqueueUniquePeriodicWork("lectura", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** Lo leído hoy (ms, todos tus libros). */
    fun hoy(ctx: Context): Long = BookFiles.leidoPorDia(ctx)[LocalDate.now().toEpochDay()]?.values?.sum() ?: 0L

    /** El libro que leíste por última vez (y que tiene su archivo aquí): id y lo que llevas (0–1). */
    fun ultimo(ctx: Context): Pair<Long, Double>? {
        val p = ctx.getSharedPreferences("lector", Context.MODE_PRIVATE)
        var best: Triple<Long, Long, Double>? = null
        for ((k, v) in p.all) {
            val id = k.toLongOrNull() ?: continue
            val o = runCatching { org.json.JSONObject(v as String) }.getOrNull() ?: continue
            if (o.optString("uri").isBlank()) continue
            val fr = o.optDouble("fraction", 0.0)
            if (fr >= 0.995) continue
            val t = o.optLong("read", 0L)
            if (best == null || t > best.second) best = Triple(id, t, fr)
        }
        return best?.let { it.first to it.third }
    }

    /** El aviso, si aún no has cumplido el objetivo de hoy (si ya lo cumpliste, nada). */
    fun avisa(ctx: Context): Boolean {
        val st = Store(ctx)
        val meta = st.metaLectura
        if (meta <= 0) return false
        val min = hoy(ctx) / 60_000L
        if (min >= meta) return false
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("lectura", "Objetivo de lectura", NotificationManager.IMPORTANCE_DEFAULT))
        val u = ultimo(ctx)
        val nombre = u?.let { (id, _) -> st.loadPlays(BOOKS.id).find { it.id == id }?.name ?: st.load(BOOKS.id).find { it.id == id }?.name }
        val intent = if (u != null && nombre != null)
            Intent(ctx, ReaderActivity::class.java).putExtra("id", u.first).putExtra("name", nombre).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        else Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, 2003, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val titulo = if (min < 1) "Hoy aún no has leído" else "Hoy llevas $min de $meta min de lectura"
        val texto = if (nombre != null) "Sigue con «$nombre»" + (u.second.takeIf { it > 0.005 }?.let { " · vas por el ${(it * 100).toInt()} %" } ?: "")
            else "Tu objetivo: $meta min al día"
        val n = Notification.Builder(ctx, "lectura")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(2003, n)
        return true
    }
}

class LecturaWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { LecturaAviso.avisa(applicationContext) }
        return Result.success()
    }
}
