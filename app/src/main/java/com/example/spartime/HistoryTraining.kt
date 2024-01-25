package com.example.spartime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.spartime.data.DBHandler
import com.example.spartime.data.models.Training
import com.example.spartime.databinding.FragmentFirstBinding
import com.example.spartime.databinding.FragmentTrainingListBinding
import com.example.spartime.uiUtils.TrainingAdapter

class HistoryTraining : Fragment() {


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val binding= FragmentTrainingListBinding.inflate(layoutInflater)
        val dbHandler = DBHandler(requireContext())
        val trainingList: List<Training> = dbHandler.getAllTraining()

        val layoutManager = LinearLayoutManager(requireContext())

        binding.recyclerView.layoutManager = layoutManager
        binding.historyBackBtn.setOnClickListener{
            this.findNavController().navigate(R.id.action_historyTraining_to_first)
        }
        val adapter = TrainingAdapter(trainingList)
        binding.recyclerView.adapter = adapter
        return binding.root
    }
}