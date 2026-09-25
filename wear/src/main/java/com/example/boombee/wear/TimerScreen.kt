package com.example.boombee.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults

private val BeeYellow = Color(0xFFFFC107)

/**
 * Pure presentation of [RoundTimerState] — the actual countdown runs in
 * [RoundTimerService] regardless of whether this screen is even composed,
 * so this just renders whatever state the service reports and forwards
 * button taps back to it.
 *
 * The wall-clock time-of-day (`TimeText`, styled in the bee-yellow accent)
 * stays visible at the top the whole time — during a round you want to
 * know what time it actually is, not just how much is left — dimming to
 * gray in ambient mode to match the low-color convention there.
 *
 * In ambient mode (screen dimmed, wrist lowered), Wear OS expects a mostly
 * static, low-color view and discourages interactive controls — so the
 * Pause/Stop buttons are hidden and text switches to plain gray. Whatever
 * [state] the service reports at the moment of composition is already
 * correct up to the moment shown; there's nothing extra to fetch.
 */
@Composable
fun TimerScreen(
    state: RoundTimerState,
    isAmbient: Boolean,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
) {
    val minutes = state.remainingSeconds / 60
    val seconds = state.remainingSeconds % 60
    val textColor = if (isAmbient) Color.Gray else Color.Unspecified
    val clockColor = if (isAmbient) Color.Gray else BeeYellow

    Scaffold(
        timeText = {
            TimeText(timeTextStyle = TimeTextDefaults.timeTextStyle(color = clockColor))
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = when (state.phase) {
                    TimerPhase.WARMUP -> "Warm Up"
                    TimerPhase.ROUND -> "Round ${state.roundNumber}/${state.totalRounds}"
                    TimerPhase.REST -> "Rest"
                },
                style = MaterialTheme.typography.caption1,
                color = textColor,
            )
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.display1,
                color = textColor,
            )
            if (!isAmbient) {
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = onPauseToggle) {
                        Text(if (state.isPaused) "Resume" else "Pause")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onStop) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}
