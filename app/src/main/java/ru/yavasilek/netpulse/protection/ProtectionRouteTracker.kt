package ru.yavasilek.netpulse.protection

import ru.yavasilek.netpulse.data.PendingNetworkEvent
import ru.yavasilek.netpulse.model.MonitorSnapshot
import ru.yavasilek.netpulse.model.NetworkEventType
import ru.yavasilek.netpulse.settings.AppSettings

internal class ProtectionRouteTracker {
    private var previousRouteSafe: Boolean? = null

    fun onSnapshot(
        snapshot: MonitorSnapshot,
        settings: AppSettings,
    ): PendingNetworkEvent? {
        if (snapshot.publicIp.isRefreshing || snapshot.publicIp.primary == null) return null
        val assessment = ProtectionEvaluator.evaluate(snapshot, settings)
        val currentSafe = when (assessment.routeState) {
            ProtectionRouteState.INCONCLUSIVE -> return null
            ProtectionRouteState.SAFE -> true
            ProtectionRouteState.UNSAFE -> false
        }
        val previous = previousRouteSafe
        previousRouteSafe = currentSafe
        if (previous != true || currentSafe) return null

        val message = when {
            ProtectionIssue.IPV6_COUNTRY_MISMATCH in assessment.issues ->
                "IPv4 и IPv6 выходят через разные страны"
            ProtectionIssue.TRUSTED_COUNTRY_MISMATCH in assessment.issues ->
                "Страна выхода отличается от доверенной"
            ProtectionIssue.TRUSTED_PROVIDER_MISMATCH in assessment.issues ->
                "Оператор выхода отличается от доверенного"
            else -> "Не удалось подтвердить доверенную точку выхода"
        }
        return PendingNetworkEvent(
            type = NetworkEventType.WARNING,
            title = "Профиль защиты изменился",
            detail = message,
        )
    }
}
