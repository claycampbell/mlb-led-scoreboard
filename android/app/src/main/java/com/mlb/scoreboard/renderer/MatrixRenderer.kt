package com.mlb.scoreboard.renderer

import android.content.Context
import android.graphics.Color
import com.mlb.scoreboard.config.ScoreboardConfig
import com.mlb.scoreboard.ui.LedMatrixView

/**
 * Base renderer providing drawing primitives on top of the LED matrix view.
 * All screen-specific renderers extend this.
 */
open class MatrixRenderer(
    protected val context: Context,
    protected val matrix: LedMatrixView,
    protected val config: ScoreboardConfig
) {
    protected val defaultFont: BdfFont by lazy { BdfFont.load(context, "4x6") }
    protected val font5x7: BdfFont by lazy { BdfFont.load(context, "5x7") }
    protected val font5x8: BdfFont by lazy { BdfFont.load(context, "5x8") }
    protected val font6x12: BdfFont by lazy { BdfFont.load(context, "6x12") }

    protected val bgColor: Int = config.getBackgroundColor()

    fun clear() {
        matrix.clearBuffer(bgColor)
    }

    fun swap() {
        matrix.swapBuffers()
    }

    fun drawText(text: String, x: Int, y: Int, color: Int, font: BdfFont = defaultFont) {
        font.drawText(text, x, y) { px, py ->
            matrix.setPixel(px, py, color)
        }
    }

    fun drawTextCentered(text: String, y: Int, color: Int, font: BdfFont = defaultFont, width: Int = LedMatrixView.MATRIX_WIDTH) {
        val textWidth = font.textWidth(text)
        val x = (width - textWidth) / 2
        drawText(text, x, y, color, font)
    }

    /**
     * Draw scrolling text. Returns the new scroll position.
     * When the text has fully scrolled off, it resets.
     */
    fun drawScrollingText(
        text: String,
        y: Int,
        color: Int,
        scrollPos: Int,
        font: BdfFont = defaultFont,
        areaWidth: Int = LedMatrixView.MATRIX_WIDTH,
        startX: Int = 0
    ): Int {
        val textWidth = font.textWidth(text)
        val drawX = areaWidth - scrollPos + startX

        font.drawText(text, drawX, y) { px, py ->
            if (px in startX until startX + areaWidth) {
                matrix.setPixel(px, py, color)
            }
        }

        return if (scrollPos > textWidth + areaWidth) 0 else scrollPos + 1
    }

    fun drawRect(x: Int, y: Int, width: Int, height: Int, color: Int) {
        matrix.drawRect(x, y, width, height, color)
    }

    fun drawLine(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        matrix.drawLine(x0, y0, x1, y1, color)
    }

    fun drawPixel(x: Int, y: Int, color: Int) {
        matrix.setPixel(x, y, color)
    }

    fun drawDiamond(cx: Int, cy: Int, size: Int, color: Int, filled: Boolean = true) {
        matrix.drawDiamond(cx, cy, size, color, filled)
    }

    fun drawCircle(cx: Int, cy: Int, radius: Int, color: Int, filled: Boolean = true) {
        matrix.drawCircle(cx, cy, radius, color, filled)
    }

    fun drawTriangleUp(x: Int, y: Int, size: Int, color: Int) {
        matrix.drawTriangleUp(x, y, size, color)
    }

    fun drawTriangleDown(x: Int, y: Int, size: Int, color: Int) {
        matrix.drawTriangleDown(x, y, size, color)
    }

    /**
     * Get text width in pixels using a specific font.
     */
    fun textWidth(text: String, font: BdfFont = defaultFont): Int {
        return font.textWidth(text)
    }
}
