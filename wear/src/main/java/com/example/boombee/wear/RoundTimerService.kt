package com.example.boombee.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.example.boombee.coach.CoachCommand
import com.example.boombee.coach.CoachSection
import com.example.boombee.coach.CoachSession
import com.example.boombee.coach.TakePlayer
import com.example.boombee.coach.CommandPart
import com.example.boombee.coach.Difficulty
import com.example.boombee.coach.Phrases
import com.example.boombee.coach.TrainingMode
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.DBHandler
import com.example.boombee.data.Dao
import com.example.boombee.data.PreferencesProvider
import com.example.boombee.data.TrainingRecords
import com.example.boombee.heartrate.HeartRateBias
import com.example.boombee.heartrate.HeartRateCoach
import com.example.boombee.heartrate.HeartRateRecorder
import com.example.boombee.heartrate.HeartRateSummary
import com.example.boombee.heartrate.HeartRateZones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private lateinit var takePlayer: TakePlayer
    private lateinit var dbHandler: DBHandler
    private lateinit var dao: Dao
    private lateinit var heartRateMonitor: HeartRateMonitor

    // Heart rate for the running session (null = off, see startHeartRate()).
    private var hrRecorder: HeartRateRecorder? = null
    private var hrCoach: HeartRateCoach? = null
    private var hrMaxHr = 0
    private var hrAdaptive = false

    private var trainingType: String = "BOXING"
    private var config: SessionConfig = SessionConfig(rounds = 3, roundMinutes = 3, restMinutes = 1)
    private var mode: TrainingMode = TrainingMode.TIMER_ONLY
    private var coachDifficulty: Difficulty? = null
    private var coachVoiceStyle: VoiceStyle = VoiceStyle.WORDS
    private var coachSession: CoachSession? = null

    private val _state = MutableStateFlow<RoundTimerState?>(null)
    val state: StateFlow<RoundTimerState?> = _state

    // History record for the running session (see TrainingRecords).
    private var sessionStartedAt = 0L
    private var activeSeconds = 0
    private var warmupSecondsUsed = 0
    private var roundReached = 0

    /** Session just saved and waiting for its effort rating; the UI clears it via [clearPendingEffort]. */
    private val _pendingEffortId = MutableStateFlow<Long?>(null)
    val pendingEffortId: StateFlow<Long?> = _pendingEffortId

    /** Heart-rate summary of the session just saved, shown above the effort prompt. */
    private val _lastHeartRate = MutableStateFlow<HeartRateSummary?>(null)
    val lastHeartRate: StateFlow<HeartRateSummary?> = _lastHeartRate

    fun clearPendingEffort() {
        _pendingEffortId.value = null
        _lastHeartRate.value = null
    }

    override fun onCreate() {
        super.onCreate()
        announcer = RoundAnnouncer(this)
        dbHandler = DBHandler(this)
        dao = Dao(PreferencesProvider(this))
        heartRateMonitor = HeartRateMonitor(this, scope)
        takePlayer = TakePlayer(this)
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
        sessionStartedAt = System.currentTimeMillis()
        activeSeconds = 0
        warmupSecondsUsed = if (trainingMode == TrainingMode.BOXING_COACH) warmupSeconds else 0
        roundReached = 1
        _pendingEffortId.value = null
        _lastHeartRate.value = null
        startHeartRate()
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
        // A playing finisher take pauses and resumes with the clock.
        if (takeRunning) {
            if (_state.value?.isPaused == true) takePlayer.pause() else takePlayer.resume()
        }
    }

    /** Stop pressed: keeps the session in history (as stopped) once a minute of rounds was done. */
    fun stopByUser() {
        if (_state.value != null && activeSeconds >= MIN_SAVED_ACTIVE_SECONDS) saveTraining(completed = false)
        stop()
    }

    fun stop() {
        tickJob?.cancel()
        coachJob?.cancel()
        stopTake()
        heartRateMonitor.stop()
        hrRecorder = null
        hrCoach = null
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
        if (phase == TimerPhase.ROUND) roundReached = roundNumber
        stopTake()
        takeStartedThisRound = false
        if (phase == TimerPhase.ROUND) {
            val target = targetZoneFor(roundNumber)
            hrRecorder?.startRound(target)
            hrCoach?.startRound(target, SystemClock.elapsedRealtime())
            _state.update { it?.copy(targetZone = target) }
        } else {
            hrRecorder?.startRest()
            hrCoach?.stopRound()
        }
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
                            // Never beep over a playing take; the vibration still marks it.
                            if (!takeRunning) announcer.playWarning()
                            announcer.vibrateWarning()
                        }
                    }
                    _state.update { it?.copy(remainingSeconds = it.remainingSeconds - 1) }
                    if (phase == TimerPhase.ROUND) {
                        activeSeconds++
                        maybeStartFinisher(roundNumber)
                    }
                    tickHeartRate(phase)
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
                    tickHeartRate(TimerPhase.WARMUP)
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
                // Quiet while this round's finisher take plays, and for a
                // short beat before it so it never cuts off a call.
                if (takeRunning || finisherIsImminent(current.remainingSeconds)) {
                    delay(200)
                    continue
                }
                val now = SystemClock.elapsedRealtime()
                val coach = hrCoach
                if (coach != null) {
                    val bias = coach.bias(now)
                    // Coaching off still keeps the safety ease-off.
                    session.heartRateBias = if (hrAdaptive || bias == HeartRateBias.OVER_LIMIT) bias else HeartRateBias.NONE
                    val cue = coach.nextCue(now)?.takeIf { hrAdaptive || bias == HeartRateBias.OVER_LIMIT }
                    if (cue != null) {
                        speakHeartRateCue(cue)
                        delay(HR_CUE_DELAY_MS)
                        continue
                    }
                }
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

    // --- Recorded take finisher (see FinisherPlanner / coach_takes_general.txt) ---

    @Volatile private var takeRunning = false
    @Volatile private var takeStartedThisRound = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun finisherIsImminent(remainingSeconds: Int): Boolean {
        val plan = coachSession?.finisher ?: return false
        return !takeStartedThisRound && remainingSeconds * 1000L - plan.startWhenRemainingMs < QUIET_BEFORE_TAKE_MS
    }

    /** Called every round second; starts the take when the remaining time reaches its planned start. */
    private fun maybeStartFinisher(roundNumber: Int) {
        val session = coachSession ?: return
        val plan = session.finisher ?: return
        val remainingMs = (_state.value?.remainingSeconds ?: return) * 1000L
        if (takeStartedThisRound || remainingMs > plan.startWhenRemainingMs || !dao.getSoundStatus()) return
        takeStartedThisRound = true
        if (remainingMs < plan.take.durationMs) {
            session.onTakeFinished()
            return
        }
        takeRunning = true
        mainHandler.post {
            if (_state.value?.roundNumber != roundNumber || _state.value?.phase != TimerPhase.ROUND) {
                takeRunning = false
                return@post
            }
            announcer.stop()
            Log.d("boombeelogs", "finisher(watch): starting ${plan.take.id} at remainingMs=$remainingMs")
            takePlayer.start(
                plan.take,
                onCue = { cue -> session.recordTakeCue(cue) },
                onDone = {
                    takeRunning = false
                    session.onTakeFinished()
                },
            )
        }
    }

    private fun stopTake() {
        takeRunning = false
        mainHandler.post { takePlayer.stop() }
    }

    private fun onPhaseFinished(phase: TimerPhase, roundNumber: Int) {
        if (phase == TimerPhase.ROUND) {
            if (roundNumber >= config.rounds) {
                saveTraining(completed = true)
                stop()
            } else {
                runPhase(TimerPhase.REST, roundNumber + 1)
            }
        } else {
            runPhase(TimerPhase.ROUND, roundNumber)
        }
    }

    private fun saveTraining(completed: Boolean) {
        val heartRateSummary = hrRecorder?.summary()
        val training = TrainingRecords.build(
            trainingType = trainingType,
            mode = mode,
            coachDifficulty = coachDifficulty,
            voiceStyle = coachVoiceStyle,
            platform = "watch",
            startedAt = sessionStartedAt,
            activeSeconds = activeSeconds,
            plannedRounds = config.rounds,
            roundsReached = if (completed) config.rounds else roundReached,
            roundMinutes = config.roundMinutes,
            restSeconds = config.restMinutes * 60,
            warmupSeconds = warmupSecondsUsed,
            completed = completed,
            stats = coachSession?.stats,
            heartRate = heartRateSummary,
        )
        val id = dbHandler.insertData(training)
        if (id > 0) {
            _lastHeartRate.value = heartRateSummary
            _pendingEffortId.value = id
        }
    }

    override fun onDestroy() {
        tickJob?.cancel()
        coachJob?.cancel()
        heartRateMonitor.stop()
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
            startForegroundTyped(notification)
            foregroundStarted = true
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Heart rate for this session, if turned on, permitted and the age is
     * set (zones need max HR = 220 − age). Adaptive coaching needs Coach
     * mode too; timer-only sessions just record.
     */
    private fun startHeartRate() {
        hrRecorder = null
        hrCoach = null
        val age = dao.getAge()
        if (!dao.getHeartRateEnabled() || age == null || !HeartRateMonitor.hasPermission(this)) return
        hrMaxHr = HeartRateZones.maxHeartRate(age)
        hrAdaptive = mode == TrainingMode.BOXING_COACH && dao.getHeartRateCoachingEnabled()
        hrRecorder = HeartRateRecorder(hrMaxHr)
        hrCoach = HeartRateCoach(hrMaxHr)
        heartRateMonitor.start()
    }

    private fun targetZoneFor(roundNumber: Int): Int {
        if (hrCoach == null || !hrAdaptive) return 0
        val difficulty = coachDifficulty ?: return 0
        return HeartRateZones.targetZone(difficulty, roundNumber, config.rounds)
    }

    /** One second of session time: record the reading and show it; safety cue in timer-only mode. */
    private fun tickHeartRate(phase: TimerPhase) {
        val coach = hrCoach ?: return
        val bpm = heartRateMonitor.currentBpm()
        val now = SystemClock.elapsedRealtime()
        if (bpm != null) coach.onReading(bpm, now)
        hrRecorder?.tick(bpm)
        val overLimit = coach.bias(now) == HeartRateBias.OVER_LIMIT
        _state.update {
            it?.copy(
                heartRate = bpm,
                zone = bpm?.let { b -> HeartRateZones.zoneOf(b, hrMaxHr) } ?: 0,
                overLimit = overLimit,
            )
        }
        // Coach mode speaks its cues from runCoachLoop(); timer-only has no
        // coach loop, so only the safety cue is spoken from here.
        if (mode != TrainingMode.BOXING_COACH && phase == TimerPhase.ROUND && overLimit) {
            coach.nextCue(now)?.let { speakHeartRateCue(it) }
        }
    }

    private fun speakHeartRateCue(cue: String) {
        Log.d("boombeelogs", "heart rate cue: $cue (bpm=${_state.value?.heartRate})")
        if (cue == Phrases.HR_SLOW_DOWN) announcer.vibrateWarning()
        // Spoken directly, not via CoachSession, so cues don't count as calls in the stats.
        announcer.announceCommand(CoachCommand(CoachSection.DEFENSE, listOf(CommandPart.Phrase(cue))), coachVoiceStyle)
    }

    /**
     * The health foreground-service type lets Health Services keep
     * delivering heart rate with the screen off. It needs the heart-rate
     * permission, so without it (or on any refusal) the service falls back
     * to the special-use type alone.
     */
    private fun startForegroundTyped(notification: android.app.Notification) {
        val special = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        val type = if (hrCoach != null) special or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else special
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (e: Exception) {
            Log.w("boombeelogs", "startForeground with health type failed, falling back", e)
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, special)
        }
    }

    companion object {
        private const val CHANNEL_ID = "boombee_training"
        private const val NOTIFICATION_ID = 1
        private const val ANNOUNCE_LEAD_MS = 2500L
        /** No new coach call this close to a finisher take, so the take never cuts one off. */
        private const val QUIET_BEFORE_TAKE_MS = 3000L
        private const val MIN_SAVED_ACTIVE_SECONDS = 60
        private const val ROUND_START_COACH_GRACE_MS = 6000L
        // Extra silence after the bell (on top of playBell() already
        // waiting for its own tone) before the next phase's "Round X"/
        // "Rest" announcement — see the round-end branch in runPhase().
        private const val EXTRA_BELL_GAP_MS = 500L
        // Pause after a heart-rate cue before the next coach call.
        private const val HR_CUE_DELAY_MS = 2000L
    }
}
