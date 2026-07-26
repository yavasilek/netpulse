package ru.yavasilek.netpulse.monitoring

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object MonitoringController {
    fun start(context: Context) {
        val intent = Intent(context, NetworkMonitorService::class.java)
            .setAction(NetworkMonitorService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, NetworkMonitorService::class.java)
            .setAction(NetworkMonitorService.ACTION_STOP)
        context.startService(intent)
    }

    fun refreshIp(context: Context) {
        val intent = Intent(context, NetworkMonitorService::class.java)
            .setAction(NetworkMonitorService.ACTION_REFRESH_IP)
        context.startService(intent)
    }
}
