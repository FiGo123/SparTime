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

/** Whether the tutorial has been finished or skipped once; it opens automatically on the first Start until then. */
object TutorialPrefs {
    private const val KEY_SEEN = "tutorial_seen"

    fun isSeen(context: Context): Boolean = PreferencesProvider(context).getBoolean(KEY_SEEN) == true

    fun markSeen(context: Context) = PreferencesProvider(context).putBoolean(KEY_SEEN, true)
}
