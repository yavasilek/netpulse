package ru.yavasilek.netpulse.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yavasilek.netpulse.model.ConnectionInfo
import ru.yavasilek.netpulse.model.ConnectionStatus
import ru.yavasilek.netpulse.model.TransportType

class NetworkTransitionPolicyTest {
    @Test
    fun replacementNetworkIdentityTriggersRefresh() {
        val previous = connection(networkIdentity = 101L)
        val replacement = connection(networkIdentity = 202L)

        assertTrue(
            NetworkTransitionPolicy.hasNetworkChanged(
                previous = previous,
                current = replacement,
            ),
        )
    }

    @Test
    fun unchangedNetworkDoesNotTriggerRefresh() {
        val previous = connection(networkIdentity = 101L)

        assertFalse(
            NetworkTransitionPolicy.hasNetworkChanged(
                previous = previous,
                current = previous.copy(changedAtMillis = 2L),
            ),
        )
    }

    private fun connection(networkIdentity: Long) = ConnectionInfo(
        status = ConnectionStatus.ONLINE,
        transports = setOf(TransportType.WIFI),
        interfaceName = "wlan0",
        networkIdentity = networkIdentity,
        isMetered = false,
        changedAtMillis = 1L,
    )
}
