package ru.yavasilek.netpulse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.yavasilek.netpulse.monitoring.MonitoringController
import ru.yavasilek.netpulse.ui.NetPulseApp
import ru.yavasilek.netpulse.ui.NetPulseViewModel
import ru.yavasilek.netpulse.ui.theme.NetPulseTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val notificationPermissionGranted = MutableStateFlow(false)
    private val openUpdateSettings = MutableStateFlow(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted.value = granted
        if (granted) {
            MonitoringController.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationPermissionGranted.value = hasNotificationPermission()
        openUpdateSettings.value = intent.getBooleanExtra(EXTRA_OPEN_UPDATE, false)

        setContent {
            val viewModel: NetPulseViewModel = viewModel(
                factory = NetPulseViewModel.Factory(application, appContainer),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val permissionGranted by notificationPermissionGranted.collectAsState()
            val shouldOpenUpdate by openUpdateSettings.collectAsState()

            LaunchedEffect(state.settings.monitoringEnabled, permissionGranted) {
                if (
                    state.settings.monitoringEnabled &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || permissionGranted)
                ) {
                    MonitoringController.start(this@MainActivity)
                }
            }

            NetPulseTheme(dynamicColor = state.settings.dynamicColor) {
                NetPulseApp(
                    state = state,
                    notificationPermissionGranted = permissionGranted,
                    openUpdateSettings = shouldOpenUpdate,
                    onUpdateSettingsOpened = { openUpdateSettings.value = false },
                    onRequestNotificationPermission = ::requestNotificationPermission,
                    onRefreshPublicIp = viewModel::refreshPublicIp,
                    onMonitoringChange = { enabled ->
                        if (enabled && !hasNotificationPermission()) {
                            requestNotificationPermission()
                        } else {
                            viewModel.setMonitoringEnabled(enabled)
                        }
                    },
                    onStartOnBootChange = viewModel::setStartOnBoot,
                    onSpeedUnitChange = viewModel::setSpeedUnit,
                    onStatusIconModeChange = viewModel::setStatusIconMode,
                    onVpnWarningChange = viewModel::setVpnWarning,
                    onIpWarningChange = viewModel::setIpWarning,
                    onAutomaticUpdatesChange = viewModel::setAutomaticUpdates,
                    onDynamicColorChange = viewModel::setDynamicColor,
                    onRequireVpnForProtectionChange = viewModel::setRequireVpnForProtection,
                    onTrustCurrentExit = viewModel::trustCurrentExit,
                    onClearTrustedExit = viewModel::clearTrustedExit,
                    onClearEvents = viewModel::clearEvents,
                    onCheckUpdates = viewModel::checkForUpdates,
                    onDownloadUpdate = viewModel::downloadUpdate,
                    onInstallUpdate = { viewModel.requestInstall() },
                )
            }
        }

        if (
            savedInstanceState == null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission() &&
            !getSharedPreferences(PERMISSION_PREFERENCES, MODE_PRIVATE)
                .getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)
        ) {
            getSharedPreferences(PERMISSION_PREFERENCES, MODE_PRIVATE).edit {
                putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true)
            }
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATE, false)) {
            openUpdateSettings.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        notificationPermissionGranted.value = hasNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationPermissionGranted.value = true
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val EXTRA_OPEN_UPDATE = "open_update"
        private const val PERMISSION_PREFERENCES = "permission_prompts"
        private const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_asked"
    }
}
