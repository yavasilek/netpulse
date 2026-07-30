package ru.yavasilek.netpulse.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.yavasilek.netpulse.model.PublicIpInfo

class PublicIpRefreshStateTest {
    @Test
    fun currentRequestEarlyExitClearsRefreshing() {
        val current = PublicIpInfo(isRefreshing = true)

        val result = current.afterRefreshAborted(
            requestSequence = 7L,
            activeSequence = 7L,
        )

        assertFalse(result.isRefreshing)
    }

    @Test
    fun staleRequestCannotClearNewerRefresh() {
        val current = PublicIpInfo(isRefreshing = true)

        val result = current.afterRefreshAborted(
            requestSequence = 6L,
            activeSequence = 7L,
        )

        assertTrue(result.isRefreshing)
    }
}
