package com.example.boombee

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.boombee.data.Dao
import com.example.boombee.data.PreferencesProvider
import com.example.boombee.safety.SafetyText
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Health & safety disclaimer: must be accepted once on first launch, readable any time from Settings. */
object SafetyDialog {

    /** First launch: shown until "I understand" is tapped; [then] runs after (e.g. the analytics prompt). */
    fun showIfNeeded(context: Context, then: () -> Unit) {
        val dao = Dao(PreferencesProvider(context))
        if (dao.getSafetyAccepted()) {
            then()
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(SafetyText.TITLE)
            .setMessage(SafetyText.GENERAL)
            .setCancelable(false)
            .setPositiveButton("I understand") { _, _ ->
                dao.saveSafetyAccepted()
                then()
            }
            .show()
    }

    fun show(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle(SafetyText.TITLE)
            .setMessage(SafetyText.GENERAL)
            .setPositiveButton("OK", null)
            .setNeutralButton("Full terms") { _, _ ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SafetyText.TERMS_URL))) }
            }
            .show()
    }
}
