package ru.yavasilek.netpulse.monitoring

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnDisconnectAlertTrackerTest {
    @Test
    fun rapidReconnectCancelsDisconnectAlert() {
        val tracker = VpnDisconnectAlertTracker()

        val actions = listOf(
            tracker.onConnectionChanged(isVpn = true, warningEnabled = true),
            tracker.onConnectionChanged(isVpn = false, warningEnabled = true),
            tracker.onConnectionChanged(isVpn = false, warningEnabled = true),
            tracker.onConnectionChanged(isVpn = true, warningEnabled = true),
        )

        assertEquals(
            listOf(
                VpnDisconnectAlertAction.CANCEL,
                VpnDisconnectAlertAction.SHOW,
                VpnDisconnectAlertAction.UPDATE,
                VpnDisconnectAlertAction.CANCEL,
            ),
            actions,
        )
    }

    @Test
    fun firstDisconnectedSnapshotClearsStaleAlertFromPreviousServiceInstance() {
        val tracker = VpnDisconnectAlertTracker()

        assertEquals(
            VpnDisconnectAlertAction.CANCEL,
            tracker.onConnectionChanged(isVpn = false, warningEnabled = true),
        )
    }

    @Test
    fun steadyConnectedVpnDoesNotRepeatedlyCancelAlert() {
        val tracker = VpnDisconnectAlertTracker()

        tracker.onConnectionChanged(isVpn = true, warningEnabled = true)

        assertEquals(
            VpnDisconnectAlertAction.NONE,
            tracker.onConnectionChanged(isVpn = true, warningEnabled = true),
        )
    }

    @Test
    fun disablingWarningClearsVisibleDisconnectAlert() {
        val tracker = VpnDisconnectAlertTracker()
        tracker.onConnectionChanged(isVpn = true, warningEnabled = true)
        tracker.onConnectionChanged(isVpn = false, warningEnabled = true)

        assertEquals(
            VpnDisconnectAlertAction.CANCEL,
            tracker.onConnectionChanged(isVpn = false, warningEnabled = false),
        )
    }
}
