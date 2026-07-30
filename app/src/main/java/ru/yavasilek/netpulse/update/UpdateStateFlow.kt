package ru.yavasilek.netpulse.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

internal fun MutableStateFlow<UpdateState>.startCheckIfAllowed(): UpdateState =
    updateAndGet(UpdateStatePolicy::beforeCheck)

internal fun MutableStateFlow<UpdateState>.publishCheckResult(
    result: UpdateState,
): UpdateState = updateAndGet { current ->
    UpdateStatePolicy.afterCheck(current, result)
}

internal fun MutableStateFlow<UpdateState>.tryStartDownload(
    versionName: String,
): Boolean {
    val preparing = UpdateState.Preparing(versionName)
    while (true) {
        val current = value
        if (!UpdateStatePolicy.canStartDownload(current)) return false
        if (compareAndSet(current, preparing)) return true
    }
}
