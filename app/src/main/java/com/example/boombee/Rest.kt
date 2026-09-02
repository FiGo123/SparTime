package com.example.boombee

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.example.boombee.databinding.FragmentRestBinding
import com.example.boombee.viewmodel.MainViewModel
import java.util.Locale

class Rest : Fragment() {

    private lateinit var binding: FragmentRestBinding
    private lateinit var countDownTimer: CountDownTimer
    private var timeRemainingInMillis = 0L
    private var textToSpeech: TextToSpeech? = null
    private var toneGenerator: ToneGenerator? = null
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRestBinding.inflate(inflater, container, false)

        binding.btnRestStop.setOnClickListener {
            if (::countDownTimer.isInitialized) countDownTimer.cancel()
            it.findNavController().navigate(R.id.action_rest_to_first)
        }

        // Round was already incremented in Second.kt before navigating here.
        // Observer guarded: cancel any running timer before starting a new one
        // so rotation (onCreateView re-called) doesn't create duplicate timers.
        mainViewModel.pauseLengthInMin.observe(viewLifecycleOwner) { minutes ->
            if (::countDownTimer.isInitialized) countDownTimer.cancel()
            timeRemainingInMillis = (minutes * 60 * 1000).toLong()
            announceRest()
            startTimer(findNavController())
        }

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

    private fun startTimer(navController: NavController) {
        binding.restTimeCounter.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_orange))

        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                updateTimeDisplay()

                if (millisUntilFinished <= 10_000) {
                    binding.restTimeCounter.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.timer_danger)
                    )
                    playWarningBeep()
                }
            }

            override fun onFinish() {
                binding.restTimeCounter.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.accent_orange)
                )
                playBellSound()
                navController.navigate(R.id.action_rest_to_second)
            }
        }.start()
    }

    private fun updateTimeDisplay() {
        val minutes = timeRemainingInMillis / 60000
        val seconds = (timeRemainingInMillis % 60000) / 1000
        binding.restTimeCounter.text = String.format("%02d:%02d", minutes, seconds)
    }

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

    private fun announceRest() {
        if (!mainViewModel.getSoundStatus()) return
        initTtsIfNeeded { tts ->
            tts.speak("Rest time. Recover!", TextToSpeech.QUEUE_FLUSH, null, "rest_announcement")
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

    override fun onDestroy() {
        super.onDestroy()
        if (::countDownTimer.isInitialized) countDownTimer.cancel()
        textToSpeech?.shutdown()
        toneGenerator?.release()
    }
}
