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
import androidx.navigation.findNavController
import com.example.spartime.databinding.FragmentRestBinding
import com.example.spartime.viewmodel.MainViewModel

class Rest : Fragment() {

    private lateinit var timeTextView: TextView
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var binding: FragmentRestBinding
    private var timeRemainingInMillis = 0L
    private var initialTimeInMinutes = 0
    private var pauseLength = 0
    private val mainViewModel: MainViewModel by activityViewModels()

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestBinding.inflate(inflater, container, false)
        timeTextView = binding.restTimeCounter
        binding.btnRestStop.setOnClickListener {
            it.findNavController().navigate(R.id.action_rest_to_second)
        }
        mainViewModel.pauseLengthInMin.observe(viewLifecycleOwner
        ) {
                pauseLengthInObserver -> initialTimeInMinutes = pauseLengthInObserver
                println("Rest duration")
                timeRemainingInMillis = (initialTimeInMinutes * 60 * 1000).toLong()
                startTimer(binding)
        }



        return binding.root
    }




    private fun startTimer(binding: FragmentRestBinding) {
        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                updateTimeText(binding)
            }

            override fun onFinish() {
                // Handle timer completion (e.g., dismiss dialog, play sound)
                binding.root.findNavController().navigate(R.id.action_rest_to_second)
            }
        }.start()
    }

    private fun updateTimeText(binding: FragmentRestBinding) {
        val minutes = timeRemainingInMillis / 60000
        val seconds = (timeRemainingInMillis % 60000) / 1000

        val formattedTime = String.format("%02d:%02d", minutes, seconds)
        binding.restTimeCounter.text = formattedTime

    }

    // ... other methods for handling dialog dismissal or actions
}



