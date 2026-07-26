package ru.yavasilek.netpulse.monitoring

import ru.yavasilek.netpulse.model.SpeedSample
import kotlin.math.max

class SpeedCalculator {
    private var previous: Counters? = null

    fun sample(
        receivedBytes: Long,
        transmittedBytes: Long,
        timestampMillis: Long,
        sourceId: String,
    ): SpeedSample {
        val current = Counters(
            receivedBytes = receivedBytes,
            transmittedBytes = transmittedBytes,
            timestampMillis = timestampMillis,
            sourceId = sourceId,
        )
        val old = previous
        previous = current

        if (
            old == null ||
            old.sourceId != sourceId ||
            receivedBytes < old.receivedBytes ||
            transmittedBytes < old.transmittedBytes
        ) {
            return SpeedSample(sampledAtMillis = timestampMillis)
        }

        val elapsedMillis = timestampMillis - old.timestampMillis
        if (elapsedMillis <= 0) {
            return SpeedSample(sampledAtMillis = timestampMillis)
        }

        return SpeedSample(
            receivedBytesPerSecond = max(
                0,
                (receivedBytes - old.receivedBytes) * 1_000 / elapsedMillis,
            ),
            transmittedBytesPerSecond = max(
                0,
                (transmittedBytes - old.transmittedBytes) * 1_000 / elapsedMillis,
            ),
            sampledAtMillis = timestampMillis,
        )
    }

    fun reset() {
        previous = null
    }

    private data class Counters(
        val receivedBytes: Long,
        val transmittedBytes: Long,
        val timestampMillis: Long,
        val sourceId: String,
    )
}
