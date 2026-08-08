package ru.yavasilek.netpulse.monitoring

import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import ru.yavasilek.netpulse.appContainer
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.protection.ProtectionRouteTracker
import ru.yavasilek.netpulse.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class NetworkMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationFactory: MonitorNotificationFactory
    private lateinit var notificationManager: NotificationManager
    private var notificationJob: Job? = null
    private val vpnDisconnectAlertTracker = VpnDisconnectAlertTracker()
    private var previousPublicIp: String? = null
    private val protectionRouteTracker = ProtectionRouteTracker()

    override fun onCreate() {
        super.onCreate()
        notificationFactory = MonitorNotificationFactory(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationFactory.ensureChannels()
        startInForeground(
            notificationFactory.build(
                snapshot = appContainer.monitorRepository.state.value,
                settings = AppSettings(),
            ),
        )
        appContainer.monitorRepository.startMonitoring()
        observeNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, MonitorNotificationFactory.ACTION_STOP -> pauseMonitoring()
            ACTION_REFRESH_IP, MonitorNotificationFactory.ACTION_REFRESH ->
                appContainer.monitorRepository.refreshPublicIp()
            MonitorNotificationFactory.ACTION_COPY -> copyPublicIp()
            ACTION_START, null -> appContainer.monitorRepository.startMonitoring()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        appContainer.monitorRepository.stopMonitoring()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeNotification() {
        notificationJob = scope.launch {
            combine(
                appContainer.monitorRepository.state,
                appContainer.settingsRepository.settings,
            ) { snapshot, settings -> snapshot to settings }
                .collect { (snapshot, settings) ->
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        notificationFactory.build(snapshot, settings),
                    )
                    maybeWarnAboutVpn(snapshot, settings)
                    maybeWarnAboutIp(snapshot, settings)
                    maybeWarnAboutProtectionRoute(snapshot, settings)
                }
        }
    }

    private fun maybeWarnAboutProtectionRoute(
        snapshot: MonitorSnapshot,
        settings: AppSettings,
    ) {
        val warning = protectionRouteTracker.onSnapshot(snapshot, settings) ?: return
        appContainer.eventStore.add(
            type = warning.type,
            title = warning.title,
            detail = warning.detail,
        )
        notificationManager.notify(
            PROTECTION_ALERT_NOTIFICATION_ID,
            notificationFactory.buildAlert(
                title = "Проверьте защиту",
                message = warning.detail,
            ),
        )
    }

    private fun maybeWarnAboutIp(snapshot: MonitorSnapshot, settings: AppSettings) {
        val current = snapshot.publicIp.primary?.address ?: return
        val previous = previousPublicIp
        previousPublicIp = current
        if (previous != null && previous != current && settings.warnWhenIpChanges) {
            notificationManager.notify(
                IP_ALERT_NOTIFICATION_ID,
                notificationFactory.buildAlert(
                    title = "Публичный IP изменился",
                    message = "$previous → $current",
                ),
            )
        }
    }

    private fun maybeWarnAboutVpn(snapshot: MonitorSnapshot, settings: AppSettings) {
        when (
            vpnDisconnectAlertTracker.onConnectionChanged(
                isVpn = snapshot.connection.isVpn,
                warningEnabled = settings.warnWhenVpnDisconnects,
            )
        ) {
            VpnDisconnectAlertAction.SHOW -> {
                postVpnDisconnectAlert(snapshot, settings)
            }

            VpnDisconnectAlertAction.UPDATE -> {
                if (isNotificationActive(VPN_ALERT_NOTIFICATION_ID)) {
                    postVpnDisconnectAlert(snapshot, settings)
                }
            }

            VpnDisconnectAlertAction.CANCEL -> {
                notificationManager.cancel(VPN_ALERT_NOTIFICATION_ID)
            }

            VpnDisconnectAlertAction.NONE -> Unit
        }
    }

    private fun postVpnDisconnectAlert(
        snapshot: MonitorSnapshot,
        settings: AppSettings,
    ) {
        notificationManager.notify(
            VPN_ALERT_NOTIFICATION_ID,
            notificationFactory.buildVpnDisconnectedAlert(snapshot, settings),
        )
    }

    private fun isNotificationActive(notificationId: Int): Boolean =
        runCatching {
            notificationManager.activeNotifications.any { it.id == notificationId }
        }.getOrDefault(false)

    private fun copyPublicIp() {
        val address = appContainer.monitorRepository.state.value.publicIp.primary?.address
        if (address == null) {
            Toast.makeText(this, "IP пока не определён", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Публичный IP", address))
        Toast.makeText(this, "IP скопирован", Toast.LENGTH_SHORT).show()
    }

    private fun pauseMonitoring() {
        scope.launch {
            appContainer.settingsRepository.setMonitoringEnabled(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startInForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_START = "ru.yavasilek.netpulse.action.START"
        const val ACTION_STOP = "ru.yavasilek.netpulse.action.STOP_SERVICE"
        const val ACTION_REFRESH_IP = "ru.yavasilek.netpulse.action.REFRESH_SERVICE_IP"
        const val NOTIFICATION_ID = 1001
        const val VPN_ALERT_NOTIFICATION_ID = 1002
        const val IP_ALERT_NOTIFICATION_ID = 1003
        const val PROTECTION_ALERT_NOTIFICATION_ID = 1004
    }
}
