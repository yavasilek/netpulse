package ru.yavasilek.netpulse.monitoring

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedCalculatorTest {
    private val calculator = SpeedCalculator()

    @Test
    fun firstSampleStartsAtZero() {
        val sample = calculator.sample(
            receivedBytes = 1_000,
            transmittedBytes = 500,
            timestampMillis = 10_000,
            sourceId = "wifi",
        )

        assertEquals(0, sample.receivedBytesPerSecond)
        assertEquals(0, sample.transmittedBytesPerSecond)
    }

    @Test
    fun calculatesBytesPerSecondUsingElapsedTime() {
        calculator.sample(1_000, 500, 10_000, "wifi")

        val sample = calculator.sample(5_000, 1_500, 12_000, "wifi")

        assertEquals(2_000, sample.receivedBytesPerSecond)
        assertEquals(500, sample.transmittedBytesPerSecond)
    }

    @Test
    fun interfaceChangeResetsTheBaseline() {
        calculator.sample(10_000, 8_000, 10_000, "wifi")

        val sample = calculator.sample(100, 50, 11_000, "vpn")

        assertEquals(0, sample.receivedBytesPerSecond)
        assertEquals(0, sample.transmittedBytesPerSecond)
    }

    @Test
    fun counterResetDoesNotProduceNegativeSpeed() {
        calculator.sample(10_000, 8_000, 10_000, "wifi")

        val sample = calculator.sample(50, 40, 11_000, "wifi")

        assertEquals(0, sample.receivedBytesPerSecond)
        assertEquals(0, sample.transmittedBytesPerSecond)
    }
}
