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
import com.example.boombee.heartrate.HeartRateZones
import com.example.boombee.wear.ui.theme.darkChipColors

private val BeeYellow = Color(0xFFFFC107)

/**
 * Age for heart-rate zones (max HR = 220 − age), with − / + or the bezel.
 * Shown once before the heart-rate permission, and from Settings → Age.
 */
@Composable
fun AgeScreen(initial: Int?, onSave: (Int) -> Unit, onSkip: () -> Unit, skipLabel: String = "Skip") {
    var age by remember { mutableIntStateOf(initial ?: HeartRateZones.DEFAULT_AGE) }
    var rotaryAccumulated by remember { mutableFloatStateOf(0f) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BackHandler(onBack = onSkip)

    fun step(delta: Int) {
        age = (age + delta).coerceIn(HeartRateZones.MIN_AGE, HeartRateZones.MAX_AGE)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                rotaryAccumulated += event.verticalScrollPixels
                if (kotlin.math.abs(rotaryAccumulated) > 40f) {
                    step(if (rotaryAccumulated > 0) 1 else -1)
                    rotaryAccumulated = 0f
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Your age", style = MaterialTheme.typography.caption1)
        Text("for heart-rate zones", style = MaterialTheme.typography.caption3, color = Color.LightGray)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { step(-1) },
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0xFF2A2A2A)),
            ) { Text("−", fontSize = 22.sp, color = Color.White) }
            Spacer(Modifier.width(12.dp))
            Text("$age", fontSize = 42.sp, fontWeight = FontWeight.Black, color = BeeYellow)
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { step(1) },
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = Color(0xFF2A2A2A)),
            ) { Text("+", fontSize = 22.sp, color = Color.White) }
        }
        Text("Max HR ${HeartRateZones.maxHeartRate(age)} bpm", style = MaterialTheme.typography.caption2, color = Color.LightGray)
        Spacer(Modifier.height(8.dp))
        Chip(label = { Text("Save") }, onClick = { onSave(age) })
        Spacer(Modifier.height(4.dp))
        CompactChip(label = { Text(skipLabel) }, onClick = onSkip, colors = darkChipColors())
    }
}
