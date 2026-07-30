package ru.yavasilek.netpulse.protection

import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.settings.AppSettings
import ru.yavasilek.netpulse.settings.TrustedExitProfile
import java.util.Locale

enum class TrustCurrentExitRejection {
    INTERNET_NOT_VALIDATED,
    VPN_REQUIRED,
    IP_REFRESHING,
    IP_LOOKUP_FAILED,
    IP_UNAVAILABLE,
    IPV6_ROUTE_MISMATCH,
    COUNTRY_UNAVAILABLE,
}

sealed interface TrustCurrentExitDecision {
    data class Ready(val profile: TrustedExitProfile) : TrustCurrentExitDecision

    data class Rejected(
        val reason: TrustCurrentExitRejection,
    ) : TrustCurrentExitDecision
}

sealed interface TrustCurrentExitResult {
    data class Saved(val profile: TrustedExitProfile) : TrustCurrentExitResult

    data class Rejected(
        val reason: TrustCurrentExitRejection,
    ) : TrustCurrentExitResult
}

object TrustCurrentExitPolicy {
    fun evaluate(
        snapshot: MonitorSnapshot,
        settings: AppSettings,
    ): TrustCurrentExitDecision {
        val assessment = ProtectionEvaluator.evaluate(snapshot, settings)
        if (snapshot.connection.status != ConnectionStatus.ONLINE) {
            return TrustCurrentExitDecision.Rejected(
                TrustCurrentExitRejection.INTERNET_NOT_VALIDATED,
            )
        }
        if (ProtectionIssue.VPN_REQUIRED in assessment.issues) {
            return TrustCurrentExitDecision.Rejected(TrustCurrentExitRejection.VPN_REQUIRED)
        }
        if (ProtectionIssue.IP_REFRESHING in assessment.issues) {
            return TrustCurrentExitDecision.Rejected(TrustCurrentExitRejection.IP_REFRESHING)
        }
        if (ProtectionIssue.IP_UNAVAILABLE in assessment.issues) {
            val reason = if (snapshot.publicIp.errorMessage != null) {
                TrustCurrentExitRejection.IP_LOOKUP_FAILED
            } else {
                TrustCurrentExitRejection.IP_UNAVAILABLE
            }
            return TrustCurrentExitDecision.Rejected(reason)
        }
        if (ProtectionIssue.IPV6_COUNTRY_MISMATCH in assessment.issues) {
            return TrustCurrentExitDecision.Rejected(
                TrustCurrentExitRejection.IPV6_ROUTE_MISMATCH,
            )
        }

        val current = snapshot.publicIp.primary
            ?: return TrustCurrentExitDecision.Rejected(
                TrustCurrentExitRejection.IP_UNAVAILABLE,
            )
        val countryCode = current.countryCode
            ?.takeIf(String::isNotBlank)
            ?: return TrustCurrentExitDecision.Rejected(
                TrustCurrentExitRejection.COUNTRY_UNAVAILABLE,
            )
        return TrustCurrentExitDecision.Ready(
            TrustedExitProfile(
                countryCode = countryCode.uppercase(Locale.ROOT),
                countryName = current.countryName,
                asnOrganization = current.asnOrganization,
            ),
        )
    }
}

class TrustCurrentExitUseCase(
    private val currentSnapshot: () -> MonitorSnapshot,
    private val currentSettings: suspend () -> AppSettings,
    private val saveProfile: suspend (TrustedExitProfile) -> Unit,
) {
    suspend fun save(): TrustCurrentExitResult {
        val settings = currentSettings()
        return when (val decision = TrustCurrentExitPolicy.evaluate(currentSnapshot(), settings)) {
            is TrustCurrentExitDecision.Ready -> {
                saveProfile(decision.profile)
                TrustCurrentExitResult.Saved(decision.profile)
            }
            is TrustCurrentExitDecision.Rejected ->
                TrustCurrentExitResult.Rejected(decision.reason)
        }
    }
}
