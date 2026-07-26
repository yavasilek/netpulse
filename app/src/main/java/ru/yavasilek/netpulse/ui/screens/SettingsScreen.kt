package ru.yavasilek.netpulse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yavasilek.netpulse.BuildConfig
import ru.yavasilek.netpulse.settings.AppSettings
import ru.yavasilek.netpulse.settings.SpeedUnit
import ru.yavasilek.netpulse.settings.StatusIconMode
import ru.yavasilek.netpulse.ui.components.SettingsSwitch
import ru.yavasilek.netpulse.update.UpdateState

@Composable
fun SettingsScreen(
    settings: AppSettings,
    updateState: UpdateState,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onMonitoringChange: (Boolean) -> Unit,
    onStartOnBootChange: (Boolean) -> Unit,
    onSpeedUnitChange: (SpeedUnit) -> Unit,
    onStatusIconModeChange: (StatusIconMode) -> Unit,
    onVpnWarningChange: (Boolean) -> Unit,
    onIpWarningChange: (Boolean) -> Unit,
    onAutomaticUpdatesChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: (ru.yavasilek.netpulse.update.ReleaseInfo) -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!notificationPermissionGranted) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Нужно разрешение", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Без уведомлений Android не сможет постоянно показывать скорость.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onRequestNotificationPermission) {
                            Text("Разрешить уведомления")
                        }
                    }
                }
            }
        }

        item {
            SettingsSection("Мониторинг") {
                SettingsSwitch(
                    title = "Показывать скорость",
                    description = "Закреплённое уведомление и статусная иконка",
                    checked = settings.monitoringEnabled,
                    onCheckedChange = onMonitoringChange,
                )
                SettingsSwitch(
                    title = "Запуск после перезагрузки",
                    description = "Возобновлять мониторинг автоматически",
                    checked = settings.startOnBoot,
                    onCheckedChange = onStartOnBootChange,
                )
                SettingsSwitch(
                    title = "Динамические цвета",
                    description = "Использовать цвета обоев на Android 12+",
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }
        }

        item {
            SettingsSection("Единицы скорости") {
                ChoiceRow(
                    title = "Мбит/с",
                    selected = settings.speedUnit == SpeedUnit.BITS_PER_SECOND,
                    onClick = { onSpeedUnitChange(SpeedUnit.BITS_PER_SECOND) },
                )
                ChoiceRow(
                    title = "МБ/с",
                    selected = settings.speedUnit == SpeedUnit.BYTES_PER_SECOND,
                    onClick = { onSpeedUnitChange(SpeedUnit.BYTES_PER_SECOND) },
                )
            }
        }

        item {
            SettingsSection("Иконка в статус-баре") {
                StatusIconMode.entries.forEach { mode ->
                    ChoiceRow(
                        title = mode.label(),
                        selected = settings.statusIconMode == mode,
                        onClick = { onStatusIconModeChange(mode) },
                    )
                }
            }
        }

        item {
            SettingsSection("Предупреждения") {
                SettingsSwitch(
                    title = "VPN отключился",
                    description = "Сообщать о потере VPN-маршрута",
                    checked = settings.warnWhenVpnDisconnects,
                    onCheckedChange = onVpnWarningChange,
                )
                SettingsSwitch(
                    title = "Публичный IP изменился",
                    description = "Записывать и показывать важные изменения",
                    checked = settings.warnWhenIpChanges,
                    onCheckedChange = onIpWarningChange,
                )
            }
        }

        item {
            SettingsSection("Обновления") {
                SettingsSwitch(
                    title = "Автоматическая проверка",
                    description = "Проверять GitHub Releases раз в сутки",
                    checked = settings.automaticUpdateChecks,
                    onCheckedChange = onAutomaticUpdatesChange,
                )
                UpdateContent(
                    state = updateState,
                    onCheck = onCheckUpdates,
                    onDownload = onDownloadUpdate,
                    onInstall = onInstallUpdate,
                )
            }
        }

        item {
            Text(
                text = "NetPulse ${BuildConfig.VERSION_NAME} · ${BuildConfig.GITHUB_REPOSITORY}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title)
    }
}

@Composable
private fun UpdateContent(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: (ru.yavasilek.netpulse.update.ReleaseInfo) -> Unit,
    onInstall: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val text = when (state) {
            UpdateState.Idle -> "Версия ещё не проверялась"
            UpdateState.Checking -> "Проверяем GitHub Releases…"
            is UpdateState.UpToDate -> "Установлена актуальная версия ${state.currentVersion}"
            is UpdateState.Available -> "Доступна версия ${state.release.versionName}"
            is UpdateState.Downloading -> "Загружается версия ${state.release.versionName}"
            is UpdateState.ReadyToInstall -> "Версия ${state.versionName} готова к установке"
            is UpdateState.Error -> state.message
        }
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (state) {
            is UpdateState.Available -> Button(onClick = { onDownload(state.release) }) {
                Text("Скачать APK")
            }
            is UpdateState.ReadyToInstall -> Button(onClick = onInstall) {
                Text("Установить")
            }
            UpdateState.Checking,
            is UpdateState.Downloading,
            -> Unit
            else -> Button(onClick = onCheck) {
                Text("Проверить обновление")
            }
        }
    }
}

private fun StatusIconMode.label(): String = when (this) {
    StatusIconMode.DOWNLOAD -> "Входящая скорость"
    StatusIconMode.UPLOAD -> "Исходящая скорость"
    StatusIconMode.DOMINANT -> "Большая из двух"
}
