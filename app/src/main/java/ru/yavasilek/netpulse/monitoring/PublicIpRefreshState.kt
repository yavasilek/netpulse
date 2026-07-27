package ru.yavasilek.netpulse.monitoring

import ru.yavasilek.netpulse.model.PublicIpInfo

internal fun PublicIpInfo.afterRefreshAborted(
    requestSequence: Long,
    activeSequence: Long,
): PublicIpInfo = if (requestSequence == activeSequence) {
    copy(isRefreshing = false)
} else {
    this
}
