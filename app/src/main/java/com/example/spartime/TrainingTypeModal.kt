package com.example.spartime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.spartime.databinding.FragmentTrainingTypeModalBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TrainingTypeModalFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentTrainingTypeModalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTrainingTypeModalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set the title text
        binding.tvTitle.text = "What training type did you do?"

        // Handle the Submit button click
        binding.btnSubmit.setOnClickListener {
            val trainingType = binding.etTrainingType.text.toString()
            if (trainingType.isNotEmpty()) {
                // Handle the submitted training type (e.g., save it, send it back to the activity, etc.)
                dismiss() // Close the modal after submission
            } else {
                binding.etTrainingType.error = "Please enter a training type"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}