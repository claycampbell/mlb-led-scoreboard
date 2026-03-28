package com.mlb.scoreboard.renderer

import android.content.Context
import com.mlb.scoreboard.config.ScoreboardConfig
import com.mlb.scoreboard.data.models.StandingsRecord
import com.mlb.scoreboard.data.models.TeamStanding
import com.mlb.scoreboard.ui.LedMatrixView

/**
 * Renders division standings on the LED matrix.
 * Shows team names, W-L records, and games back.
 */
class StandingsRenderer(
    context: Context,
    matrix: LedMatrixView,
    config: ScoreboardConfig
) : MatrixRenderer(context, matrix, config) {

    private var divisionIndex = 0
    private var showWins = true // toggle between W and L columns

    /**
     * Render standings for the current division.
     */
    fun render(records: List<StandingsRecord>): Boolean {
        if (records.isEmpty()) return false

        val record = records[divisionIndex % records.size]
        val teams = record.teamRecords ?: return false
        val divName = record.division?.abbreviation ?: record.division?.name ?: "DIV"

        // Determine if AL or NL for divider color
        val isNL = divName.contains("NL") || divName.contains("National")
        val dividerColor = if (isNL) {
            config.getColor("standings.nl.divider")
        } else {
            config.getColor("standings.al.divider")
        }

        val bgColor = config.getColor("standings.background")
        val nameColor = config.getColor("standings.team.name")
        val statColor = config.getColor("standings.team.stat")
        val titleColor = config.getColor("standings.text")

        // Fill standings background
        drawRect(0, 0, 64, 32, bgColor)

        // Draw division title
        drawTextCentered(divName, 6, titleColor)

        // Draw divider line
        val divX = config.getLayoutInt("standings.divider.x")
        drawLine(divX, 0, divX, 32, config.getColor("standings.divider"))

        // Draw stat column header
        val statTitleX = config.getLayoutInt("standings.stat_title.x")
        val statHeader = if (showWins) "W" else "L"
        drawText(statHeader, statTitleX, 6, titleColor)

        // Draw "GB" header
        drawText("GB", 52, 6, titleColor)

        // Draw teams
        val nameX = config.getLayoutInt("standings.team.name.x")
        val recordX = config.getLayoutInt("standings.team.record.x")
        val gbX = config.getLayoutInt("standings.team.games_back.x")
        val offset = config.getLayoutInt("standings.offset")

        for ((idx, team) in teams.withIndex()) {
            if (idx >= 5) break // max 5 teams per division
            val y = offset + (idx + 1) * 5

            // Team name (abbreviated)
            val teamId = team.team?.id ?: 0
            val teamAbbrev = ScoreboardConfig.teamDisplayName(teamId)
            val teamNameColor = when {
                team.clinched -> config.getColor("standings.team.clinched")
                team.eliminationNumber == "E" -> config.getColor("standings.team.elim")
                else -> nameColor
            }
            drawText(teamAbbrev, nameX, y, teamNameColor)

            // W or L stat
            val stat = if (showWins) team.wins.toString() else team.losses.toString()
            drawText(stat, recordX, y, statColor)

            // Games back (right aligned)
            val gb = team.gamesBack ?: "-"
            val gbStr = if (gb == "0" || gb == "-" || gb == "0.0") "-" else gb
            val gbWidth = defaultFont.textWidth(gbStr)
            drawText(gbStr, gbX - gbWidth, y, statColor)
        }

        return true
    }

    fun nextDivision(totalDivisions: Int) {
        divisionIndex = (divisionIndex + 1) % totalDivisions
    }

    fun toggleStat() {
        showWins = !showWins
    }

    fun reset() {
        divisionIndex = 0
        showWins = true
    }
}
