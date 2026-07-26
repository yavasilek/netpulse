package ru.yavasilek.netpulse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ru.yavasilek.netpulse.AppContainer
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.model.NetworkEvent
import ru.yavasilek.netpulse.monitoring.MonitoringController
import ru.yavasilek.netpulse.settings.AppSettings
import ru.yavasilek.netpulse.settings.SpeedUnit
import ru.yavasilek.netpulse.settings.StatusIconMode
import ru.yavasilek.netpulse.settings.TrustedExitProfile
import ru.yavasilek.netpulse.update.ReleaseInfo
import ru.yavasilek.netpulse.update.UpdateScheduler
import ru.yavasilek.netpulse.update.UpdateState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class NetPulseUiState(
    val monitor: MonitorSnapshot = MonitorSnapshot(),
    val settings: AppSettings = AppSettings(),
    val events: List<NetworkEvent> = emptyList(),
    val update: UpdateState = UpdateState.Idle,
)

class NetPulseViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {
    val uiState: StateFlow<NetPulseUiState> = combine(
        container.monitorRepository.state,
        container.settingsRepository.settings,
        container.eventStore.events,
        container.updateManager.state,
    ) { monitor, settings, events, update ->
        NetPulseUiState(
            monitor = monitor,
            settings = settings,
            events = events,
            update = update,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NetPulseUiState(),
    )

    fun refreshPublicIp() {
        container.monitorRepository.refreshPublicIp()
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setMonitoringEnabled(enabled)
            if (enabled) {
                MonitoringController.start(getApplication())
            } else {
                MonitoringController.stop(getApplication())
            }
        }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setStartOnBoot(enabled)
        }
    }

    fun setSpeedUnit(unit: SpeedUnit) {
        viewModelScope.launch {
            container.settingsRepository.setSpeedUnit(unit)
        }
    }

    fun setStatusIconMode(mode: StatusIconMode) {
        viewModelScope.launch {
            container.settingsRepository.setStatusIconMode(mode)
        }
    }

    fun setVpnWarning(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setWarnWhenVpnDisconnects(enabled)
        }
    }

    fun setIpWarning(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setWarnWhenIpChanges(enabled)
        }
    }

    fun setAutomaticUpdates(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setAutomaticUpdateChecks(enabled)
            if (enabled) {
                UpdateScheduler.sync(getApplication())
            } else {
                UpdateScheduler.cancel(getApplication())
            }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setRequireVpnForProtection(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setRequireVpnForProtection(enabled)
        }
    }

    fun trustCurrentExit() {
        val current = uiState.value.monitor.publicIp.primary ?: return
        val countryCode = current.countryCode ?: return
        viewModelScope.launch {
            container.settingsRepository.setTrustedExitProfile(
                TrustedExitProfile(
                    countryCode = countryCode.uppercase(Locale.ROOT),
                    countryName = current.countryName,
                    asnOrganization = current.asnOrganization,
                ),
            )
        }
    }

    fun clearTrustedExit() {
        viewModelScope.launch {
            container.settingsRepository.setTrustedExitProfile(null)
        }
    }

    fun clearEvents() {
        container.eventStore.clear()
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            container.updateManager.check()
        }
    }

    fun downloadUpdate(release: ReleaseInfo) {
        container.updateManager.download(release)
    }

    fun requestInstall(): ru.yavasilek.netpulse.update.UpdateManager.InstallRequestResult =
        container.updateManager.requestInstall()

    class Factory(
        private val application: Application,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NetPulseViewModel::class.java))
            return NetPulseViewModel(application, container) as T
        }
    }
}
