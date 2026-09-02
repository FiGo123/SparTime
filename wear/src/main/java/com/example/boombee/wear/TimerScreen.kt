package com.example.boombee.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import kotlinx.coroutines.delay

/**
 * Drives one round or one rest period. Pausing works by keying the ticking
 * [LaunchedEffect] on [isPaused]: flipping it cancels the running coroutine
 * (freezing [remainingSeconds] at its current value) and, if resumed,
 * relaunches the countdown from wherever it left off.
 */
@Composable
fun TimerScreen(
    phase: TimerPhase,
    roundNumber: Int,
    totalRounds: Int,
    durationMinutes: Int,
    announcer: RoundAnnouncer,
    onFinish: () -> Unit,
    onStop: () -> Unit,
) {
    var remainingSeconds by remember(phase, roundNumber, durationMinutes) {
        mutableStateOf(durationMinutes * 60)
    }
    var isPaused by remember(phase, roundNumber) { mutableStateOf(false) }

    LaunchedEffect(phase, roundNumber) {
        announcer.announce(if (phase == TimerPhase.ROUND) "Round $roundNumber" else "Rest")
    }

    LaunchedEffect(phase, roundNumber, isPaused) {
        if (!isPaused) {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds -= 1
            }
            announcer.playBell()
            announcer.vibrate()
            onFinish()
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60

    Scaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (phase == TimerPhase.ROUND) "Round $roundNumber/$totalRounds" else "Rest",
                style = MaterialTheme.typography.caption1,
            )
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.display1,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = { isPaused = !isPaused }) {
                    Text(if (isPaused) "Resume" else "Pause")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onStop) {
                    Text("Stop")
                }
            }
        }
    }
}
