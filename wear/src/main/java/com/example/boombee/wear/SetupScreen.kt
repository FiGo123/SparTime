package com.example.boombee.wear

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import com.example.boombee.AppVersion
import com.example.boombee.coach.Difficulty
import com.example.boombee.coach.TrainingMode
import com.example.boombee.coach.VoiceStyle

private val BeeYellow = Color(0xFFFFC107)

@Composable
fun SetupScreen(
    initialConfig: SessionConfig,
    trainingTypeLabel: String,
    onStart: (SessionConfig, TrainingMode, Difficulty?, VoiceStyle, Int) -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
) {
    var rounds by remember { mutableStateOf(initialConfig.rounds) }
    var roundMin by remember { mutableStateOf(initialConfig.roundMinutes) }
    var restMin by remember { mutableStateOf(initialConfig.restMinutes) }
    var mode by remember { mutableStateOf(TrainingMode.TIMER_ONLY) }
    var difficulty by remember { mutableStateOf(Difficulty.BEGINNER) }
    var voiceStyle by remember { mutableStateOf(VoiceStyle.WORDS) }
    var warmupSeconds by remember { mutableStateOf(0) }

    Scaffold(timeText = { TimeText(timeTextStyle = TimeTextDefaults.timeTextStyle(color = BeeYellow)) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ListHeader { Text("BoomBee") } }
            item { Text(trainingTypeLabel, style = MaterialTheme.typography.caption2) }
            item {
                NumberStepperRow("Rounds", rounds, min = 1, max = 50) { rounds = it }
            }
            item {
                NumberStepperRow("Round min", roundMin, min = 1, max = 60) { roundMin = it }
            }
            item {
                NumberStepperRow("Rest min", restMin, min = 1, max = 60) { restMin = it }
            }
            item {
                Chip(
                    label = { Text(if (mode == TrainingMode.BOXING_COACH) "Mode: Coach" else "Mode: Timer only") },
                    onClick = {
                        mode = if (mode == TrainingMode.BOXING_COACH) TrainingMode.TIMER_ONLY else TrainingMode.BOXING_COACH
                    },
                )
            }
            if (mode == TrainingMode.BOXING_COACH) {
                item {
                    Chip(
                        label = { Text("Difficulty: ${difficulty.name.lowercase().replaceFirstChar { it.uppercase() }}") },
                        onClick = {
                            difficulty = when (difficulty) {
                                Difficulty.BEGINNER -> Difficulty.INTERMEDIATE
                                Difficulty.INTERMEDIATE -> Difficulty.ADVANCED
                                Difficulty.ADVANCED -> Difficulty.BEGINNER
                            }
                        },
                    )
                }
                item {
                    Chip(
                        label = { Text(if (voiceStyle == VoiceStyle.WORDS) "Voice: Words" else "Voice: Numbers") },
                        onClick = {
                            voiceStyle = if (voiceStyle == VoiceStyle.WORDS) VoiceStyle.NUMBERS else VoiceStyle.WORDS
                        },
                    )
                }
                item {
                    Chip(
                        label = { Text(if (warmupSeconds == 0) "Warm-up: Off" else "Warm-up: ${warmupSeconds}s") },
                        onClick = {
                            warmupSeconds = when (warmupSeconds) {
                                0 -> 30
                                30 -> 60
                                else -> 0
                            }
                        },
                    )
                }
            }
            item {
                Chip(
                    label = { Text("Start") },
                    onClick = {
                        onStart(
                            SessionConfig(rounds, roundMin, restMin),
                            mode,
                            if (mode == TrainingMode.BOXING_COACH) difficulty else null,
                            voiceStyle,
                            warmupSeconds,
                        )
                    },
                    colors = ChipDefaults.primaryChipColors(),
                )
            }
            item {
                Row {
                    CompactChip(label = { Text("Settings") }, onClick = onSettings)
                    Spacer(Modifier.width(8.dp))
                    CompactChip(label = { Text("History") }, onClick = onHistory)
                }
            }
            item {
                Text(
                    "v${AppVersion.VERSION}",
                    style = MaterialTheme.typography.caption3,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
private fun NumberStepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { if (value > min) onChange(value - 1) }, modifier = Modifier.size(32.dp)) {
            Text("-")
        }
        Spacer(Modifier.width(6.dp))
        Text("$label: $value", style = MaterialTheme.typography.body2)
        Spacer(Modifier.width(6.dp))
        Button(onClick = { if (value < max) onChange(value + 1) }, modifier = Modifier.size(32.dp)) {
            Text("+")
        }
    }
}
