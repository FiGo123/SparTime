package com.example.boombee.wear

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.boombee.coach.ClipPlayer
import com.example.boombee.coach.CoachAudio
import com.example.boombee.coach.ComboLibrary
import com.example.boombee.coach.CommandPart
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.tutorial.ComboGame
import com.example.boombee.wear.ui.theme.darkChipColors
import com.example.boombee.tutorial.ComboGame.TapResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BeeYellow = Color(0xFFFFC107)
private val WrongRed = Color(0xFFF44336)
private val IdleButton = Color(0xFF2A2A2A)

private const val START_DELAY_MS = 700L
private const val REPLAY_DELAY_MS = 900L
private const val NEXT_COMBO_DELAY_MS = 800L
private const val FLASH_MS = 250L

/**
 * "Tap the combo" on the watch — same rules as the phone tutorial's game
 * ([ComboGame] in :core). The fighter is imagined facing you, like the
 * phone artwork, so the lead-hand numbers 1/3/5 are on the right.
 */
@Composable
fun ComboGameScreen(clipPlayer: ClipPlayer, voiceStyle: VoiceStyle, onDone: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val level3Pool = remember {
        val library = ComboLibrary(context)
        (3..4).flatMap(library::combosOfLength).mapNotNull { combo ->
            combo.parts.map { (it as? CommandPart.Punch)?.number ?: return@mapNotNull null }
        }
    }
    var game by remember { mutableStateOf(ComboGame.build(level3Pool)) }
    // ComboGame isn't observable; bump this after every change to redraw.
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }
    val flashes = remember { mutableStateMapOf<Int, Color>() }
    var callJob by remember { mutableStateOf<Job?>(null) }

    fun callCurrent(delayMs: Long) {
        callJob?.cancel()
        callJob = scope.launch {
            delay(delayMs)
            if (game.isOver) return@launch
            message = null
            revision++
            val keys = CoachAudio.keysFor(game.currentCombo.map { CommandPart.Punch(it) }, voiceStyle) ?: return@launch
            if (game.level == 3) clipPlayer.play(keys, 1.5f, -30L)
            else clipPlayer.playCommand(keys, isCombo = keys.size > 1)
        }
    }

    fun flash(punch: Int, color: Color) {
        flashes[punch] = color
        scope.launch {
            delay(FLASH_MS)
            flashes.remove(punch)
        }
    }

    fun onTap(punch: Int) {
        if (game.isOver) return
        val expected = game.expectedPunch
        when (game.tap(punch)) {
            TapResult.CORRECT -> {
                flash(punch, BeeYellow)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            TapResult.WRONG -> {
                flash(punch, WrongRed)
                flash(expected, BeeYellow)
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                )
                message = "✗ Again"
                callCurrent(REPLAY_DELAY_MS)
            }
            TapResult.COMBO_DONE -> {
                flash(punch, BeeYellow)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                message = "✓"
                callCurrent(NEXT_COMBO_DELAY_MS)
            }
            TapResult.GAME_OVER -> {
                flash(punch, BeeYellow)
                message = "✓"
                callJob?.cancel()
                callJob = scope.launch {
                    delay(NEXT_COMBO_DELAY_MS)
                    showResult = true
                }
            }
        }
        revision++
    }

    fun restart() {
        game = ComboGame.build(level3Pool)
        showResult = false
        message = null
        revision++
        callCurrent(START_DELAY_MS)
    }

    DisposableEffect(Unit) {
        callCurrent(START_DELAY_MS)
        onDispose {
            callJob?.cancel()
            clipPlayer.stop()
        }
    }
    BackHandler(onBack = onDone)

    if (showResult) {
        ResultView(score = game.score, total = game.total, onPlayAgain = ::restart, onDone = onDone)
        return
    }

    // Keyed on revision so every tap redraws.
    val status = remember(revision, game) { "${game.comboIndex + 1}/${game.total} · L${game.level}" }
    val prompt = remember(revision, game, message) {
        message ?: if (game.level == 1) game.currentCombo.joinToString("-") else "🎧"
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PunchColumn(listOf(2, 4, 6), flashes, ::onTap)
            Column(
                modifier = Modifier.width(84.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    status,
                    style = MaterialTheme.typography.caption3,
                    color = Color.Gray,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    prompt,
                    fontSize = if (prompt.length > 5) 18.sp else 24.sp,
                    fontWeight = FontWeight.Black,
                    color = BeeYellow,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                CompactChip(
                    label = { Text("🔊") },
                    onClick = { callCurrent(0L) },
                    colors = darkChipColors(),
                )
            }
            PunchColumn(listOf(1, 3, 5), flashes, ::onTap)
        }
    }
}

@Composable
private fun PunchColumn(punches: List<Int>, flashes: Map<Int, Color>, onTap: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (punch in punches) {
            Button(
                onClick = { onTap(punch) },
                modifier = Modifier.size(44.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = flashes[punch] ?: IdleButton),
            ) {
                Text(
                    "$punch",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = if (flashes[punch] == BeeYellow) Color.Black else Color.White,
                )
            }
        }
    }
}

@Composable
private fun ResultView(score: Int, total: Int, onPlayAgain: () -> Unit, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$score/$total 🐝", fontSize = 30.sp, fontWeight = FontWeight.Black, color = BeeYellow)
        Text(
            if (score >= total - 1) "You know your numbers!" else "Nice start!",
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Chip(label = { Text("Play again") }, onClick = onPlayAgain, colors = ChipDefaults.primaryChipColors())
        Spacer(Modifier.height(4.dp))
        CompactChip(label = { Text("Done") }, onClick = onDone)
    }
}
