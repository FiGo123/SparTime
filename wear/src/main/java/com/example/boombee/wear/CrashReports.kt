package com.example.boombee.wear

import android.content.Context
import com.example.boombee.data.PreferencesProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Opt-in Crashlytics on the watch (no analytics here). Off by default in the
 * manifest; asked once on first launch, changeable in Settings.
 */
object CrashReports {
    private const val KEY_CONSENT = "crash_reports_consent"

    /** null = not asked yet. */
    fun consent(context: Context): Boolean? = PreferencesProvider(context).getBoolean(KEY_CONSENT)

    fun setConsent(context: Context, granted: Boolean) {
        PreferencesProvider(context).putBoolean(KEY_CONSENT, granted)
        apply(context)
    }

    fun apply(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = consent(context) == true
    }
}
