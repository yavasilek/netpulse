package ru.yavasilek.netpulse.monitoring

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import androidx.core.graphics.createBitmap
import ru.yavasilek.netpulse.model.SpeedSample
import ru.yavasilek.netpulse.settings.AppSettings
import ru.yavasilek.netpulse.settings.StatusIconMode
import ru.yavasilek.netpulse.util.SpeedFormatter
import kotlin.math.max

class StatusIconRenderer {
    fun render(speed: SpeedSample, settings: AppSettings): Icon {
        val bytes = when (settings.statusIconMode) {
            StatusIconMode.DOWNLOAD -> speed.receivedBytesPerSecond
            StatusIconMode.UPLOAD -> speed.transmittedBytesPerSecond
            StatusIconMode.DOMINANT -> max(
                speed.receivedBytesPerSecond,
                speed.transmittedBytesPerSecond,
            )
        }
        val text = SpeedFormatter.formatStatusIcon(bytes, settings.speedUnit)
        return Icon.createWithBitmap(draw(text))
    }

    private fun draw(text: String): android.graphics.Bitmap {
        val bitmap = createBitmap(ICON_SIZE, ICON_SIZE)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when (text.length) {
                1 -> 42f
                2 -> 36f
                else -> 29f
            }
        }
        val baseline = ICON_SIZE / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(text, ICON_SIZE / 2f, baseline, paint)
        return bitmap
    }

    private companion object {
        const val ICON_SIZE = 72
    }
}
