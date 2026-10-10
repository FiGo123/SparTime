package com.example.boombee.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import com.example.boombee.coach.PunchNaming
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.HistoryStats
import com.example.boombee.data.models.Training
import com.example.boombee.wear.ui.theme.darkChipColors
import java.time.format.DateTimeFormatter

private val BeeYellow = Color(0xFFFFC107)
private val PunchColors = listOf(
    Color(0xFFFFD54F), Color(0xFFFF9800), Color(0xFFFFC107),
    Color(0xFFF57C00), Color(0xFFC79100), Color(0xFFE65100),
)

internal fun effortColor(effort: Int) = when (effort) {
    in 1..4 -> Color(0xFF4CAF50)
    in 5..6 -> Color(0xFFFFC107)
    in 7..8 -> Color(0xFFFF6F00)
    else -> Color(0xFFF44336)
}

/** One session on the watch: stats, effort, punch mix and punches per round, same data as the phone. */
@Composable
fun SessionDetailScreen(
    training: Training,
    onEditEffort: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf(false) }
    val t = training
    val counts = t.punchCounts()
    val mix = HistoryStats.PunchMix(counts)
    val perRound = t.roundPunchList()

    Scaffold(timeText = { TimeText(timeTextStyle = TimeTextDefaults.timeTextStyle(color = BeeYellow)) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                ListHeader {
                    Text(
                        HistoryStats.startTime(t).format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm")),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Text(
                    listOfNotNull(
                        if (t.isCoach) "COACH" else "TIMER",
                        t.coachDifficulty?.lowercase()?.replaceFirstChar { it.uppercase() },
                        if (t.platform == "watch") "⌚" else "📱",
                        if (t.completed) null else "Stopped",
                    ).joinToString(" · "),
                    color = BeeYellow,
                    style = MaterialTheme.typography.caption2,
                )
            }
            item { StatLine("Active time", HistoryStats.formatDuration(t.activeSecondsOrEstimate())) }
            item { StatLine("Rounds", "${t.numberOfRounds}/${t.plannedRoundsOrDone()} × ${t.roundDuration}′") }
            if (t.restSeconds > 0) item { StatLine("Rest", "${t.restSeconds / 60}′") }
            if (t.isCoach) {
                item { StatLine("Punches called", "${t.totalPunches}") }
                item { StatLine("Combos", "${t.totalCombos}") }
            }
            item {
                val effort = t.effort
                Chip(
                    label = { Text(if (effort != null) "Effort $effort · ${HistoryStats.effortLabel(effort)}" else "Rate effort") },
                    secondaryLabel = { Text(if (effort != null) "Tap to change" else "Not rated yet") },
                    onClick = onEditEffort,
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (effort != null) effortColor(effort) else Color(0xFF2A2A2A),
                        contentColor = if (effort != null) Color.Black else Color.White,
                        secondaryContentColor = if (effort != null) Color.Black else BeeYellow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!t.notes.isNullOrBlank()) {
                item { Text(t.notes ?: "", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center) }
            }
            if (t.isCoach && mix.total > 0) {
                item { SectionTitle("Punch mix") }
                item {
                    Text(
                        "Left ${mix.percent(mix.left)}% · Right ${mix.percent(mix.right)}%",
                        style = MaterialTheme.typography.caption2,
                        color = Color.LightGray,
                    )
                }
                val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
                counts.forEachIndexed { i, count ->
                    item { BarLine("${i + 1} ${PunchNaming.spoken(i + 1, VoiceStyle.WORDS)}", count, max, PunchColors[i]) }
                }
            }
            if (t.isCoach && perRound.isNotEmpty()) {
                item { SectionTitle("Punches per round") }
                val max = perRound.maxOrNull()?.coerceAtLeast(1) ?: 1
                perRound.forEachIndexed { i, count -> item { BarLine("R${i + 1}", count, max, BeeYellow) } }
            }
            if (t.isCoach) {
                item { SectionTitle("Coach calls") }
                item {
                    Text(
                        "Defense ${t.defenseCalls} · Distance ${t.distanceCalls} · Freestyle ${t.freestyleCalls}",
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Chip(
                    label = { Text(if (confirmDelete) "Tap again to delete" else "Delete") },
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (confirmDelete) Color(0xFFF44336) else Color(0xFF2A2A2A),
                        contentColor = if (confirmDelete) Color.Black else Color(0xFFF44336),
                    ),
                )
            }
            item { Chip(label = { Text("Back") }, onClick = onBack, colors = darkChipColors()) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp),
        fontWeight = FontWeight.Bold,
        color = Color.White,
        style = MaterialTheme.typography.body2,
    )
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.LightGray, style = MaterialTheme.typography.caption1, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun BarLine(label: String, value: Int, max: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.caption3, color = Color.White, modifier = Modifier.width(62.dp), maxLines = 1)
        Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = 5.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(value.toFloat() / max)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
        Spacer(Modifier.width(4.dp))
        Text("$value", style = MaterialTheme.typography.caption3, color = Color.White, modifier = Modifier.width(24.dp), textAlign = TextAlign.End)
    }
}
