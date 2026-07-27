package ru.yavasilek.netpulse.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

internal fun MutableStateFlow<UpdateState>.publishCheckResult(
    result: UpdateState,
): UpdateState = updateAndGet { current ->
    UpdateStatePolicy.afterCheck(current, result)
}
