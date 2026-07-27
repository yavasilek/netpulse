package ru.yavasilek.netpulse.protection

import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.settings.AppSettings
import java.util.Locale

enum class ProtectionStatus {
    PROTECTED,
    ATTENTION,
    DANGER,
}

enum class ProtectionIssue {
    INTERNET_CHECKING,
    CAPTIVE_PORTAL,
    LIMITED_INTERNET,
    OFFLINE,
    VPN_REQUIRED,
    IP_REFRESHING,
    IP_UNAVAILABLE,
    IPV6_COUNTRY_MISMATCH,
    TRUSTED_EXIT_UNVERIFIED,
    TRUSTED_COUNTRY_MISMATCH,
    TRUSTED_PROVIDER_MISMATCH,
}

enum class ProtectionRouteState {
    SAFE,
    UNSAFE,
    INCONCLUSIVE,
}

data class ProtectionAssessment(
    val status: ProtectionStatus,
    val issues: Set<ProtectionIssue>,
    private val trustedExitConfigured: Boolean,
) {
    val routeState: ProtectionRouteState
        get() = when {
            issues.any {
                it == ProtectionIssue.INTERNET_CHECKING ||
                    it == ProtectionIssue.CAPTIVE_PORTAL ||
                    it == ProtectionIssue.OFFLINE ||
                    it == ProtectionIssue.IP_REFRESHING ||
                    it == ProtectionIssue.IP_UNAVAILABLE
            } -> ProtectionRouteState.INCONCLUSIVE
            issues.any {
                it == ProtectionIssue.IPV6_COUNTRY_MISMATCH ||
                    it == ProtectionIssue.TRUSTED_EXIT_UNVERIFIED ||
                    it == ProtectionIssue.TRUSTED_COUNTRY_MISMATCH ||
                    it == ProtectionIssue.TRUSTED_PROVIDER_MISMATCH
            } -> ProtectionRouteState.UNSAFE
            else -> ProtectionRouteState.SAFE
        }

    val trustedExitVerified: Boolean
        get() = trustedExitConfigured && status == ProtectionStatus.PROTECTED
}

object ProtectionEvaluator {
    fun evaluate(
        snapshot: MonitorSnapshot,
        settings: AppSettings,
    ): ProtectionAssessment {
        val issues = linkedSetOf<ProtectionIssue>()

        when (snapshot.connection.status) {
            ConnectionStatus.CHECKING -> issues += ProtectionIssue.INTERNET_CHECKING
            ConnectionStatus.ONLINE -> Unit
            ConnectionStatus.CAPTIVE_PORTAL -> issues += ProtectionIssue.CAPTIVE_PORTAL
            ConnectionStatus.LIMITED -> issues += ProtectionIssue.LIMITED_INTERNET
            ConnectionStatus.OFFLINE -> issues += ProtectionIssue.OFFLINE
        }

        if (settings.requireVpnForProtection && !snapshot.connection.isVpn) {
            issues += ProtectionIssue.VPN_REQUIRED
        }

        val publicIp = snapshot.publicIp
        when {
            publicIp.isRefreshing -> issues += ProtectionIssue.IP_REFRESHING
            publicIp.primary == null || publicIp.errorMessage != null ->
                issues += ProtectionIssue.IP_UNAVAILABLE
            else -> {
                if (publicIp.hasPossibleIpv6Leak) {
                    issues += ProtectionIssue.IPV6_COUNTRY_MISMATCH
                }
                settings.trustedExitProfile?.let { trusted ->
                    val current = publicIp.primary
                    val currentCountry = current?.countryCode
                    when {
                        currentCountry.isNullOrBlank() ->
                            issues += ProtectionIssue.TRUSTED_EXIT_UNVERIFIED
                        !currentCountry.equals(trusted.countryCode, ignoreCase = true) ->
                            issues += ProtectionIssue.TRUSTED_COUNTRY_MISMATCH
                    }

                    val trustedProvider = trusted.asnOrganization.normalized()
                    if (trustedProvider.isNotEmpty()) {
                        val currentProvider = current?.asnOrganization.normalized()
                        when {
                            currentProvider.isEmpty() ->
                                issues += ProtectionIssue.TRUSTED_EXIT_UNVERIFIED
                            currentProvider != trustedProvider ->
                                issues += ProtectionIssue.TRUSTED_PROVIDER_MISMATCH
                        }
                    }
                }
            }
        }

        val dangerIssues = setOf(
            ProtectionIssue.CAPTIVE_PORTAL,
            ProtectionIssue.OFFLINE,
            ProtectionIssue.VPN_REQUIRED,
            ProtectionIssue.IPV6_COUNTRY_MISMATCH,
        )
        val status = when {
            issues.any(dangerIssues::contains) -> ProtectionStatus.DANGER
            issues.isNotEmpty() -> ProtectionStatus.ATTENTION
            else -> ProtectionStatus.PROTECTED
        }
        return ProtectionAssessment(
            status = status,
            issues = issues,
            trustedExitConfigured = settings.trustedExitProfile != null,
        )
    }
}

private fun String?.normalized(): String = this
    ?.trim()
    ?.replace(Regex("\\s+"), " ")
    ?.lowercase(Locale.ROOT)
    ?.takeIf(String::isNotEmpty)
    .orEmpty()
