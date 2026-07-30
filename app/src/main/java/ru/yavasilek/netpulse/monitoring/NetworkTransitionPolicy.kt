package ru.yavasilek.netpulse.monitoring

import ru.yavasilek.netpulse.model.ConnectionInfo

internal object NetworkTransitionPolicy {
    fun hasNetworkChanged(
        previous: ConnectionInfo?,
        current: ConnectionInfo,
    ): Boolean = previous == null ||
        previous.networkIdentity != current.networkIdentity ||
        previous.interfaceName != current.interfaceName ||
        previous.transports != current.transports
}
