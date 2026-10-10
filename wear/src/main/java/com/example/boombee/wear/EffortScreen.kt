package com.example.boombee.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.boombee.data.HistoryStats
import com.example.boombee.wear.ui.theme.darkChipColors

private fun effortColor(effort: Int) = when (effort) {
    in 1..4 -> Color(0xFF4CAF50)
    in 5..6 -> Color(0xFFFFC107)
    in 7..8 -> Color(0xFFFF6F00)
    else -> Color(0xFFF44336)
}

/**
 * Post-session "How hard was that?" on the watch: effort 1–10 with − / +
 * or the rotating bezel. Notes are phone-only (no typing on the wrist).
 */
@Composable
fun EffortScreen(onSave: (Int) -> Unit, onSkip: () -> Unit) {
    var effort by remember { mutableIntStateOf(6) }
    var rotaryAccumulated by remember { mutableFloatStateOf(0f) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BackHandler(onBack = onSkip)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                // One step per ~notch of the bezel.
                rotaryAccumulated += event.verticalScrollPixels
                if (kotlin.math.abs(rotaryAccumulated) > 40f) {
                    effort = (effort + if (rotaryAccumulated > 0) 1 else -1).coerceIn(1, 10)
                    rotaryAccumulated = 0f
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("How hard was that?", style = MaterialTheme.typography.caption1)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { effort = (effort - 1).coerceAtLeast(1) },
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0xFF2A2A2A)),
            ) { Text("−", fontSize = 22.sp, color = Color.White) }
            Spacer(Modifier.width(12.dp))
            Text("$effort", fontSize = 46.sp, fontWeight = FontWeight.Black, color = effortColor(effort))
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { effort = (effort + 1).coerceAtMost(10) },
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0xFF2A2A2A)),
            ) { Text("+", fontSize = 22.sp, color = Color.White) }
        }
        Text(HistoryStats.effortLabel(effort), color = effortColor(effort), style = MaterialTheme.typography.body2)
        Spacer(Modifier.height(10.dp))
        Chip(label = { Text("Save") }, onClick = { onSave(effort) })
        Spacer(Modifier.height(4.dp))
        CompactChip(label = { Text("Skip") }, onClick = onSkip, colors = darkChipColors())
    }
}
