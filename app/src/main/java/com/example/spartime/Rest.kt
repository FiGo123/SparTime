package com.example.spartime

import android.annotation.SuppressLint
import android.media.RingtoneManager
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
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
import java.util.Locale

class Rest : Fragment() {

    private lateinit var timeTextView: TextView
    private lateinit var countDownTimer: CountDownTimer
    private lateinit var binding: FragmentRestBinding
    private var timeRemainingInMillis = 0L
    private var initialTimeInMinutes = 0
    private var textToSpeech: TextToSpeech? = null
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

        mainViewModel.pauseLengthInMin.observe(viewLifecycleOwner) { pauseLengthInObserver ->
            initialTimeInMinutes = pauseLengthInObserver
            timeRemainingInMillis = (initialTimeInMinutes * 60 * 1000).toLong()
            announceRest()
            startTimer(binding, findNavController())
        }

        val valueFromPreviousRound = mainViewModel.currentRound.value
        val currentRoundValue = valueFromPreviousRound?.plus(1)
        if (currentRoundValue != null) {
            mainViewModel.setCurrentRound(currentRoundValue)
        }
        return binding.root
    }

    private fun announceRest() {
        if (!mainViewModel.getSoundStatus()) return
        if (textToSpeech != null) {
            speakRest()
        } else {
            textToSpeech = TextToSpeech(requireContext()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale.getDefault())
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        speakRest()
                    }
                }
            }
        }
    }

    private fun speakRest() {
        textToSpeech?.speak("Rest", TextToSpeech.QUEUE_FLUSH, null, "rest_announcement")
    }

    private fun playBellSound() {
        if (!mainViewModel.getSoundStatus()) return
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(requireContext(), notification)
            ringtone?.play()
        } catch (e: Exception) {
            // Fallback - no sound if system notification fails
        }
    }

    private fun startTimer(binding: FragmentRestBinding, findNavController: NavController) {
        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                updateTimeText(binding)
            }

            override fun onFinish() {
                playBellSound()
                findNavController.navigate(R.id.action_rest_to_second)
            }
        }.start()
    }

    private fun updateTimeText(binding: FragmentRestBinding) {
        val minutes = timeRemainingInMillis / 60000
        val seconds = (timeRemainingInMillis % 60000) / 1000
        binding.restTimeCounter.text = String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.shutdown()
        if (::countDownTimer.isInitialized) {
            countDownTimer.cancel()
        }
    }
}
