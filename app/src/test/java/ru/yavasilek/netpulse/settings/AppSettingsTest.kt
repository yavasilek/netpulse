package ru.yavasilek.netpulse.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test
    fun migratesRemovedStaticIconToDownloadSpeed() {
        assertEquals(
            StatusIconMode.DOWNLOAD,
            StatusIconMode.fromStoredValue("STATIC"),
        )
    }

    @Test
    fun preservesSelectedDynamicIconMode() {
        assertEquals(
            StatusIconMode.UPLOAD,
            StatusIconMode.fromStoredValue("UPLOAD"),
        )
    }
}
