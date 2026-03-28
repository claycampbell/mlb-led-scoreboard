package com.mlb.scoreboard.renderer

import android.content.Context
import android.graphics.Color
import com.mlb.scoreboard.config.ScoreboardConfig
import com.mlb.scoreboard.ui.LedMatrixView
import kotlin.random.Random

/**
 * Renders fun celebration animations for big plays (home runs, strikeouts, etc.).
 * Kid-friendly feature: colorful flashing effects on the LED matrix.
 */
class CelebrationRenderer(
    context: Context,
    matrix: LedMatrixView,
    config: ScoreboardConfig
) : MatrixRenderer(context, matrix, config) {

    private var celebrationFrames = 0
    private var celebrationType = CelebrationType.NONE
    private var frame = 0

    enum class CelebrationType {
        NONE,
        HOME_RUN,
        STRIKEOUT,
        WIN
    }

    // Fun celebration colors
    private val fireworkColors = intArrayOf(
        Color.rgb(255, 0, 0),      // red
        Color.rgb(255, 165, 0),    // orange
        Color.rgb(255, 255, 0),    // yellow
        Color.rgb(0, 255, 0),      // green
        Color.rgb(0, 150, 255),    // blue
        Color.rgb(200, 0, 255),    // purple
        Color.rgb(255, 105, 180),  // pink
        Color.rgb(255, 255, 255),  // white
    )

    /**
     * Start a celebration animation.
     */
    fun startCelebration(type: CelebrationType) {
        celebrationType = type
        celebrationFrames = when (type) {
            CelebrationType.HOME_RUN -> 90  // ~3 seconds at 30fps
            CelebrationType.STRIKEOUT -> 45 // ~1.5 seconds
            CelebrationType.WIN -> 120      // ~4 seconds
            CelebrationType.NONE -> 0
        }
        frame = 0
    }

    /**
     * Returns true if a celebration is currently active.
     */
    fun isActive(): Boolean = celebrationFrames > 0

    /**
     * Render one frame of the celebration overlay.
     * Call this AFTER the normal game frame is drawn, BEFORE swap.
     */
    fun renderFrame() {
        if (celebrationFrames <= 0) return

        celebrationFrames--
        frame++

        when (celebrationType) {
            CelebrationType.HOME_RUN -> renderHomeRunCelebration()
            CelebrationType.STRIKEOUT -> renderStrikeoutCelebration()
            CelebrationType.WIN -> renderWinCelebration()
            CelebrationType.NONE -> {}
        }

        if (celebrationFrames <= 0) {
            celebrationType = CelebrationType.NONE
        }
    }

    private fun renderHomeRunCelebration() {
        // Fireworks effect: random sparkle pixels
        val sparkleCount = 8 + (frame % 5)
        for (i in 0 until sparkleCount) {
            val x = Random.nextInt(0, LedMatrixView.MATRIX_WIDTH)
            val y = Random.nextInt(14, LedMatrixView.MATRIX_HEIGHT) // below team banner
            val color = fireworkColors[Random.nextInt(fireworkColors.size)]
            drawPixel(x, y, color)

            // Expand sparkle to adjacent pixels for visibility
            if (Random.nextBoolean()) {
                drawPixel(x + 1, y, color)
                drawPixel(x, y + 1, color)
            }
        }

        // Flash "HR!" text periodically
        if (frame % 20 < 10) {
            val textColor = fireworkColors[(frame / 4) % fireworkColors.size]
            drawText("HR!", 26, 24, textColor, font5x7)
        }
    }

    private fun renderStrikeoutCelebration() {
        // Flash a big "K" in red
        if (frame % 10 < 6) {
            val kColor = Color.rgb(255, (60 * (frame % 4)).coerceAtMost(255), 0)
            drawText("K", 28, 26, kColor, font6x12)
        }
    }

    private fun renderWinCelebration() {
        // Colorful border flash + "WIN!" text
        val borderColor = fireworkColors[(frame / 3) % fireworkColors.size]

        // Top and bottom borders (below team banner area)
        for (x in 0 until LedMatrixView.MATRIX_WIDTH) {
            if ((x + frame) % 4 < 2) {
                drawPixel(x, 14, borderColor)
                drawPixel(x, 31, borderColor)
            }
        }
        // Left and right borders
        for (y in 14 until LedMatrixView.MATRIX_HEIGHT) {
            if ((y + frame) % 4 < 2) {
                drawPixel(0, y, borderColor)
                drawPixel(63, y, borderColor)
            }
        }

        // "WIN!" text
        if (frame % 16 < 10) {
            val textColor = fireworkColors[(frame / 5) % fireworkColors.size]
            drawTextCentered("WIN!", 25, textColor, font5x7)
        }
    }
}
