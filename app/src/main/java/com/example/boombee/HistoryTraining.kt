package com.example.boombee

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.boombee.data.DBHandler
import com.example.boombee.databinding.FragmentTrainingListBinding
import com.example.boombee.uiUtils.TrainingAdapter

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
            binding.emptyStateText.visibility = if (trainingList.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.emptyStateText.visibility = if (trainingList.isEmpty()) View.VISIBLE else View.GONE

        binding.historyBackBtn.setOnClickListener {
            findNavController().navigate(R.id.action_historyTraining_to_first)
        }

        return binding.root
    }
}
