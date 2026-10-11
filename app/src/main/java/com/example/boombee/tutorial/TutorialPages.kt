package com.example.boombee.tutorial

import android.content.Context
import androidx.annotation.StringRes
import com.example.boombee.R
import com.example.boombee.coach.CoachAudio
import com.example.boombee.coach.CommandPart
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.PreferencesProvider

data class TutorialPage(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val type: Type = Type.INFO,
    val pose: FighterPose? = null,
    val emoji: String? = null,
    /** Clip keys for the 🔊 button, in the user's voice style; null hides the button. */
    val clips: ((VoiceStyle) -> List<String>)? = null,
    val clipMode: ClipMode = ClipMode.SEQUENCE,
) {
    enum class Type { INFO, GAME }

    /** SEQUENCE: separate calls with a pause between. COMBO: one combo, timed like the real coach. */
    enum class ClipMode { SEQUENCE, COMBO }
}

object TutorialPages {
    private fun punch(n: Int, @StringRes title: Int, @StringRes body: Int, pose: FighterPose) =
        TutorialPage(title, body, pose = pose, clips = { listOf("number_$n", "word_$n") })

    val ALL: List<TutorialPage> = listOf(
        TutorialPage(R.string.tutorial_welcome_title, R.string.tutorial_welcome_body, emoji = "🥊"),
        TutorialPage(
            R.string.tutorial_stance_title, R.string.tutorial_stance_body,
            pose = FighterPose.STANCE, clips = { listOf("phrase_hands_up") },
        ),
        punch(1, R.string.tutorial_jab_title, R.string.tutorial_jab_body, FighterPose.JAB),
        punch(2, R.string.tutorial_cross_title, R.string.tutorial_cross_body, FighterPose.CROSS),
        punch(3, R.string.tutorial_lead_hook_title, R.string.tutorial_lead_hook_body, FighterPose.LEAD_HOOK),
        punch(4, R.string.tutorial_rear_hook_title, R.string.tutorial_rear_hook_body, FighterPose.REAR_HOOK),
        punch(5, R.string.tutorial_lead_uppercut_title, R.string.tutorial_lead_uppercut_body, FighterPose.LEAD_UPPERCUT),
        punch(6, R.string.tutorial_rear_uppercut_title, R.string.tutorial_rear_uppercut_body, FighterPose.REAR_UPPERCUT),
        TutorialPage(R.string.tutorial_summary_title, R.string.tutorial_summary_body, pose = FighterPose.SUMMARY),
        TutorialPage(
            R.string.tutorial_defense_title, R.string.tutorial_defense_body,
            pose = FighterPose.DEFENSE, clips = { listOf("phrase_slip", "phrase_duck_under", "phrase_hands_up") },
        ),
        TutorialPage(
            R.string.tutorial_distance_title, R.string.tutorial_distance_body,
            pose = FighterPose.DISTANCE,
            clips = { listOf("phrase_close_distance", "phrase_make_distance", "phrase_move_more") },
        ),
        TutorialPage(
            R.string.tutorial_combos_title, R.string.tutorial_combos_body, emoji = "🗣️",
            clips = { style -> CoachAudio.keysFor(listOf(CommandPart.Punch(1), CommandPart.Punch(2)), style).orEmpty() },
            clipMode = TutorialPage.ClipMode.COMBO,
        ),
        TutorialPage(R.string.tutorial_game_title, R.string.tutorial_game_body, type = TutorialPage.Type.GAME),
        TutorialPage(R.string.tutorial_done_title, R.string.tutorial_done_body, emoji = "🐝"),
    )
}

/** A chapter of the Learn tab's "book": a slice of [TutorialPages.ALL] opened on its own. */
data class TutorialChapter(val id: String, val title: String, val subtitle: String, val pages: IntRange, val isGame: Boolean = false)

object TutorialChapters {
    // Indexes into TutorialPages.ALL (0 welcome · 1 stance · 2–7 punches ·
    // 8 summary · 9 defense · 10 distance · 11 combos · 12 game · 13 done).
    val ALL = listOf(
        TutorialChapter("stance", "Stance & guard", "Feet, hands, southpaw", 1..1),
        TutorialChapter("punches", "Punches 1–6", "Jab to rear uppercut, odd = left", 2..8),
        TutorialChapter("defense", "Defense", "Slip, duck under, hands up", 9..9),
        TutorialChapter("distance", "Distance", "Close, make, move", 10..10),
        TutorialChapter("combos", "How combos sound", "Numbers vs words", 11..11),
    )
    val GAME = TutorialChapter("combo_game", "Tap the combo", "Hear it, tap it — 8 combos", 12..12, isGame = true)

    fun byId(id: String?): TutorialChapter? = (ALL + GAME).firstOrNull { it.id == id }
}

/** Learn-tab progress: chapters read and the combo game's best score. */
object LearnProgress {
    private fun key(id: String) = "learn_done_$id"
    private const val KEY_GAME_BEST = "learn_game_best"

    fun isDone(context: Context, chapterId: String) = PreferencesProvider(context).getBoolean(key(chapterId)) == true

    fun markDone(context: Context, chapterId: String) = PreferencesProvider(context).putBoolean(key(chapterId), true)

    fun markAllDone(context: Context) = TutorialChapters.ALL.forEach { markDone(context, it.id) }

    fun gameBest(context: Context): Int = PreferencesProvider(context).getInt(KEY_GAME_BEST)

    fun recordGame(context: Context, score: Int) {
        if (score > gameBest(context)) PreferencesProvider(context).putInt(KEY_GAME_BEST, score)
        markDone(context, TutorialChapters.GAME.id)
    }
}

/** Whether the tutorial has been finished or skipped once; it opens automatically on the first Start until then. */
object TutorialPrefs {
    private const val KEY_SEEN = "tutorial_seen"

    fun isSeen(context: Context): Boolean = PreferencesProvider(context).getBoolean(KEY_SEEN) == true

    fun markSeen(context: Context) = PreferencesProvider(context).putBoolean(KEY_SEEN, true)
}
