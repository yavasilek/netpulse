package ru.yavasilek.netpulse.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import ru.yavasilek.netpulse.settings.SpeedUnit
import ru.yavasilek.netpulse.settings.StatusIconMode
import ru.yavasilek.netpulse.ui.screens.EventsScreen
import ru.yavasilek.netpulse.ui.screens.GuardScreen
import ru.yavasilek.netpulse.ui.screens.PulseScreen
import ru.yavasilek.netpulse.ui.screens.SettingsScreen
import ru.yavasilek.netpulse.update.ReleaseInfo

private enum class MainDestination(
    val label: String,
    val icon: ImageVector,
) {
    PULSE("Пульс", Icons.Outlined.Speed),
    GUARD("Защита", Icons.Outlined.Shield),
    EVENTS("События", Icons.Outlined.History),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetPulseApp(
    state: NetPulseUiState,
    notificationPermissionGranted: Boolean,
    batteryOptimizationIgnored: Boolean,
    openUpdateSettings: Boolean,
    onUpdateSettingsOpened: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefreshPublicIp: () -> Unit,
    onRefreshNetworkQuality: () -> Unit,
    onShareDiagnosticReport: () -> Unit,
    onShareEvents: () -> Unit,
    onMonitoringChange: (Boolean) -> Unit,
    onStartOnBootChange: (Boolean) -> Unit,
    onSpeedUnitChange: (SpeedUnit) -> Unit,
    onStatusIconModeChange: (StatusIconMode) -> Unit,
    onVpnWarningChange: (Boolean) -> Unit,
    onIpWarningChange: (Boolean) -> Unit,
    onAutomaticUpdatesChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onLockScreenDetailsChange: (Boolean) -> Unit,
    onRequireVpnForProtectionChange: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenVpnSettings: () -> Unit,
    onTrustCurrentExit: () -> Unit,
    onClearTrustedExit: () -> Unit,
    onClearEvents: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: (ReleaseInfo) -> Unit,
    onInstallUpdate: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(MainDestination.PULSE) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openUpdateSettings) {
        if (openUpdateSettings) {
            showSettings = true
            onUpdateSettingsOpened()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "Настройки" else "NetPulse") },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = if (showSettings) {
                                "Закрыть настройки"
                            } else {
                                "Открыть настройки"
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    MainDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { destination = item },
                            icon = {
                                Icon(item.icon, contentDescription = null)
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        if (showSettings) {
            SettingsScreen(
                settings = state.settings,
                snapshot = state.monitor,
                updateState = state.update,
                notificationPermissionGranted = notificationPermissionGranted,
                batteryOptimizationIgnored = batteryOptimizationIgnored,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onMonitoringChange = onMonitoringChange,
                onStartOnBootChange = onStartOnBootChange,
                onSpeedUnitChange = onSpeedUnitChange,
                onStatusIconModeChange = onStatusIconModeChange,
                onVpnWarningChange = onVpnWarningChange,
                onIpWarningChange = onIpWarningChange,
                onAutomaticUpdatesChange = onAutomaticUpdatesChange,
                onDynamicColorChange = onDynamicColorChange,
                onLockScreenDetailsChange = onLockScreenDetailsChange,
                onRequireVpnForProtectionChange = onRequireVpnForProtectionChange,
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenVpnSettings = onOpenVpnSettings,
                onCheckUpdates = onCheckUpdates,
                onDownloadUpdate = onDownloadUpdate,
                onInstallUpdate = onInstallUpdate,
                modifier = modifier,
            )
        } else {
            when (destination) {
                MainDestination.PULSE -> PulseScreen(
                    snapshot = state.monitor,
                    speedUnit = state.settings.speedUnit,
                    onRefreshQuality = onRefreshNetworkQuality,
                    onShareDiagnosticReport = onShareDiagnosticReport,
                    modifier = modifier,
                )
                MainDestination.GUARD -> GuardScreen(
                    snapshot = state.monitor,
                    settings = state.settings,
                    onRefresh = onRefreshPublicIp,
                    onTrustCurrentExit = onTrustCurrentExit,
                    onClearTrustedExit = onClearTrustedExit,
                    modifier = modifier,
                )
                MainDestination.EVENTS -> EventsScreen(
                    events = state.events,
                    onClear = onClearEvents,
                    onShare = onShareEvents,
                    modifier = modifier,
                )
            }
        }
    }
}
