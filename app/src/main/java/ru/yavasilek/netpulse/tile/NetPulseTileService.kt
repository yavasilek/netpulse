package ru.yavasilek.netpulse.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.os.Build
import ru.yavasilek.netpulse.appContainer
import ru.yavasilek.netpulse.monitoring.MonitoringController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NetPulseTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        observeJob?.cancel()
        observeJob = scope.launch {
            appContainer.monitorRepository.state.collectLatest { snapshot ->
                qsTile?.apply {
                    state = if (snapshot.isMonitoring) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        subtitle = when {
                            snapshot.connection.isVpn ->
                                "VPN · ${snapshot.publicIp.countryCode ?: "…"}"
                            snapshot.isMonitoring -> "Мониторинг включён"
                            else -> "Мониторинг выключен"
                        }
                    }
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        observeJob?.cancel()
        observeJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val enabled = !appContainer.monitorRepository.state.value.isMonitoring
            appContainer.settingsRepository.setMonitoringEnabled(enabled)
            if (enabled) {
                MonitoringController.start(this@NetPulseTileService)
            } else {
                MonitoringController.stop(this@NetPulseTileService)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
