package com.example.boombee.analytics

import android.content.Context
import android.os.Bundle
import com.example.boombee.coach.TrainingMode
import com.example.boombee.data.PreferencesProvider
import com.example.boombee.viewmodel.MainViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Google Analytics for Firebase + Crashlytics (RELEASE_ANALYTICS.md).
 *
 * Nothing is collected until the user says yes to the consent prompt
 * (collection is off by default in the manifest), and the Settings
 * toggle can turn it off again. No ads, no advertising ID, and never any
 * free text (e.g. the survey comment). Every call is a no-op on builds
 * without `google-services.json`.
 */
object Analytics {
    private const val KEY_CONSENT = "analytics_consent"

    /** null = not asked yet. */
    fun consent(context: Context): Boolean? = PreferencesProvider(context).getBoolean(KEY_CONSENT)

    fun setConsent(context: Context, granted: Boolean) {
        PreferencesProvider(context).putBoolean(KEY_CONSENT, granted)
        applyConsent(context)
    }

    /** Pushes the stored choice to Firebase; call once at app start. */
    fun applyConsent(context: Context) {
        if (!isConfigured(context)) return
        val granted = consent(context) == true
        val status = if (granted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        FirebaseAnalytics.getInstance(context).apply {
            setConsent(mapOf(FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to status))
            setAnalyticsCollectionEnabled(granted)
        }
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = granted
    }

    fun log(context: Context, event: String, vararg params: Pair<String, Any?>) {
        if (!isConfigured(context) || consent(context) != true) return
        val bundle = Bundle()
        for ((key, value) in params) {
            when (value) {
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Boolean -> bundle.putString(key, value.toString())
                null -> Unit
                else -> bundle.putString(key, value.toString().take(100))
            }
        }
        FirebaseAnalytics.getInstance(context).logEvent(event, bundle)
    }

    fun logScreen(context: Context, screenName: String) =
        log(context, FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalytics.Param.SCREEN_NAME to screenName)

    /** Shared parameters for training_start / training_complete. */
    fun trainingParams(viewModel: MainViewModel): Array<Pair<String, Any?>> {
        val mode = viewModel.trainingMode.value ?: TrainingMode.TIMER_ONLY
        val isCoach = mode == TrainingMode.BOXING_COACH
        return arrayOf(
            "mode" to if (isCoach) "coach" else "timer",
            "difficulty" to if (isCoach) viewModel.coachDifficulty.value?.name?.lowercase() else null,
            "voice_style" to if (isCoach) viewModel.voiceStyle.value?.name?.lowercase() else null,
            "rounds" to viewModel.numOfRounds.value,
            "round_min" to viewModel.roundLengthInMin.value,
            "warmup" to if (isCoach) viewModel.warmupSeconds.value else null,
        )
    }

    private fun isConfigured(context: Context) = FirebaseApp.getApps(context).isNotEmpty()
}
