package com.example.boombee.wear

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import com.example.boombee.data.Dao

private val BeeYellow = Color(0xFFFFC107)

@Composable
fun SettingsScreen(dao: Dao, onBack: () -> Unit) {
    var trainingType by remember { mutableStateOf(dao.getDefault() ?: "BOXING") }
    var soundOn by remember { mutableStateOf(dao.getSoundStatus()) }
    var freestyleOn by remember { mutableStateOf(dao.getFreestyleEnabled()) }
    var tacticalCommandsOn by remember { mutableStateOf(dao.getTacticalCommandsEnabled()) }

    Scaffold(timeText = { TimeText(timeTextStyle = TimeTextDefaults.timeTextStyle(color = BeeYellow)) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ListHeader { Text("Settings") } }
            item {
                Chip(
                    label = { Text(if (trainingType == "MMA") "Type: MMA" else "Type: Boxing") },
                    onClick = {
                        trainingType = if (trainingType == "MMA") "BOXING" else "MMA"
                        dao.saveDefault(trainingType)
                    },
                )
            }
            item {
                Chip(
                    label = { Text(if (soundOn) "Sound: On" else "Sound: Off") },
                    onClick = {
                        soundOn = !soundOn
                        dao.saveSoundStatus(soundOn)
                    },
                )
            }
            item {
                Chip(
                    label = { Text(if (tacticalCommandsOn) "Tactics: On" else "Tactics: Off") },
                    onClick = {
                        tacticalCommandsOn = !tacticalCommandsOn
                        dao.saveTacticalCommandsEnabled(tacticalCommandsOn)
                    },
                )
            }
            item {
                Chip(
                    label = { Text(if (freestyleOn) "Freestyle: On" else "Freestyle: Off") },
                    onClick = {
                        freestyleOn = !freestyleOn
                        dao.saveFreestyleEnabled(freestyleOn)
                    },
                )
            }
            item { Chip(label = { Text("Back") }, onClick = onBack) }
        }
    }
}
