package com.mlb.scoreboard.renderer

import android.content.Context
import android.graphics.Color
import com.mlb.scoreboard.config.ScoreboardConfig
import com.mlb.scoreboard.data.models.ScheduleGame
import com.mlb.scoreboard.ui.LedMatrixView

/**
 * Renders the team banner (top 14 rows): team backgrounds, names, scores.
 * This matches the original LED scoreboard's team display.
 *
 * Layout (64x32, top 14 rows):
 * Row 0-6:  Away team background + accent + name + score
 * Row 7-13: Home team background + accent + name + score
 */
class TeamBannerRenderer(
    context: Context,
    matrix: LedMatrixView,
    config: ScoreboardConfig
) : MatrixRenderer(context, matrix, config) {

    fun render(game: ScheduleGame) {
        val awayTeamId = game.teams?.away?.team?.id ?: 0
        val homeTeamId = game.teams?.home?.team?.id ?: 0
        val awayAbbrev = ScoreboardConfig.teamAbbrev(awayTeamId)
        val homeAbbrev = ScoreboardConfig.teamAbbrev(homeTeamId)

        // Team background colors
        val awayBg = config.getTeamColor(awayAbbrev, "home")
        val homeBg = config.getTeamColor(homeAbbrev, "home")
        val awayText = config.getTeamColor(awayAbbrev, "text")
        val homeText = config.getTeamColor(homeAbbrev, "text")
        val awayAccent = config.getTeamColor(awayAbbrev, "accent")
        val homeAccent = config.getTeamColor(homeAbbrev, "accent")

        // Draw backgrounds (full width, 7px tall each)
        drawRect(0, 0, 64, 7, awayBg)
        drawRect(0, 7, 64, 7, homeBg)

        // Draw accent stripes (2px wide on left)
        drawRect(0, 0, 2, 7, awayAccent)
        drawRect(0, 7, 2, 7, homeAccent)

        // Draw team names
        val awayName = ScoreboardConfig.teamDisplayName(awayTeamId)
        val homeName = ScoreboardConfig.teamDisplayName(homeTeamId)
        drawText(awayName, 4, 6, awayText)
        drawText(homeName, 4, 13, homeText)

        // Draw scores if game is live or final
        val state = game.status?.abstractGameState
        if (state == "Live" || state == "Final") {
            val awayScore = game.teams?.away?.score ?: 0
            val homeScore = game.teams?.home?.score ?: 0

            // Right-align scores
            val awayScoreStr = awayScore.toString()
            val homeScoreStr = homeScore.toString()
            val awayScoreWidth = defaultFont.textWidth(awayScoreStr)
            val homeScoreWidth = defaultFont.textWidth(homeScoreStr)

            drawText(awayScoreStr, 62 - awayScoreWidth, 6, awayText)
            drawText(homeScoreStr, 62 - homeScoreWidth, 13, homeText)

            // Draw hits and errors if R/H/E is enabled
            val awayHits = game.linescore?.teams?.away?.hits ?: 0
            val homeHits = game.linescore?.teams?.home?.hits ?: 0
            val awayErrors = game.linescore?.teams?.away?.errors ?: 0
            val homeErrors = game.linescore?.teams?.home?.errors ?: 0

            // R H E display - compact, right side
            val rheX = 42
            val awayRHE = "$awayScore  $awayHits  $awayErrors"
            val homeRHE = "$homeScore  $homeHits  $homeErrors"

            // Only show full R/H/E if there's enough room
            if (awayName.length <= 3 && homeName.length <= 3) {
                drawText(awayRHE, rheX, 6, awayText)
                drawText(homeRHE, rheX, 13, homeText)
            }
        }
    }
}
