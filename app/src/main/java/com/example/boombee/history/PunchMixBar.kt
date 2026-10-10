package com.example.boombee.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * One stacked bar of called punches 1–6: lead hand (1/3/5) in yellows,
 * rear hand (2/4/6) in oranges, darker = hooks/uppercuts.
 */
class PunchMixBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var counts = IntArray(6)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val rect = RectF()

    fun setCounts(counts: IntArray) {
        this.counts = counts
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = counts.sum()
        val radius = height / 2f
        clip.reset()
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        if (total == 0) {
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(rect, paint)
        } else {
            var x = 0f
            counts.forEachIndexed { i, count ->
                val w = width * count.toFloat() / total
                paint.color = COLORS[i]
                canvas.drawRect(x, 0f, x + w, height.toFloat(), paint)
                x += w
            }
        }
        canvas.restore()
    }

    companion object {
        /** Punch 1…6; shared with the legend on the detail screen. */
        val COLORS = intArrayOf(
            Color.parseColor("#FFD54F"), // 1 jab
            Color.parseColor("#FF9800"), // 2 cross
            Color.parseColor("#FFC107"), // 3 lead hook
            Color.parseColor("#F57C00"), // 4 rear hook
            Color.parseColor("#C79100"), // 5 lead uppercut
            Color.parseColor("#E65100"), // 6 rear uppercut
        )
    }
}
