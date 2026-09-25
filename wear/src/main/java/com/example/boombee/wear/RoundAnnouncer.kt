package com.example.boombee.wear

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import com.example.boombee.coach.ClipPlayer
import com.example.boombee.coach.CoachAudio
import com.example.boombee.coach.CoachCommand
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.Dao
import com.example.boombee.data.PreferencesProvider
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Watch-native feedback for round/rest boundaries: haptic buzz, TTS
 * announcements, and tone-generator beeps, all gated by the shared
 * sound_status preference.
 *
 * Bells use [ToneGenerator] on [AudioManager.STREAM_ALARM] rather than
 * [android.media.RingtoneManager]'s default notification sound: on a watch
 * the notification-ringtone slot is very often set to silent/none by the
 * user, which made the round-end bell inaudible. Alarm-stream tones are
 * reliably audible regardless of that setting (and typically survive Do Not
 * Disturb, matching what a training timer needs).
 */
class RoundAnnouncer(context: Context) {

    private val appContext = context.applicationContext
    private val dao = Dao(PreferencesProvider(appContext))
    private val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
    private val clipPlayer = ClipPlayer(appContext)

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.language = Locale.getDefault()
        }
    }

    private fun soundEnabled() = dao.getSoundStatus()

    /**
     * @param rate TTS speech rate multiplier (1.0 = normal). Coach Word-mode
     * calls pass a faster rate since named punches ("Left uppercut") take
     * longer to say than a bare digit — kept explicit per-call rather than
     * a persistent engine setting so it can't leak into the next Round/Rest
     * announcement.
     */
    fun announce(text: String, rate: Float = 1.0f) {
        if (!soundEnabled() || !ttsReady) return
        tts?.setSpeechRate(rate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /**
     * Speaks [command] using your own recorded voice if every part of it
     * has a clip (see [CoachAudio]/[ClipPlayer]), falling back to
     * [announce]/TTS otherwise — a partially-recorded voice never breaks
     * playback, it just means some commands still use TTS until more
     * clips exist.
     */
    fun announceCommand(command: CoachCommand, voiceStyle: VoiceStyle) {
        if (!soundEnabled()) return
        val keys = CoachAudio.keysFor(command.parts, voiceStyle)
        if (keys != null && clipPlayer.hasAllClips(keys)) {
            clipPlayer.playCommand(keys, isCombo = command.parts.size > 1)
        } else {
            announce(command.spokenText(voiceStyle), command.speechRate)
        }
    }

    /**
     * Announces a fixed phrase that isn't a [CoachCommand] — the Round/Rest
     * phase boundary — using your recorded voice if [keys] all exist,
     * falling back to [text]/TTS otherwise. Round numbers past what's
     * recorded (only 1-6 have a number clip) fall back automatically since
     * [ClipPlayer.hasAllClips] simply won't find that key.
     */
    fun announcePhrase(text: String, keys: List<String>) {
        if (!soundEnabled()) return
        if (clipPlayer.hasAllClips(keys)) {
            clipPlayer.play(keys)
        } else {
            announce(text)
        }
    }

    /**
     * A coach TTS/clip call and a bell/warning tone run on independent
     * audio paths (TTS engine / [ClipPlayer]'s MediaPlayer vs.
     * [ToneGenerator]) with no built-in coordination — [TextToSpeech
     * .QUEUE_FLUSH] stops one TTS utterance for the next, but does nothing
     * for a tone starting mid-speech (or mid-clip), which is audible as
     * garbled overlap. Called right before every tone below.
     */
    private fun stopSpeaking() {
        tts?.stop()
        clipPlayer.stop()
    }

    /**
     * Round/rest boundary — the main "time's up" cue. A double clang so it
     * reads unmistakably as "that's it" versus the single short beep used
     * for the 10-second warning.
     */
    suspend fun playBell() {
        if (!soundEnabled()) return
        try {
            stopSpeaking()
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
            delay(400)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
            // Wait for the second clang to actually finish before returning —
            // startTone() is fire-and-forget, so without this the caller's
            // very next step (runPhase() -> announce("Round X")) started
            // speaking while the bell was still audibly ringing.
            delay(300)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Short, distinct heads-up beep for the 10-seconds-remaining warning. */
    fun playWarning() {
        if (!soundEnabled()) return
        try {
            stopSpeaking()
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Strong pulse for round/rest end. */
    fun vibrateEnd() {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(400)
        }
    }

    /** Two short taps for the 10-seconds-remaining warning. */
    fun vibrateWarning() {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 80, 80, 80), -1)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        toneGenerator.release()
        clipPlayer.shutdown()
    }
}
