package com.example.spartime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.spartime.data.DBHandler
import com.example.spartime.databinding.FragmentTrainingListBinding
import com.example.spartime.uiUtils.TrainingAdapter

class HistoryTraining : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentTrainingListBinding.inflate(layoutInflater)
        val dbHandler = DBHandler(requireContext())
        val trainingList = dbHandler.getAllTraining().toMutableList()

        lateinit var adapter: TrainingAdapter
        adapter = TrainingAdapter(trainingList) { training, position ->
            dbHandler.deleteTraining(training.id)
            adapter.removeAt(position)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.historyBackBtn.setOnClickListener {
            findNavController().navigate(R.id.action_historyTraining_to_first)
        }

        return binding.root
    }
}
