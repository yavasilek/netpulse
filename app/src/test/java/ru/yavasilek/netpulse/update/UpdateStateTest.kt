package ru.yavasilek.netpulse.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateStateTest {
    @Test
    fun calculatesDownloadProgress() {
        val state = UpdateState.Downloading(
            versionName = "0.1.3",
            downloadedBytes = 750,
            totalBytes = 1_000,
        )

        assertEquals(0.75f, state.progressFraction)
        assertEquals(75, state.percent)
    }

    @Test
    fun clampsProgressAndHandlesUnknownTotal() {
        val completed = UpdateState.Downloading(
            versionName = "0.1.3",
            downloadedBytes = 1_200,
            totalBytes = 1_000,
        )
        val unknown = UpdateState.Downloading(
            versionName = "0.1.3",
            downloadedBytes = 400,
            totalBytes = null,
        )

        assertEquals(1f, completed.progressFraction)
        assertEquals(100, completed.percent)
        assertNull(unknown.progressFraction)
        assertNull(unknown.percent)
    }
}
