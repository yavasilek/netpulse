package ru.yavasilek.netpulse.network

import ru.yavasilek.netpulse.model.NetworkQuality
import ru.yavasilek.netpulse.model.NetworkQualityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import kotlin.math.roundToInt

class NetworkQualityProbe {
    suspend fun probe(): NetworkQuality = withContext(Dispatchers.IO) {
        val primary = measureEndpoint(PRIMARY_ENDPOINT)
        val samples = if (primary.any { it != null }) {
            primary
        } else {
            measureEndpoint(FALLBACK_ENDPOINT)
        }
        QualityCalculator.calculate(samples)
    }

    private suspend fun measureEndpoint(host: String): List<Long?> = buildList {
        repeat(PROBE_COUNT) { index ->
            add(measureConnection(host))
            if (index < PROBE_COUNT - 1) delay(PROBE_GAP_MILLIS)
        }
    }

    private fun measureConnection(host: String): Long? = runCatching {
        val startedAt = System.nanoTime()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, HTTPS_PORT), CONNECT_TIMEOUT_MILLIS)
        }
        ((System.nanoTime() - startedAt) / NANOS_PER_MILLI)
            .coerceAtLeast(1L)
    }.getOrNull()

    private companion object {
        const val PRIMARY_ENDPOINT = "1.1.1.1"
        const val FALLBACK_ENDPOINT = "8.8.8.8"
        const val HTTPS_PORT = 443
        const val PROBE_COUNT = 3
        const val CONNECT_TIMEOUT_MILLIS = 1_500
        const val PROBE_GAP_MILLIS = 120L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

object QualityCalculator {
    fun calculate(
        samplesMillis: List<Long?>,
        checkedAtMillis: Long = System.currentTimeMillis(),
    ): NetworkQuality {
        if (samplesMillis.isEmpty()) {
            return unavailable(checkedAtMillis)
        }

        val successful = samplesMillis.filterNotNull()
        val loss = (
            (samplesMillis.size - successful.size) * 100.0 / samplesMillis.size
        ).roundToInt()
        if (successful.isEmpty()) {
            return unavailable(checkedAtMillis, packetLossPercent = 100)
        }

        val sorted = successful.sorted()
        val latency = sorted[sorted.size / 2].coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val jitter = successful
            .zipWithNext { first, second -> abs(second - first) }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToInt()
            ?: 0
        val status = when {
            loss == 0 && latency < 80 && jitter < 25 ->
                NetworkQualityStatus.EXCELLENT
            loss <= 34 && latency < 250 && jitter < 80 ->
                NetworkQualityStatus.GOOD
            else -> NetworkQualityStatus.UNSTABLE
        }
        return NetworkQuality(
            status = status,
            latencyMillis = latency,
            jitterMillis = jitter,
            packetLossPercent = loss,
            checkedAtMillis = checkedAtMillis,
        )
    }

    fun unavailable(
        checkedAtMillis: Long = System.currentTimeMillis(),
        packetLossPercent: Int? = null,
        message: String = "Не удалось измерить качество соединения",
    ): NetworkQuality = NetworkQuality(
        status = NetworkQualityStatus.UNAVAILABLE,
        packetLossPercent = packetLossPercent,
        checkedAtMillis = checkedAtMillis,
        errorMessage = message,
    )
}
