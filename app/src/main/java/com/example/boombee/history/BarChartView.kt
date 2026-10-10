package com.example.boombee.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.boombee.R

/** Simple vertical bar chart: weekly rounds on History, punches per round on the detail screen. */
class BarChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var values: List<Int> = emptyList()
    private var labels: List<String> = emptyList()
    private var highlightLast = false

    private val density = resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11 * resources.displayMetrics.scaledDensity
    }
    private val yellow = ContextCompat.getColor(context, R.color.timer_active)
    private val dimBar = ContextCompat.getColor(context, R.color.gray_dark)
    private val labelColor = ContextCompat.getColor(context, R.color.gray_medium)
    private val rect = RectF()

    fun setData(values: List<Int>, labels: List<String>, highlightLast: Boolean = false) {
        this.values = values
        this.labels = labels
        this.highlightLast = highlightLast
        contentDescription = labels.zip(values).joinToString { (l, v) -> "$l: $v" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val labelSpace = 18 * density
        val valueSpace = 16 * density
        val chartTop = valueSpace
        val chartBottom = height - labelSpace
        val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val slot = width.toFloat() / values.size
        val barWidth = (slot * 0.6f).coerceAtMost(28 * density)
        values.forEachIndexed { i, value ->
            val cx = slot * i + slot / 2
            val top = chartBottom - (chartBottom - chartTop) * value / max
            val isHighlight = !highlightLast || i == values.lastIndex
            barPaint.color = if (isHighlight) yellow else dimBar
            rect.set(cx - barWidth / 2, if (value == 0) chartBottom - 2 * density else top, cx + barWidth / 2, chartBottom)
            canvas.drawRoundRect(rect, 4 * density, 4 * density, barPaint)
            if (value > 0) {
                textPaint.color = if (isHighlight) yellow else labelColor
                canvas.drawText(value.toString(), cx, rect.top - 4 * density, textPaint)
            }
            textPaint.color = labelColor
            labels.getOrNull(i)?.let { canvas.drawText(it, cx, height - 4 * density, textPaint) }
        }
    }
}
