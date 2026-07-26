package ru.yavasilek.netpulse.network

import android.net.TrafficStats
import android.os.Build
import androidx.annotation.RequiresApi
import ru.yavasilek.netpulse.model.ConnectionInfo
import ru.yavasilek.netpulse.model.SpeedSample
import ru.yavasilek.netpulse.monitoring.SpeedCalculator

class TrafficSampler(
    private val calculator: SpeedCalculator = SpeedCalculator(),
) {
    fun sample(
        connection: ConnectionInfo,
        timestampMillis: Long = System.currentTimeMillis(),
    ): SpeedSample {
        val interfaceName = connection.interfaceName
        val interfaceCounters = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !interfaceName.isNullOrBlank()
        ) {
            readInterface(interfaceName)
        } else {
            null
        }

        val counters = interfaceCounters ?: readTotal()
        return calculator.sample(
            receivedBytes = counters.received,
            transmittedBytes = counters.transmitted,
            timestampMillis = timestampMillis,
            sourceId = counters.sourceId,
        )
    }

    fun reset() {
        calculator.reset()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun readInterface(interfaceName: String): Counters? {
        val received = TrafficStats.getRxBytes(interfaceName)
        val transmitted = TrafficStats.getTxBytes(interfaceName)
        if (
            received == TrafficStats.UNSUPPORTED.toLong() ||
            transmitted == TrafficStats.UNSUPPORTED.toLong()
        ) {
            return null
        }
        return Counters(
            received = received,
            transmitted = transmitted,
            sourceId = "interface:$interfaceName",
        )
    }

    private fun readTotal(): Counters = Counters(
        received = TrafficStats.getTotalRxBytes().coerceAtLeast(0),
        transmitted = TrafficStats.getTotalTxBytes().coerceAtLeast(0),
        sourceId = "device-total",
    )

    private data class Counters(
        val received: Long,
        val transmitted: Long,
        val sourceId: String,
    )
}
