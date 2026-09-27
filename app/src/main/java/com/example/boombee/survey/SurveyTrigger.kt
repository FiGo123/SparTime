package com.example.boombee.survey

import android.content.Context
import com.example.boombee.AppVersion
import com.example.boombee.data.PreferencesProvider

/**
 * Decides when the satisfaction survey pops up on its own (see
 * RELEASE_SURVEY.md). Only after a completed training, and only when:
 * - at least [MIN_COMPLETED_TRAININGS] trainings completed in total
 * - at least [MIN_DAYS_BETWEEN] days since it was last shown
 * - shown fewer than [MAX_TIMES_SHOWN] times in total
 * - not already answered for this version, and not "Don't ask again"
 *
 * Settings → "Rate & feedback" opens it directly and ignores these rules.
 */
object SurveyTrigger {

    const val MIN_COMPLETED_TRAININGS = 10
    const val MIN_DAYS_BETWEEN = 14
    const val MAX_TIMES_SHOWN = 3

    private const val KEY_COMPLETED = "survey_completed_trainings"
    private const val KEY_LAST_SHOWN_AT = "survey_last_shown_at"
    private const val KEY_TIMES_SHOWN = "survey_times_shown"
    private const val KEY_SUBMITTED_VERSION = "survey_submitted_version"
    private const val KEY_NEVER = "survey_never"
    private const val KEY_PENDING = "survey_pending"

    /** Called by the round screen when the last round of a session finishes. */
    fun onTrainingCompleted(context: Context) {
        val prefs = PreferencesProvider(context)
        prefs.putInt(KEY_COMPLETED, prefs.getInt(KEY_COMPLETED) + 1)
        if (isEligible(prefs, System.currentTimeMillis())) prefs.putBoolean(KEY_PENDING, true)
    }

    /**
     * Called by the home screen when it resumes. Returns true once per
     * eligible completed training and records the survey as shown.
     */
    fun consumePending(context: Context): Boolean {
        val prefs = PreferencesProvider(context)
        if (prefs.getBoolean(KEY_PENDING) != true) return false
        prefs.remove(KEY_PENDING)
        val now = System.currentTimeMillis()
        if (!isEligible(prefs, now)) return false
        prefs.putString(KEY_LAST_SHOWN_AT, now.toString())
        prefs.putInt(KEY_TIMES_SHOWN, prefs.getInt(KEY_TIMES_SHOWN) + 1)
        return true
    }

    fun completedTrainings(context: Context): Int = PreferencesProvider(context).getInt(KEY_COMPLETED)

    fun markSubmitted(context: Context) =
        PreferencesProvider(context).putString(KEY_SUBMITTED_VERSION, AppVersion.VERSION)

    fun markNeverAsk(context: Context) = PreferencesProvider(context).putBoolean(KEY_NEVER, true)

    private fun isEligible(prefs: PreferencesProvider, now: Long): Boolean {
        if (prefs.getBoolean(KEY_NEVER) == true) return false
        if (prefs.getString(KEY_SUBMITTED_VERSION) == AppVersion.VERSION) return false
        if (prefs.getInt(KEY_COMPLETED) < MIN_COMPLETED_TRAININGS) return false
        if (prefs.getInt(KEY_TIMES_SHOWN) >= MAX_TIMES_SHOWN) return false
        val lastShown = prefs.getString(KEY_LAST_SHOWN_AT)?.toLongOrNull() ?: return true
        return now - lastShown >= MIN_DAYS_BETWEEN * 24L * 60 * 60 * 1000
    }
}
