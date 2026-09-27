package com.example.boombee

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.activity.addCallback
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.example.boombee.coach.ClipPlayer
import com.example.boombee.coach.CoachAudio
import com.example.boombee.coach.CoachCommand
import com.example.boombee.coach.CoachSession
import com.example.boombee.coach.TrainingMode
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.DBHandler
import com.example.boombee.data.models.Training
import com.example.boombee.databinding.FragmentSecondBinding
import com.example.boombee.survey.SurveyTrigger
import com.example.boombee.viewmodel.MainViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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

    private val coachHandler = Handler(Looper.getMainLooper())
    private var coachRunnable: Runnable? = null

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSecondBinding.inflate(inflater, container, false)
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
        }
    }

    private fun resumeTimer() {
        isTimerPaused = false
        updateButtonState()
        startCoachLoopIfNeeded()
        val resumeColor = if (timeRemainingInMillis <= 10_000) R.color.timer_danger else R.color.timer_active
        binding.timeCounter.setTextColor(ContextCompat.getColor(requireContext(), resumeColor))

        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                updateTimeDisplay()
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
        navController.navigate(R.id.action_second_to_dialog)
    }

    private fun handleRoundFinish(navController: NavController) {
        stopCoachLoop()
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
        val db = DBHandler(requireContext())
        val time = getCurrentDateTime()
        val trainingType = mainViewModel.trainingType.value ?: "Custom"
        val mode = mainViewModel.trainingMode.value ?: TrainingMode.TIMER_ONLY
        val stats = mainViewModel.coachSession?.stats
        val title = when (trainingType) {
            "BOXING" -> "Boxing Training"
            "MMA" -> "MMA Training"
            else -> "Custom Training"
        }
        val description = if (mode == TrainingMode.BOXING_COACH) "Completed boxing coach session" else "Completed training"
        val training = Training(
            title,
            time,
            roundNum,
            roundLength,
            3,
            description,
            trainingMode = mode.name,
            coachDifficulty = mainViewModel.coachDifficulty.value?.name,
            totalPunches = stats?.totalPunches() ?: 0,
            totalCombos = stats?.attackCalls ?: 0,
            totalTacticalCommands = (stats?.defenseCalls ?: 0) + (stats?.distanceCalls ?: 0),
            punchBreakdown = stats?.serializeBreakdown(),
        )
        db.insertData(training)
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

    /**
     * Speaks [command] using your own recorded voice if every part of it
     * has a clip (see [CoachAudio]/[ClipPlayer]), falling back to TTS
     * otherwise — a partially-recorded voice never breaks playback, it
     * just means some commands still use TTS until more clips exist.
     */
    private fun speak(command: CoachCommand, voiceStyle: VoiceStyle, utteranceId: String) {
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
    private fun startCoachLoopIfNeeded(isRoundStart: Boolean = false) {
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
        val initialDelay = if (isRoundStart) ROUND_START_COACH_GRACE_MS else ANNOUNCE_LEAD_MS
        coachHandler.postDelayed(runnable, initialDelay)
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
        if (!mainViewModel.getSoundStatus()) return
        try {
            stopSpeaking()
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 800)
        } catch (e: Exception) { /* no-op */ }
    }

    private fun playWarningBeep() {
        if (!mainViewModel.getSoundStatus()) return
        try {
            stopSpeaking()
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 80)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) { /* no-op */ }
    }

    private fun getCurrentDateTime(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return LocalDateTime.now().format(formatter)
    }

    override fun onDestroy() {
        super.onDestroy()
        prepareCountDownTimer?.cancel()
        if (::countDownTimer.isInitialized) countDownTimer.cancel()
        cancelPendingRoundStart()
        cancelPendingRestNavigate()
        stopWarmupCoachLoop()
        stopCoachLoop()
        textToSpeech?.shutdown()
        toneGenerator?.release()
        clipPlayer.shutdown()
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = Second()

        private const val ANNOUNCE_LEAD_MS = 2500L
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
