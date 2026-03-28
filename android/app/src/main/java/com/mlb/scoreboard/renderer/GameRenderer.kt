package com.mlb.scoreboard.renderer

import android.content.Context
import android.graphics.Color
import com.mlb.scoreboard.config.ScoreboardConfig
import com.mlb.scoreboard.data.models.*
import com.mlb.scoreboard.ui.LedMatrixView

/**
 * Renders the live game area (rows 14-31): at-bat info, count, bases, outs, inning.
 * Faithfully replicates the original LED matrix scoreboard layout.
 */
class GameRenderer(
    context: Context,
    matrix: LedMatrixView,
    config: ScoreboardConfig
) : MatrixRenderer(context, matrix, config) {

    private var scrollPos = 0
    private var playResultCountdown = 0
    private var lastEvent: String? = null

    /**
     * Render a live game's lower panel.
     */
    fun renderLiveGame(game: ScheduleGame, liveFeed: LiveFeedResponse?) {
        val linescore = liveFeed?.liveData?.linescore ?: game.linescore
        val currentPlay = liveFeed?.liveData?.plays?.currentPlay

        // Draw inning indicator
        renderInning(linescore)

        // Draw bases
        renderBases(linescore)

        // Draw outs
        renderOuts(linescore, currentPlay)

        // Draw batter count (balls-strikes)
        renderCount(linescore, currentPlay)

        // Draw at-bat info (pitcher/batter names or play result)
        renderAtBat(currentPlay, linescore)
    }

    /**
     * Render pre-game: start time + probable pitchers
     */
    fun renderPregame(game: ScheduleGame, liveFeed: LiveFeedResponse?): Int {
        val gameData = liveFeed?.gameData
        val startTime = gameData?.datetime?.time ?: ""
        val ampm = gameData?.datetime?.ampm ?: ""
        val timeStr = "$startTime $ampm"

        val timeColor = config.getColor("pregame.start_time")
        val scrollColor = config.getColor("pregame.scrolling_text")

        // Check if warming up
        val isWarmup = game.status?.detailedState?.contains("Warmup", ignoreCase = true) == true

        if (isWarmup) {
            drawTextCentered("WARMUP", 20, timeColor)
        } else {
            // Draw start time centered
            drawTextCentered(timeStr, 20, timeColor)
        }

        // Build scrolling info text
        val awayPitcher = gameData?.probablePitchers?.away?.fullName ?: "TBD"
        val homePitcher = gameData?.probablePitchers?.home?.fullName ?: "TBD"
        val scrollText = "   $awayPitcher  vs  $homePitcher   "

        scrollPos = drawScrollingText(scrollText, 31, scrollColor, scrollPos)
        return scrollPos
    }

    /**
     * Render post-game: final indicator + decisions
     */
    fun renderPostgame(game: ScheduleGame): Int {
        val finalColor = config.getColor("final.inning")
        val scrollColor = config.getColor("final.scrolling_text")

        // Draw "FINAL" or "FINAL/X" for extras
        val linescore = game.linescore
        val innings = linescore?.currentInning ?: 9
        val scheduledInnings = linescore?.scheduledInnings ?: 9
        val finalText = if (innings > scheduledInnings) "F/$innings" else "FINAL"

        drawTextCentered(finalText, 20, finalColor)

        // Build decision scroll text
        val decisions = game.decisions
        val parts = mutableListOf<String>()

        decisions?.winner?.let {
            parts.add("W: ${it.fullName ?: it.lastName ?: "?"}")
        }
        decisions?.loser?.let {
            parts.add("L: ${it.fullName ?: it.lastName ?: "?"}")
        }
        decisions?.save?.let {
            parts.add("SV: ${it.fullName ?: it.lastName ?: "?"}")
        }

        val scrollText = if (parts.isNotEmpty()) {
            "   " + parts.joinToString("   ") + "   "
        } else {
            "   GAME OVER   "
        }

        scrollPos = drawScrollingText(scrollText, 31, scrollColor, scrollPos)
        return scrollPos
    }

    /**
     * Render irregular game status (delayed, suspended, etc.)
     */
    fun renderStatus(game: ScheduleGame): Int {
        val statusColor = config.getColor("status.text")
        val scrollColor = config.getColor("status.scrolling_text")

        val statusText = game.status?.detailedState ?: "Unknown"
        drawTextCentered(statusText, 20, statusColor)

        val reason = game.status?.reason
        if (reason != null) {
            scrollPos = drawScrollingText("   $reason   ", 31, scrollColor, scrollPos)
        }
        return scrollPos
    }

    fun resetScroll() {
        scrollPos = 0
    }

    // ---- Private rendering methods ----

    private fun renderInning(linescore: Linescore?) {
        val inning = linescore?.currentInning ?: return
        val isTop = linescore.isTopInning ?: true
        val inningColor = config.getColor("inning.number")
        val arrowColor = if (isTop) config.getColor("inning.arrow.up") else config.getColor("inning.arrow.down")

        // Inning number position from layout
        val numX = config.getLayoutInt("inning.number.x")
        val numY = config.getLayoutInt("inning.number.y")
        val arrowSize = config.getLayoutInt("inning.arrow.size")

        // Draw inning number
        drawText(inning.toString(), numX, numY, inningColor)

        // Draw inning half arrow
        if (isTop) {
            val upXOff = config.getLayoutInt("inning.arrow.up.x_offset")
            val upYOff = config.getLayoutInt("inning.arrow.up.y_offset")
            drawTriangleUp(numX + upXOff, numY + upYOff, arrowSize, arrowColor)
        } else {
            val downXOff = config.getLayoutInt("inning.arrow.down.x_offset")
            val downYOff = config.getLayoutInt("inning.arrow.down.y_offset")
            drawTriangleDown(numX + downXOff, numY + downYOff, arrowSize, arrowColor)
        }
    }

    private fun renderBases(linescore: Linescore?) {
        val offense = linescore?.offense

        val baseColor1B = config.getColor("bases.1B")
        val baseColor2B = config.getColor("bases.2B")
        val baseColor3B = config.getColor("bases.3B")

        // Base positions from layout
        val b1x = config.getLayoutInt("bases.1B.x")
        val b1y = config.getLayoutInt("bases.1B.y")
        val b2x = config.getLayoutInt("bases.2B.x")
        val b2y = config.getLayoutInt("bases.2B.y")
        val b3x = config.getLayoutInt("bases.3B.x")
        val b3y = config.getLayoutInt("bases.3B.y")
        val size = config.getLayoutInt("bases.1B.size")

        val half = size / 2

        // 1B
        val on1B = offense?.first != null
        drawDiamond(b1x + half, b1y + half, size, baseColor1B, filled = on1B)

        // 2B
        val on2B = offense?.second != null
        drawDiamond(b2x + half, b2y + half, size, baseColor2B, filled = on2B)

        // 3B
        val on3B = offense?.third != null
        drawDiamond(b3x + half, b3y + half, size, baseColor3B, filled = on3B)
    }

    private fun renderOuts(linescore: Linescore?, currentPlay: CurrentPlay?) {
        val outs = currentPlay?.count?.outs ?: linescore?.outs ?: 0
        val outColor = config.getColor("outs.1")

        for (i in 1..3) {
            val ox = config.getLayoutInt("outs.$i.x")
            val oy = config.getLayoutInt("outs.$i.y")
            val size = config.getLayoutInt("outs.$i.size")

            val filled = i <= outs
            drawCircle(ox + size / 2, oy + size / 2, size / 2, outColor, filled = filled)
        }
    }

    private fun renderCount(linescore: Linescore?, currentPlay: CurrentPlay?) {
        val balls = currentPlay?.count?.balls ?: linescore?.balls ?: 0
        val strikes = currentPlay?.count?.strikes ?: linescore?.strikes ?: 0
        val countColor = config.getColor("batter_count")

        val cx = config.getLayoutInt("batter_count.x")
        val cy = config.getLayoutInt("batter_count.y")

        val countText = "$balls-$strikes"
        drawText(countText, cx, cy, countColor)
    }

    private fun renderAtBat(currentPlay: CurrentPlay?, linescore: Linescore?) {
        val pitcherColor = config.getColor("atbat.pitcher")
        val batterColor = config.getColor("atbat.batter")

        val pitcherX = config.getLayoutInt("atbat.pitcher.x")
        val pitcherY = config.getLayoutInt("atbat.pitcher.y")
        val pitcherW = config.getLayoutInt("atbat.pitcher.width")

        val batterX = config.getLayoutInt("atbat.batter.x")
        val batterY = config.getLayoutInt("atbat.batter.y")
        val batterW = config.getLayoutInt("atbat.batter.width")

        // Check for play result to display
        val event = currentPlay?.result?.event
        val isComplete = currentPlay?.about?.isComplete ?: false

        if (isComplete && event != null && event != lastEvent) {
            lastEvent = event
            playResultCountdown = 50 // show result for ~50 frames
        }

        if (playResultCountdown > 0) {
            playResultCountdown--
            val resultText = getShortPlayResult(event ?: "")
            val resultColor = if (isStrikeout(event ?: "")) {
                config.getColor("atbat.strikeout")
            } else {
                config.getColor("atbat.play_result")
            }

            val resultX = config.getLayoutInt("atbat.play_result.x")
            val resultY = config.getLayoutInt("atbat.play_result.y")
            drawText(resultText, resultX, resultY, resultColor, font5x7)
        }

        // Draw pitcher name
        val pitcherName = currentPlay?.matchup?.pitcher?.lastName
            ?: linescore?.defense?.pitcher?.lastName ?: ""
        if (pitcherName.isNotEmpty()) {
            val truncatedPitcher = truncateText(pitcherName, pitcherW)
            drawText(truncatedPitcher, pitcherX, pitcherY, pitcherColor)
        }

        // Draw batter name
        val batterName = currentPlay?.matchup?.batter?.lastName
            ?: linescore?.offense?.batter?.lastName ?: ""
        if (batterName.isNotEmpty()) {
            val truncatedBatter = truncateText(batterName, batterW)
            drawText(truncatedBatter, batterX, batterY, batterColor)
        }
    }

    private fun truncateText(text: String, maxWidth: Int, font: BdfFont = defaultFont): String {
        if (font.textWidth(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && font.textWidth(truncated) > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return truncated
    }

    private fun getShortPlayResult(event: String): String {
        return when {
            event.contains("Strikeout", ignoreCase = true) -> "K"
            event.contains("Groundout", ignoreCase = true) -> "GO"
            event.contains("Flyout", ignoreCase = true) -> "FO"
            event.contains("Lineout", ignoreCase = true) -> "LO"
            event.contains("Pop Out", ignoreCase = true) -> "PO"
            event.contains("Single", ignoreCase = true) -> "1B"
            event.contains("Double", ignoreCase = true) && !event.contains("Play") -> "2B"
            event.contains("Triple", ignoreCase = true) -> "3B"
            event.contains("Home Run", ignoreCase = true) -> "HR"
            event.contains("Walk", ignoreCase = true) -> "BB"
            event.contains("Hit By Pitch", ignoreCase = true) -> "HBP"
            event.contains("Sac Fly", ignoreCase = true) -> "SF"
            event.contains("Sac Bunt", ignoreCase = true) -> "SAC"
            event.contains("Error", ignoreCase = true) -> "E"
            event.contains("Field", ignoreCase = true) -> "FC"
            event.contains("Double Play", ignoreCase = true) -> "DP"
            event.contains("Triple Play", ignoreCase = true) -> "TP"
            else -> event.take(3).uppercase()
        }
    }

    private fun isStrikeout(event: String): Boolean {
        return event.contains("Strikeout", ignoreCase = true)
    }
}
