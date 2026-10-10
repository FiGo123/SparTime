package com.example.boombee.history

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.boombee.R
import com.example.boombee.data.HistoryStats
import com.example.boombee.data.models.Training
import com.example.boombee.databinding.ItemHistoryHeaderBinding
import com.example.boombee.databinding.ItemHistoryWeekBinding
import com.example.boombee.databinding.ItemStatCellBinding
import com.example.boombee.databinding.ItemTrainingBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** History list: summary header, then sessions grouped by week. */
class HistoryAdapter(
    private val onOpen: (Training) -> Unit,
    private val onFilter: (Filter) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class Filter { ALL, COACH, TIMER }

    sealed class Row {
        data class Header(
            val week: HistoryStats.Totals,
            val streak: Int,
            val weekly: List<Pair<LocalDate, Int>>,
            val allTime: HistoryStats.Totals,
            val filter: Filter,
        ) : Row()
        data class Week(val label: String) : Row()
        data class Session(val training: Training) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(newRows: List<Row>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is Row.Header -> 0
        is Row.Week -> 1
        is Row.Session -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> HeaderHolder(ItemHistoryHeaderBinding.inflate(inflater, parent, false))
            1 -> WeekHolder(ItemHistoryWeekBinding.inflate(inflater, parent, false))
            else -> SessionHolder(ItemTrainingBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).bind(row)
            is Row.Week -> (holder as WeekHolder).binding.weekLabel.text = row.label
            is Row.Session -> (holder as SessionHolder).bind(row.training)
        }
    }

    private inner class HeaderHolder(val binding: ItemHistoryHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.filterGroup.addOnButtonCheckedListener { _, id, checked ->
                if (!checked) return@addOnButtonCheckedListener
                onFilter(
                    when (id) {
                        R.id.filterCoach -> Filter.COACH
                        R.id.filterTimer -> Filter.TIMER
                        else -> Filter.ALL
                    }
                )
            }
        }

        fun bind(h: Row.Header) {
            val inflater = LayoutInflater.from(binding.root.context)
            binding.weekStats.removeAllViews()
            listOf(
                "${h.week.sessions}" to "sessions",
                "${h.week.rounds}" to "rounds",
                HistoryStats.formatDuration(h.week.activeSeconds) to "active",
                (if (h.streak > 0) "🔥${h.streak}" else "0") to "day streak",
            ).forEach { (value, label) ->
                val cell = ItemStatCellBinding.inflate(inflater, binding.weekStats, false)
                cell.statValue.text = value
                cell.statLabel.text = label
                cell.root.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                binding.weekStats.addView(cell.root)
            }
            val labelFormat = DateTimeFormatter.ofPattern("d/M")
            binding.weeklyChart.setData(
                h.weekly.map { it.second },
                h.weekly.mapIndexed { i, (start, _) -> if (i == h.weekly.lastIndex) "now" else start.format(labelFormat) },
                highlightLast = true,
            )
            binding.allTimeText.text = "All time: ${h.allTime.sessions} sessions · ${h.allTime.rounds} rounds · " +
                "${HistoryStats.formatDuration(h.allTime.activeSeconds)}" +
                if (h.allTime.punches > 0) " · ${h.allTime.punches} punches called" else ""
            val checkedId = when (h.filter) {
                Filter.ALL -> R.id.filterAll
                Filter.COACH -> R.id.filterCoach
                Filter.TIMER -> R.id.filterTimer
            }
            if (binding.filterGroup.checkedButtonId != checkedId) binding.filterGroup.check(checkedId)
        }
    }

    private class WeekHolder(val binding: ItemHistoryWeekBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class SessionHolder(val binding: ItemTrainingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(t: Training) {
            binding.root.setOnClickListener { onOpen(t) }
            binding.sessionDate.text = HistoryStats.startTime(t)
                .format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault()))
            binding.sessionMeta.text = listOfNotNull(
                if (t.isCoach) "COACH" else "TIMER",
                t.coachDifficulty?.lowercase()?.replaceFirstChar { it.uppercase() },
                if (t.platform == "watch") "⌚" else null,
                if (!t.completed) "Stopped" else null,
            ).joinToString(" · ")
            val rounds = if (t.completed) "${t.numberOfRounds} rds" else "${t.numberOfRounds}/${t.plannedRoundsOrDone()} rds"
            binding.sessionStats.text = listOfNotNull(
                rounds,
                HistoryStats.formatDuration(t.activeSecondsOrEstimate()),
                if (t.isCoach && t.totalPunches > 0) "${t.totalPunches} punches" else null,
            ).joinToString("  ·  ")
            val effort = t.effort
            binding.sessionEffort.visibility = if (effort != null) View.VISIBLE else View.GONE
            if (effort != null) {
                binding.sessionEffort.text = "Effort $effort"
                binding.sessionEffort.background = GradientDrawable().apply {
                    cornerRadius = 30f
                    setColor(EffortPrompt.color(effort))
                }
            }
            val counts = t.punchCounts()
            binding.sessionMix.visibility = if (t.isCoach && counts.sum() > 0) View.VISIBLE else View.GONE
            binding.sessionMix.setCounts(counts)
        }
    }
}
