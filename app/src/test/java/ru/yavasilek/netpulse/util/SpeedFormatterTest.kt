package ru.yavasilek.netpulse.util

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yavasilek.netpulse.settings.SpeedUnit

class SpeedFormatterTest {
    @Test
    fun formatsCompactStatusValuesInBits() {
        assertEquals(
            "0",
            SpeedFormatter.formatStatusIcon(0, SpeedUnit.BITS_PER_SECOND),
        )
        assertEquals(
            "64K",
            SpeedFormatter.formatStatusIcon(8_000, SpeedUnit.BITS_PER_SECOND),
        )
        assertEquals(
            "2M",
            SpeedFormatter.formatStatusIcon(250_000, SpeedUnit.BITS_PER_SECOND),
        )
    }

    @Test
    fun formatsCompactStatusValuesInBytes() {
        assertEquals(
            "1M",
            SpeedFormatter.formatStatusIcon(1_500_000, SpeedUnit.BYTES_PER_SECOND),
        )
    }
}
