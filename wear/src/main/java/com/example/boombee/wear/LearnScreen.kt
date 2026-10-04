package com.example.boombee.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import com.example.boombee.coach.ClipPlayer
import com.example.boombee.coach.PunchNaming
import com.example.boombee.coach.VoiceStyle

private val BeeYellow = Color(0xFFFFC107)

/**
 * Optional "Learn" screen: the 6 punch numbers (tap to hear the coach say
 * them) and the entry to the combo game. Opened from Setup, never forced.
 */
@Composable
fun LearnScreen(
    clipPlayer: ClipPlayer,
    voiceStyle: VoiceStyle,
    onVoiceStyleChange: (VoiceStyle) -> Unit,
    onPlayGame: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(timeText = { TimeText(timeTextStyle = TimeTextDefaults.timeTextStyle(color = BeeYellow)) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ListHeader { Text("Punch numbers") } }
            item {
                Text(
                    "Odd = left hand, even = right hand",
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Chip(
                    label = { Text("Combo game") },
                    secondaryLabel = { Text("Test what you know") },
                    onClick = onPlayGame,
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            for (punch in 1..6) {
                item {
                    Chip(
                        label = { Text("$punch · ${PunchNaming.spoken(punch, VoiceStyle.WORDS)}") },
                        secondaryLabel = { Text(if (punch % 2 == 1) "Left hand · tap to hear" else "Right hand · tap to hear") },
                        onClick = { clipPlayer.play(listOf("number_$punch", "word_$punch"), gapMs = 450L) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Chip(
                    label = { Text(if (voiceStyle == VoiceStyle.WORDS) "Game voice: Words" else "Game voice: Numbers") },
                    onClick = {
                        onVoiceStyleChange(if (voiceStyle == VoiceStyle.WORDS) VoiceStyle.NUMBERS else VoiceStyle.WORDS)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Chip(label = { Text("Back") }, onClick = onBack) }
        }
    }
}
