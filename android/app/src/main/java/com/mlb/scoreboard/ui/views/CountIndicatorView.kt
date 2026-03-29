package com.mlb.scoreboard.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CountIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private var balls = 0
    private var strikes = 0
    private var outs = 0

    fun setCount(balls: Int, strikes: Int, outs: Int) {
        this.balls = balls
        this.strikes = strikes
        this.outs = outs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val rowHeight = h / 3f
        val dotRadius = rowHeight * 0.22f
        val labelWidth = w * 0.22f
        val dotSpacing = dotRadius * 2.8f
        val startX = labelWidth

        labelPaint.textSize = rowHeight * 0.55f

        // Balls row (4 dots)
        val ballColor = Color.rgb(76, 175, 80)
        drawRow(canvas, "B", startX, rowHeight * 0.5f, dotRadius, dotSpacing, 4, balls, ballColor)

        // Strikes row (3 dots)
        val strikeColor = Color.rgb(229, 57, 53)
        drawRow(canvas, "S", startX, rowHeight * 1.5f, dotRadius, dotSpacing, 3, strikes, strikeColor)

        // Outs row (3 dots)
        val outColor = Color.rgb(255, 171, 0)
        drawRow(canvas, "O", startX, rowHeight * 2.5f, dotRadius, dotSpacing, 3, outs, outColor)
    }

    private fun drawRow(
        canvas: Canvas,
        label: String,
        startX: Float,
        centerY: Float,
        radius: Float,
        spacing: Float,
        total: Int,
        filled: Int,
        activeColor: Int
    ) {
        // Draw label
        labelPaint.textSize = radius * 2.2f
        canvas.drawText(label, radius * 0.5f, centerY + radius * 0.75f, labelPaint)

        // Draw dots
        activePaint.color = activeColor
        for (i in 0 until total) {
            val cx = startX + spacing * i + radius
            val paint = if (i < filled) activePaint else inactivePaint
            canvas.drawCircle(cx, centerY, radius, paint)
        }
    }
}
