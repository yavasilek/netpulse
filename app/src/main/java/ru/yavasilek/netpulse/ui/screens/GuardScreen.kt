package ru.yavasilek.netpulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.ui.components.DetailCard

@Composable
fun GuardScreen(
    snapshot: MonitorSnapshot,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vpnActive = snapshot.connection.isVpn
    val refreshing = snapshot.publicIp.isRefreshing
    val refreshingLabel = "Обновляется…"
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .background(
                    color = if (vpnActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = if (vpnActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
        }

        Text(
            text = if (vpnActive) {
                "VPN защищает соединение"
            } else {
                "VPN не обнаружен"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (snapshot.connection.status == ConnectionStatus.ONLINE) {
                "Доступ в интернет подтверждён"
            } else {
                "Состояние интернета уточняется"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DetailCard(
            rows = listOf(
                "Точка выхода" to if (refreshing) {
                    refreshingLabel
                } else {
                    listOfNotNull(
                        snapshot.publicIp.countryName,
                        snapshot.publicIp.countryCode,
                    ).joinToString(" · ").ifBlank { "Определяется" }
                },
                "Публичный IPv4" to if (refreshing) {
                    refreshingLabel
                } else {
                    snapshot.publicIp.ipv4?.address ?: "Не определён"
                },
                "IPv6" to if (refreshing) {
                    refreshingLabel
                } else {
                    snapshot.publicIp.ipv6?.address ?: "Не обнаружен"
                },
                "Оператор узла" to if (refreshing) {
                    refreshingLabel
                } else {
                    snapshot.publicIp.primary?.asnOrganization ?: "Не определён"
                },
            ),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecurityCheck(
                ok = vpnActive,
                text = if (vpnActive) "VPN активен" else "VPN сейчас не защищает трафик",
            )
            SecurityCheck(
                ok = !snapshot.publicIp.hasPossibleIpv6Leak,
                text = if (snapshot.publicIp.hasPossibleIpv6Leak) {
                    "IPv4 и IPv6 выходят через разные страны"
                } else {
                    "Признаков IPv6-утечки нет"
                },
            )
            SecurityCheck(
                ok = snapshot.publicIp.errorMessage == null,
                text = when {
                    refreshing -> "Обновляем IP и страну"
                    snapshot.publicIp.errorMessage != null -> snapshot.publicIp.errorMessage
                    else -> "Публичный IP проверен"
                },
            )
        }

        Button(
            onClick = onRefresh,
            enabled = !snapshot.publicIp.isRefreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (snapshot.publicIp.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Проверить сейчас")
            }
        }
    }
}

@Composable
private fun SecurityCheck(
    ok: Boolean,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(text, modifier = Modifier.weight(1f))
    }
}
