package com.mlb.scoreboard.data

import com.mlb.scoreboard.data.models.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * MLB Stats API v1.1 interface.
 * Base URL: https://statsapi.mlb.com/api/v1/
 */
interface MlbApi {

    @GET("schedule")
    suspend fun getSchedule(
        @Query("sportId") sportId: Int = 1,
        @Query("date") date: String,
        @Query("hydrate") hydrate: String = "linescore,decisions,team,probablePitcher"
    ): ScheduleResponse

    @GET("game/{gamePk}/feed/live")
    suspend fun getLiveFeed(
        @Path("gamePk") gamePk: Int
    ): LiveFeedResponse

    @GET("standings")
    suspend fun getStandings(
        @Query("leagueId") leagueId: String = "103,104", // AL=103, NL=104
        @Query("season") season: Int,
        @Query("standingsTypes") standingsTypes: String = "regularSeason",
        @Query("hydrate") hydrate: String = "team"
    ): StandingsResponse
}
