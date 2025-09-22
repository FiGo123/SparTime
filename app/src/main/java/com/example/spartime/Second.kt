package com.example.spartime

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.example.spartime.databinding.FragmentSecondBinding
import com.example.spartime.viewmodel.MainViewModel
import android.os.CountDownTimer
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.example.spartime.data.DBHandler
import com.example.spartime.data.models.Training
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [Second.newInstance] factory method to
 * create an instance of this fragment.
 */
class Second : Fragment() {
    private var currentRoundNumber = 0
    private lateinit var countDownTimer: CountDownTimer
    private var timeRemainingInMillis = 0L
    private var initialTimeInMinutes = 0
    private val timeForSave: MutableMap<String, Int> = mutableMapOf()
    private var isTimerPaused = false
    private var isTimerRunning = false
    private var textToSpeech: TextToSpeech? = null

    private var param1: String? = null
    private var param2: String? = null
    private var currentRound = 0
    private var roundNum = 0
    private var roundLength = 0

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentSecondBinding.inflate(inflater, container, false)

        setupNavigation(binding)
        observeViewModel(binding)

        return binding.root
    }

    private fun setupNavigation(binding: FragmentSecondBinding) {
        binding.roundFragmentBtn.setOnClickListener {
            if (isTimerRunning && !isTimerPaused) {
                pauseTimer(binding)
            } else if (isTimerPaused) {
                resumeTimer(binding)
            } else {
                stopTimer(it.findNavController())
            }
        }
        
        binding.stopTrainingBtn.setOnClickListener {
            stopTimer(it.findNavController())
        }
    }

    private fun saveCurrentState() {
        timeForSave["round"] = currentRound
        val leftTimeInSeconds = (timeRemainingInMillis / 1000).toInt()
        timeForSave["leftTime"] = leftTimeInSeconds
        mainViewModel.setLeftTime(leftTimeInSeconds)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun observeViewModel(binding: FragmentSecondBinding) {
        mainViewModel.apply {
            currentRound.observe(viewLifecycleOwner) { round ->
                currentRoundNumber = round
                binding.roundNum.text = "Round $round"
            }

            numOfRounds.observe(viewLifecycleOwner) { rounds ->
                roundNum = rounds
            }

            roundLengthInMin.observe(viewLifecycleOwner) { length ->
                setupRoundTimer(binding, length)
            }
            
            numOfRounds.observe(viewLifecycleOwner) { total ->
                binding.totalRoundsIndicator.text = total.toString()
            }
            
            currentRound.observe(viewLifecycleOwner) { current ->
                binding.currentRoundIndicator.text = current.toString()
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupRoundTimer(binding: FragmentSecondBinding, length: Int) {
        roundLength = length
        initialTimeInMinutes = length
        timeRemainingInMillis = (initialTimeInMinutes * 60 * 1000).toLong()

        if (currentRound > roundNum) {
            saveTraining()
        } else {
            startTimer(binding, findNavController())
        }
        playRoundSound(currentRound)
    }

    private fun playRoundSound(round: Int) {
        initializeTextToSpeech()
        announceRound(round)
    }
    
    private fun initializeTextToSpeech() {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(requireContext()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.getDefault()
                }
            }
        }
    }
    
    private fun announceRound(round: Int) {
        textToSpeech?.speak(
            "Round $round",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "round_announcement_$round"
        )
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

    private fun startTimer(binding: FragmentSecondBinding, navController: NavController) {
        isTimerRunning = true
        isTimerPaused = false
        updateButtonState(binding)
        
        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                updateTimeText(binding)
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onFinish() {
                isTimerRunning = false
                isTimerPaused = false
                handleTimerFinish(navController)
            }
        }.start()
    }
    
    private fun pauseTimer(binding: FragmentSecondBinding) {
        if (::countDownTimer.isInitialized && isTimerRunning) {
            countDownTimer.cancel()
            isTimerPaused = true
            updateButtonState(binding)
        }
    }
    
    private fun resumeTimer(binding: FragmentSecondBinding) {
        isTimerPaused = false
        updateButtonState(binding)
        
        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                updateTimeText(binding)
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onFinish() {
                isTimerRunning = false
                isTimerPaused = false
                handleTimerFinish(findNavController())
            }
        }.start()
    }
    
    private fun stopTimer(navController: NavController) {
        if (::countDownTimer.isInitialized) {
            countDownTimer.cancel()
        }
        isTimerRunning = false
        isTimerPaused = false
        navController.navigate(R.id.action_second_to_dialog)
        saveCurrentState()
    }
    
    private fun updateButtonState(binding: FragmentSecondBinding) {
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleTimerFinish(navController: NavController) {
        playComplianceBellSound()

        if (currentRound >= roundNum) {
            saveTraining()
        } else {
            navController.navigate(R.id.action_second_to_rest)
        }
    }
    
    private fun playComplianceBellSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(requireContext(), notification)
            ringtone?.play()
        } catch (e: Exception) {
            // Fallback - no sound if system notification fails
        }
    }

    private fun updateTimeText(binding: FragmentSecondBinding) {
        val minutes = timeRemainingInMillis / 60000
        val seconds = (timeRemainingInMillis % 60000) / 1000
        val formattedTime = String.format("%02d:%02d", minutes, seconds)

        timeForSave["leftTime"] = (minutes * 60 + seconds).toInt()
        binding.timeCounter.text = formattedTime
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            Second().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getCurrentDateTime(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return LocalDateTime.now().format(formatter)
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.shutdown()
        if (::countDownTimer.isInitialized) {
            countDownTimer.cancel()
        }
    }
}
