package com.example.boombee.history

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import com.example.boombee.R
import com.example.boombee.analytics.Analytics
import com.example.boombee.data.DBHandler
import com.example.boombee.data.HistoryStats
import com.example.boombee.databinding.FragmentEffortBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

/**
 * "How hard was that?" — effort 1–10 (rate of perceived exertion) plus
 * optional notes, after a session or from the session detail screen.
 */
class EffortSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentEffortBinding? = null
    private val binding get() = _binding!!
    private var selected: Int? = null
    private val buttons = mutableListOf<MaterialButton>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEffortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view.parent as? View)?.setBackgroundColor(ContextCompat.getColor(view.context, R.color.surface))
        val trainingId = requireArguments().getLong(ARG_ID)
        val isEdit = requireArguments().getBoolean(ARG_EDIT)

        for (effort in 1..10) {
            val row = if (effort <= 5) binding.effortRow1 else binding.effortRow2
            val button = layoutInflater.inflate(R.layout.item_effort_button, row, false) as MaterialButton
            button.text = effort.toString()
            button.layoutParams = LinearLayout.LayoutParams(0, button.layoutParams.height, 1f).apply {
                marginStart = 4.dp; marginEnd = 4.dp
            }
            button.setOnClickListener { select(effort) }
            row.addView(button)
            buttons.add(button)
        }

        val initial = savedInstanceState?.getInt(STATE_SELECTED)?.takeIf { it > 0 }
            ?: requireArguments().getInt(ARG_EFFORT).takeIf { it > 0 }
        if (savedInstanceState == null) binding.effortNotes.setText(requireArguments().getString(ARG_NOTES))
        select(initial)
        binding.effortSkip.text = if (isEdit) "Cancel" else "Skip"

        binding.effortSave.setOnClickListener {
            val context = requireContext().applicationContext
            DBHandler(context).updateEffort(trainingId, selected, binding.effortNotes.text?.toString())
            selected?.let { Analytics.log(context, "effort_rated", "effort" to it, "source" to if (isEdit) "detail" else "post_session") }
            setFragmentResult(RESULT_KEY, bundleOf(ARG_ID to trainingId))
            dismiss()
        }
        binding.effortSkip.setOnClickListener { dismiss() }
    }

    private fun select(effort: Int?) {
        selected = effort
        buttons.forEachIndexed { i, button ->
            val value = i + 1
            val active = value == effort
            button.backgroundTintList = ColorStateList.valueOf(
                if (active) EffortPrompt.color(value) else ContextCompat.getColor(requireContext(), R.color.background_secondary)
            )
            button.setTextColor(ContextCompat.getColor(requireContext(), if (active) R.color.black else R.color.white))
        }
        binding.effortLabel.text = effort?.let { "$it · ${HistoryStats.effortLabel(it)}" } ?: "1 = very easy · 10 = all-out"
        binding.effortSave.isEnabled = effort != null
        binding.effortSave.alpha = if (effort != null) 1f else 0.4f
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED, selected ?: 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        buttons.clear()
        _binding = null
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "EffortSheet"
        const val RESULT_KEY = "effort_saved"
        private const val ARG_ID = "training_id"
        private const val ARG_EFFORT = "effort"
        private const val ARG_NOTES = "notes"
        private const val ARG_EDIT = "edit"
        private const val STATE_SELECTED = "selected"

        fun show(fragmentManager: FragmentManager, trainingId: Long, effort: Int? = null, notes: String? = null, isEdit: Boolean = false) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            EffortSheet().apply {
                arguments = bundleOf(ARG_ID to trainingId, ARG_EFFORT to (effort ?: 0), ARG_NOTES to notes, ARG_EDIT to isEdit)
            }.show(fragmentManager, TAG)
        }
    }
}
