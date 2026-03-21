package com.example.spartime

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.example.spartime.data.DBHandler
import com.example.spartime.data.models.Training
import com.example.spartime.databinding.FragmentSecondBinding
import com.example.spartime.viewmodel.MainViewModel
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
    private var isTimerRunning = false
    private var isTimerPaused = false

    private var currentRoundNumber = 0
    private var roundNum = 0
    private var roundLength = 0

    private var textToSpeech: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null

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
    }

    @RequiresApi(Build.VERSION_CODES.O)
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupRoundTimer(length: Int) {
        // Cancel any in-flight timers before setting up (guards against observer re-fires on rotation)
        prepareCountDownTimer?.cancel()
        if (::countDownTimer.isInitialized) countDownTimer.cancel()
        isTimerRunning = false

        roundLength = length
        initialTimeInMillis = (length * 60 * 1000).toLong()
        timeRemainingInMillis = initialTimeInMillis
        halfTimeBellPlayed = false

        if (currentRoundNumber > roundNum) {
            saveTraining()
            return
        }

        if (currentRoundNumber == 1) {
            startPrepareCountdown()
        } else {
            playRoundSound(currentRoundNumber)
            startTimer(findNavController())
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

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onFinish() {
                binding.timeCounter.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.timer_active)
                )
                binding.roundNum.text = "Round $currentRoundNumber"
                binding.pauseHintText.text = "Tap to pause training"
                playRoundSound(currentRoundNumber)
                startTimer(findNavController())
            }
        }.start()
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

                // Last 10 seconds: color turns red + beep each second
                if (millisUntilFinished <= 10_000) {
                    binding.timeCounter.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.timer_danger)
                    )
                    playWarningBeep()
                }
            }

            @RequiresApi(Build.VERSION_CODES.O)
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
        }
    }

    private fun resumeTimer() {
        isTimerPaused = false
        updateButtonState()
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
                    playWarningBeep()
                }
            }

            @RequiresApi(Build.VERSION_CODES.O)
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
        isTimerRunning = false
        isTimerPaused = false
        mainViewModel.setLeftTime((timeRemainingInMillis / 1000).toInt())
        navController.navigate(R.id.action_second_to_dialog)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleRoundFinish(navController: NavController) {
        if (currentRoundNumber >= roundNum) {
            announceWorkoutDone()
            saveTraining()
        } else {
            playBellSound()
            mainViewModel.setCurrentRound(currentRoundNumber + 1)
            navController.navigate(R.id.action_second_to_rest)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveTraining() {
        val db = DBHandler(requireContext())
        val time = getCurrentDateTime()
        val trainingType = mainViewModel.trainingType.value ?: "Custom"
        val training = when (trainingType) {
            "BOXING" -> Training("Boxing Training", time, roundNum, roundLength, 3, "Completed boxing training")
            "MMA" -> Training("MMA Training", time, roundNum, roundLength, 3, "Completed MMA training")
            else -> Training("Custom Training", time, roundNum, roundLength, 3, "Completed custom training")
        }
        db.insertData(training)
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

    private fun playRoundSound(round: Int) {
        if (!mainViewModel.getSoundStatus()) return
        initTtsIfNeeded { tts ->
            tts.speak("Round $round, begin!", TextToSpeech.QUEUE_FLUSH, null, "round_$round")
        }
    }

    private fun announceWorkoutDone() {
        if (!mainViewModel.getSoundStatus()) return
        playBellSound()
        initTtsIfNeeded { tts ->
            tts.speak("Workout complete! Well done!", TextToSpeech.QUEUE_FLUSH, null, "workout_done")
        }
    }

    private fun playBellSound() {
        if (!mainViewModel.getSoundStatus()) return
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 800)
        } catch (e: Exception) { /* no-op */ }
    }

    private fun playWarningBeep() {
        if (!mainViewModel.getSoundStatus()) return
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 80)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) { /* no-op */ }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentDateTime(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return LocalDateTime.now().format(formatter)
    }

    override fun onDestroy() {
        super.onDestroy()
        prepareCountDownTimer?.cancel()
        if (::countDownTimer.isInitialized) countDownTimer.cancel()
        textToSpeech?.shutdown()
        toneGenerator?.release()
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) = Second()
    }
}
