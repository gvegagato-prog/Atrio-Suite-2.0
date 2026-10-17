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
