package com.example.boombee.tutorial

import android.content.res.ColorStateList
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.ContextCompat
import com.example.boombee.R
import com.example.boombee.analytics.Analytics
import com.example.boombee.coach.ClipPlayer
import com.example.boombee.coach.CoachAudio
import com.example.boombee.coach.CommandPart
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.databinding.ItemTutorialGameBinding
import com.example.boombee.tutorial.ComboGame.TapResult
import com.google.android.material.button.MaterialButton

/** Drives the "Tap the combo" page: calls combos out loud, reacts to taps, shows the score. */
class ComboGameController(
    private val binding: ItemTutorialGameBinding,
    private val clipPlayer: () -> ClipPlayer?,
    private val voiceStyle: () -> VoiceStyle,
    private val level3Pool: List<List<Int>>,
    private val onContinue: () -> Unit,
) {
    private val context = binding.root.context
    private val handler = Handler(Looper.getMainLooper())
    private var game: ComboGame? = null
    private var resultLogged = false

    private val idleTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface))
    private val correctTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.timer_active))
    private val wrongTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.timer_danger))

    private val buttons: Map<Int, MaterialButton> = with(binding) {
        mapOf(1 to punch1, 2 to punch2, 3 to punch3, 4 to punch4, 5 to punch5, 6 to punch6)
    }

    init {
        buttons.forEach { (punch, button) -> button.setOnClickListener { onTap(punch, button) } }
        binding.repeatBtn.setOnClickListener { callCurrent() }
        binding.playAgainBtn.setOnClickListener { newGame() }
        binding.continueBtn.setOnClickListener { onContinue() }
    }

    fun onShown() {
        val current = game
        when {
            current == null -> newGame()
            current.isOver -> showResult()
            else -> scheduleCall(START_DELAY_MS)
        }
    }

    fun onHidden() {
        handler.removeCallbacksAndMessages(null)
        clipPlayer()?.stop()
    }

    private fun newGame() {
        game = ComboGame.build(level3Pool)
        resultLogged = false
        binding.playingGroup.visibility = View.VISIBLE
        binding.resultGroup.visibility = View.GONE
        render()
        scheduleCall(START_DELAY_MS)
    }

    private fun scheduleCall(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ callCurrent() }, delayMs)
    }

    private fun callCurrent() {
        val game = game?.takeIf { !it.isOver } ?: return
        render()
        val keys = CoachAudio.keysFor(game.currentCombo.map { CommandPart.Punch(it) }, voiceStyle()) ?: return
        val player = clipPlayer() ?: return
        if (game.level == 3) player.play(keys, LEVEL_3_SPEED, LEVEL_3_GAP_MS)
        else player.playCommand(keys, isCombo = keys.size > 1)
    }

    private fun onTap(punch: Int, button: MaterialButton) {
        val game = game?.takeIf { !it.isOver } ?: return
        val expected = game.expectedPunch
        when (game.tap(punch)) {
            TapResult.CORRECT -> {
                flash(button, correctTint)
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                render()
            }
            TapResult.WRONG -> {
                flash(button, wrongTint)
                button.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
                )
                buttons[expected]?.let(::pulse)
                render()
                binding.prompt.text = context.getString(R.string.tutorial_game_wrong)
                scheduleCall(REPLAY_DELAY_MS)
            }
            TapResult.COMBO_DONE -> {
                flash(button, correctTint)
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                binding.prompt.text = context.getString(R.string.tutorial_game_correct)
                scheduleCall(NEXT_COMBO_DELAY_MS)
            }
            TapResult.GAME_OVER -> {
                flash(button, correctTint)
                binding.prompt.text = context.getString(R.string.tutorial_game_correct)
                handler.postDelayed(::showResult, NEXT_COMBO_DELAY_MS)
            }
        }
    }

    private fun render() {
        val game = game ?: return
        if (game.isOver) return
        binding.status.text = context.getString(
            R.string.tutorial_game_status, game.comboIndex + 1, game.total, game.level
        )
        binding.prompt.text = if (game.level == 1) {
            game.currentCombo.joinToString(" – ")
        } else {
            context.getString(R.string.tutorial_game_listen)
        }
    }

    private fun showResult() {
        val game = game ?: return
        binding.playingGroup.visibility = View.GONE
        binding.resultGroup.visibility = View.VISIBLE
        binding.resultScore.text = context.getString(R.string.tutorial_game_score, game.score, game.total)
        if (!resultLogged) {
            Analytics.log(context, "combo_game_result", "correct" to game.score, "total" to game.total)
            LearnProgress.recordGame(context, game.score)
            resultLogged = true
        }
        binding.resultMessage.setText(
            if (game.score >= game.total - 1) R.string.tutorial_game_result_great else R.string.tutorial_game_result_ok
        )
    }

    private fun flash(button: MaterialButton, tint: ColorStateList) {
        button.backgroundTintList = tint
        button.postDelayed({ button.backgroundTintList = idleTint }, FLASH_MS)
    }

    private fun pulse(button: View) {
        button.animate().scaleX(1.25f).scaleY(1.25f).setDuration(150).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    companion object {
        private const val START_DELAY_MS = 700L
        private const val REPLAY_DELAY_MS = 900L
        private const val NEXT_COMBO_DELAY_MS = 800L
        private const val FLASH_MS = 250L
        private const val LEVEL_3_SPEED = 1.5f
        private const val LEVEL_3_GAP_MS = -30L
    }
}
