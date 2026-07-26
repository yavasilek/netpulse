package ru.yavasilek.netpulse.util

import ru.yavasilek.netpulse.settings.SpeedUnit
import java.util.Locale
import kotlin.math.max

object SpeedFormatter {
    fun format(bytesPerSecond: Long, unit: SpeedUnit): String {
        val safeBytes = max(0, bytesPerSecond).toDouble()
        return when (unit) {
            SpeedUnit.BITS_PER_SECOND -> formatScaled(
                value = safeBytes * 8,
                units = arrayOf("бит/с", "Кбит/с", "Мбит/с", "Гбит/с"),
            )
            SpeedUnit.BYTES_PER_SECOND -> formatScaled(
                value = safeBytes,
                units = arrayOf("Б/с", "КБ/с", "МБ/с", "ГБ/с"),
            )
        }
    }

    fun formatStatusIcon(bytesPerSecond: Long, unit: SpeedUnit): String {
        val value = if (unit == SpeedUnit.BITS_PER_SECOND) {
            max(0, bytesPerSecond).toDouble() * 8
        } else {
            max(0, bytesPerSecond).toDouble()
        }
        return when {
            value < 1_000 -> value.toLong().coerceAtMost(999).toString()
            value < 1_000_000 -> "${(value / 1_000).toLong().coerceAtMost(999)}K"
            value < 1_000_000_000 -> "${(value / 1_000_000).toLong().coerceAtMost(99)}M"
            else -> "${(value / 1_000_000_000).toLong().coerceAtMost(9)}G"
        }
    }

    private fun formatScaled(value: Double, units: Array<String>): String {
        var scaled = value
        var index = 0
        while (scaled >= 1_000 && index < units.lastIndex) {
            scaled /= 1_000
            index += 1
        }
        val pattern = when {
            scaled >= 100 -> "%.0f"
            scaled >= 10 -> "%.1f"
            else -> "%.2f"
        }
        return "${String.format(Locale.getDefault(), pattern, scaled)} ${units[index]}"
    }
}
