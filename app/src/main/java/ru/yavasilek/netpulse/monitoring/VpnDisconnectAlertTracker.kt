package ru.yavasilek.netpulse.monitoring

internal enum class VpnDisconnectAlertAction {
    NONE,
    SHOW,
    UPDATE,
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

        if (previous == null) {
            alertVisible = false
            return VpnDisconnectAlertAction.CANCEL
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

        if (alertVisible) {
            return VpnDisconnectAlertAction.UPDATE
        }

        return VpnDisconnectAlertAction.NONE
    }
}
