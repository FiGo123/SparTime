package com.example.boombee.coach

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log

/** A call inside a [Take], [atMs] into the recording. */
data class TakeCue(val atMs: Long, val parts: List<CommandPart>)

/** One natural recording of a whole attack sequence, played as a round's finisher. */
data class Take(val id: String, val clipKey: String, val durationMs: Long, val cues: List<TakeCue>)

/**
 * Loads `coach_takes_*.txt` (see that file's header for the format) and
 * measures each take's real length from its audio file, which is what the
 * finisher timing is planned around. A take whose audio is missing is
 * skipped, so adding a line before the WAV exists is harmless.
 */
class TakeLibrary(context: Context, schools: List<String> = listOf("general")) {

    val takes: List<Take> = schools
        .mapNotNull { school -> runCatching { context.assets.open("coach_takes_$school.txt") }.getOrNull() }
        .flatMap { stream -> stream.bufferedReader().readLines() }
        .mapNotNull { line -> parseLine(line)?.let { (id, clip, cues) -> measure(context, clip)?.let { Take(id, clip, it, cues) } } }

    private fun measure(context: Context, clipKey: String): Long? {
        val file = runCatching { context.assets.list(AUDIO_DIR)?.firstOrNull { it.startsWith("$clipKey.") } }.getOrNull() ?: return null
        return runCatching {
            context.assets.openFd("$AUDIO_DIR/$file").use { fd ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                } finally {
                    retriever.release()
                }
            }
        }.onFailure { Log.d("boombeelogs", "TakeLibrary: can't measure $clipKey: ${it.message}") }.getOrNull()
    }

    companion object {
        private const val AUDIO_DIR = "audio"

        /** Parses `id | clip | 0.00=1 ; 1.30=1-1 ; …` into (id, clip, cues); null for comments, blanks or bad lines. */
        fun parseLine(rawLine: String): Triple<String, String, List<TakeCue>>? {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return null
            val fields = line.split("|").map { it.trim() }
            if (fields.size < 3 || fields[0].isEmpty() || fields[1].isEmpty()) return null
            val cues = fields[2].split(";").map { it.trim() }.filter { it.isNotEmpty() }.map { cue ->
                val (time, call) = cue.split("=").map { it.trim() }.takeIf { it.size == 2 } ?: return null
                val seconds = time.toDoubleOrNull() ?: return null
                TakeCue((seconds * 1000).toLong(), parseCall(call.lowercase()) ?: return null)
            }
            return if (cues.isEmpty()) null else Triple(fields[0], fields[1], cues.sortedBy { it.atMs })
        }

        private fun parseCall(call: String): List<CommandPart>? {
            PHRASES[call]?.let { return listOf(CommandPart.Phrase(it)) }
            val parts = call.split("-").map { token ->
                when (token) {
                    "slip" -> CommandPart.Phrase(Phrases.SLIP)
                    "duck" -> CommandPart.Phrase(Phrases.DUCK_UNDER)
                    else -> token.toIntOrNull()?.takeIf { it in 1..6 }?.let { CommandPart.Punch(it) } ?: return null
                }
            }
            return parts.ifEmpty { null }
        }

        private val PHRASES = mapOf(
            "make_distance" to Phrases.MAKE_DISTANCE,
            "close_distance" to Phrases.CLOSE_DISTANCE,
            "move_more" to Phrases.MOVE_MORE,
            "hands_up" to Phrases.HANDS_UP,
            "breathe" to Phrases.BREATHE,
            "move_out" to Phrases.MOVE_OUT,
        )
    }
}

/** When a round's finisher take starts: as soon as the round's remaining time reaches [startWhenRemainingMs]. */
data class FinisherPlan(val take: Take, val startWhenRemainingMs: Long)

/**
 * The finisher rules, kept free of Android so they're unit-tested: not at
 * Beginner, about half the rounds, at least a minute of normal coaching
 * first, ending with the round or right where freestyle begins, and not the
 * same take twice in a row.
 */
object FinisherPlanner {
    const val CHANCE = 0.5
    const val MIN_BUILDUP_MS = 60_000L
    /** Breathing room between the take's last word and the bell / freestyle call. */
    const val END_MARGIN_MS = 1_500L

    fun plan(
        takes: List<Take>,
        difficulty: Difficulty,
        roundDurationMs: Long,
        freestyleMs: Long,
        lastTakeId: String?,
        roll: Double,
        pick: (List<Take>) -> Take,
    ): FinisherPlan? {
        if (difficulty == Difficulty.BEGINNER || takes.isEmpty() || roll >= CHANCE) return null
        val endOffsetMs = freestyleMs + END_MARGIN_MS
        val fitting = takes.filter { roundDurationMs - endOffsetMs - it.durationMs >= MIN_BUILDUP_MS }
        val choices = fitting.filter { it.id != lastTakeId }.ifEmpty { fitting }
        if (choices.isEmpty()) return null
        val take = pick(choices)
        return FinisherPlan(take, startWhenRemainingMs = endOffsetMs + take.durationMs)
    }
}
