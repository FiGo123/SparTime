package com.example.boombee

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.boombee.data.DBHandler
import com.example.boombee.data.HistoryStats
import com.example.boombee.data.models.Training
import com.example.boombee.databinding.FragmentTrainingListBinding
import com.example.boombee.history.HistoryAdapter
import com.example.boombee.history.HistoryAdapter.Filter
import com.example.boombee.history.HistoryAdapter.Row
import com.example.boombee.history.SessionDetailFragment
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Training history: this-week summary, rounds-per-week chart, sessions grouped by week. Tap one for detail. */
class HistoryTraining : Fragment() {

    private var _binding: FragmentTrainingListBinding? = null
    private val binding get() = _binding!!
    private var filter = Filter.ALL
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrainingListBinding.inflate(inflater, container, false)
        filter = savedInstanceState?.getString(STATE_FILTER)?.let(Filter::valueOf) ?: Filter.ALL
        adapter = HistoryAdapter(
            onOpen = { training ->
                findNavController().navigate(
                    R.id.action_historyTraining_to_sessionDetail,
                    bundleOf(SessionDetailFragment.ARG_ID to training.id.toLong()),
                )
            },
            onFilter = { selected ->
                if (selected != filter) {
                    filter = selected
                    render()
                }
            },
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.historyBackBtn.setOnClickListener { findNavController().popBackStack() }
        return binding.root
    }

    // Reload every time so edits/deletes on the detail screen show up.
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val all = DBHandler(requireContext()).getAllTraining()
        binding.emptyStateText.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        if (all.isEmpty()) {
            adapter.submit(emptyList())
            return
        }
        val today = LocalDate.now()
        val thisWeek = HistoryStats.thisWeek(all, today)
        val rows = mutableListOf<Row>(
            Row.Header(
                week = HistoryStats.totals(thisWeek),
                streak = HistoryStats.streakDays(all, today),
                weekly = HistoryStats.weeklyRounds(all, today),
                allTime = HistoryStats.totals(all),
                filter = filter,
            )
        )
        val shown = all.filter {
            when (filter) {
                Filter.ALL -> true
                Filter.COACH -> it.isCoach
                Filter.TIMER -> !it.isCoach
            }
        }
        val currentWeek = HistoryStats.weekStart(today)
        val rangeFormat = DateTimeFormatter.ofPattern("d MMM")
        HistoryStats.groupByWeek(shown).forEach { group ->
            val name = when (group.weekStart) {
                currentWeek -> "This week"
                currentWeek.minusWeeks(1) -> "Last week"
                else -> "${group.weekStart.format(rangeFormat)} – ${group.weekStart.plusDays(6).format(rangeFormat)}"
            }
            val totals = HistoryStats.totals(group.trainings)
            rows.add(Row.Week("$name  ·  ${totals.sessions} sessions · ${totals.rounds} rounds"))
            group.trainings.forEach { rows.add(Row.Session(it)) }
        }
        adapter.submit(rows)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_FILTER, filter.name)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val STATE_FILTER = "filter"
    }
}
