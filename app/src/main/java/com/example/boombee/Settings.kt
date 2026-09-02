package com.example.boombee

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.example.boombee.databinding.FragmentSettingsBinding
import com.example.boombee.viewmodel.MainViewModel

class Settings : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()
    private var selectedTrainingType: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        // Show current training type
        val currentType = mainViewModel.trainingType.value
        if (currentType != null) {
            binding.textTrainingType.text = if (currentType == "MMA") "MMA — 5 × 5 min" else "Boxing — 12 × 3 min"
            selectedTrainingType = currentType
        }

        // Toggle dropdown on header tap
        binding.textTrainingType.setOnClickListener {
            val isVisible = binding.dropdownChoices.visibility == View.VISIBLE
            binding.dropdownChoices.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        // Boxing choice
        binding.boxingTrainingTextView.setOnClickListener {
            selectedTrainingType = "BOXING"
            binding.textTrainingType.text = "Boxing — 12 × 3 min"
            binding.dropdownChoices.visibility = View.GONE
        }

        // MMA choice
        binding.mmaTrainingTextView.setOnClickListener {
            selectedTrainingType = "MMA"
            binding.textTrainingType.text = "MMA — 5 × 5 min"
            binding.dropdownChoices.visibility = View.GONE
        }

        // Sound toggle — read current state
        binding.checkboxSound.isChecked = mainViewModel.getSoundStatus()
        binding.checkboxSound.setOnCheckedChangeListener { _, isChecked ->
            mainViewModel.setSoundSettings(isChecked)
        }

        binding.btnSave.setOnClickListener {
            if (selectedTrainingType != null) {
                mainViewModel.setDefaultTrainingType(selectedTrainingType!!)
                Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Select a training type first.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnBack.setOnClickListener {
            it.findNavController().navigate(R.id.action_settings_to_first)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
