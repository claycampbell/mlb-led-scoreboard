package com.mlb.scoreboard.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.mlb.scoreboard.config.ScoreboardConfig
import com.mlb.scoreboard.data.MlbRepository
import com.mlb.scoreboard.data.models.*
import com.mlb.scoreboard.renderer.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main full-screen activity that drives the LED matrix scoreboard display.
 * Runs in landscape, hides system UI, and keeps the screen on.
 */
class ScoreboardActivity : AppCompatActivity() {

    private lateinit var matrixView: LedMatrixView
    private lateinit var config: ScoreboardConfig
    private lateinit var repository: MlbRepository

    // Renderers
    private lateinit var teamBanner: TeamBannerRenderer
    private lateinit var gameRenderer: GameRenderer
    private lateinit var standingsRenderer: StandingsRenderer
    private lateinit var offdayRenderer: OffdayRenderer
    private lateinit var celebrationRenderer: CelebrationRenderer

    // State
    private var games: List<ScheduleGame> = emptyList()
    private var standings: List<StandingsRecord> = emptyList()
    private var currentGameIndex = 0
    private var liveFeed: LiveFeedResponse? = null
    private var isRunning = false
    private var networkError = false
    private var lastPlayEvent: String? = null

    // Coroutine scopes
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val dataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Timing
    private var lastDataRefresh = 0L
    private var lastScheduleRefresh = 0L
    private var lastStandingsRefresh = 0L
    private var lastRotation = 0L
    private var frameCount = 0L

    // Screen type
    private enum class ScreenType {
        GAMEDAY, STANDINGS, OFFDAY
    }
    private var currentScreen = ScreenType.OFFDAY
    private var standingsTimer = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create matrix view programmatically
        matrixView = LedMatrixView(this)
        setContentView(matrixView)

        // Keep screen on and set brightness for always-on display
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        config = ScoreboardConfig(this)
        repository = MlbRepository.getInstance()

        // Initialize renderers
        teamBanner = TeamBannerRenderer(this, matrixView, config)
        gameRenderer = GameRenderer(this, matrixView, config)
        standingsRenderer = StandingsRenderer(this, matrixView, config)
        offdayRenderer = OffdayRenderer(this, matrixView, config)
        celebrationRenderer = CelebrationRenderer(this, matrixView, config)

        // Double-tap opens settings
        matrixView.onDoubleTapListener = {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        hideSystemUI()
    }

    override fun onResume() {
        super.onResume()
        isRunning = true
        hideSystemUI()

        // Reload config in case settings changed
        config = ScoreboardConfig(this)

        // Apply night mode if configured
        applyNightMode()

        // Start data fetching and render loop
        startDataLoop()
        startRenderLoop()
    }

    override fun onPause() {
        super.onPause()
        isRunning = false
        mainScope.coroutineContext.cancelChildren()
        dataScope.coroutineContext.cancelChildren()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        dataScope.cancel()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    /**
     * Background data fetching loop.
     */
    private fun startDataLoop() {
        dataScope.launch {
            while (isRunning) {
                val now = System.currentTimeMillis()

                // Refresh schedule every 6 minutes
                if (now - lastScheduleRefresh > 360_000 || lastScheduleRefresh == 0L) {
                    refreshSchedule()
                    lastScheduleRefresh = now
                }

                // Refresh live game data every 10 seconds
                if (now - lastDataRefresh > 10_000 || lastDataRefresh == 0L) {
                    refreshGameData()
                    lastDataRefresh = now
                }

                // Refresh standings every 15 minutes
                if (now - lastStandingsRefresh > 900_000 || lastStandingsRefresh == 0L) {
                    refreshStandings()
                    lastStandingsRefresh = now
                }

                delay(5000) // check every 5 seconds
            }
        }
    }

    /**
     * Main render loop running at ~30fps for smooth scrolling.
     */
    private fun startRenderLoop() {
        mainScope.launch {
            while (isRunning) {
                val frameStart = System.currentTimeMillis()

                renderFrame()
                frameCount++

                // Handle game rotation
                handleRotation()

                // Target ~30fps (33ms per frame)
                val elapsed = System.currentTimeMillis() - frameStart
                val sleepMs = maxOf(16L, 33L - elapsed)
                delay(sleepMs)
            }
        }
    }

    private fun renderFrame() {
        // Clear the back buffer
        teamBanner.clear()

        // Determine what to show
        val screenType = determineScreenType()
        currentScreen = screenType

        when (screenType) {
            ScreenType.GAMEDAY -> {
                val game = getCurrentGame() ?: return
                val state = game.status?.abstractGameState

                // Always draw team banner for games
                teamBanner.render(game)

                when (state) {
                    "Preview" -> gameRenderer.renderPregame(game, liveFeed)
                    "Live" -> {
                        val detailed = game.status?.detailedState ?: ""
                        if (isIrregularStatus(detailed)) {
                            gameRenderer.renderStatus(game)
                        } else {
                            gameRenderer.renderLiveGame(game, liveFeed)
                        }
                    }
                    "Final" -> gameRenderer.renderPostgame(game)
                    else -> gameRenderer.renderStatus(game)
                }

                // Check for celebration-worthy events (kid-friendly)
                checkForCelebrations()

                // Draw celebration overlay if active
                if (celebrationRenderer.isActive()) {
                    celebrationRenderer.renderFrame()
                }

                // Draw network error indicator if needed
                if (networkError) {
                    drawNetworkError()
                }
            }

            ScreenType.STANDINGS -> {
                standingsRenderer.render(standings)
            }

            ScreenType.OFFDAY -> {
                offdayRenderer.render()
            }
        }

        // Swap to display
        teamBanner.swap()
    }

    private fun determineScreenType(): ScreenType {
        // Check if we have games
        if (games.isEmpty()) {
            return if (standings.isNotEmpty() && config.showStandings) {
                // Alternate between offday and standings
                if (standingsTimer < 300) { // ~10 seconds at 30fps
                    standingsTimer++
                    ScreenType.OFFDAY
                } else if (standingsTimer < 600) {
                    standingsTimer++
                    ScreenType.STANDINGS
                } else {
                    standingsTimer = 0
                    ScreenType.OFFDAY
                }
            } else {
                ScreenType.OFFDAY
            }
        }

        // We have games
        return ScreenType.GAMEDAY
    }

    private fun handleRotation() {
        if (!config.rotationEnabled || games.size <= 1) return

        val now = System.currentTimeMillis()
        val rotationMs = (config.rotationRate * 1000).toLong()

        if (now - lastRotation > rotationMs) {
            currentGameIndex = (currentGameIndex + 1) % games.size
            gameRenderer.resetScroll()
            lastRotation = now

            // Trigger a live feed refresh for the new game
            dataScope.launch { refreshGameData() }
        }
    }

    private fun getCurrentGame(): ScheduleGame? {
        if (games.isEmpty()) return null
        return games[currentGameIndex % games.size]
    }

    // ---- Data Refresh ----

    private suspend fun refreshSchedule() {
        val newGames = repository.getTodaySchedule()
        withContext(Dispatchers.Main) {
            if (newGames.isNotEmpty()) {
                networkError = false
                games = filterAndSortGames(newGames)
                if (currentGameIndex >= games.size) {
                    currentGameIndex = 0
                }
            } else if (games.isEmpty()) {
                // Only set empty if we had nothing before (avoid clearing during network blip)
                networkError = true
            }
        }
    }

    private suspend fun refreshGameData() {
        val game = getCurrentGame() ?: return
        val state = game.status?.abstractGameState

        // Only fetch live feed for active games
        if (state == "Live" || state == "Preview") {
            val feed = repository.getLiveFeed(game.gamePk)
            withContext(Dispatchers.Main) {
                if (feed != null) {
                    liveFeed = feed
                    networkError = false

                    // Update schedule game with latest linescore for score display
                    feed.liveData?.linescore?.let { ls ->
                        updateGameFromFeed(game, feed)
                    }
                }
            }
        }
    }

    private suspend fun refreshStandings() {
        val response = repository.getStandings()
        withContext(Dispatchers.Main) {
            response?.records?.let {
                standings = it
            }
        }
    }

    private fun filterAndSortGames(allGames: List<ScheduleGame>): List<ScheduleGame> {
        val preferred = config.preferredTeams

        return if (preferred.isNotEmpty()) {
            // Preferred teams first, then others
            val preferredGames = allGames.filter { game ->
                val awayId = game.teams?.away?.team?.id ?: 0
                val homeId = game.teams?.home?.team?.id ?: 0
                val awayAbbrev = ScoreboardConfig.teamAbbrev(awayId)
                val homeAbbrev = ScoreboardConfig.teamAbbrev(homeId)
                preferred.contains(awayAbbrev) || preferred.contains(homeAbbrev)
            }
            val otherGames = allGames.filter { it !in preferredGames }

            // Sort: live games first, then preview, then final
            val comparator = compareBy<ScheduleGame> { gameStateOrder(it) }
            preferredGames.sortedWith(comparator) + otherGames.sortedWith(comparator)
        } else {
            allGames.sortedBy { gameStateOrder(it) }
        }
    }

    private fun gameStateOrder(game: ScheduleGame): Int {
        return when (game.status?.abstractGameState) {
            "Live" -> 0
            "Preview" -> 1
            "Final" -> 2
            else -> 3
        }
    }

    private fun updateGameFromFeed(game: ScheduleGame, feed: LiveFeedResponse) {
        // Update the game list with fresh score data
        val idx = games.indexOfFirst { it.gamePk == game.gamePk }
        if (idx >= 0) {
            val ls = feed.liveData?.linescore
            val updatedGame = game.copy(
                linescore = ls,
                status = feed.gameData?.status ?: game.status,
                decisions = feed.liveData?.decisions ?: game.decisions
            )
            games = games.toMutableList().also { it[idx] = updatedGame }
        }
    }

    private fun isIrregularStatus(detailed: String): Boolean {
        val irregulars = listOf("Delayed", "Suspended", "Postponed", "Challenge", "Review")
        return irregulars.any { detailed.contains(it, ignoreCase = true) }
    }

    private fun checkForCelebrations() {
        val showCelebrations = androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(this)
            .getBoolean("show_emoji_celebrations", true)
        if (!showCelebrations) return

        val currentPlay = liveFeed?.liveData?.plays?.currentPlay
        val event = currentPlay?.result?.event ?: return
        val isComplete = currentPlay.about?.isComplete ?: false

        if (isComplete && event != lastPlayEvent) {
            lastPlayEvent = event
            when {
                event.contains("Home Run", ignoreCase = true) -> {
                    celebrationRenderer.startCelebration(CelebrationRenderer.CelebrationType.HOME_RUN)
                }
                event.contains("Strikeout", ignoreCase = true) -> {
                    // Only celebrate strikeouts for preferred team's pitcher
                    celebrationRenderer.startCelebration(CelebrationRenderer.CelebrationType.STRIKEOUT)
                }
            }
        }

        // Celebrate wins
        val game = getCurrentGame()
        if (game?.status?.abstractGameState == "Final" && lastPlayEvent != "GAME_FINAL_${game.gamePk}") {
            lastPlayEvent = "GAME_FINAL_${game.gamePk}"
            celebrationRenderer.startCelebration(CelebrationRenderer.CelebrationType.WIN)
        }
    }

    private fun applyNightMode() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val autoNight = prefs.getBoolean("night_mode_auto", false)
        if (!autoNight) {
            window.attributes = window.attributes.apply { screenBrightness = -1f } // system default
            return
        }

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour >= 20 || hour < 6) {
            val brightness = prefs.getString("night_mode_brightness", "30")?.toIntOrNull() ?: 30
            window.attributes = window.attributes.apply {
                screenBrightness = brightness / 100f
            }
        } else {
            window.attributes = window.attributes.apply { screenBrightness = -1f }
        }
    }

    private fun drawNetworkError() {
        val bgColor = config.getColor("network.background")
        val textColor = config.getColor("network.text")

        val nx = config.getLayoutInt("network.background.x")
        val ny = config.getLayoutInt("network.background.y")
        val nw = config.getLayoutInt("network.background.width")
        val nh = config.getLayoutInt("network.background.height")

        teamBanner.drawRect(nx, ny, nw, nh, bgColor)

        val tx = config.getLayoutInt("network.text.x")
        val ty = config.getLayoutInt("network.text.y")
        teamBanner.drawText("!", tx, ty, textColor)
    }
}
