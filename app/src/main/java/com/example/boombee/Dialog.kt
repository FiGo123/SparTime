package com.example.boombee

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.example.boombee.databinding.FragmentDialogBinding
import com.example.boombee.viewmodel.MainViewModel

class Dialog : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentDialogBinding.inflate(inflater, container, false)

        binding.yesButton.setOnClickListener {
            val rating = binding.difficultyRating.rating.toInt().coerceIn(1, 5)
            mainViewModel.setSelectedDifficulty(rating)
            mainViewModel.setDialogAnswer(true)
            it.findNavController().navigate(R.id.action_dialog_to_first)
        }

        binding.noButton.setOnClickListener {
            mainViewModel.setDialogAnswer(false)
            it.findNavController().navigate(R.id.action_dialog_to_first)
        }

        return binding.root
    }
}
