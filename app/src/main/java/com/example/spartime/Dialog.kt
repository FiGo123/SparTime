package com.example.spartime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.example.spartime.databinding.FragmentDialogBinding
import com.example.spartime.databinding.FragmentFirstBinding
import com.example.spartime.viewmodel.MainViewModel

class Dialog : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentDialogBinding.inflate(inflater, container, false)
        binding.yesButton.setOnClickListener{
            it.findNavController().navigate(R.id.action_dialog_to_first)
        }
        binding.noButton.setOnClickListener{
            it.findNavController().navigate(R.id.action_dialog_to_first)
        }
        return binding.root
    }
}