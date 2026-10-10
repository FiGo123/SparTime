package com.example.boombee.history

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.boombee.coach.PunchNaming
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.DBHandler
import com.example.boombee.data.HistoryStats
import com.example.boombee.data.models.Training
import com.example.boombee.databinding.FragmentSessionDetailBinding
import com.example.boombee.databinding.ItemPunchRowBinding
import com.example.boombee.databinding.ItemStatCellBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One session in full: time, rounds, effort + notes, punch mix, punches per round, coach calls. */
class SessionDetailFragment : Fragment() {

    private var _binding: FragmentSessionDetailBinding? = null
    private val binding get() = _binding!!
    private val trainingId: Long get() = requireArguments().getLong(ARG_ID)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSessionDetailBinding.inflate(inflater, container, false)
        binding.detailBack.setOnClickListener { findNavController().popBackStack() }
        childFragmentManager.setFragmentResultListener(EffortSheet.RESULT_KEY, viewLifecycleOwner) { _, _ -> bind() }
        bind()
        return binding.root
    }

    private fun bind() {
        val t = DBHandler(requireContext()).getTraining(trainingId)
        if (t == null) {
            findNavController().popBackStack()
            return
        }
        val start = HistoryStats.startTime(t)
        binding.detailDate.text = start.format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault()))
        binding.detailSubtitle.text = listOfNotNull(
            if (t.isCoach) "COACH" else "TIMER",
            t.coachDifficulty?.uppercase(),
            if (t.platform == "watch") "⌚ WATCH" else "📱 PHONE",
            if (t.completed) "COMPLETED" else "STOPPED",
        ).joinToString(" · ")

        bindStats(t)
        bindEffort(t)
        bindPunches(t)
        bindRounds(t)
        bindCalls(t)

        binding.detailDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete this session?")
                .setMessage("It will be removed from your history. This can't be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    DBHandler(requireContext()).deleteTraining(t.id)
                    findNavController().popBackStack()
                }
                .show()
        }
    }

    private fun bindStats(t: Training) {
        val restText = if (t.restSeconds > 0) " · ${t.restSeconds / 60}′ rest" else ""
        val cells = listOf(
            HistoryStats.formatDuration(t.activeSecondsOrEstimate()) to "active time",
            "${t.numberOfRounds}/${t.plannedRoundsOrDone()}" to "rounds",
            "${t.roundDuration}′" to "per round$restText",
            (if (t.isCoach) "${t.totalPunches}" else "—") to "punches called",
            (if (t.isCoach) "${t.totalCombos}" else "—") to "combos",
            (t.effort?.toString() ?: "—") to "effort",
        )
        listOf(binding.statsRow1, binding.statsRow2).forEach { it.removeAllViews() }
        cells.forEachIndexed { i, (value, label) ->
            val row = if (i < 3) binding.statsRow1 else binding.statsRow2
            val cell = ItemStatCellBinding.inflate(layoutInflater, row, false)
            cell.statValue.text = value
            cell.statLabel.text = label
            cell.root.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(cell.root)
        }
    }

    private fun bindEffort(t: Training) {
        val effort = t.effort
        if (effort != null) {
            binding.detailEffortBadge.visibility = View.VISIBLE
            binding.detailEffortBadge.text = effort.toString()
            binding.detailEffortBadge.background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(EffortPrompt.color(effort))
            }
            binding.detailEffortLabel.text = HistoryStats.effortLabel(effort)
            binding.detailEditEffort.text = "Edit"
        } else {
            binding.detailEffortBadge.visibility = View.GONE
            binding.detailEffortLabel.text = "Not rated"
            binding.detailEffortLabel.alpha = 0.6f
            binding.detailEditEffort.text = "Rate"
        }
        binding.detailNotes.text = t.notes
        binding.detailNotes.visibility = if (t.notes.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.detailEditEffort.setOnClickListener {
            EffortSheet.show(childFragmentManager, t.id.toLong(), t.effort, t.notes, isEdit = true)
        }
    }

    private fun bindPunches(t: Training) {
        val counts = t.punchCounts()
        val mix = HistoryStats.PunchMix(counts)
        binding.punchCard.visibility = if (t.isCoach && mix.total > 0) View.VISIBLE else View.GONE
        if (mix.total == 0) return
        binding.detailHands.text = "Left hand ${mix.percent(mix.left)}% · Right hand ${mix.percent(mix.right)}%\n" +
            "Straights ${mix.percent(mix.straights)}% · Hooks ${mix.percent(mix.hooks)}% · Uppercuts ${mix.percent(mix.uppercuts)}%"
        // Rows after the title + hands line are rebuilt each bind.
        while (binding.punchContent.childCount > 2) binding.punchContent.removeViewAt(2)
        val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        counts.forEachIndexed { i, count ->
            val row = ItemPunchRowBinding.inflate(layoutInflater, binding.punchContent, false)
            row.punchName.text = "${i + 1}  ${PunchNaming.spoken(i + 1, VoiceStyle.WORDS)}"
            row.punchCount.text = count.toString()
            row.punchBar.backgroundTintList = null
            row.punchBar.background = GradientDrawable().apply {
                cornerRadius = 12f
                setColor(PunchMixBar.COLORS[i])
            }
            (row.punchBar.layoutParams as LinearLayout.LayoutParams).weight = count.toFloat()
            (row.punchBarRest.layoutParams as LinearLayout.LayoutParams).weight = (max - count).toFloat()
            binding.punchContent.addView(row.root)
        }
    }

    private fun bindRounds(t: Training) {
        val perRound = t.roundPunchList()
        binding.roundsCard.visibility = if (t.isCoach && perRound.isNotEmpty()) View.VISIBLE else View.GONE
        if (perRound.isEmpty()) return
        binding.detailRoundsChart.setData(perRound, perRound.indices.map { "R${it + 1}" })
    }

    private fun bindCalls(t: Training) {
        binding.callsCard.visibility = if (t.isCoach) View.VISIBLE else View.GONE
        if (!t.isCoach) return
        binding.callsRow.removeAllViews()
        // Pre-v3 rows only have defense + distance combined.
        val hasSplit = t.defenseCalls + t.distanceCalls > 0 || t.startedAt > 0
        val cells = if (hasSplit) {
            listOf("${t.totalCombos}" to "attacks", "${t.defenseCalls}" to "defense", "${t.distanceCalls}" to "distance", "${t.freestyleCalls}" to "freestyle")
        } else {
            listOf("${t.totalCombos}" to "attacks", "${t.totalTacticalCommands}" to "defense + distance")
        }
        cells.forEach { (value, label) ->
            val cell = ItemStatCellBinding.inflate(layoutInflater, binding.callsRow, false)
            cell.statValue.text = value
            cell.statValue.textSize = 18f
            cell.statLabel.text = label
            cell.root.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            binding.callsRow.addView(cell.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ID = "trainingId"
    }
}
