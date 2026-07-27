package ru.yavasilek.netpulse.data

import ru.yavasilek.netpulse.model.NetworkEventType

internal data class PendingNetworkEvent(
    val type: NetworkEventType,
    val title: String,
    val detail: String,
)
