package com.mlb.scoreboard.data.models

import com.google.gson.annotations.SerializedName

// ---- Schedule Response ----

data class ScheduleResponse(
    val dates: List<ScheduleDate>? = null
)

data class ScheduleDate(
    val date: String? = null,
    val games: List<ScheduleGame>? = null
)

data class ScheduleGame(
    val gamePk: Int = 0,
    val gameDate: String? = null,
    val status: GameStatus? = null,
    val teams: GameTeams? = null,
    val linescore: Linescore? = null,
    val decisions: Decisions? = null,
    val seriesDescription: String? = null,
    val seriesGameNumber: Int? = null,
    val gamesInSeries: Int? = null
)

data class GameStatus(
    val abstractGameState: String? = null,    // "Preview", "Live", "Final"
    val detailedState: String? = null,         // "Scheduled", "In Progress", "Final", etc.
    val statusCode: String? = null,
    val reason: String? = null
)

data class GameTeams(
    val away: GameTeamInfo? = null,
    val home: GameTeamInfo? = null
)

data class GameTeamInfo(
    val team: Team? = null,
    val score: Int? = null,
    val isWinner: Boolean? = null,
    val leagueRecord: LeagueRecord? = null
)

data class Team(
    val id: Int = 0,
    val name: String? = null,
    val abbreviation: String? = null,
    val teamName: String? = null,
    val shortName: String? = null
)

data class LeagueRecord(
    val wins: Int = 0,
    val losses: Int = 0,
    val pct: String? = null
)

data class Linescore(
    val currentInning: Int? = null,
    val currentInningOrdinal: String? = null,
    val inningHalf: String? = null,      // "Top" or "Bottom"
    val isTopInning: Boolean? = null,
    val scheduledInnings: Int? = null,
    val innings: List<InningDetail>? = null,
    val teams: LinescoreTeams? = null,
    val offense: OffenseInfo? = null,
    val defense: DefenseInfo? = null,
    val balls: Int? = null,
    val strikes: Int? = null,
    val outs: Int? = null
)

data class InningDetail(
    val num: Int = 0,
    val home: InningRuns? = null,
    val away: InningRuns? = null
)

data class InningRuns(
    val runs: Int? = null,
    val hits: Int? = null,
    val errors: Int? = null
)

data class LinescoreTeams(
    val home: LinescoreTeamStats? = null,
    val away: LinescoreTeamStats? = null
)

data class LinescoreTeamStats(
    val runs: Int? = null,
    val hits: Int? = null,
    val errors: Int? = null
)

data class OffenseInfo(
    val batter: PlayerInfo? = null,
    val onDeck: PlayerInfo? = null,
    val inHole: PlayerInfo? = null,
    val first: PlayerInfo? = null,
    val second: PlayerInfo? = null,
    val third: PlayerInfo? = null
)

data class DefenseInfo(
    val pitcher: PlayerInfo? = null
)

data class PlayerInfo(
    val id: Int = 0,
    val fullName: String? = null,
    val lastName: String? = null
)

data class Decisions(
    val winner: DecisionPitcher? = null,
    val loser: DecisionPitcher? = null,
    val save: DecisionPitcher? = null
)

data class DecisionPitcher(
    val id: Int = 0,
    val fullName: String? = null,
    val lastName: String? = null
)

// ---- Live Game Feed ----

data class LiveFeedResponse(
    val gameData: GameData? = null,
    val liveData: LiveData? = null
)

data class GameData(
    val datetime: GameDateTime? = null,
    val status: GameStatus? = null,
    val teams: GameDataTeams? = null,
    val probablePitchers: ProbablePitchers? = null,
    val weather: Weather? = null
)

data class GameDateTime(
    val dateTime: String? = null,
    val originalDate: String? = null,
    val time: String? = null,
    val ampm: String? = null
)

data class GameDataTeams(
    val away: GameDataTeamInfo? = null,
    val home: GameDataTeamInfo? = null
)

data class GameDataTeamInfo(
    val id: Int = 0,
    val name: String? = null,
    val abbreviation: String? = null,
    val teamName: String? = null,
    val shortName: String? = null,
    val record: TeamRecord? = null
)

data class TeamRecord(
    val wins: Int = 0,
    val losses: Int = 0,
    val winningPercentage: String? = null
)

data class ProbablePitchers(
    val away: PitcherInfo? = null,
    val home: PitcherInfo? = null
)

data class PitcherInfo(
    val id: Int = 0,
    val fullName: String? = null,
    val lastName: String? = null
)

data class Weather(
    val condition: String? = null,
    val temp: String? = null,
    val wind: String? = null
)

data class LiveData(
    val linescore: Linescore? = null,
    val plays: Plays? = null,
    val decisions: Decisions? = null
)

data class Plays(
    val currentPlay: CurrentPlay? = null
)

data class CurrentPlay(
    val result: PlayResult? = null,
    val about: PlayAbout? = null,
    val count: PlayCount? = null,
    val matchup: PlayMatchup? = null,
    val runners: List<Runner>? = null
)

data class PlayResult(
    val event: String? = null,
    val eventType: String? = null,
    val description: String? = null,
    val rbi: Int? = null
)

data class PlayAbout(
    val isTopInning: Boolean? = null,
    val inning: Int? = null,
    val isComplete: Boolean? = null,
    val halfInning: String? = null
)

data class PlayCount(
    val balls: Int = 0,
    val strikes: Int = 0,
    val outs: Int = 0
)

data class PlayMatchup(
    val batter: PlayerInfo? = null,
    val pitcher: PlayerInfo? = null
)

data class Runner(
    val movement: RunnerMovement? = null
)

data class RunnerMovement(
    val start: String? = null,
    val end: String? = null,
    val isOut: Boolean? = null
)

// ---- Standings ----

data class StandingsResponse(
    val records: List<StandingsRecord>? = null
)

data class StandingsRecord(
    val division: Division? = null,
    val teamRecords: List<TeamStanding>? = null
)

data class Division(
    val id: Int = 0,
    val name: String? = null,
    val abbreviation: String? = null
)

data class TeamStanding(
    val team: Team? = null,
    val wins: Int = 0,
    val losses: Int = 0,
    val winningPercentage: String? = null,
    val gamesBack: String? = null,
    val wildCardGamesBack: String? = null,
    val divisionRank: String? = null,
    val clinched: Boolean = false,
    val eliminationNumber: String? = null
)
