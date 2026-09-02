package com.example.boombee.wear

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import com.example.boombee.data.Dao
import com.example.boombee.data.PreferencesProvider
import java.util.Locale

/**
 * Watch-native feedback for round/rest boundaries: haptic buzz (primary,
 * since a silent wrist vibration is the expected Wear OS cue), plus the same
 * TTS announcement and notification-ringtone bell the phone app uses,
 * gated by the shared sound_status preference.
 */
class RoundAnnouncer(context: Context) {

    private val appContext = context.applicationContext
    private val dao = Dao(PreferencesProvider(appContext))
    private val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.language = Locale.getDefault()
        }
    }

    private fun soundEnabled() = dao.getSoundStatus()

    fun announce(text: String) {
        if (!soundEnabled() || !ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun playBell() {
        if (!soundEnabled()) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(appContext, uri)?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun vibrate() {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(400)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
