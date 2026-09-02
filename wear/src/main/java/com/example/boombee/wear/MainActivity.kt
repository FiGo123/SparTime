package com.example.boombee.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.boombee.data.DBHandler
import com.example.boombee.data.Dao
import com.example.boombee.data.PreferencesProvider
import com.example.boombee.data.models.Training
import com.example.boombee.wear.ui.theme.BoomBeeWearTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BoomBeeWearTheme {
                BoomBeeWearApp()
            }
        }
    }
}

@Composable
fun BoomBeeWearApp() {
    val context = LocalContext.current
    val dao = remember { Dao(PreferencesProvider(context)) }
    val dbHandler = remember { DBHandler(context) }
    val announcer = remember { RoundAnnouncer(context) }

    DisposableEffect(Unit) {
        onDispose { announcer.shutdown() }
    }

    var trainingType by remember { mutableStateOf(dao.getDefault() ?: "BOXING") }
    var config by remember { mutableStateOf(defaultsFor(trainingType)) }
    var screen by remember { mutableStateOf<WearScreen>(WearScreen.Setup) }

    when (val current = screen) {
        is WearScreen.Setup -> {
            SetupScreen(
                initialConfig = config,
                trainingTypeLabel = if (trainingType == "MMA") "MMA" else "Boxing",
                onStart = { newConfig ->
                    config = newConfig
                    screen = WearScreen.RoundTimer(round = 1)
                },
                onSettings = { screen = WearScreen.Settings },
                onHistory = { screen = WearScreen.History },
            )
        }

        is WearScreen.RoundTimer -> {
            TimerScreen(
                phase = TimerPhase.ROUND,
                roundNumber = current.round,
                totalRounds = config.rounds,
                durationMinutes = config.roundMinutes,
                announcer = announcer,
                onFinish = {
                    if (current.round >= config.rounds) {
                        saveTraining(dbHandler, trainingType, config)
                        screen = WearScreen.Setup
                    } else {
                        screen = WearScreen.Rest(nextRound = current.round + 1)
                    }
                },
                onStop = { screen = WearScreen.Setup },
            )
        }

        is WearScreen.Rest -> {
            TimerScreen(
                phase = TimerPhase.REST,
                roundNumber = current.nextRound,
                totalRounds = config.rounds,
                durationMinutes = config.restMinutes,
                announcer = announcer,
                onFinish = { screen = WearScreen.RoundTimer(round = current.nextRound) },
                onStop = { screen = WearScreen.Setup },
            )
        }

        is WearScreen.Settings -> {
            SettingsScreen(
                dao = dao,
                onBack = {
                    trainingType = dao.getDefault() ?: "BOXING"
                    config = defaultsFor(trainingType)
                    screen = WearScreen.Setup
                },
            )
        }

        is WearScreen.History -> {
            HistoryScreen(
                dbHandler = dbHandler,
                onBack = { screen = WearScreen.Setup },
            )
        }
    }
}

private fun saveTraining(dbHandler: DBHandler, trainingType: String, config: SessionConfig) {
    val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    val training = when (trainingType) {
        "BOXING" -> Training("Boxing Training", time, config.rounds, config.roundMinutes, 3, "Completed boxing training")
        "MMA" -> Training("MMA Training", time, config.rounds, config.roundMinutes, 3, "Completed MMA training")
        else -> Training("Custom Training", time, config.rounds, config.roundMinutes, 3, "Completed custom training")
    }
    dbHandler.insertData(training)
}
