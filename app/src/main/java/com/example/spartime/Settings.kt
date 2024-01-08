package com.example.spartime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.example.spartime.databinding.FragmentSettingsBinding
import com.example.spartime.viewmodel.MainViewModel

class Settings : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()
    lateinit var trainingType: String
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        binding.mmaTrainingTextView.setOnClickListener {
            println("pozivi")
            val isVisible = binding.dropdownChoices.visibility == View.VISIBLE
            binding.dropdownChoices.visibility = if (isVisible) View.GONE else View.VISIBLE
            trainingType = "MMA"

        }
        binding.boxingTrainingTextView.setOnClickListener {
            println("pozi2222vi")
            val isVisible = binding.dropdownChoices.visibility == View.VISIBLE
            binding.dropdownChoices.visibility = if (isVisible) View.GONE else View.VISIBLE
            trainingType = "BOXING"
        }
        binding.checkboxSound.setOnCheckedChangeListener{ _, isChecked ->

            if (isChecked) {
                mainViewModel.setSoundSettings(true)
            } else {
                mainViewModel.setSoundSettings(false)
            }
        }
        binding.btnSave.setOnClickListener{
            mainViewModel.setDefaultTrainingType(trainingType)
        }


        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDropdown()
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


    }


}