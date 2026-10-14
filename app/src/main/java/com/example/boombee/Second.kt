package com.example.boombee

import com.example.boombee.history.PhoneSessionRecord
import com.example.boombee.history.EffortPrompt
import com.example.boombee.analytics.Analytics
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.activity.addCallback
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.example.boombee.coach.CallDisplay
import com.example.boombee.coach.ClipPlayer
import com.example.boombee.coach.CoachAudio
import com.example.boombee.coach.CoachCommand
import com.example.boombee.coach.CoachSession
import com.example.boombee.coach.TakePlayer
import com.example.boombee.coach.TrainingMode
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.DBHandler
import com.example.boombee.data.models.Training
import com.example.boombee.databinding.FragmentSecondBinding
import com.example.boombee.survey.SurveyTrigger
import com.example.boombee.viewmodel.MainViewModel
import java.util.Locale

class Second : Fragment() {

    private lateinit var binding: FragmentSecondBinding
    private lateinit var countDownTimer: CountDownTimer
    private var prepareCountDownTimer: CountDownTimer? = null

    private var timeRemainingInMillis = 0L
    private var initialTimeInMillis = 0L
    private var halfTimeBellPlayed = false
    private var finalWarningPlayed = false
    private var isTimerRunning = false
    private var isTimerPaused = false

    private var currentRoundNumber = 0
    private var roundNum = 0
    private var roundLength = 0

    private val timerHandler = Handler(Looper.getMainLooper())
    private var pendingRoundStartRunnable: Runnable? = null
    private var pendingRestNavigateRunnable: Runnable? = null

    private var warmupCountdownTimer: CountDownTimer? = null
    private val warmupHandler = Handler(Looper.getMainLooper())
    private var warmupRunnable: Runnable? = null

    private var textToSpeech: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null
    private val clipPlayer: ClipPlayer by lazy { ClipPlayer(requireContext()) }
    private val takePlayer: TakePlayer by lazy { TakePlayer(requireContext()) }

    private val coachHandler = Handler(Looper.getMainLooper())
    private var coachRunnable: Runnable? = null

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSecondBinding.inflate(inflater, container, false)
        setupCallDisplay()
        setupNavigation()
        observeViewModel()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupNavigation() {
        binding.roundFragmentBtn.setOnClickListener {
            if (isTimerRunning && !isTimerPaused) {
                pauseTimer()
            } else if (isTimerPaused) {
                resumeTimer()
            } else {
                stopSession(it.findNavController())
            }
        }
        binding.stopTrainingBtn.setOnClickListener {
            stopSession(it.findNavController())
        }
        // Back mid-round would otherwise pop straight to the home screen and
        // silently drop the session; treat it exactly like STOP.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            stopSession(findNavController())
        }
    }

    private fun observeViewModel() {
        mainViewModel.apply {
            currentRound.observe(viewLifecycleOwner) { round ->
                currentRoundNumber = round
                binding.roundNum.text = "Round $round"
                binding.callText.text = ""
                binding.currentRoundIndicator.text = round.toString()
            }
            numOfRounds.observe(viewLifecycleOwner) { rounds ->
                roundNum = rounds
                binding.totalRoundsIndicator.text = rounds.toString()
            }
            roundLengthInMin.observe(viewLifecycleOwner) { length ->
                setupRoundTimer(length)
            }
        }
    }

    private fun setupRoundTimer(length: Int) {
        // Cancel any in-flight timers before setting up (guards against observer re-fires on rotation)
        prepareCountDownTimer?.cancel()
        if (::countDownTimer.isInitialized) countDownTimer.cancel()
        cancelPendingRoundStart()
        cancelPendingRestNavigate()
        stopWarmupCoachLoop()
        isTimerRunning = false

        roundLength = length
        initialTimeInMillis = (length * 60 * 1000).toLong()
        timeRemainingInMillis = initialTimeInMillis
        halfTimeBellPlayed = false
        finalWarningPlayed = false

        if (currentRoundNumber > roundNum) {
            saveTraining()
            return
        }

        if (currentRoundNumber == 1) {
            startPrepareCountdown()
        } else {
            playRoundSound(currentRoundNumber)
            beginRoundWithLeadIn(findNavController())
            startCoachLoopIfNeeded(isRoundStart = true)
        }
    }

    // 10-second "GET READY" countdown before round 1
    private fun startPrepareCountdown() {
        binding.roundNum.text = "GET READY"
        binding.timeCounter.setTextColor(ContextCompat.getColor(requireContext(), R.color.timer_warning))
        binding.pauseHintText.text = "Get in position!"

        prepareCountDownTimer = object : CountDownTimer(10_000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                binding.timeCounter.text = secondsLeft.toString()
                if (secondsLeft <= 3) {
                    playWarningBeep()
                }
            }

            override fun onFinish() {
                binding.timeCounter.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.timer_active)
                )
                binding.pauseHintText.text = "Tap to pause training"
                startRoundOneFlow()
            }
        }.start()
    }

    /**
     * Round 1 only: an optional dedicated warm-up phase (jab-led, slow
     * pace — see [CoachSession.nextWarmupCommand]) runs to completion
     * *before* "Round 1, begin!" is even spoken, so it doesn't eat into
     * Round 1's own configured length. Skipped straight to the normal
     * round-start sequence if warm-up is off, not Coach mode, or there's
     * no active [CoachSession].
     */
    private fun startRoundOneFlow() {
        val warmupSeconds = mainViewModel.warmupSeconds.value ?: 0
        val session = mainViewModel.coachSession
        if (mainViewModel.trainingMode.value == TrainingMode.BOXING_COACH && warmupSeconds > 0 && session != null) {
            runWarmupPhase(warmupSeconds, session) { beginRoundOne() }
        } else {
            beginRoundOne()
        }
    }

    private fun beginRoundOne() {
        binding.roundNum.text = "Round $currentRoundNumber"
        playRoundSound(currentRoundNumber)
        beginRoundWithLeadIn(findNavController())
        startCoachLoopIfNeeded(isRoundStart = true)
    }

    /** Jab-led, slow-paced pre-round warm-up — see [startRoundOneFlow]. Purely to get the user familiar with the coach's voice/pacing before Round 1 actually starts. */
    private fun runWarmupPhase(durationSeconds: Int, session: CoachSession, onComplete: () -> Unit) {
        Log.d("boombeelogs", "=== WARMUP PHASE START durationSec=$durationSeconds ===")
        binding.roundNum.text = "WARM UP"
        binding.timeCounter.setTextColor(ContextCompat.getColor(requireContext(), R.color.timer_warning))
        binding.pauseHintText.text = "Get familiar with the coach"

        warmupCountdownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                binding.timeCounter.text = String.format(Locale.US, "00:%02d", secondsLeft.coerceAtMost(99))
            }

            override fun onFinish() {
                stopWarmupCoachLoop()
                Log.d("boombeelogs", "=== WARMUP PHASE END ===")
                binding.timeCounter.setTextColor(ContextCompat.getColor(requireContext(), R.color.timer_active))
                onComplete()
            }
        }.start()

        val voiceStyle = mainViewModel.voiceStyle.value ?: VoiceStyle.WORDS
        val runnable = object : Runnable {
            override fun run() {
                if (mainViewModel.getSoundStatus()) {
                    val command = session.nextWarmupCommand()
                    speak(command, voiceStyle, "warmup")
                    warmupHandler.postDelayed(this, session.warmupDelayMillis(command))
                } else {
                    warmupHandler.postDelayed(this, 1000)
                }
            }
        }
        warmupRunnable = runnable
        warmupHandler.post(runnable)
    }

    private fun stopWarmupCoachLoop() {
        warmupCountdownTimer?.cancel()
        warmupCountdownTimer = null
        warmupRunnable?.let { warmupHandler.removeCallbacks(it) }
        warmupRunnable = null
    }

    /**
     * Waits out [ANNOUNCE_LEAD_MS] (same constant the coach loop uses)
     * before the visual countdown actually starts ticking, so "Round X,
     * begin!" finishes speaking before the displayed time moves — without
     * this, the clock was already a couple seconds in by the time the
     * announcement finished, so the timer and the voice looked out of sync.
     * The full round time is shown immediately so the screen isn't blank
     * or stale during the lead-in.
     */
    private fun beginRoundWithLeadIn(navController: NavController) {
        Log.d("boombeelogs", "beginRoundWithLeadIn: round=$currentRoundNumber leadMs=$ANNOUNCE_LEAD_MS initialMs=$initialTimeInMillis")
        updateTimeDisplay()
        binding.timeCounter.setTextColor(ContextCompat.getColor(requireContext(), R.color.timer_active))
        val runnable = Runnable { startTimer(navController) }
        pendingRoundStartRunnable = runnable
        timerHandler.postDelayed(runnable, ANNOUNCE_LEAD_MS)
    }

    private fun cancelPendingRoundStart() {
        pendingRoundStartRunnable?.let { timerHandler.removeCallbacks(it) }
        pendingRoundStartRunnable = null
    }

    private fun startTimer(navController: NavController) {
        isTimerRunning = true
        isTimerPaused = false
        updateButtonState()
        binding.timeCounter.setTextColor(ContextCompat.getColor(requireContext(), R.color.timer_active))

        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                updateTimeDisplay()
                maybeStartFinisher(millisUntilFinished)
                maybeCheckpoint(millisUntilFinished)

                // Half-time bell
                if (!halfTimeBellPlayed && millisUntilFinished <= initialTimeInMillis / 2) {
                    halfTimeBellPlayed = true
                    playBellSound()
                }

                // Last 10 seconds: color turns red; a single warning beep
                // marks entering it (see [finalWarningPlayed]) — the coach
                // keeps calling right through the end of the round.
                if (millisUntilFinished <= 10_000) {
                    binding.timeCounter.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.timer_danger)
                    )
                    if (!finalWarningPlayed) {
                        finalWarningPlayed = true
                        playWarningBeep()
                    }
                }
            }

            override fun onFinish() {
                isTimerRunning = false
                isTimerPaused = false
                binding.timeCounter.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.timer_active)
                )
                handleRoundFinish(navController)
            }
        }.start()
    }

    private fun pauseTimer() {
        if (::countDownTimer.isInitialized && isTimerRunning) {
            countDownTimer.cancel()
            isTimerPaused = true
            mainViewModel.setLeftTime((timeRemainingInMillis / 1000).toInt())
            updateButtonState()
            stopCoachLoop()
            takePlayer.pause()
        }
    }

    private fun resumeTimer() {
        isTimerPaused = false
        updateButtonState()
        // A paused finisher take carries on where it stopped; otherwise the coach resumes.
        if (takePlayer.isActive) takePlayer.resume() else startCoachLoopIfNeeded()
        val resumeColor = if (timeRemainingInMillis <= 10_000) R.color.timer_danger else R.color.timer_active
        binding.timeCounter.setTextColor(ContextCompat.getColor(requireContext(), resumeColor))

        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                updateTimeDisplay()
                maybeStartFinisher(millisUntilFinished)
                maybeCheckpoint(millisUntilFinished)
                if (!halfTimeBellPlayed && millisUntilFinished <= initialTimeInMillis / 2) {
                    halfTimeBellPlayed = true
                    playBellSound()
                }
                if (millisUntilFinished <= 10_000) {
                    binding.timeCounter.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.timer_danger)
                    )
                    if (!finalWarningPlayed) {
                        finalWarningPlayed = true
                        playWarningBeep()
                    }
                }
            }

            override fun onFinish() {
                isTimerRunning = false
                isTimerPaused = false
                binding.timeCounter.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.timer_active)
                )
                handleRoundFinish(findNavController())
            }
        }.start()
        isTimerRunning = true
    }

    private fun stopSession(navController: NavController) {
        prepareCountDownTimer?.cancel()
        if (::countDownTimer.isInitialized) countDownTimer.cancel()
        cancelPendingRoundStart()
        cancelPendingRestNavigate()
        stopWarmupCoachLoop()
        isTimerRunning = false
        isTimerPaused = false
        mainViewModel.setLeftTime((timeRemainingInMillis / 1000).toInt())
        stopCoachLoop()
        takePlayer.stop()
        Analytics.log(requireContext(), "training_abort", "round_reached" to currentRoundNumber)
        navController.navigate(R.id.action_second_to_dialog)
    }

    private fun handleRoundFinish(navController: NavController) {
        stopCoachLoop()
        takePlayer.stop()
        if (currentRoundNumber >= roundNum) {
            announceWorkoutDone()
            saveTraining()
        } else {
            playBellSound()
            mainViewModel.setCurrentRound(currentRoundNumber + 1)
            // Rest.kt has its own separate TextToSpeech instance, so it has
            // no way to know this fragment's bell tone is still physically
            // ringing — navigating immediately made "Rest time, recover!"
            // start speaking right over it. Wait out the bell's duration first.
            val runnable = Runnable {
                if (isAdded) navController.navigate(R.id.action_second_to_rest)
            }
            pendingRestNavigateRunnable = runnable
            timerHandler.postDelayed(runnable, BELL_DURATION_MS)
        }
    }

    private fun cancelPendingRestNavigate() {
        pendingRestNavigateRunnable?.let { timerHandler.removeCallbacks(it) }
        pendingRestNavigateRunnable = null
    }

    private fun saveTraining() {
        val id = PhoneSessionRecord.save(
            requireContext(), mainViewModel,
            completed = true, roundsReached = roundNum, activeSeconds = roundNum * roundLength * 60,
        )
        EffortPrompt.markPending(requireContext(), id)
        Analytics.log(requireContext(), "training_complete", *Analytics.trainingParams(mainViewModel))
        SurveyTrigger.onTrainingCompleted(requireContext())
        findNavController().navigate(R.id.action_second_to_first)
    }

    private fun updateTimeDisplay() {
        val minutes = timeRemainingInMillis / 60000
        val seconds = (timeRemainingInMillis % 60000) / 1000
        binding.timeCounter.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateButtonState() {
        if (isTimerPaused) {
            binding.roundFragmentBtn.text = "RESUME"
            binding.roundFragmentBtn.icon = context?.getDrawable(android.R.drawable.ic_media_play)
            binding.pauseHintText.text = "Tap to resume training"
        } else if (isTimerRunning) {
            binding.roundFragmentBtn.text = "PAUSE"
            binding.roundFragmentBtn.icon = context?.getDrawable(android.R.drawable.ic_media_pause)
            binding.pauseHintText.text = "Tap to pause training"
        }
    }

    // --- Audio ---

    private fun initTtsIfNeeded(onReady: (TextToSpeech) -> Unit) {
        val tts = textToSpeech
        if (tts != null) {
            onReady(tts)
        } else {
            textToSpeech = TextToSpeech(requireContext()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val ttsReady = textToSpeech ?: return@TextToSpeech
                    var result = ttsReady.setLanguage(Locale.getDefault())
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        result = ttsReady.setLanguage(Locale.ENGLISH)
                    }
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        onReady(ttsReady)
                    }
                }
            }
        }
    }

    private var showCalls = false

    // Big call text with the timer shrunk to make room — FightFlow-style,
    // only in Boxing Coach mode with "Show calls on screen" on.
    private fun setupCallDisplay() {
        showCalls = mainViewModel.trainingMode.value == TrainingMode.BOXING_COACH && mainViewModel.getShowCallsOnScreen()
        binding.callText.visibility = if (showCalls) View.VISIBLE else View.GONE
        if (!showCalls) return
        binding.callText.text = ""
        binding.timeCounter.layoutParams = binding.timeCounter.layoutParams.apply { height = (84 * resources.displayMetrics.density).toInt() }
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(binding.timeCounter, 32, 64, 2, TypedValue.COMPLEX_UNIT_SP)
        binding.roundNum.textSize = 32f
    }

    private fun showCall(command: CoachCommand, voiceStyle: VoiceStyle) {
        if (!showCalls || !isAdded) return
        binding.callText.text = CallDisplay.text(command.parts, voiceStyle)
        // Quick pop so a repeated call still reads as new from across the room.
        binding.callText.scaleX = 0.85f
        binding.callText.scaleY = 0.85f
        binding.callText.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
    }

    /**
     * Speaks [command] using your own recorded voice if every part of it
     * has a clip (see [CoachAudio]/[ClipPlayer]), falling back to TTS
     * otherwise — a partially-recorded voice never breaks playback, it
     * just means some commands still use TTS until more clips exist.
     */
    private fun speak(command: CoachCommand, voiceStyle: VoiceStyle, utteranceId: String) {
        showCall(command, voiceStyle)
        val keys = CoachAudio.keysFor(command.parts, voiceStyle)
        if (keys != null && clipPlayer.hasAllClips(keys)) {
            clipPlayer.playCommand(keys, isCombo = command.parts.size > 1)
        } else {
            initTtsIfNeeded { tts ->
                tts.setSpeechRate(command.speechRate)
                tts.speak(command.spokenText(voiceStyle), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
        }
    }

    private fun playRoundSound(round: Int) {
        if (!mainViewModel.getSoundStatus()) return
        // Your recorded voice only covers "Round" + the number (1-6) — no
        // "begin!" was recorded — so the clip version drops that word.
        // Rounds past 6, or before any clips exist, fall back to the full
        // original TTS phrase automatically since hasAllClips() won't find
        // a number clip that high.
        val keys = listOf("misc_round", "number_$round")
        if (clipPlayer.hasAllClips(keys)) {
            clipPlayer.play(keys)
        } else {
            initTtsIfNeeded { tts ->
                // Explicit normal rate: a prior Coach Word-mode call can leave
                // the shared TTS engine's rate faster than default otherwise.
                tts.setSpeechRate(1.0f)
                tts.speak("Round $round, begin!", TextToSpeech.QUEUE_FLUSH, null, "round_$round")
            }
        }
    }

    // --- Boxing Coach (v2) ---
    // Calls punches/tactical commands via TTS on its own schedule during an
    // active round only — driven by the shared CoachSession held in
    // MainViewModel so it survives this fragment being recreated between
    // rounds. A plain Handler loop (not tied to the round CountDownTimer)
    // since pacing depends on combo length/difficulty, not the 1s round tick.

    /** @param isRoundStart true only for a genuine new round (not a resume-from-pause) — forces that round's first call to Attack. */
    private fun startCoachLoopIfNeeded(isRoundStart: Boolean = false, afterTake: Boolean = false) {
        if (mainViewModel.trainingMode.value != TrainingMode.BOXING_COACH) return
        val session = mainViewModel.coachSession ?: return
        Log.d(
            "boombeelogs",
            "startCoachLoopIfNeeded: isRoundStart=$isRoundStart round=$currentRoundNumber roundLength=$roundLength warmupSeconds=${mainViewModel.warmupSeconds.value} voiceStyle=${mainViewModel.voiceStyle.value}"
        )
        if (isRoundStart) session.startRound(currentRoundNumber, roundLength * 60)
        stopCoachLoop()
        val runnable = object : Runnable {
            override fun run() {
                // Leave a short quiet beat before a finisher take instead of
                // starting a call it would have to cut off.
                if (finisherIsImminent()) {
                    coachHandler.postDelayed(this, 500)
                    return
                }
                if (mainViewModel.getSoundStatus()) {
                    val voiceStyle = mainViewModel.voiceStyle.value ?: VoiceStyle.WORDS
                    val command = session.nextCommand(voiceStyle)
                    // null = a deliberately silent beat (freestyle mode) —
                    // nothing to say, just wait out the delay and check again.
                    if (command != null) {
                        speak(command, voiceStyle, "coach")
                    }
                    coachHandler.postDelayed(this, session.delayMillisFor(command))
                } else {
                    coachHandler.postDelayed(this, 1000)
                }
            }
        }
        coachRunnable = runnable
        // A genuine round start gets a longer grace period than just
        // waiting out "Round X, begin!" — a few free seconds to feel out
        // the opening before the coach starts calling, like a real round.
        // Resuming from pause has no such announcement to wait for at all,
        // so it only needs the plain lead-in.
        val initialDelay = when {
            isRoundStart -> ROUND_START_COACH_GRACE_MS
            afterTake -> AFTER_TAKE_LEAD_MS
            else -> ANNOUNCE_LEAD_MS
        }
        coachHandler.postDelayed(runnable, initialDelay)
    }

    /** Keeps the History row current every 15 s of round time (see PhoneSessionRecord). */
    private fun maybeCheckpoint(remainingMs: Long) {
        val remainingSeconds = (remainingMs / 1000).toInt()
        if (remainingSeconds % 15 != 0) return
        val active = PhoneSessionRecord.activeSeconds(mainViewModel, currentRoundNumber, remainingSeconds)
        PhoneSessionRecord.checkpoint(requireContext(), mainViewModel, currentRoundNumber, active)
    }

    // --- Recorded take finisher (see FinisherPlanner / coach_takes_general.txt) ---

    private fun finisherIsImminent(): Boolean {
        val plan = mainViewModel.coachSession?.finisher ?: return false
        return !takePlayer.isActive && timeRemainingInMillis - plan.startWhenRemainingMs < QUIET_BEFORE_TAKE_MS
    }

    /** Starts this round's take when the remaining time reaches its planned start, so it ends with the round (or at freestyle). */
    private fun maybeStartFinisher(remainingMs: Long) {
        val session = mainViewModel.coachSession ?: return
        val plan = session.finisher ?: return
        if (takePlayer.isActive || remainingMs > plan.startWhenRemainingMs || !mainViewModel.getSoundStatus()) return
        if (remainingMs < plan.take.durationMs) {
            // Too late to fit it whole (e.g. resumed past the start): skip it this round.
            session.onTakeFinished()
            return
        }
        stopCoachLoop()
        stopSpeaking()
        val voiceStyle = mainViewModel.voiceStyle.value ?: VoiceStyle.WORDS
        Log.d("boombeelogs", "finisher: starting ${plan.take.id} at remainingMs=$remainingMs")
        takePlayer.start(
            plan.take,
            onCue = { cue -> showCall(session.recordTakeCue(cue), voiceStyle) },
            onDone = {
                session.onTakeFinished()
                if (isAdded && isTimerRunning && !isTimerPaused) startCoachLoopIfNeeded(afterTake = true)
            },
        )
    }

    private fun stopCoachLoop() {
        coachRunnable?.let { coachHandler.removeCallbacks(it) }
        coachRunnable = null
    }

    private fun announceWorkoutDone() {
        if (!mainViewModel.getSoundStatus()) return
        playBellSound()
        initTtsIfNeeded { tts ->
            tts.setSpeechRate(1.0f)
            tts.speak("Workout complete! Well done!", TextToSpeech.QUEUE_FLUSH, null, "workout_done")
        }
    }

    /**
     * A coach TTS call and a bell/warning tone run on independent audio
     * paths (TTS engine vs. [ToneGenerator]) with no built-in coordination
     * — [TextToSpeech.QUEUE_FLUSH] stops one TTS utterance for the next,
     * but does nothing for a tone starting mid-speech, which is audible as
     * garbled overlap. Called right before every tone below.
     */
    private fun stopSpeaking() {
        textToSpeech?.stop()
        clipPlayer.stop()
    }

    private fun playBellSound() {
        if (!mainViewModel.getSoundStatus() || takePlayer.isActive) return
        try {
            stopSpeaking()
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 800)
        } catch (e: Exception) { /* no-op */ }
    }

    private fun playWarningBeep() {
        if (!mainViewModel.getSoundStatus() || takePlayer.isActive) return
        try {
            stopSpeaking()
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 80)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) { /* no-op */ }
    }


    override fun onDestroy() {
        super.onDestroy()
        prepareCountDownTimer?.cancel()
        if (::countDownTimer.isInitialized) countDownTimer.cancel()
        cancelPendingRoundStart()
        cancelPendingRestNavigate()
        stopWarmupCoachLoop()
        stopCoachLoop()
        takePlayer.stop()
        textToSpeech?.shutdown()
        toneGenerator?.release()
        clipPlayer.shutdown()
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = Second()

        private const val ANNOUNCE_LEAD_MS = 2500L
        /** No new coach call this close to a finisher take, so the take never cuts one off. */
        private const val QUIET_BEFORE_TAKE_MS = 3000L
        /** The take already ends with a breath; freestyle (or the last seconds) follows almost straight away. */
        private const val AFTER_TAKE_LEAD_MS = 300L
        private const val ROUND_START_COACH_GRACE_MS = 6000L
        // playBellSound()'s ToneGenerator duration (800ms) plus a little
        // extra breathing room — Rest.kt has its own separate TTS instance
        // with no way to know this fragment's bell is still ringing, so
        // navigation waits it out first. Real-device testing found the
        // bare tone duration alone still read as running into the next
        // announcement, hence the padding on top.
        private const val BELL_DURATION_MS = 1300L
    }
}
