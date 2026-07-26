package ru.yavasilek.netpulse.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import ru.yavasilek.netpulse.MainActivity
import ru.yavasilek.netpulse.R
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.settings.AppSettings
import ru.yavasilek.netpulse.util.SpeedFormatter

class MonitorNotificationFactory(
    private val context: Context,
) {
    private val iconRenderer = StatusIconRenderer()

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    MONITOR_CHANNEL_ID,
                    context.getString(R.string.notification_channel_monitor_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description =
                        context.getString(R.string.notification_channel_monitor_description)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(
                    ALERTS_CHANNEL_ID,
                    context.getString(R.string.notification_channel_alerts_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description =
                        context.getString(R.string.notification_channel_alerts_description)
                },
            ),
        )
    }

    fun build(
        snapshot: MonitorSnapshot,
        settings: AppSettings,
    ): Notification {
        val download = SpeedFormatter.format(
            snapshot.speed.receivedBytesPerSecond,
            settings.speedUnit,
        )
        val upload = SpeedFormatter.format(
            snapshot.speed.transmittedBytesPerSecond,
            settings.speedUnit,
        )
        val status = snapshot.connection.status.label()
        val country = snapshot.publicIp.countryName
        val refreshingPublicIp = snapshot.publicIp.isRefreshing
        val summary = buildList {
            add(status)
            if (snapshot.connection.isVpn) add("VPN")
            if (refreshingPublicIp) {
                add("IP обновляется")
            } else if (!country.isNullOrBlank()) {
                add(country)
            }
        }.joinToString(" · ")
        val bigText = buildString {
            appendLine(summary)
            appendLine(snapshot.connection.transportLabel)
            append("IPv4: ")
            append(
                if (refreshingPublicIp) {
                    "обновляется…"
                } else {
                    snapshot.publicIp.ipv4?.address ?: "не определён"
                },
            )
            appendLine()
            append("IPv6: ")
            append(
                if (refreshingPublicIp) {
                    "обновляется…"
                } else {
                    snapshot.publicIp.ipv6?.address ?: "не обнаружен"
                },
            )
        }

        return Notification.Builder(context, MONITOR_CHANNEL_ID)
            .setSmallIcon(iconRenderer.render(snapshot.speed, settings))
            .setContentTitle("↓ $download · ↑ $upload")
            .setContentText(summary)
            .setStyle(Notification.BigTextStyle().bigText(bigText))
            .setContentIntent(activityPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(ContextCompat.getColor(context, R.color.launcher_background))
            .addAction(action(ACTION_REFRESH, R.string.notification_action_refresh))
            .addAction(action(ACTION_COPY, R.string.notification_action_copy))
            .addAction(action(ACTION_STOP, R.string.notification_action_pause))
            .build()
    }

    fun buildAlert(title: String, message: String): Notification =
        Notification.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(activityPendingIntent())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .setColor(ContextCompat.getColor(context, R.color.launcher_background))
            .build()

    private fun action(action: String, labelRes: Int): Notification.Action {
        val intent = Intent(context, NetworkMonitorService::class.java).setAction(action)
        val pendingIntent = PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_notification),
            context.getString(labelRes),
            pendingIntent,
        ).build()
    }

    private fun activityPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ConnectionStatus.label(): String = when (this) {
        ConnectionStatus.CHECKING -> "Проверка интернета"
        ConnectionStatus.ONLINE -> "Интернет работает"
        ConnectionStatus.CAPTIVE_PORTAL -> "Требуется вход"
        ConnectionStatus.LIMITED -> "Интернет не подтверждён"
        ConnectionStatus.OFFLINE -> "Нет интернета"
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "network_monitor"
        const val ALERTS_CHANNEL_ID = "network_alerts"
        const val ACTION_REFRESH = "ru.yavasilek.netpulse.action.REFRESH_IP"
        const val ACTION_COPY = "ru.yavasilek.netpulse.action.COPY_IP"
        const val ACTION_STOP = "ru.yavasilek.netpulse.action.STOP"
    }
}
