package com.example.boombee.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.boombee.coach.CoachSession
import com.example.boombee.coach.Difficulty
import com.example.boombee.coach.TrainingMode
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.DBHandler
import com.example.boombee.data.models.Training
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Owns the round/rest countdown as a foreground service instead of Activity
 * state. A bare Activity holding a screen-on flag is not enough to survive:
 * Wear OS (and Samsung's watch software especially) force-finishes an
 * Activity it decides has held the screen awake too long, killing the whole
 * process — which silently stopped round tracking after ~1 round in testing
 * on a real Galaxy Watch. A foreground service with a real notification is
 * the mechanism actual workout/timer apps rely on to keep running
 * legitimately, screen on or off, and lets the watch idle normally.
 *
 * That alone wasn't sufficient either: even surviving as a process, this
 * service's ticking coroutine would silently freeze mid-`delay()` once the
 * watch's CPU went to sleep (screen off + idle), only catching up once
 * something woke the CPU again (e.g. reopening the app) — so a round's
 * bell/vibration never fired at the real right time while backgrounded. A
 * partial [PowerManager.WakeLock] held for the duration of a session keeps
 * the CPU from fully sleeping, which is what real workout-timer apps do to
 * guarantee on-time alerts; it costs more battery than a pure Doze-friendly
 * alarm-based design would, but that trade-off is normal for the length of
 * a training session and far simpler to get right.
 */
class RoundTimerService : Service() {

    inner class LocalBinder : Binder() {
        val service: RoundTimerService get() = this@RoundTimerService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickJob: Job? = null
    private var coachJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var announcer: RoundAnnouncer
    private lateinit var dbHandler: DBHandler

    private var trainingType: String = "BOXING"
    private var config: SessionConfig = SessionConfig(rounds = 3, roundMinutes = 3, restMinutes = 1)
    private var mode: TrainingMode = TrainingMode.TIMER_ONLY
    private var coachDifficulty: Difficulty? = null
    private var coachVoiceStyle: VoiceStyle = VoiceStyle.WORDS
    private var coachSession: CoachSession? = null

    private val _state = MutableStateFlow<RoundTimerState?>(null)
    val state: StateFlow<RoundTimerState?> = _state

    override fun onCreate() {
        super.onCreate()
        announcer = RoundAnnouncer(this)
        dbHandler = DBHandler(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private var foregroundStarted = false

    fun startSession(
        sessionConfig: SessionConfig,
        sessionTrainingType: String,
        trainingMode: TrainingMode = TrainingMode.TIMER_ONLY,
        difficulty: Difficulty? = null,
        voiceStyle: VoiceStyle = VoiceStyle.WORDS,
        warmupSeconds: Int = 0,
    ) {
        config = sessionConfig
        trainingType = sessionTrainingType
        mode = trainingMode
        coachDifficulty = difficulty
        coachVoiceStyle = voiceStyle
        coachSession = if (trainingMode == TrainingMode.BOXING_COACH) {
            CoachSession(this, difficulty ?: Difficulty.BEGINNER)
        } else {
            null
        }
        foregroundStarted = false
        acquireWakeLock()
        if (trainingMode == TrainingMode.BOXING_COACH && warmupSeconds > 0) {
            runWarmupPhase(warmupSeconds)
        } else {
            runPhase(TimerPhase.ROUND, roundNumber = 1)
        }
    }

    fun togglePause() {
        _state.update { it?.copy(isPaused = !it.isPaused) }
        _state.value?.let { pushOngoingStatus(it) }
    }

    fun stop() {
        tickJob?.cancel()
        coachJob?.cancel()
        _state.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BoomBee:RoundTimer").apply {
            setReferenceCounted(false)
            // Safety-net timeout so a missed release() can't leak the lock
            // and drain the battery indefinitely — no real session runs 2h.
            acquire(2 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun runPhase(phase: TimerPhase, roundNumber: Int) {
        tickJob?.cancel()
        coachJob?.cancel()
        val durationMinutes = if (phase == TimerPhase.ROUND) config.roundMinutes else config.restMinutes
        _state.value = RoundTimerState(
            phase = phase,
            roundNumber = roundNumber,
            totalRounds = config.rounds,
            remainingSeconds = durationMinutes * 60,
            isPaused = false,
        )
        if (phase == TimerPhase.ROUND) {
            announcer.announcePhrase("Round $roundNumber", listOf("misc_round", "number_$roundNumber"))
        } else {
            announcer.announcePhrase("Rest", listOf("misc_rest"))
        }
        pushOngoingStatus(_state.value!!)

        // Coach Mode only calls punches/tactical commands during ROUND —
        // rest stays a pure recovery period, nothing to coach there.
        if (phase == TimerPhase.ROUND && mode == TrainingMode.BOXING_COACH) {
            coachJob = scope.launch { runCoachLoop(phase, roundNumber) }
        }

        var warned = false
        tickJob = scope.launch {
            // Let "Round X"/"Rest" actually finish playing before the clock
            // starts moving — without this, the countdown was already a
            // couple of seconds in by the time the announcement finished,
            // so the round felt like it started before you heard it start.
            delay(ANNOUNCE_LEAD_MS)
            while (true) {
                val current = _state.value ?: break
                if (!current.isPaused) {
                    when {
                        current.remainingSeconds <= 0 -> {
                            // Cancel the round's coach loop BEFORE the bell,
                            // not after (onPhaseFinished() below used to be
                            // the only place that happened). coachJob is an
                            // independent coroutine — if its own delay()
                            // happened to expire while the bell was still
                            // ringing, it would call announce() right over
                            // it, since announce() has no idea a tone is
                            // playing. Cancelling first removes that window
                            // entirely instead of relying on timing luck.
                            coachJob?.cancel()
                            announcer.playBell()
                            announcer.vibrateEnd()
                            // Extra breathing room on top of playBell()
                            // already waiting for its own tone to finish —
                            // real-device testing found the very next
                            // announcement ("Round X"/"Rest") still read as
                            // running into the bell's tail on this watch,
                            // so this pads the gap further rather than
                            // assuming the tone is truly silent the instant
                            // playBell() returns.
                            delay(EXTRA_BELL_GAP_MS)
                            onPhaseFinished(phase, roundNumber)
                            break
                        }
                        current.remainingSeconds == 10 && !warned -> {
                            warned = true
                            announcer.playWarning()
                            announcer.vibrateWarning()
                        }
                    }
                    _state.update { it?.copy(remainingSeconds = it.remainingSeconds - 1) }
                }
                delay(1000)
            }
        }
    }

    /**
     * Optional pre-round phase (see [CoachSession.nextWarmupCommand]) that
     * runs to completion *before* Round 1's own timer/coaching starts —
     * jab-led, slow-paced calls purely to get the user familiar with the
     * coach's voice, not a simplified slice of Round 1 itself. Only ever
     * called for round 1, from [startSession].
     */
    private fun runWarmupPhase(durationSeconds: Int) {
        tickJob?.cancel()
        coachJob?.cancel()
        _state.value = RoundTimerState(
            phase = TimerPhase.WARMUP,
            roundNumber = 1,
            totalRounds = config.rounds,
            remainingSeconds = durationSeconds,
            isPaused = false,
        )
        pushOngoingStatus(_state.value!!)

        val session = coachSession
        if (session != null) {
            coachJob = scope.launch { runWarmupCoachLoop(session) }
        }

        tickJob = scope.launch {
            while (true) {
                val current = _state.value ?: break
                if (current.phase != TimerPhase.WARMUP) break
                if (!current.isPaused) {
                    if (current.remainingSeconds <= 0) {
                        coachJob?.cancel()
                        runPhase(TimerPhase.ROUND, roundNumber = 1)
                        break
                    }
                    _state.update { it?.copy(remainingSeconds = it.remainingSeconds - 1) }
                }
                delay(1000)
            }
        }
    }

    private suspend fun runWarmupCoachLoop(session: CoachSession) {
        while (true) {
            val current = _state.value ?: break
            if (current.phase != TimerPhase.WARMUP) break
            if (!current.isPaused) {
                val command = session.nextWarmupCommand()
                announcer.announceCommand(command, coachVoiceStyle)
                delay(session.warmupDelayMillis(command))
            } else {
                delay(200)
            }
        }
    }

    /**
     * Calls punches/tactical commands via TTS at a pace [CoachSession]
     * decides is realistic for a person actually throwing them — runs only
     * while [phase]/[roundNumber] are still the current one, so it stops
     * cleanly the moment the round ends, is paused, or the session stops.
     */
    private suspend fun runCoachLoop(phase: TimerPhase, roundNumber: Int) {
        val session = coachSession ?: return
        // This coroutine is (re)launched exactly once per round (see
        // runPhase), so this is the correct single place to force the
        // round's first call to Attack — it won't re-fire on pause/resume,
        // which reuse this same running loop rather than relaunching it.
        session.startRound(roundNumber, config.roundMinutes * 60)
        // A few free seconds to feel out the round's opening before the
        // coach starts calling, like a real round — longer than just
        // waiting out "Round X" itself (ANNOUNCE_LEAD_MS).
        delay(ROUND_START_COACH_GRACE_MS)
        while (true) {
            val current = _state.value ?: break
            if (current.phase != phase || current.roundNumber != roundNumber) break
            if (!current.isPaused) {
                val command = session.nextCommand(coachVoiceStyle)
                // null = a deliberately silent beat (freestyle mode) —
                // nothing to say, just wait out the delay and check again.
                if (command != null) {
                    // Word mode was 1.2x but real-device testing found it
                    // too fast to actually catch — normal rate for both,
                    // except a flow-sourced command's own faster rate (see
                    // CoachCommand.speechRate/CoachSession.buildFlowStep).
                    announcer.announceCommand(command, coachVoiceStyle)
                }
                delay(session.delayMillisFor(command))
            } else {
                delay(200)
            }
        }
    }

    private fun onPhaseFinished(phase: TimerPhase, roundNumber: Int) {
        if (phase == TimerPhase.ROUND) {
            if (roundNumber >= config.rounds) {
                saveTraining()
                stop()
            } else {
                runPhase(TimerPhase.REST, roundNumber + 1)
            }
        } else {
            runPhase(TimerPhase.ROUND, roundNumber)
        }
    }

    private fun saveTraining() {
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val stats = coachSession?.stats
        val description = if (mode == TrainingMode.BOXING_COACH) "Completed boxing coach session" else "Completed training"
        val title = when (trainingType) {
            "BOXING" -> "Boxing Training"
            "MMA" -> "MMA Training"
            else -> "Custom Training"
        }
        val training = Training(
            title,
            time,
            config.rounds,
            config.roundMinutes,
            3,
            description,
            trainingMode = mode.name,
            coachDifficulty = coachDifficulty?.name,
            totalPunches = stats?.totalPunches() ?: 0,
            totalCombos = stats?.attackCalls ?: 0,
            totalTacticalCommands = (stats?.defenseCalls ?: 0) + (stats?.distanceCalls ?: 0),
            punchBreakdown = stats?.serializeBreakdown(),
        )
        dbHandler.insertData(training)
    }

    override fun onDestroy() {
        tickJob?.cancel()
        coachJob?.cancel()
        releaseWakeLock()
        announcer.shutdown()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Training session", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Posts/updates the foreground-service notification, and — via
     * [OngoingActivity] — a live, system-rendered status (round + a
     * genuinely ticking countdown, driven by [Status.TimerPart] rather
     * than our own per-second redraws) that Wear OS can surface directly
     * on the watch face while the app isn't open.
     *
     * The public API here is `androidx.wear.ongoing.Status` +
     * `Status.TimerPart` — *not* the similarly-named `OngoingActivityStatus`/
     * `TimerPart` classes some docs/samples reference, which turned out to
     * be internal parcelable types (package-private in both 1.0.0 and
     * 1.1.0). Confirmed by inspecting the actual `wear-ongoing:1.1.0` AAR's
     * class list — see WEAR_OS_IMPLEMENTATION.md Notes.
     */
    private fun pushOngoingStatus(current: RoundTimerState) {
        val phaseLabel = when (current.phase) {
            TimerPhase.WARMUP -> "Warm Up"
            TimerPhase.ROUND -> "Round ${current.roundNumber}/${current.totalRounds}"
            TimerPhase.REST -> "Rest"
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BoomBee")
            .setContentText(phaseLabel)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val status = if (current.isPaused) {
            Status.Builder()
                .addTemplate("$phaseLabel — Paused")
                .build()
        } else {
            val endTimeMillis = System.currentTimeMillis() + current.remainingSeconds * 1000L
            Status.Builder()
                .addTemplate("$phaseLabel — #T#")
                .addPart("T", Status.TimerPart(endTimeMillis))
                .build()
        }

        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notificationBuilder)
            .setStaticIcon(android.R.drawable.ic_media_play)
            .setTouchIntent(contentIntent)
            .setStatus(status)
            .build()
            .apply(applicationContext)

        val notification = notificationBuilder.build()
        if (!foregroundStarted) {
            startForeground(NOTIFICATION_ID, notification)
            foregroundStarted = true
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "boombee_training"
        private const val NOTIFICATION_ID = 1
        private const val ANNOUNCE_LEAD_MS = 2500L
        private const val ROUND_START_COACH_GRACE_MS = 6000L
        // Extra silence after the bell (on top of playBell() already
        // waiting for its own tone) before the next phase's "Round X"/
        // "Rest" announcement — see the round-end branch in runPhase().
        private const val EXTRA_BELL_GAP_MS = 500L
    }
}
