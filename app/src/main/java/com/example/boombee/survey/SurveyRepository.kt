package com.example.boombee.survey

import android.content.Context
import android.os.Build
import com.example.boombee.AppVersion
import com.example.boombee.data.PreferencesProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Sends survey answers to the Firestore `feedback` collection (create-only
 * rules in firestore.rules). Answers go to a local outbox first and the
 * outbox is flushed whenever Firebase is available, so nothing is lost on
 * builds made before `google-services.json` was added. Once handed to
 * Firestore, its own offline queue delivers the write when the phone is back
 * online, so the UI never waits on the network.
 */
object SurveyRepository {

    private const val KEY_OUTBOX = "survey_outbox"
    private const val COLLECTION = "feedback"
    const val MAX_COMMENT_LENGTH = 1000

    fun submit(context: Context, rating: Int, comment: String) {
        val entry = JSONObject()
            .put("rating", rating.coerceIn(1, 5))
            .put("comment", comment.trim().take(MAX_COMMENT_LENGTH))
            .put("appVersion", AppVersion.VERSION)
            .put("platform", "phone")
            .put("androidSdk", Build.VERSION.SDK_INT)
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("completedTrainings", SurveyTrigger.completedTrainings(context))
        val prefs = PreferencesProvider(context)
        val outbox = readOutbox(prefs).put(entry)
        prefs.putString(KEY_OUTBOX, outbox.toString())
        flush(context)
    }

    /** Hands queued answers to Firestore. No-op until Firebase is configured. */
    fun flush(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val prefs = PreferencesProvider(context)
        val outbox = readOutbox(prefs)
        if (outbox.length() == 0) return
        val firestore = FirebaseFirestore.getInstance()
        for (i in 0 until outbox.length()) {
            val entry = outbox.getJSONObject(i)
            val doc = hashMapOf<String, Any>(
                "rating" to entry.getInt("rating"),
                "appVersion" to entry.getString("appVersion"),
                "platform" to entry.getString("platform"),
                "androidSdk" to entry.getInt("androidSdk"),
                "locale" to entry.getString("locale"),
                "completedTrainings" to entry.getInt("completedTrainings"),
                "createdAt" to FieldValue.serverTimestamp(),
            )
            entry.optString("comment").takeIf { it.isNotEmpty() }?.let { doc["comment"] = it }
            firestore.collection(COLLECTION).add(doc)
        }
        prefs.remove(KEY_OUTBOX)
    }

    private fun readOutbox(prefs: PreferencesProvider): JSONArray =
        prefs.getString(KEY_OUTBOX)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
}
