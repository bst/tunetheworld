package de.example.tunetheworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.example.tunetheworld.R
import de.example.tunetheworld.engine.TunerConfig
import kotlin.math.roundToInt

private val Heard = Color(0xFF2E7D32)
private val TooQuiet = Color(0xFF9E9E9E)

/**
 * Horizontal input level bar. The marker shows the gate: the bar turns green once the level
 * reaches it, i.e. once the tuner actually analyses the sound.
 */
@Composable
fun LevelMeter(levelDb: Float, gateDb: Float, modifier: Modifier = Modifier) {
    val floor = TunerConfig.METER_FLOOR_DB
    fun fraction(db: Float) = ((db - floor) / -floor).coerceIn(0f, 1f)
    val heard = levelDb >= gateDb
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.onSurface
    val description = stringResource(R.string.a11y_level, levelDb.roundToInt(), gateDb.roundToInt())

    Column(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Text(
            stringResource(if (heard) R.string.level_ok else R.string.level_too_quiet),
            style = MaterialTheme.typography.labelMedium
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            val corner = CornerRadius(size.height / 2f)
            drawRoundRect(trackColor, size = size, cornerRadius = corner)
            drawRoundRect(
                color = if (heard) Heard else TooQuiet,
                size = Size(size.width * fraction(levelDb), size.height),
                cornerRadius = corner
            )
            val x = size.width * fraction(gateDb)
            drawLine(markerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}
