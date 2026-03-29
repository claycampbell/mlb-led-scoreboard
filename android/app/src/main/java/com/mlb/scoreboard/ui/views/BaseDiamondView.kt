package com.mlb.scoreboard.ui.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class BaseDiamondView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val occupiedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 202, 40)
        style = Paint.Style.FILL
    }

    private val occupiedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 235, 150)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 202, 40)
        style = Paint.Style.FILL
    }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(30, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var firstBase = false
    private var secondBase = false
    private var thirdBase = false
    private var glowAlpha = 0f

    private val glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 600
        interpolator = AccelerateDecelerateInterpolator()
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            glowAlpha = it.animatedValue as Float
            invalidate()
        }
    }

    fun setRunners(first: Boolean, second: Boolean, third: Boolean) {
        val hadRunners = firstBase || secondBase || thirdBase
        firstBase = first
        secondBase = second
        thirdBase = third

        val hasRunners = first || second || third
        if (hasRunners && !hadRunners) {
            glowAnimator.start()
        } else if (!hasRunners && hadRunners) {
            glowAnimator.cancel()
            glowAlpha = 0f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.48f
        val baseSize = w * 0.16f
        val spread = w * 0.30f

        // Base positions (diamond layout)
        val secondX = cx
        val secondY = cy - spread
        val firstX = cx + spread
        val firstY = cy
        val thirdX = cx - spread
        val thirdY = cy
        val homeX = cx
        val homeY = cy + spread

        // Draw baselines
        canvas.drawLine(homeX, homeY, firstX, firstY, baselinePaint)
        canvas.drawLine(firstX, firstY, secondX, secondY, baselinePaint)
        canvas.drawLine(secondX, secondY, thirdX, thirdY, baselinePaint)
        canvas.drawLine(thirdX, thirdY, homeX, homeY, baselinePaint)

        // Draw bases as rotated squares (diamonds)
        drawBase(canvas, secondX, secondY, baseSize, secondBase)
        drawBase(canvas, firstX, firstY, baseSize, firstBase)
        drawBase(canvas, thirdX, thirdY, baseSize, thirdBase)

        // Draw home plate (pentagon)
        val hpSize = baseSize * 0.6f
        val homePath = Path().apply {
            moveTo(homeX - hpSize / 2f, homeY)
            lineTo(homeX, homeY - hpSize / 2f)
            lineTo(homeX + hpSize / 2f, homeY)
            lineTo(homeX + hpSize / 3f, homeY + hpSize / 3f)
            lineTo(homeX - hpSize / 3f, homeY + hpSize / 3f)
            close()
        }
        canvas.drawPath(homePath, emptyPaint)
    }

    private fun drawBase(canvas: Canvas, cx: Float, cy: Float, size: Float, occupied: Boolean) {
        val half = size / 2f
        val path = Path().apply {
            moveTo(cx, cy - half)
            lineTo(cx + half, cy)
            lineTo(cx, cy + half)
            lineTo(cx - half, cy)
            close()
        }

        if (occupied) {
            // Glow effect
            val glowSize = size * (1f + glowAlpha * 0.3f)
            val glowHalf = glowSize / 2f
            val glowPath = Path().apply {
                moveTo(cx, cy - glowHalf)
                lineTo(cx + glowHalf, cy)
                lineTo(cx, cy + glowHalf)
                lineTo(cx - glowHalf, cy)
                close()
            }
            glowPaint.alpha = (40 * glowAlpha).toInt()
            canvas.drawPath(glowPath, glowPaint)

            canvas.drawPath(path, occupiedPaint)
            canvas.drawPath(path, occupiedBorderPaint)
        } else {
            canvas.drawPath(path, emptyPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glowAnimator.cancel()
    }
}
