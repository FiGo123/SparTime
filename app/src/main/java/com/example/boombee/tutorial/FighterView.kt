package com.example.boombee.tutorial

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.boombee.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class FighterPose {
    GUARD, STANCE, JAB, CROSS, LEAD_HOOK, REAR_HOOK, LEAD_UPPERCUT, REAR_UPPERCUT, SUMMARY, DEFENSE, DISTANCE,
}

/**
 * Placeholder tutorial artwork: an orthodox fighter facing the viewer, so
 * their lead (left) hand and lead foot are on the RIGHT of the screen, the
 * lead foot forward = closer = lower. The punching arm is bee-yellow
 * and an arrow for the motion. Drawn on a 100×100 grid scaled to the view.
 * Final illustrations replace this per page (see RELEASE_TUTORIAL.md → Artwork).
 */
class FighterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var pose: FighterPose = FighterPose.GUARD
        set(value) {
            field = value
            invalidate()
        }

    private val yellow = ContextCompat.getColor(context, R.color.timer_active)
    private val orange = ContextCompat.getColor(context, R.color.accent_orange)
    private val bodyColor = 0xFF3A3A3A.toInt()
    private val limbColor = 0xFF6A6A6A.toInt()
    private val headColor = 0xFF4A4A4A.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val torsoFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = bodyColor
        pathEffect = CornerPathEffect(5f)
    }
    private val limb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xE6FFFFFF.toInt()
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private class Arm(val shoulder: PointF, val elbow: PointF, val glove: PointF, val gloveRadius: Float = 6.5f) {
        fun mirrored() = Arm(shoulder.mirror(), elbow.mirror(), glove.mirror(), gloveRadius)
    }

    // Lead (left) arm, on the viewer's right; the rear arm is its mirror.
    private val leadGuard = Arm(PointF(67f, 52f), PointF(73f, 64f), PointF(64f, 39f))
    private val leadStraight = Arm(PointF(67f, 52f), PointF(63f, 35f), PointF(57f, 19f), 5.5f)
    private val leadHook = Arm(PointF(67f, 52f), PointF(81f, 42f), PointF(63f, 25f), 6f)
    private val leadUppercut = Arm(PointF(67f, 52f), PointF(78f, 68f), PointF(77f, 53f))

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width, height) / 100f
        canvas.save()
        canvas.translate((width - 100f * scale) / 2f, (height - 100f * scale) / 2f)
        canvas.scale(scale, scale)
        drawPose(canvas)
        canvas.restore()
    }

    private fun drawPose(canvas: Canvas) {
        val leadArm: Arm
        val rearArm: Arm
        var leadColor = limbColor
        var rearColor = limbColor
        when (pose) {
            FighterPose.JAB -> { leadArm = leadStraight; rearArm = leadGuard.mirrored(); leadColor = yellow }
            FighterPose.CROSS -> { leadArm = leadGuard; rearArm = leadStraight.mirrored(); rearColor = yellow }
            FighterPose.LEAD_HOOK -> { leadArm = leadHook; rearArm = leadGuard.mirrored(); leadColor = yellow }
            FighterPose.REAR_HOOK -> { leadArm = leadGuard; rearArm = leadHook.mirrored(); rearColor = yellow }
            FighterPose.LEAD_UPPERCUT -> { leadArm = leadUppercut; rearArm = leadGuard.mirrored(); leadColor = yellow }
            FighterPose.REAR_UPPERCUT -> { leadArm = leadGuard; rearArm = leadUppercut.mirrored(); rearColor = yellow }
            FighterPose.SUMMARY -> { leadArm = leadGuard; rearArm = leadGuard.mirrored(); leadColor = yellow; rearColor = orange }
            FighterPose.DEFENSE -> { leadArm = leadGuard; rearArm = leadGuard.mirrored(); leadColor = yellow; rearColor = yellow }
            else -> { leadArm = leadGuard; rearArm = leadGuard.mirrored() }
        }

        // Feet: lead (left) foot on the viewer's right, forward = closer = lower on screen.
        limb.color = bodyColor
        canvas.drawLine(59f, 80f, 62f, 93f, limb)
        canvas.drawLine(41f, 80f, 39f, 87f, limb)
        fill.color = if (pose == FighterPose.STANCE) yellow else limbColor
        canvas.drawOval(RectF(55f, 91f, 70f, 99f), fill)
        fill.color = limbColor
        canvas.drawOval(RectF(33f, 85f, 45f, 91f), fill)

        val torso = Path().apply {
            moveTo(29f, 49f); lineTo(71f, 49f); lineTo(62f, 83f); lineTo(38f, 83f); close()
        }
        canvas.drawPath(torso, torsoFill)
        fill.color = headColor
        canvas.drawCircle(50f, 38f, 10f, fill)

        drawArm(canvas, leadArm, leadColor)
        drawArm(canvas, rearArm, rearColor)

        when (pose) {
            FighterPose.JAB -> straightArrow(canvas, PointF(59f, 30f), PointF(54f, 5f))
            FighterPose.CROSS -> straightArrow(canvas, PointF(41f, 30f), PointF(46f, 5f))
            FighterPose.LEAD_HOOK -> curvedArrow(canvas, PointF(88f, 38f), PointF(86f, 10f), PointF(58f, 10f))
            FighterPose.REAR_HOOK -> curvedArrow(canvas, PointF(12f, 38f), PointF(14f, 10f), PointF(42f, 10f))
            FighterPose.LEAD_UPPERCUT -> curvedArrow(canvas, PointF(76f, 44f), PointF(76f, 16f), PointF(58f, 10f))
            FighterPose.REAR_UPPERCUT -> curvedArrow(canvas, PointF(24f, 44f), PointF(24f, 16f), PointF(42f, 10f))
            FighterPose.SUMMARY -> {
                drawColumn(canvas, 90f, listOf("1", "3", "5"), yellow)
                drawColumn(canvas, 10f, listOf("2", "4", "6"), orange)
            }
            FighterPose.DEFENSE -> {
                doubleArrow(canvas, PointF(32f, 20f), PointF(68f, 20f))
                drawLabel(canvas, "SLIP", 50f, 13f)
                curvedArrow(canvas, PointF(8f, 24f), PointF(14f, 62f), PointF(22f, 30f))
                drawLabel(canvas, "DUCK", 15f, 18f)
            }
            FighterPose.DISTANCE -> {
                straightArrow(canvas, PointF(10f, 80f), PointF(10f, 45f))
                drawLabel(canvas, "IN", 10f, 88f)
                straightArrow(canvas, PointF(90f, 45f), PointF(90f, 80f))
                drawLabel(canvas, "OUT", 90f, 39f)
                doubleArrow(canvas, PointF(30f, 10f), PointF(70f, 10f))
                drawLabel(canvas, "MOVE", 50f, 5f)
            }
            else -> Unit
        }
    }

    private fun drawArm(canvas: Canvas, arm: Arm, color: Int) {
        limb.color = color
        val path = Path().apply {
            moveTo(arm.shoulder.x, arm.shoulder.y)
            lineTo(arm.elbow.x, arm.elbow.y)
            lineTo(arm.glove.x, arm.glove.y)
        }
        canvas.drawPath(path, limb)
        fill.color = color
        canvas.drawCircle(arm.glove.x, arm.glove.y, arm.gloveRadius, fill)
    }

    private fun drawColumn(canvas: Canvas, x: Float, texts: List<String>, color: Int) {
        label.color = color
        label.textSize = 12f
        // Rows line up with where each punch type lands: straight, hook, uppercut.
        texts.forEachIndexed { i, text -> canvas.drawText(text, x, 24f + i * 18f, label) }
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float) {
        label.color = 0xB3FFFFFF.toInt()
        label.textSize = 5.5f
        canvas.drawText(text, x, y, label)
    }

    private fun straightArrow(canvas: Canvas, from: PointF, to: PointF) {
        canvas.drawLine(from.x, from.y, to.x, to.y, arrow)
        arrowHead(canvas, from, to)
    }

    private fun doubleArrow(canvas: Canvas, a: PointF, b: PointF) {
        canvas.drawLine(a.x, a.y, b.x, b.y, arrow)
        arrowHead(canvas, a, b)
        arrowHead(canvas, b, a)
    }

    private fun curvedArrow(canvas: Canvas, from: PointF, control: PointF, to: PointF) {
        val path = Path().apply {
            moveTo(from.x, from.y)
            quadTo(control.x, control.y, to.x, to.y)
        }
        canvas.drawPath(path, arrow)
        arrowHead(canvas, control, to)
    }

    /** Arrowhead at [tip], pointing along the direction from [from]. */
    private fun arrowHead(canvas: Canvas, from: PointF, tip: PointF) {
        val angle = atan2(tip.y - from.y, tip.x - from.x)
        val length = 4.5f
        val spread = 0.5f
        for (side in listOf(-1, 1)) {
            val a = angle + Math.PI.toFloat() + side * spread
            canvas.drawLine(tip.x, tip.y, tip.x + length * cos(a), tip.y + length * sin(a), arrow)
        }
    }
}

private fun PointF.mirror() = PointF(100f - x, y)
