package com.example.spartime

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
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
            countDownTimer.cancel()
            it.findNavController().navigate(R.id.action_second_to_dialog)
            saveCurrentState()
        }
    }

    private fun saveCurrentState() {
        timeForSave["round"] = currentRound
        timeForSave["leftTime"]?.let { mainViewModel.setLeftTime(it) }
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
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupRoundTimer(binding: FragmentSecondBinding, length: Int) {
        roundLength = length
        initialTimeInMinutes = length
        timeRemainingInMillis = (initialTimeInMinutes * 60 * 1000).toLong()

        if (currentRound > roundNum && currentRound > 0) {
            saveTraining()
        } else {
            startTimer(binding, findNavController())
        }
        playRoundSound(currentRound)
    }

    private fun playRoundSound(round: Int) {
        val soundResource = "sound_round_$round"
        playSound(requireContext(), soundResource)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveTraining() {
        val db = DBHandler(requireContext())
        val time = getCurrentDateTime()
        val trainingType = mainViewModel.trainingType.value ?: "Custom"
        val training = when (trainingType) {
            "BOXING" -> Training("Boxing Training", time, currentRound, roundLength, 3, "Completed boxing training")
            "MMA" -> Training("MMA Training", time, currentRound, roundLength, 3, "Completed MMA training")
            else -> Training("Custom Training", time, currentRound, roundLength, 3, "Completed custom training")
        }
        db.insertData(training)
        findNavController().navigate(R.id.action_second_to_first)
    }

    private fun startTimer(binding: FragmentSecondBinding, navController: NavController) {
        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                updateTimeText(binding)
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onFinish() {
                handleTimerFinish(navController)
            }
        }.start()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleTimerFinish(navController: NavController) {
        val mediaPlayer = MediaPlayer.create(context, R.raw.boxingbell)
        mediaPlayer.start()

        if (currentRound == roundNum) {
            saveTraining()
        } else {
            navController.navigate(R.id.action_second_to_rest)
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

    private fun playSound(context: Context, resourceName: String) {
        val resId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
        if (resId != 0) {
            MediaPlayer.create(context, resId).start()
        } else {
            println("Resource not found for $resourceName")
        }
    }
}
