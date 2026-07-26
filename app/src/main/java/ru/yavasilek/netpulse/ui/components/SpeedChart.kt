package ru.yavasilek.netpulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ru.yavasilek.netpulse.model.SpeedSample
import kotlin.math.max

@Composable
fun SpeedChart(
    samples: List<SpeedSample>,
    modifier: Modifier = Modifier,
) {
    val downloadColor = MaterialTheme.colorScheme.primary
    val uploadColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp),
    ) {
        repeat(3) { index ->
            val y = size.height * index / 2f
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        if (samples.size < 2) return@Canvas

        val maximum = samples.maxOf { sample ->
            max(sample.receivedBytesPerSecond, sample.transmittedBytesPerSecond)
        }.coerceAtLeast(1)

        fun pathFor(value: (SpeedSample) -> Long): Path {
            val path = Path()
            samples.forEachIndexed { index, sample ->
                val x = size.width * index / (samples.size - 1).coerceAtLeast(1)
                val ratio = value(sample).toFloat() / maximum
                val y = size.height - size.height * ratio.coerceIn(0f, 1f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        drawPath(
            path = pathFor(SpeedSample::receivedBytesPerSecond),
            color = downloadColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = pathFor(SpeedSample::transmittedBytesPerSecond),
            color = uploadColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
