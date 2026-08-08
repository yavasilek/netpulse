package ru.yavasilek.netpulse.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import ru.yavasilek.netpulse.MainActivity
import ru.yavasilek.netpulse.R
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.model.NetworkQualityStatus
import ru.yavasilek.netpulse.protection.ProtectionEvaluator
import ru.yavasilek.netpulse.protection.ProtectionStatus
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
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description =
                        context.getString(R.string.notification_channel_monitor_description)
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
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
        val protection = ProtectionEvaluator.evaluate(snapshot, settings)
        val country = snapshot.publicIp.countryName
        val refreshingPublicIp = snapshot.publicIp.isRefreshing
        val summary = buildList {
            add(status)
            add(snapshot.quality.notificationLabel())
            add(protection.status.notificationLabel())
            if (refreshingPublicIp) {
                add("IP обновляется")
            } else if (!country.isNullOrBlank()) {
                add(country)
            }
        }.joinToString(" · ")
        val compactSummary = if (settings.showNetworkDetailsOnLockScreen) {
            buildList {
                if (refreshingPublicIp) {
                    add("IP обновляется")
                } else {
                    snapshot.publicIp.primary?.address?.let { add("IP: $it") }
                    if (!country.isNullOrBlank()) add(country)
                }
                add(protection.status.notificationLabel())
            }.joinToString(" · ").ifBlank { summary }
        } else {
            summary
        }
        val bigText = buildString {
            appendLine(summary)
            appendLine(snapshot.connection.transportLabel)
            append("Качество: ")
            append(snapshot.quality.detailLabel())
            appendLine()
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

        val visibility = if (settings.showNetworkDetailsOnLockScreen) {
            Notification.VISIBILITY_PUBLIC
        } else {
            Notification.VISIBILITY_PRIVATE
        }
        val builder = Notification.Builder(context, MONITOR_CHANNEL_ID)
            .setSmallIcon(iconRenderer.render(snapshot.speed, settings))
            .setContentTitle("↓ $download · ↑ $upload")
            .setContentText(compactSummary)
            .setStyle(Notification.BigTextStyle().bigText(bigText))
            .setContentIntent(activityPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(visibility)
            .setColor(ContextCompat.getColor(context, R.color.launcher_background))
            .addAction(action(ACTION_REFRESH, R.string.notification_action_refresh))
            .addAction(action(ACTION_COPY, R.string.notification_action_copy))
            .addAction(action(ACTION_STOP, R.string.notification_action_pause))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (!settings.showNetworkDetailsOnLockScreen) {
            val publicBuilder = Notification.Builder(context, MONITOR_CHANNEL_ID)
                    .setSmallIcon(iconRenderer.render(snapshot.speed, settings))
                    .setContentTitle("↓ $download · ↑ $upload")
                    .setContentText("NetPulse работает")
                    .setOngoing(true)
                    .setShowWhen(false)
                    .setCategory(Notification.CATEGORY_STATUS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                publicBuilder.setForegroundServiceBehavior(
                    Notification.FOREGROUND_SERVICE_IMMEDIATE,
                )
            }
            builder.setPublicVersion(publicBuilder.build())
        }
        return builder.build()
    }

    fun buildAlert(title: String, message: String): Notification =
        buildAlert(
            title = title,
            message = message,
            smallIcon = Icon.createWithResource(context, R.drawable.ic_notification),
        )

    fun buildVpnDisconnectedAlert(
        snapshot: MonitorSnapshot,
        settings: AppSettings,
    ): Notification = buildAlert(
        title = "VPN отключён",
        message = "Текущее соединение: ${snapshot.connection.transportLabel}",
        smallIcon = iconRenderer.render(snapshot.speed, settings),
    )

    private fun buildAlert(
        title: String,
        message: String,
        smallIcon: Icon,
    ): Notification =
        Notification.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(activityPendingIntent())
            .setOnlyAlertOnce(true)
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

    private fun ProtectionStatus.notificationLabel(): String = when (this) {
        ProtectionStatus.PROTECTED -> "Защита: норма"
        ProtectionStatus.ATTENTION -> "Защита: проверка"
        ProtectionStatus.DANGER -> "Защита: риск"
    }

    private fun ru.yavasilek.netpulse.model.NetworkQuality.notificationLabel(): String =
        when (status) {
            NetworkQualityStatus.CHECKING -> "Качество: проверка"
            NetworkQualityStatus.EXCELLENT -> "${latencyMillis ?: "—"} мс"
            NetworkQualityStatus.GOOD -> "${latencyMillis ?: "—"} мс"
            NetworkQualityStatus.UNSTABLE -> "Связь нестабильна"
            NetworkQualityStatus.UNAVAILABLE -> "Качество недоступно"
        }

    private fun ru.yavasilek.netpulse.model.NetworkQuality.detailLabel(): String =
        when (status) {
            NetworkQualityStatus.CHECKING -> "проверяется"
            NetworkQualityStatus.UNAVAILABLE -> errorMessage ?: "недоступно"
            else -> listOfNotNull(
                latencyMillis?.let { "$it мс" },
                jitterMillis?.let { "джиттер $it мс" },
                packetLossPercent?.let { "потери $it%" },
            ).joinToString(" · ")
        }

    companion object {
        const val MONITOR_CHANNEL_ID = "status_speed_v2"
        const val ALERTS_CHANNEL_ID = "network_alerts"
        const val ACTION_REFRESH = "ru.yavasilek.netpulse.action.REFRESH_IP"
        const val ACTION_COPY = "ru.yavasilek.netpulse.action.COPY_IP"
        const val ACTION_STOP = "ru.yavasilek.netpulse.action.STOP"
    }
}
