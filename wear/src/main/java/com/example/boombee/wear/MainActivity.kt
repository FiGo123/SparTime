package com.example.boombee.wear

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.example.boombee.coach.ClipPlayer
import com.example.boombee.coach.Difficulty
import com.example.boombee.coach.TrainingMode
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.DBHandler
import com.example.boombee.data.Dao
import com.example.boombee.data.PreferencesProvider
import com.example.boombee.data.models.Training
import com.example.boombee.wear.ui.theme.BoomBeeWearTheme

/**
 * Extends [FragmentActivity] (not plain [ComponentActivity]) because
 * [AmbientModeSupport.attach] requires it. Ambient Mode keeps this same
 * Activity as the visible surface — just dimmed — instead of Wear OS handing
 * focus back to the watch face when idle, so raising the wrist un-dims
 * straight back to the current round/time rather than requiring the user to
 * reopen the app. The countdown itself is unaffected either way: it's owned
 * by [RoundTimerService], not this Activity.
 */
class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    private var boundService by mutableStateOf<RoundTimerService?>(null)
    private var isBound = false
    private var isAmbient by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            boundService = (binder as RoundTimerService.LocalBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AmbientModeSupport.attach(this)
        CrashReports.apply(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        isBound = bindService(Intent(this, RoundTimerService::class.java), connection, Context.BIND_AUTO_CREATE)

        setContent {
            BoomBeeWearTheme {
                val service = boundService
                if (service == null) {
                    Scaffold { Text("Loading…") }
                } else {
                    BoomBeeWearApp(service, isAmbient)
                }
            }
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback =
        object : AmbientModeSupport.AmbientCallback() {
            override fun onEnterAmbient(ambientDetails: Bundle) {
                isAmbient = true
            }

            override fun onExitAmbient() {
                isAmbient = false
            }
        }
}

@Composable
fun BoomBeeWearApp(service: RoundTimerService, isAmbient: Boolean) {
    val context = LocalContext.current
    val dao = remember { Dao(PreferencesProvider(context)) }
    val dbHandler = remember { DBHandler(context) }

    var trainingType by remember { mutableStateOf(dao.getDefault() ?: "BOXING") }
    var config by remember { mutableStateOf(defaultsFor(trainingType)) }
    var screen by remember { mutableStateOf(WearScreen.Setup) }
    val clipPlayer = remember { ClipPlayer(context) }
    var learnVoiceStyle by remember { mutableStateOf(VoiceStyle.WORDS) }
    var crashConsentAsked by remember { mutableStateOf(CrashReports.consent(context) != null) }
    var openTraining by remember { mutableStateOf<Training?>(null) }
    var editingEffort by remember { mutableStateOf(false) }
    var safetyAccepted by remember { mutableStateOf(dao.getSafetyAccepted()) }
    var heartRateSetupAsked by remember { mutableStateOf(dao.getHeartRateSetupAsked()) }
    // Bumped after a permission answer so Settings re-reads it.
    var permissionVersion by remember { mutableStateOf(0) }
    val heartRatePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionVersion++
    }
    fun requestHeartRate() {
        if (!HeartRateMonitor.hasPermission(context)) heartRatePermission.launch(HeartRateMonitor.PERMISSION)
    }
    DisposableEffect(Unit) { onDispose { clipPlayer.shutdown() } }

    val timerState by service.state.collectAsState()
    val pendingEffortId by service.pendingEffortId.collectAsState()
    val lastHeartRate by service.lastHeartRate.collectAsState()
    val activeState = timerState

    if (activeState != null) {
        TimerScreen(
            state = activeState,
            isAmbient = isAmbient,
            onPauseToggle = { service.togglePause() },
            onStop = { service.stopByUser() },
        )
        return
    }

    // Right after a session is saved: "How hard was that?"
    pendingEffortId?.let { trainingId ->
        EffortScreen(
            onSave = { effort ->
                dbHandler.updateEffort(trainingId, effort, null)
                service.clearPendingEffort()
            },
            onSkip = { service.clearPendingEffort() },
            heartRate = lastHeartRate,
        )
        return
    }

    if (!safetyAccepted) {
        SafetyScreen(
            onAccept = {
                dao.saveSafetyAccepted()
                safetyAccepted = true
            },
            onBack = null,
        )
        return
    }

    if (!crashConsentAsked) {
        CrashConsentScreen { granted ->
            CrashReports.setConsent(context, granted)
            crashConsentAsked = true
        }
        return
    }

    // Once: age for heart-rate zones, then the heart-rate permission.
    if (!heartRateSetupAsked) {
        AgeScreen(
            initial = dao.getAge(),
            onSave = { age ->
                dao.saveAge(age)
                dao.saveHeartRateEnabled(true)
                dao.saveHeartRateSetupAsked()
                heartRateSetupAsked = true
                requestHeartRate()
            },
            onSkip = {
                dao.saveHeartRateEnabled(false)
                dao.saveHeartRateSetupAsked()
                heartRateSetupAsked = true
            },
            skipLabel = "Not now",
        )
        return
    }

    when (screen) {
        WearScreen.Setup -> {
            SetupScreen(
                initialConfig = config,
                trainingTypeLabel = if (trainingType == "MMA") "MMA" else "Boxing",
                onStart = { newConfig, mode, difficulty, voiceStyle, warmupSeconds ->
                    config = newConfig
                    // Promote to a *started* service (not just bound) so it
                    // keeps running even if this Activity is later fully
                    // destroyed, not merely backgrounded.
                    ContextCompat.startForegroundService(context, Intent(context, RoundTimerService::class.java))
                    service.startSession(newConfig, trainingType, mode, difficulty, voiceStyle, warmupSeconds)
                },
                onSettings = { screen = WearScreen.Settings },
                onHistory = { screen = WearScreen.History },
                onLearn = { screen = WearScreen.Learn },
            )
        }

        WearScreen.Settings -> {
            SettingsScreen(
                dao = dao,
                permissionVersion = permissionVersion,
                onRequestHeartRate = { requestHeartRate() },
                onAge = { screen = WearScreen.Age },
                onSafety = { screen = WearScreen.Safety },
                onBack = {
                    trainingType = dao.getDefault() ?: "BOXING"
                    config = defaultsFor(trainingType)
                    screen = WearScreen.Setup
                },
            )
        }

        WearScreen.Age -> {
            AgeScreen(
                initial = dao.getAge(),
                onSave = { age ->
                    dao.saveAge(age)
                    screen = WearScreen.Settings
                },
                onSkip = { screen = WearScreen.Settings },
                skipLabel = "Cancel",
            )
        }

        WearScreen.Safety -> {
            SafetyScreen(onAccept = null, onBack = { screen = WearScreen.Settings })
        }

        WearScreen.Learn -> {
            LearnScreen(
                clipPlayer = clipPlayer,
                voiceStyle = learnVoiceStyle,
                onVoiceStyleChange = { learnVoiceStyle = it },
                onPlayGame = { screen = WearScreen.ComboGame },
                onBack = {
                    clipPlayer.stop()
                    screen = WearScreen.Setup
                },
            )
        }

        WearScreen.ComboGame -> {
            ComboGameScreen(
                clipPlayer = clipPlayer,
                voiceStyle = learnVoiceStyle,
                onDone = { screen = WearScreen.Learn },
            )
        }

        WearScreen.SessionDetail -> {
            val training = openTraining
            when {
                training == null -> screen = WearScreen.History
                editingEffort -> EffortScreen(
                    initial = training.effort ?: 6,
                    skipLabel = "Cancel",
                    onSave = { effort ->
                        dbHandler.updateEffort(training.id.toLong(), effort, training.notes)
                        openTraining = dbHandler.getTraining(training.id.toLong())
                        editingEffort = false
                    },
                    onSkip = { editingEffort = false },
                )
                else -> SessionDetailScreen(
                    training = training,
                    onEditEffort = { editingEffort = true },
                    onDelete = {
                        dbHandler.deleteTraining(training.id)
                        openTraining = null
                        screen = WearScreen.History
                    },
                    onBack = { screen = WearScreen.History },
                )
            }
        }

        WearScreen.History -> {
            HistoryScreen(
                dbHandler = dbHandler,
                onOpen = {
                    openTraining = it
                    screen = WearScreen.SessionDetail
                },
                onBack = { screen = WearScreen.Setup },
            )
        }
    }
}
