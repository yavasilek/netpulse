package ru.yavasilek.netpulse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.yavasilek.netpulse.model.ConnectionInfo
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.protection.ProtectionAssessment
import ru.yavasilek.netpulse.protection.ProtectionIssue
import ru.yavasilek.netpulse.protection.ProtectionStatus
import ru.yavasilek.netpulse.settings.TrustedExitProfile
import ru.yavasilek.netpulse.ui.theme.ProtectionActiveContainerDark
import ru.yavasilek.netpulse.ui.theme.ProtectionActiveContainerLight
import ru.yavasilek.netpulse.ui.theme.ProtectionActiveContentDark
import ru.yavasilek.netpulse.ui.theme.ProtectionActiveContentLight
import ru.yavasilek.netpulse.ui.theme.ProtectionAttentionContainerDark
import ru.yavasilek.netpulse.ui.theme.ProtectionAttentionContainerLight
import ru.yavasilek.netpulse.ui.theme.ProtectionAttentionContentDark
import ru.yavasilek.netpulse.ui.theme.ProtectionAttentionContentLight
import ru.yavasilek.netpulse.ui.theme.ProtectionAttentionDark
import ru.yavasilek.netpulse.ui.theme.ProtectionAttentionLight
import ru.yavasilek.netpulse.ui.theme.PulseGreen
import ru.yavasilek.netpulse.ui.theme.PulseGreenDark

@Composable
internal fun TrustedExitCard(
    snapshot: MonitorSnapshot,
    profile: TrustedExitProfile?,
    trustedExitMatches: Boolean,
    vpnRequired: Boolean,
    darkTheme: Boolean,
    onTrustCurrentExit: () -> Unit,
    onClearTrustedExit: () -> Unit,
) {
    val current = snapshot.publicIp.primary
    val waitingForVpn = vpnRequired && !snapshot.connection.isVpn
    val canTrust = snapshot.connection.status == ConnectionStatus.ONLINE &&
        !waitingForVpn &&
        !snapshot.publicIp.isRefreshing &&
        snapshot.publicIp.errorMessage == null &&
        !current?.countryCode.isNullOrBlank()
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Доверенная точка",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (profile == null) {
                Text(
                    text = if (waitingForVpn) {
                        "Подключите VPN, затем сохраните его страну и оператора как доверенные."
                    } else {
                        "Сохраните текущую страну и оператора. NetPulse предупредит, если маршрут изменится."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onTrustCurrentExit,
                    enabled = canTrust,
                ) {
                    Text("Запомнить текущую")
                }
            } else {
                Text(profile.placeLabel(), fontWeight = FontWeight.Medium)
                profile.asnOrganization?.let {
                    Text(
                        "Оператор: $it",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val verified = canTrust && trustedExitMatches
                Text(
                    text = when {
                        waitingForVpn -> "Подключите VPN для проверки эталона"
                        verified -> "Текущий маршрут совпадает с эталоном"
                        snapshot.publicIp.isRefreshing -> "Проверяем текущий маршрут…"
                        else -> "Текущий маршрут отличается или ещё не проверен"
                    },
                    color = if (verified) {
                        if (darkTheme) PulseGreenDark else PulseGreen
                    } else {
                        if (darkTheme) ProtectionAttentionDark else ProtectionAttentionLight
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onTrustCurrentExit,
                        enabled = canTrust,
                    ) {
                        Text("Обновить эталон")
                    }
                    TextButton(onClick = onClearTrustedExit) {
                        Text("Удалить")
                    }
                }
            }
        }
    }
}

internal enum class CheckState {
    SUCCESS,
    ATTENTION,
    DANGER,
}

@Composable
internal fun ProtectionCheckRow(
    state: CheckState,
    text: String,
    darkTheme: Boolean,
) {
    val color = when (state) {
        CheckState.SUCCESS -> if (darkTheme) PulseGreenDark else PulseGreen
        CheckState.ATTENTION -> if (darkTheme) {
            ProtectionAttentionDark
        } else {
            ProtectionAttentionLight
        }
        CheckState.DANGER -> MaterialTheme.colorScheme.error
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (state) {
                CheckState.SUCCESS -> Icons.Outlined.CheckCircle
                CheckState.ATTENTION -> Icons.Outlined.WarningAmber
                CheckState.DANGER -> Icons.Outlined.ErrorOutline
            },
            contentDescription = null,
            tint = color,
        )
        Text(text, modifier = Modifier.weight(1f))
    }
}

internal data class GuardPalette(
    val container: Color,
    val content: Color,
    val accent: Color,
)

@Composable
internal fun guardPalette(
    status: ProtectionStatus,
    darkTheme: Boolean,
): GuardPalette = when (status) {
    ProtectionStatus.PROTECTED -> GuardPalette(
        container = if (darkTheme) {
            ProtectionActiveContainerDark
        } else {
            ProtectionActiveContainerLight
        },
        content = if (darkTheme) {
            ProtectionActiveContentDark
        } else {
            ProtectionActiveContentLight
        },
        accent = if (darkTheme) PulseGreenDark else PulseGreen,
    )
    ProtectionStatus.ATTENTION -> GuardPalette(
        container = if (darkTheme) {
            ProtectionAttentionContainerDark
        } else {
            ProtectionAttentionContainerLight
        },
        content = if (darkTheme) {
            ProtectionAttentionContentDark
        } else {
            ProtectionAttentionContentLight
        },
        accent = if (darkTheme) ProtectionAttentionDark else ProtectionAttentionLight,
    )
    ProtectionStatus.DANGER -> GuardPalette(
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        accent = MaterialTheme.colorScheme.error,
    )
}

internal fun ProtectionAssessment.title(snapshot: MonitorSnapshot): String = when {
    ProtectionIssue.OFFLINE in issues -> "Нет доступа к интернету"
    ProtectionIssue.CAPTIVE_PORTAL in issues -> "Требуется вход в сеть"
    status == ProtectionStatus.DANGER -> "Соединение не защищено"
    status == ProtectionStatus.ATTENTION -> "Защита требует внимания"
    snapshot.connection.isVpn -> "Соединение защищено"
    else -> "Соединение соответствует профилю"
}

internal fun ProtectionAssessment.summary(): String = when {
    ProtectionIssue.VPN_REQUIRED in issues -> "VPN не обнаружен"
    ProtectionIssue.IPV6_COUNTRY_MISMATCH in issues ->
        "Маршруты IPv4 и IPv6 различаются"
    ProtectionIssue.TRUSTED_COUNTRY_MISMATCH in issues ->
        "Страна выхода отличается от доверенной"
    ProtectionIssue.TRUSTED_PROVIDER_MISMATCH in issues ->
        "Оператор выхода отличается от доверенного"
    ProtectionIssue.TRUSTED_EXIT_UNVERIFIED in issues ->
        "Не удалось сравнить точку выхода"
    ProtectionIssue.IP_REFRESHING in issues -> "Проверяем IP и страну"
    ProtectionIssue.IP_UNAVAILABLE in issues -> "Публичный IP пока не определён"
    ProtectionIssue.CAPTIVE_PORTAL in issues -> "Подключитесь к публичной сети"
    ProtectionIssue.OFFLINE in issues -> "Соединение потеряно"
    ProtectionIssue.LIMITED_INTERNET in issues -> "Интернет пока не подтверждён"
    ProtectionIssue.INTERNET_CHECKING in issues -> "Проверяем доступ в интернет"
    else -> "Все проверки пройдены"
}

internal fun ConnectionInfo.checkState(): CheckState = when (status) {
    ConnectionStatus.ONLINE -> CheckState.SUCCESS
    ConnectionStatus.CHECKING,
    ConnectionStatus.LIMITED,
    -> CheckState.ATTENTION
    ConnectionStatus.CAPTIVE_PORTAL,
    ConnectionStatus.OFFLINE,
    -> CheckState.DANGER
}

internal fun ConnectionStatus.checkLabel(): String = when (this) {
    ConnectionStatus.CHECKING -> "Проверяем доступ в интернет"
    ConnectionStatus.ONLINE -> "Доступ в интернет подтверждён"
    ConnectionStatus.CAPTIVE_PORTAL -> "Требуется войти в сеть"
    ConnectionStatus.LIMITED -> "Интернет пока не подтверждён"
    ConnectionStatus.OFFLINE -> "Нет доступа в интернет"
}

private fun TrustedExitProfile.placeLabel(): String = listOfNotNull(
    countryName,
    countryCode,
).joinToString(" · ")
