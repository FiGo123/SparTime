package com.example.spartime

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.example.spartime.databinding.FragmentSettingsBinding

class Settings : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setting up your UI components
        setupDropdown()
        setupCheckBox()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupDropdown() {
        binding.btnBack.setOnClickListener{
            it.findNavController().navigate(R.id.action_settings_to_first)
        }
        binding.textTrainingType.setOnClickListener {
            // Toggle visibility of dropdownChoices LinearLayout
            val isVisible = binding.dropdownChoices.visibility == View.VISIBLE
            binding.dropdownChoices.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        // Handle dropdown choice click events here
        // Example:

        binding.mmaTrainingTextView.setOnClickListener {
            // Handle MMA Training selection
        }
        // ... handle other dropdown choices similarly
    }

    private fun setupCheckBox() {
        binding.checkboxSound.setOnCheckedChangeListener { _, isChecked ->
            // Handle checkbox state change
            if (isChecked) {
                // Checkbox is checked, do something
            } else {
                // Checkbox is unchecked, do something else
            }
        }
    }
}