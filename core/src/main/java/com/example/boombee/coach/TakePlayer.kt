package com.example.boombee.coach

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Plays one recorded [Take] and fires each [TakeCue] as the audio reaches
 * it, polling the real playback position so pausing (or a slow start)
 * never puts the on-screen calls out of step with the voice.
 */
class TakePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var take: Take? = null
    private var nextCue = 0
    private var onCue: (TakeCue) -> Unit = {}
    private var onDone: () -> Unit = {}

    /** True from [start] until the take finishes or is stopped (also while paused). */
    val isActive: Boolean get() = player != null

    private val poll = object : Runnable {
        override fun run() {
            val p = player ?: return
            fireCuesUpTo(runCatching { p.currentPosition.toLong() }.getOrDefault(0L))
            handler.postDelayed(this, POLL_MS)
        }
    }

    fun start(take: Take, onCue: (TakeCue) -> Unit, onDone: () -> Unit) {
        stop()
        this.take = take
        this.onCue = onCue
        this.onDone = onDone
        nextCue = 0
        try {
            val file = appContext.assets.list(AUDIO_DIR)?.firstOrNull { it.startsWith("${take.clipKey}.") }
                ?: return finish()
            val mp = MediaPlayer()
            appContext.assets.openFd("$AUDIO_DIR/$file").use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setOnCompletionListener { finish() }
            mp.prepare()
            player = mp
            mp.start()
            handler.post(poll)
        } catch (e: Exception) {
            Log.d("boombeelogs", "TakePlayer: failed to play ${take.id}: ${e.message}")
            finish()
        }
    }

    fun pause() {
        handler.removeCallbacks(poll)
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    fun resume() {
        val p = player ?: return
        runCatching { p.start() }
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    /** Stops without calling onDone (round ended, session stopped). */
    fun stop() {
        handler.removeCallbacks(poll)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        take = null
    }

    private fun fireCuesUpTo(positionMs: Long) {
        val cues = take?.cues ?: return
        while (nextCue < cues.size && cues[nextCue].atMs <= positionMs) {
            onCue(cues[nextCue])
            nextCue++
        }
    }

    private fun finish() {
        fireCuesUpTo(Long.MAX_VALUE)
        val done = onDone
        stop()
        done()
    }

    private companion object {
        const val AUDIO_DIR = "audio"
        const val POLL_MS = 100L
    }
}
