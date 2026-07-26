package ru.yavasilek.netpulse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.MonitorSnapshot
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

        DetailCard(
            rows = listOf(
                "VPN" to if (snapshot.connection.isVpn) {
                    "Активен · ${snapshot.publicIp.countryName ?: "страна уточняется"}"
                } else {
                    "Не обнаружен"
                },
                "IPv4" to (snapshot.publicIp.ipv4?.address ?: "Не определён"),
                "Подключение" to snapshot.connection.transportLabel,
            ),
        )
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
