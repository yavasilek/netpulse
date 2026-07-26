package ru.yavasilek.netpulse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import ru.yavasilek.netpulse.protection.ProtectionEvaluator
import ru.yavasilek.netpulse.settings.AppSettings
import ru.yavasilek.netpulse.ui.components.DetailCard

@Composable
fun GuardScreen(
    snapshot: MonitorSnapshot,
    settings: AppSettings,
    onRefresh: () -> Unit,
    onTrustCurrentExit: () -> Unit,
    onClearTrustedExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val assessment = ProtectionEvaluator.evaluate(snapshot, settings)
    val darkTheme = isSystemInDarkTheme()
    val palette = guardPalette(assessment.status, darkTheme)
    val refreshing = snapshot.publicIp.isRefreshing
    val refreshingLabel = "Обновляется…"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .background(palette.container, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = palette.content,
                    )
                }
                Text(
                    text = assessment.title(snapshot),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = assessment.summary(),
                    color = palette.accent,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
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
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProtectionCheckRow(
                    state = snapshot.connection.checkState(),
                    text = snapshot.connection.status.checkLabel(),
                    darkTheme = darkTheme,
                )
                ProtectionCheckRow(
                    state = when {
                        snapshot.connection.isVpn -> CheckState.SUCCESS
                        settings.requireVpnForProtection -> CheckState.DANGER
                        else -> CheckState.SUCCESS
                    },
                    text = when {
                        snapshot.connection.isVpn -> "VPN активен"
                        settings.requireVpnForProtection -> "VPN обязателен, но не обнаружен"
                        else -> "VPN не требуется профилем"
                    },
                    darkTheme = darkTheme,
                )
                ProtectionCheckRow(
                    state = when {
                        refreshing -> CheckState.ATTENTION
                        snapshot.publicIp.errorMessage != null -> CheckState.ATTENTION
                        snapshot.publicIp.primary == null -> CheckState.ATTENTION
                        else -> CheckState.SUCCESS
                    },
                    text = when {
                        refreshing -> "Обновляем IP и страну"
                        snapshot.publicIp.errorMessage != null ->
                            requireNotNull(snapshot.publicIp.errorMessage)
                        snapshot.publicIp.primary == null -> "Публичный IP ещё не проверен"
                        else -> "Публичный IP проверен"
                    },
                    darkTheme = darkTheme,
                )
                ProtectionCheckRow(
                    state = when {
                        refreshing ||
                            snapshot.publicIp.errorMessage != null ||
                            snapshot.publicIp.primary == null -> CheckState.ATTENTION
                        snapshot.publicIp.hasPossibleIpv6Leak -> CheckState.DANGER
                        else -> CheckState.SUCCESS
                    },
                    text = when {
                        refreshing -> "Проверяем маршрут IPv6"
                        snapshot.publicIp.errorMessage != null ||
                            snapshot.publicIp.primary == null -> "IPv6-маршрут ещё не проверен"
                        snapshot.publicIp.hasPossibleIpv6Leak ->
                            "IPv4 и IPv6 выходят через разные страны"
                        else -> "Признаков IPv6-утечки нет"
                    },
                    darkTheme = darkTheme,
                )
                settings.trustedExitProfile?.let {
                    val vpnRequirementMet =
                        !settings.requireVpnForProtection || snapshot.connection.isVpn
                    val trustedExitVerified =
                        vpnRequirementMet &&
                        !refreshing &&
                        assessment.trustedExitMatches &&
                        snapshot.publicIp.primary != null
                    ProtectionCheckRow(
                        state = if (trustedExitVerified) {
                            CheckState.SUCCESS
                        } else {
                            CheckState.ATTENTION
                        },
                        text = if (trustedExitVerified) {
                            "Точка выхода совпадает с доверенной"
                        } else {
                            "Точка выхода требует проверки"
                        },
                        darkTheme = darkTheme,
                    )
                }
            }
        }

        item {
            TrustedExitCard(
                snapshot = snapshot,
                profile = settings.trustedExitProfile,
                trustedExitMatches = assessment.trustedExitMatches,
                vpnRequired = settings.requireVpnForProtection,
                darkTheme = darkTheme,
                onTrustCurrentExit = onTrustCurrentExit,
                onClearTrustedExit = onClearTrustedExit,
            )
        }

        item {
            Button(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (refreshing) {
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
}
