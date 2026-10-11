package com.example.boombee.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.boombee.heartrate.HeartRateSummary
import com.example.boombee.heartrate.HeartRateZones

internal fun zoneColor(zone: Int): Color = when (zone) {
    1 -> Color(0xFF90A4AE)
    2 -> Color(0xFF42A5F5)
    3 -> Color(0xFF66BB6A)
    4 -> Color(0xFFFFA726)
    5 -> Color(0xFFEF5350)
    else -> Color(0xFF616161)
}

/** "2:35" / "45s". */
internal fun shortDuration(seconds: Int): String =
    if (seconds >= 60) "${seconds / 60}:${"%02d".format(seconds % 60)}" else "${seconds}s"

/**
 * Heart rate over the session (one point per 5s), coloured by zone, with
 * the rounds shaded. Round positions come from the session's own warm-up,
 * round and rest lengths — the recorder only ticks while not paused, so
 * the timeline lines up with them.
 */
@Composable
internal fun HeartRateGraph(
    summary: HeartRateSummary,
    warmupSeconds: Int,
    roundSeconds: Int,
    restSeconds: Int,
    rounds: Int,
) {
    val samples = summary.samples
    if (samples.count { it > 0 } < 2) return
    val maxHr = summary.maxHr
    val low = (maxHr * 0.45f)
    val high = maxHr.toFloat()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(64.dp),
    ) {
        val totalSeconds = samples.size * HeartRateSummary.SAMPLE_SECONDS.toFloat()
        fun x(seconds: Float) = seconds / totalSeconds * size.width
        fun y(bpm: Int) = size.height - ((bpm.coerceIn(low.toInt(), maxHr) - low) / (high - low)) * size.height

        // Rounds shaded.
        var t = warmupSeconds.toFloat()
        repeat(rounds) {
            drawRect(
                color = Color.White.copy(alpha = 0.07f),
                topLeft = Offset(x(t), 0f),
                size = Size((x(t + roundSeconds) - x(t)).coerceAtLeast(0f), size.height),
            )
            t += roundSeconds + restSeconds
        }
        // Zone 4 floor as a guide line.
        val z4 = (maxHr * 0.8f).toInt()
        drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, y(z4)), Offset(size.width, y(z4)), strokeWidth = 1f)

        var previous: Pair<Float, Int>? = null
        samples.forEachIndexed { i, bpm ->
            if (bpm <= 0) {
                previous = null
                return@forEachIndexed
            }
            val px = x((i + 0.5f) * HeartRateSummary.SAMPLE_SECONDS)
            previous?.let { (ppx, pbpm) ->
                drawLine(
                    color = zoneColor(HeartRateZones.zoneOf(bpm, maxHr)),
                    start = Offset(ppx, y(pbpm)),
                    end = Offset(px, y(bpm)),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
            previous = px to bpm
        }
    }
}
