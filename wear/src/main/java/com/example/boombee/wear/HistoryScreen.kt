package com.example.boombee.wear

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.boombee.data.DBHandler

@Composable
fun HistoryScreen(dbHandler: DBHandler, onBack: () -> Unit) {
    val trainings = remember { dbHandler.getAllTraining() }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ListHeader { Text("History") } }
            if (trainings.isEmpty()) {
                item { Text("No sessions yet") }
            } else {
                items(trainings) { training ->
                    Chip(
                        label = { Text(training.title) },
                        secondaryLabel = { Text("${training.date} · ${training.numberOfRounds}x${training.roundDuration}m") },
                        onClick = {},
                    )
                }
            }
            item { Chip(label = { Text("Back") }, onClick = onBack) }
        }
    }
}
