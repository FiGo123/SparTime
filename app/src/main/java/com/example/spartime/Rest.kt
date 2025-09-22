package com.example.spartime

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.example.spartime.databinding.FragmentRestBinding
import com.example.spartime.viewmodel.MainViewModel

class Rest : Fragment() {

    private lateinit var timeTextView: TextView
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var binding: FragmentRestBinding
    private var timeRemainingInMillis = 0L
    private var initialTimeInMinutes = 0
    private val mainViewModel: MainViewModel by activityViewModels()

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestBinding.inflate(inflater, container, false)
        timeTextView = binding.restTimeCounter
        binding.btnRestStop.setOnClickListener {
            if (::countDownTimer.isInitialized) {
                countDownTimer.cancel()
            }
            it.findNavController().navigate(R.id.action_rest_to_first)
        }
        mainViewModel.pauseLengthInMin.observe(viewLifecycleOwner
        ) {
                pauseLengthInObserver -> initialTimeInMinutes = pauseLengthInObserver
                timeRemainingInMillis = (initialTimeInMinutes * 60 * 1000).toLong()
                startTimer(binding, findNavController())
        }

        val valueFromPreviousRound = mainViewModel.currentRound.value
        val currentRoundValue = valueFromPreviousRound?.plus(1)
        if (currentRoundValue != null) {
            mainViewModel.setCurrentRound(currentRoundValue)
        }
        return binding.root
    }




    private fun startTimer(binding: FragmentRestBinding, findNavController: NavController) {
        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                updateTimeText(binding, findNavController)
            }

            override fun onFinish() {
                findNavController.navigate(R.id.action_rest_to_second)

                // Handle timer completion (e.g., dismiss dialog, play sound) countDownTimer.cancel()
            }
        }.start()
    }

    private fun updateTimeText(binding: FragmentRestBinding, findNavController: NavController) {
        val minutes = timeRemainingInMillis / 60000
        val seconds = (timeRemainingInMillis % 60000) / 1000

        val formattedTime = String.format("%02d:%02d", minutes, seconds)
        binding.restTimeCounter.text = formattedTime


    }

    // ... other methods for handling dialog dismissal or actions
}



