package com.example.boombee.uiUtils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.boombee.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Keeps a bottom sheet's buttons above the keyboard: opens it fully expanded
 * and pads [content] by the keyboard / navigation bar height. Without this,
 * on small phones the keyboard covered the Save/Send button under the notes
 * field. Call from onStart().
 */
fun BottomSheetDialogFragment.keepAboveKeyboard(content: View) {
    val dialog = dialog as? BottomSheetDialog ?: return
    dialog.behavior.skipCollapsed = true
    dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

    val basePadding = content.getTag(R.id.tag_sheet_base_padding) as? Int ?: content.paddingBottom.also {
        content.setTag(R.id.tag_sheet_base_padding, it)
    }
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        view.updatePadding(bottom = basePadding + maxOf(ime, nav))
        insets
    }
    ViewCompat.requestApplyInsets(content)
}
