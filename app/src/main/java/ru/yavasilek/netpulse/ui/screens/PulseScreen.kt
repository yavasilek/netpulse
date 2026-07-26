package ru.yavasilek.netpulse.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.model.NetworkQuality
import ru.yavasilek.netpulse.model.NetworkQualityStatus
import ru.yavasilek.netpulse.settings.SpeedUnit
import ru.yavasilek.netpulse.ui.components.DetailCard
import ru.yavasilek.netpulse.ui.components.SpeedChart
import ru.yavasilek.netpulse.ui.components.StatusLine
import ru.yavasilek.netpulse.ui.theme.PulseAmber
import ru.yavasilek.netpulse.ui.theme.PulseGreen
import ru.yavasilek.netpulse.ui.theme.PulseRed
import ru.yavasilek.netpulse.util.SpeedFormatter

@Composable
fun PulseScreen(
    snapshot: MonitorSnapshot,
    speedUnit: SpeedUnit,
    onRefreshQuality: () -> Unit,
    onShareDiagnosticReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (snapshot.connection.status) {
        ConnectionStatus.ONLINE -> PulseGreen
        ConnectionStatus.CHECKING,
        ConnectionStatus.CAPTIVE_PORTAL,
        ConnectionStatus.LIMITED,
        -> PulseAmber
        ConnectionStatus.OFFLINE -> PulseRed
    }
    val statusText = when (snapshot.connection.status) {
        ConnectionStatus.ONLINE -> "Интернет работает"
        ConnectionStatus.CHECKING -> "Проверяем соединение"
        ConnectionStatus.CAPTIVE_PORTAL -> "Требуется вход в сеть"
        ConnectionStatus.LIMITED -> "Интернет не подтверждён"
        ConnectionStatus.OFFLINE -> "Нет подключения"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StatusLine(title = statusText, color = statusColor)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SpeedValue(
                label = "Входящая",
                value = SpeedFormatter.format(
                    snapshot.speed.receivedBytesPerSecond,
                    speedUnit,
                ),
                modifier = Modifier.weight(1f),
            )
            SpeedValue(
                label = "Исходящая",
                value = SpeedFormatter.format(
                    snapshot.speed.transmittedBytesPerSecond,
                    speedUnit,
                ),
                modifier = Modifier.weight(1f),
            )
        }

        SpeedChart(samples = snapshot.speedHistory)

        QualityCard(
            quality = snapshot.quality,
            onRefresh = onRefreshQuality,
        )

        DetailCard(
            rows = listOf(
                "VPN" to if (snapshot.connection.isVpn) {
                    if (snapshot.publicIp.isRefreshing) {
                        "Активен · страна обновляется"
                    } else {
                        "Активен · ${snapshot.publicIp.countryName ?: "страна уточняется"}"
                    }
                } else {
                    "Не обнаружен"
                },
                "IPv4" to if (snapshot.publicIp.isRefreshing) {
                    "Обновляется…"
                } else {
                    snapshot.publicIp.ipv4?.address ?: "Не определён"
                },
                "Подключение" to snapshot.connection.transportLabel,
            ),
        )

        OutlinedButton(
            onClick = onShareDiagnosticReport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Text(
                text = "Поделиться диагностикой",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun QualityCard(
    quality: NetworkQuality,
    onRefresh: () -> Unit,
) {
    val color = when (quality.status) {
        NetworkQualityStatus.EXCELLENT,
        NetworkQualityStatus.GOOD,
        -> PulseGreen
        NetworkQualityStatus.CHECKING -> PulseAmber
        NetworkQualityStatus.UNSTABLE,
        NetworkQualityStatus.UNAVAILABLE,
        -> PulseRed
    }
    val title = when (quality.status) {
        NetworkQualityStatus.CHECKING -> "Проверяем качество"
        NetworkQualityStatus.EXCELLENT -> "Отличное соединение"
        NetworkQualityStatus.GOOD -> "Хорошее соединение"
        NetworkQualityStatus.UNSTABLE -> "Нестабильное соединение"
        NetworkQualityStatus.UNAVAILABLE -> "Качество не определено"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                StatusLine(title = title, color = color)
                IconButton(
                    onClick = onRefresh,
                    enabled = quality.status != NetworkQualityStatus.CHECKING,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Проверить качество")
                }
            }
            if (quality.status == NetworkQualityStatus.UNAVAILABLE) {
                Text(
                    quality.errorMessage ?: "Проверочные узлы не ответили",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    QualityMetric("Задержка", quality.latencyMillis?.let { "$it мс" } ?: "—")
                    QualityMetric("Джиттер", quality.jitterMillis?.let { "$it мс" } ?: "—")
                    QualityMetric(
                        "Потери",
                        quality.packetLossPercent?.let { "$it%" } ?: "—",
                    )
                }
            }
            Text(
                "Лёгкая проверка TCP, без расходующего трафик speed test.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QualityMetric(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SpeedValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = value.substringBeforeLast(' ', value),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = value.substringAfterLast(' ', ""),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
