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
    // TODO: Rename and change types of parameters
    private lateinit var countDownTimer: CountDownTimer
    private var timeRemainingInMillis = 0L
    private var initialTimeInMinutes = 0
    val timeForSave: MutableMap<String, Int> = mutableMapOf()
    private lateinit var timeTextView: TextView


    private var param1: String? = null
    private var param2: String? = null
    private var currentRound = 0
    private var roundNum = 0
    private var roundLength = 0
    private var pauseLength = 0

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

    }


    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentSecondBinding.inflate(inflater, container, false)

        val navController = findNavController()
        binding.roundFragmentBtn.setOnClickListener {
            countDownTimer.cancel()
            it.findNavController().navigate(R.id.action_second_to_dialog)
            timeForSave["round"] = currentRound
            timeForSave["leftTime"]?.let { it1 -> mainViewModel.setLeftTime(it1) }
            println("Time If Interupt $timeForSave $currentRound")
            println(timeForSave)

        }

        if (mainViewModel.currentRound.value!! > mainViewModel.numOfRounds.value!! && mainViewModel.currentRound.value!! > 0){
            var db = DBHandler(requireContext())
            val time = getCurrentDateTime()
            if(mainViewModel.trainingType.value == "BOXING"){
                val training = Training("Boxing Training", time, currentRound, roundLength,3, "Odradjen boks trening")
                db.insertData(training)

            }else if (mainViewModel.trainingType.value == "MMA"){
                val training = Training("MMA Training", time, currentRound, roundLength,3, "Odradjen mma trening")
                db.insertData(training)
            }else{
                val training = Training("Custom Test", time, currentRound, roundLength,3, "Odradjen trening")
                db.insertData(training)
            }

            navController.navigate(R.id.action_second_to_first)
        }

        val roundTime = binding.timeCounter
        mainViewModel.currentRound.observe(viewLifecycleOwner
        ) {
            currentRoundValue -> currentRound = currentRoundValue
            binding.roundNum.text = "Round $currentRound"
        }
        mainViewModel.numOfRounds.observe(viewLifecycleOwner
        ) {
                numOfRoundsValue -> roundNum = numOfRoundsValue
        }
        mainViewModel.roundLengthInMin.observe(viewLifecycleOwner
        ) {
                length -> roundTime.text = length.toString()
            roundLength = length
            initialTimeInMinutes = roundLength // Example value
            timeRemainingInMillis = (initialTimeInMinutes * 60 * 1000).toLong()
            val currRound = mainViewModel.currentRound.value!!
            val numOfRounds = mainViewModel.numOfRounds.value!!
            if (currRound > numOfRounds && currRound > 0){
                println()
            }else{
                startTimer(binding,findNavController(), currRound, numOfRounds)
            }

            when (currentRound) {
                1 -> playSound(requireContext(),"sound_round_one")
                2 -> playSound(requireContext(),"sound_round_two")
                3 -> playSound(requireContext(),"sound_round_three")
                4 -> playSound(requireContext(),"sound_round_four")
                5 -> playSound(requireContext(),"sound_round_five")
                6 -> playSound(requireContext(),"sound_round_six")
                7 -> playSound(requireContext(),"sound_round_seven")
                8 -> playSound(requireContext(),"sound_round_eight")
                9 -> playSound(requireContext(),"sound_round_nine")
                10 -> playSound(requireContext(),"sound_round_ten")
                11 -> playSound(requireContext(),"sound_round_eleven")
                12 -> playSound(requireContext(),"sound_round_twelve")
                else -> ""
            }


        }


        // Inflate the layout for this fragment
        return binding.root
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment Second.
         */
        // TODO: Rename and change types and number of parameters
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
    fun getCurrentDateTime(): String {
        val currentDateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return currentDateTime.format(formatter)
    }

    fun playSound(context: Context, resourceName: String) {
        val resId = context.resources.getIdentifier(resourceName, "raw", context.packageName)

        if (resId != 0) {
            val mediaPlayer = MediaPlayer.create(context, resId)
            mediaPlayer.start()
        } else {
            println("Resource not found for $resourceName")
        }
    }

    private fun startTimer(roundTime: FragmentSecondBinding, findNavController: NavController, currRound: Int, numOfRounds: Int) {
        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                updateTimeText(roundTime, findNavController)
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onFinish() {
                val mediaPlayer = MediaPlayer.create(context, R.raw.boxingbell)
                mediaPlayer.start()
                var db = DBHandler(requireContext())

                if (currRound == numOfRounds){
                    if(mainViewModel.trainingType.value == "BOXING"){
                        val training = Training("Boxing Training", getCurrentDateTime(), 12, 3,3, "Odradjen boks trening")
                        db.insertData(training)

                    }else if (mainViewModel.trainingType.value == "MMA"){
                        val training = Training("MMA Training", getCurrentDateTime(), 5, 5,3, "Odradjen mma trening")
                        db.insertData(training)
                    }else{
                        val training = Training("Custom Test", getCurrentDateTime(), 5, 5,3, "Odradjen mma trening")
                        db.insertData(training)
                    }
                    findNavController.navigate(R.id.action_second_to_first)

                }else{
                    findNavController.navigate(R.id.action_second_to_rest)
                }


            }
        }.start()
    }

    private fun updateTimeText(binding: FragmentSecondBinding, findNavController: NavController) {
        val minutes = timeRemainingInMillis / 60000
        val seconds = (timeRemainingInMillis % 60000) / 1000
        val formattedTime = String.format("%02d:%02d", minutes, seconds)
        val numTime = minutes * 60 + seconds
        timeForSave["leftTime"] = numTime.toInt()

        binding.timeCounter.text = formattedTime

    }

}