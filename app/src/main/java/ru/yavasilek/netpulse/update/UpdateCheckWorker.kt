package ru.yavasilek.netpulse.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ru.yavasilek.netpulse.MainActivity
import ru.yavasilek.netpulse.R
import ru.yavasilek.netpulse.appContainer

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = applicationContext.appContainer.settingsRepository.current()
        if (!settings.automaticUpdateChecks) return Result.success()
        return when (val state = applicationContext.appContainer.updateManager.check()) {
            is UpdateState.Available -> {
                notifyAvailable(state.release)
                Result.success()
            }
            is UpdateState.Error -> Result.retry()
            else -> Result.success()
        }
    }

    private fun notifyAvailable(release: ReleaseInfo) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Обновления приложения",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_UPDATE, true)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            42,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Доступен NetPulse ${release.versionName}")
                .setContentText("Нажмите, чтобы посмотреть и скачать")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 2002
    }
}
