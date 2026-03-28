package com.mlb.scoreboard.renderer

import android.content.Context
import com.mlb.scoreboard.config.ScoreboardConfig
import com.mlb.scoreboard.ui.LedMatrixView
import java.text.SimpleDateFormat
import java.util.*

/**
 * Renders the off-day screen: current time, date, and "No Games Today" message.
 */
class OffdayRenderer(
    context: Context,
    matrix: LedMatrixView,
    config: ScoreboardConfig
) : MatrixRenderer(context, matrix, config) {

    private var scrollPos = 0

    fun render(newsText: String? = null): Int {
        val timeColor = config.getColor("offday.time")
        val textColor = config.getColor("offday.scrolling_text")

        // Draw current time
        val now = Calendar.getInstance()
        val is12h = config.timeFormat == "12h"
        val timeFormat = if (is12h) {
            SimpleDateFormat("h:mm a", Locale.US)
        } else {
            SimpleDateFormat("HH:mm", Locale.US)
        }
        val timeStr = timeFormat.format(now.time)
        drawTextCentered(timeStr, 12, timeColor, font5x8)

        // Draw date
        val dateFormat = SimpleDateFormat("EEE MMM d", Locale.US)
        val dateStr = dateFormat.format(now.time)
        drawTextCentered(dateStr, 22, timeColor)

        // Draw scrolling news or "No Games Today"
        val scrollText = newsText ?: "   No Games Today   "
        scrollPos = drawScrollingText(scrollText, 31, textColor, scrollPos)

        return scrollPos
    }

    fun resetScroll() {
        scrollPos = 0
    }
}
