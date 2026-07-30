package ru.yavasilek.netpulse.monitoring

import ru.yavasilek.netpulse.data.PendingNetworkEvent
import ru.yavasilek.netpulse.model.NetworkEventType
import ru.yavasilek.netpulse.model.PublicIpInfo

internal object PublicIpRefreshEventFactory {
    fun create(
        previous: PublicIpInfo,
        current: PublicIpInfo,
    ): List<PendingNetworkEvent> = buildList {
        val oldAddress = previous.primary?.address
        val newAddress = current.primary?.address
        if (oldAddress != null && newAddress != null && oldAddress != newAddress) {
            add(
                PendingNetworkEvent(
                    type = NetworkEventType.IP,
                    title = "Публичный IP изменился",
                    detail = "$oldAddress → $newAddress",
                ),
            )
        } else if (oldAddress == null && newAddress != null) {
            add(
                PendingNetworkEvent(
                    type = NetworkEventType.IP,
                    title = "Публичный IP определён",
                    detail = newAddress,
                ),
            )
        }
    }
}
