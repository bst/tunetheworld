package de.example.saxtuner.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.example.saxtuner.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Green = Color(0xFF2E7D32)
private val Yellow = Color(0xFFF9A825)
private val Red = Color(0xFFC62828)

private const val CENTER_ANGLE = 270f   // in Compose, 270 degrees points up
private const val DEGREES_PER_PERCENT = 1.2f

private fun zoneColor(percent: Float): Color = when {
    abs(percent) <= 5f -> Green
    abs(percent) <= 15f -> Yellow
    else -> Red
}

@Composable
fun NeedleGauge(percent: Float?, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = percent ?: 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "needle"
    )
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = TextStyle(fontSize = 12.sp, color = labelColor)
    val tickColor = MaterialTheme.colorScheme.onSurface
    val needleColor = if (percent == null) Color.Gray.copy(alpha = 0.4f) else zoneColor(animated)
    val minusFifty = "−50"
    val zero = "0"
    val plusFifty = "+50"
    val tooLow = stringResource(R.string.too_low)
    val tooHigh = stringResource(R.string.too_high)

    Canvas(modifier = modifier.aspectRatio(2f)) {
        val center = Offset(size.width / 2f, size.height * 0.9f)
        val radius = 0.9f * min(size.width / 2f, size.height * 0.9f) - 16.dp.toPx()
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        val zoneStroke = Stroke(width = 14.dp.toPx())

        fun point(angleDeg: Float, r: Float): Offset {
            val rad = Math.toRadians(angleDeg.toDouble())
            return Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
        }

        fun zone(fromPercent: Float, toPercent: Float, color: Color) {
            drawArc(
                color = color.copy(alpha = 0.5f),
                startAngle = CENTER_ANGLE + fromPercent * DEGREES_PER_PERCENT,
                sweepAngle = (toPercent - fromPercent) * DEGREES_PER_PERCENT,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = zoneStroke
            )
        }
        zone(-50f, -15f, Red)
        zone(-15f, -5f, Yellow)
        zone(-5f, 5f, Green)
        zone(5f, 15f, Yellow)
        zone(15f, 50f, Red)

        // Ticks every 10 %, longer at 0 and the ends
        for (p in -50..50 step 10) {
            val long = p == 0 || abs(p) == 50
            val angle = CENTER_ANGLE + p * DEGREES_PER_PERCENT
            val inner = radius - 7.dp.toPx() - (if (long) 10.dp.toPx() else 5.dp.toPx())
            drawLine(
                color = tickColor,
                start = point(angle, inner),
                end = point(angle, radius - 7.dp.toPx()),
                strokeWidth = if (long) 2.dp.toPx() else 1.dp.toPx()
            )
        }

        // Labels outside the arc
        fun label(text: String, p: Int, dy: Float = 0f, extra: Float = 0f) {
            val layout = measurer.measure(text, labelStyle)
            val at = point(CENTER_ANGLE + p * DEGREES_PER_PERCENT, radius + 14.dp.toPx() + extra)
            drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f + dy))
        }
        // End labels sit further out so they do not touch the arc
        label(minusFifty, -50, extra = 14.dp.toPx())
        label(zero, 0)
        label(plusFifty, 50, extra = 14.dp.toPx())
        label(tooLow, -50, dy = 18.dp.toPx(), extra = 14.dp.toPx())
        label(tooHigh, 50, dy = 18.dp.toPx(), extra = 14.dp.toPx())

        // Needle
        val tip = point(CENTER_ANGLE + animated * DEGREES_PER_PERCENT, radius - 4.dp.toPx())
        drawLine(needleColor, center, tip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(needleColor, radius = 8.dp.toPx(), center = center)
    }
}
