package ru.yavasilek.netpulse.network

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.yavasilek.netpulse.model.NetworkQualityStatus

class QualityCalculatorTest {
    @Test
    fun excellentWhenAllProbesAreFastAndStable() {
        val quality = QualityCalculator.calculate(listOf(31, 35, 33), checkedAtMillis = 1)

        assertEquals(NetworkQualityStatus.EXCELLENT, quality.status)
        assertEquals(33, quality.latencyMillis)
        assertEquals(3, quality.jitterMillis)
        assertEquals(0, quality.packetLossPercent)
    }

    @Test
    fun goodWhenOneOfThreeProbesFails() {
        val quality = QualityCalculator.calculate(listOf(71, null, 84), checkedAtMillis = 1)

        assertEquals(NetworkQualityStatus.GOOD, quality.status)
        assertEquals(84, quality.latencyMillis)
        assertEquals(13, quality.jitterMillis)
        assertEquals(33, quality.packetLossPercent)
    }

    @Test
    fun unavailableWhenEveryProbeFails() {
        val quality = QualityCalculator.calculate(listOf(null, null, null), checkedAtMillis = 1)

        assertEquals(NetworkQualityStatus.UNAVAILABLE, quality.status)
        assertEquals(100, quality.packetLossPercent)
    }

    @Test
    fun unstableWhenLatencyIsHigh() {
        val quality = QualityCalculator.calculate(listOf(420, 460, 500), checkedAtMillis = 1)

        assertEquals(NetworkQualityStatus.UNSTABLE, quality.status)
        assertEquals(460, quality.latencyMillis)
    }
}
