package com.example.spartime

import android.annotation.SuppressLint
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
    private lateinit var timeTextView: TextView


    private var param1: String? = null
    private var param2: String? = null
    private var numRounds = 0
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

    // override fun onBackPressed() {
    //    println("catch on back pressed")
    //     view?.let { Navigation.findNavController(it).navigate(R.id.action_second_to_first) }
    // }
    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentSecondBinding.inflate(inflater, container, false)
        binding.roundFragmentBtn.setOnClickListener {
            countDownTimer.cancel()
            it.findNavController().navigate(R.id.action_second_to_first)
        }


        val roundTimePlaceholder = binding.roundNum
        val roundTime = binding.timeCounter
        mainViewModel.numOfRounds.observe(viewLifecycleOwner
        ) {
                num -> numRounds = num
            //roundTimePlaceholder.text = "Round $num"
        }
        mainViewModel.roundLengthInMin.observe(viewLifecycleOwner
        ) {
                length -> roundTime.text = length.toString()
            roundLength = length
            initialTimeInMinutes = roundLength // Example value
            timeRemainingInMillis = (initialTimeInMinutes * 60 * 1000).toLong()
            startTimer(binding)
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


    private fun startTimer(roundTime: FragmentSecondBinding) {
        println("startTimer")
        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                println(millisUntilFinished)
                timeRemainingInMillis = millisUntilFinished
                updateTimeText(roundTime)
            }

            override fun onFinish() {
                roundTime.root.findNavController().navigate(R.id.action_second_to_rest)
                // Handle timer completion, e.g., display a message or reset the timer
            }
        }.start()
    }

    private fun updateTimeText(binding: FragmentSecondBinding) {
        val minutes = timeRemainingInMillis / 60000
        val seconds = (timeRemainingInMillis % 60000) / 1000

        val formattedTime = String.format("%02d:%02d", minutes, seconds)
        binding.timeCounter.text = formattedTime

    }

}