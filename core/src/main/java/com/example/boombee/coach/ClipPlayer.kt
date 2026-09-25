package com.example.boombee.coach

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.util.Log

private const val TAG = "boombeelogs"

/**
 * Plays a sequence of pre-recorded voice clips from `assets/audio/` back
 * to back, with a short gap (or, for combos, a small *overlap* — see
 * [playCommand]) between them — the pre-recorded-voice alternative to
 * [CoachCommand.spokenText] + TextToSpeech, keyed by [CoachAudio]. Shared
 * between phone and watch (both already depend on `:core`) rather than
 * duplicated per platform, the same split as [CoachSession]/
 * `RoundAnnouncer` (core logic shared, platform code owns only what's
 * actually platform-specific).
 *
 * Callers are responsible for checking [hasAllClips] first and falling
 * back to TTS themselves if it's false — this class has no TTS engine
 * handle of its own, it only knows how to play clips that exist.
 */
class ClipPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var pendingNextRunnable: Runnable? = null

    // Listed once — assets don't change during a running session, and
    // checking assets.list() on every single command would be wasteful.
    private val availableAssets: Set<String> by lazy {
        runCatching { appContext.assets.list(AUDIO_DIR)?.toSet() }.getOrNull() ?: emptySet()
    }

    /** True only if every key in [keys] has a matching file under `assets/audio/` — check this before [play]/[playCommand]. */
    fun hasAllClips(keys: List<String>): Boolean =
        keys.isNotEmpty() && keys.all { key -> availableAssets.any { it.startsWith("$key.") } }

    /**
     * Convenience for playing a whole command's clips: a real combo
     * ([isCombo] — 2+ parts) plays a little faster and starts each next
     * clip [COMBO_GAP_MS] *before* the previous one actually finishes
     * (a negative gap — see [play]) so it reads as one continuous combo
     * the way a fluid spoken phrase would, rather than separately-recorded
     * words stitched together at a leisurely pace. A solo call ([isCombo]
     * false) is unaffected — there's nothing to tighten with only one clip.
     */
    fun playCommand(keys: List<String>, isCombo: Boolean, onDone: () -> Unit = {}) {
        val speed = if (isCombo) COMBO_SPEED else 1.0f
        val gapMs = if (isCombo) COMBO_GAP_MS else DEFAULT_GAP_MS
        play(keys, speed, gapMs, onDone)
    }

    /**
     * Plays [keys] in sequence at [speed] (1.0 = normal, pitch-corrected so
     * it doesn't sound chipmunked). [gapMs] is the pause between clips —
     * **negative** means the next clip starts that many ms *before* the
     * current one's audio actually finishes (a brief overlap, tightening
     * the perceived timing beyond what raw [speed] alone can without
     * further speed-up artifacts), not a delay after it. Calls [onDone]
     * once every clip has been *started* (not necessarily finished
     * playing, when overlapping).
     */
    fun play(keys: List<String>, speed: Float = 1.0f, gapMs: Long = DEFAULT_GAP_MS, onDone: () -> Unit = {}) {
        stop()
        playNext(keys, 0, speed, gapMs, onDone)
    }

    private fun playNext(keys: List<String>, index: Int, speed: Float, gapMs: Long, onDone: () -> Unit) {
        if (index >= keys.size) {
            onDone()
            return
        }
        val fileName = availableAssets.firstOrNull { it.startsWith("${keys[index]}.") }
        if (fileName == null) {
            Log.d(TAG, "ClipPlayer: missing clip for ${keys[index]}, skipping")
            playNext(keys, index + 1, speed, gapMs, onDone)
            return
        }
        try {
            val afd = appContext.assets.openFd("$AUDIO_DIR/$fileName")
            val player = MediaPlayer()
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            player.setOnCompletionListener { it.release() }
            player.prepare()
            if (speed != 1.0f) {
                // Speed up without shifting pitch (setPitch(1.0f)) — a raw
                // playback-rate change would otherwise sound chipmunked.
                // Some older/OEM decoders don't support this; failing
                // silently just means that one clip plays at normal speed
                // (and this clip's contribution to the schedule below falls
                // back to its un-sped-up duration too).
                runCatching { player.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1.0f) }
            }
            mediaPlayer = player
            player.start()

            // getDuration() is the clip's length at normal speed; actual
            // wall-clock playback time shrinks by `speed`. gapMs is added on
            // top (and, negative, brings the next clip's start earlier —
            // still clamped so it can never start *before* this one, or in
            // the middle of a still-loading player).
            val playbackMs = (player.duration / speed).toLong()
            val nextDelayMs = (playbackMs + gapMs).coerceAtLeast(0)
            val runnable = Runnable { playNext(keys, index + 1, speed, gapMs, onDone) }
            pendingNextRunnable = runnable
            handler.postDelayed(runnable, nextDelayMs)
        } catch (e: Exception) {
            Log.d(TAG, "ClipPlayer: failed to play ${keys[index]}: ${e.message}")
            playNext(keys, index + 1, speed, gapMs, onDone)
        }
    }

    /** Stops whatever's currently playing (and cancels the next scheduled clip, if any) — call before anything that would otherwise overlap it (a bell/warning tone, the next command, etc.). */
    fun stop() {
        pendingNextRunnable?.let { handler.removeCallbacks(it) }
        pendingNextRunnable = null
        mediaPlayer?.let {
            runCatching { it.stop() }
            it.release()
        }
        mediaPlayer = null
    }

    fun shutdown() {
        stop()
    }

    companion object {
        private const val AUDIO_DIR = "audio"
        private const val DEFAULT_GAP_MS = 100L

        // A little faster than a solo call, and (via the negative gap
        // below) each next clip starts slightly before the last one
        // finishes — see playCommand(). Speed history: 1.15x -> 1.25x ->
        // 1.5x -> 1.3x (real-device testing found 1.5x fast enough that the
        // -10ms overlap below was asked for as an alternative way to
        // tighten timing instead of pushing speed higher and risking
        // MediaPlayer time-stretch artifacts). Gap history: 100ms (default,
        // untouched) -> 50ms -> 20ms -> 5ms -> 0ms -> -10ms (overlap).
        private const val COMBO_SPEED = 1.3f
        private const val COMBO_GAP_MS = -10L
    }
}
