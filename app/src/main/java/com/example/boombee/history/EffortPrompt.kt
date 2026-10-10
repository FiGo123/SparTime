package com.example.boombee.history

import android.content.Context
import android.graphics.Color
import com.example.boombee.data.PreferencesProvider

/** The session waiting for its post-workout effort rating, shown on the home screen. */
object EffortPrompt {
    private const val KEY_PENDING = "effort_pending_id"

    fun markPending(context: Context, trainingId: Long) {
        if (trainingId > 0) PreferencesProvider(context).putString(KEY_PENDING, trainingId.toString())
    }

    /** Returns the waiting session id once, then clears it. */
    fun consumePending(context: Context): Long? {
        val prefs = PreferencesProvider(context)
        val id = prefs.getString(KEY_PENDING)?.toLongOrNull() ?: return null
        prefs.remove(KEY_PENDING)
        return id
    }

    /** Green (easy) → yellow → orange → red (max), for chips and badges. */
    fun color(effort: Int): Int = when (effort) {
        in 1..4 -> Color.parseColor("#4CAF50")
        in 5..6 -> Color.parseColor("#FFC107")
        in 7..8 -> Color.parseColor("#FF6F00")
        else -> Color.parseColor("#F44336")
    }
}
