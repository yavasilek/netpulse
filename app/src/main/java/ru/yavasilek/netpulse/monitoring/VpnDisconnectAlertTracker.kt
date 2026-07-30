package ru.yavasilek.netpulse.monitoring

internal enum class VpnDisconnectAlertAction {
    NONE,
    SHOW,
    CANCEL,
}

internal class VpnDisconnectAlertTracker {
    private var previousVpnState: Boolean? = null
    private var alertVisible = false

    fun onConnectionChanged(
        isVpn: Boolean,
        warningEnabled: Boolean,
    ): VpnDisconnectAlertAction {
        val previous = previousVpnState
        previousVpnState = isVpn

        if (isVpn) {
            val shouldCancel = alertVisible || previous != true
            alertVisible = false
            return if (shouldCancel) {
                VpnDisconnectAlertAction.CANCEL
            } else {
                VpnDisconnectAlertAction.NONE
            }
        }

        if (!warningEnabled) {
            val shouldCancel = alertVisible
            alertVisible = false
            return if (shouldCancel) {
                VpnDisconnectAlertAction.CANCEL
            } else {
                VpnDisconnectAlertAction.NONE
            }
        }

        if (previous == true) {
            alertVisible = true
            return VpnDisconnectAlertAction.SHOW
        }

        return VpnDisconnectAlertAction.NONE
    }
}
