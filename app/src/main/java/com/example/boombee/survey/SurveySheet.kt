package com.example.boombee.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.example.boombee.R
import com.example.boombee.databinding.FragmentSurveyBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** 1–5 star satisfaction survey with an optional "what should we improve" comment. */
class SurveySheet : BottomSheetDialogFragment() {

    private var _binding: FragmentSurveyBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurveyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fromSettings = arguments?.getBoolean(ARG_FROM_SETTINGS) == true
        // The sheet container keeps the light theme's background, which shows
        // as a white strip behind the navigation bar; match it to the card.
        (view.parent as? View)?.setBackgroundColor(ContextCompat.getColor(view.context, R.color.surface))

        setSendEnabled(false)
        binding.surveyRating.setOnRatingBarChangeListener { _, rating, _ ->
            setSendEnabled(rating >= 1f)
        }
        binding.surveySend.setOnClickListener {
            val context = requireContext().applicationContext
            SurveyRepository.submit(
                context,
                binding.surveyRating.rating.toInt(),
                binding.surveyComment.text?.toString().orEmpty()
            )
            SurveyTrigger.markSubmitted(context)
            Toast.makeText(context, "Thanks! 🐝", Toast.LENGTH_SHORT).show()
            dismiss()
        }
        binding.surveyNotNow.setOnClickListener { dismiss() }

        // Opened on purpose from Settings, so there's nothing to opt out of.
        binding.surveyNever.visibility = if (fromSettings) View.GONE else View.VISIBLE
        binding.surveyNever.setOnClickListener {
            SurveyTrigger.markNeverAsk(requireContext())
            dismiss()
        }
    }

    // The yellow backgroundTint hides the disabled state, so dim it by hand.
    private fun setSendEnabled(enabled: Boolean) {
        binding.surveySend.isEnabled = enabled
        binding.surveySend.alpha = if (enabled) 1f else 0.4f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "SurveySheet"
        private const val ARG_FROM_SETTINGS = "from_settings"

        fun show(fragmentManager: FragmentManager, fromSettings: Boolean) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            SurveySheet().apply {
                arguments = Bundle().apply { putBoolean(ARG_FROM_SETTINGS, fromSettings) }
            }.show(fragmentManager, TAG)
        }
    }
}
