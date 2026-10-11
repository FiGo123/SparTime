package com.example.boombee.wear

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import com.example.boombee.data.Dao
import com.example.boombee.heartrate.HeartRateZones

private val BeeYellow = Color(0xFFFFC107)

@Composable
fun SettingsScreen(
    dao: Dao,
    permissionVersion: Int,
    onRequestHeartRate: () -> Unit,
    onAge: () -> Unit,
    onSafety: () -> Unit,
    onBack: () -> Unit,
) {
    var trainingType by remember { mutableStateOf(dao.getDefault() ?: "BOXING") }
    var soundOn by remember { mutableStateOf(dao.getSoundStatus()) }
    var freestyleOn by remember { mutableStateOf(dao.getFreestyleEnabled()) }
    var tacticalCommandsOn by remember { mutableStateOf(dao.getTacticalCommandsEnabled()) }
    val context = LocalContext.current
    var crashReportsOn by remember { mutableStateOf(CrashReports.consent(context) == true) }
    var heartRateOn by remember { mutableStateOf(dao.getHeartRateEnabled()) }
    var hrCoachingOn by remember { mutableStateOf(dao.getHeartRateCoachingEnabled()) }
    val hasPermission = remember(permissionVersion) { HeartRateMonitor.hasPermission(context) }
    val age = dao.getAge()

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
                    label = { Text(if (crashReportsOn) "Crash reports: On" else "Crash reports: Off") },
                    onClick = {
                        crashReportsOn = !crashReportsOn
                        CrashReports.setConsent(context, crashReportsOn)
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
            item { ListHeader { Text("Heart rate") } }
            item {
                Chip(
                    label = { Text(if (heartRateOn) "Heart rate: On" else "Heart rate: Off") },
                    secondaryLabel = when {
                        heartRateOn && !hasPermission -> { { Text("Tap to allow access") } }
                        heartRateOn && age == null -> { { Text("Set your age below") } }
                        else -> null
                    },
                    onClick = {
                        if (heartRateOn && !hasPermission) {
                            onRequestHeartRate()
                        } else {
                            heartRateOn = !heartRateOn
                            dao.saveHeartRateEnabled(heartRateOn)
                            if (heartRateOn) onRequestHeartRate()
                        }
                    },
                )
            }
            item {
                Chip(
                    label = { Text(if (age != null) "Age: $age" else "Age: not set") },
                    secondaryLabel = { Text(if (age != null) "Max HR ${HeartRateZones.maxHeartRate(age)}" else "Needed for zones") },
                    onClick = onAge,
                )
            }
            item {
                Chip(
                    label = { Text(if (hrCoachingOn) "HR coaching: On" else "HR coaching: Off") },
                    secondaryLabel = { Text("Coach adapts to your zone") },
                    onClick = {
                        hrCoachingOn = !hrCoachingOn
                        dao.saveHeartRateCoachingEnabled(hrCoachingOn)
                    },
                )
            }
            item { Chip(label = { Text("Safety & disclaimer") }, onClick = onSafety) }
            item { Chip(label = { Text("Back") }, onClick = onBack) }
        }
    }
}
