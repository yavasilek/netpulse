package ru.yavasilek.netpulse.util

import java.util.Locale
import kotlin.math.max

object FileSizeFormatter {
    fun format(bytes: Long): String {
        val safeBytes = max(0, bytes).toDouble()
        return when {
            safeBytes >= BYTES_IN_MEGABYTE -> String.format(
                Locale.getDefault(),
                "%.2f МБ",
                safeBytes / BYTES_IN_MEGABYTE,
            )
            safeBytes >= BYTES_IN_KILOBYTE -> String.format(
                Locale.getDefault(),
                "%.0f КБ",
                safeBytes / BYTES_IN_KILOBYTE,
            )
            else -> "${safeBytes.toLong()} Б"
        }
    }

    private const val BYTES_IN_KILOBYTE = 1_024.0
    private const val BYTES_IN_MEGABYTE = 1_024.0 * 1_024.0
}
