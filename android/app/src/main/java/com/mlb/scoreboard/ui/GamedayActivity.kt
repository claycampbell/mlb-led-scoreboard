package com.mlb.scoreboard.ui

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mlb.scoreboard.R
import com.mlb.scoreboard.config.ScoreboardConfig
import com.mlb.scoreboard.data.MlbRepository
import com.mlb.scoreboard.data.models.*
import com.mlb.scoreboard.ui.views.BaseDiamondView
import com.mlb.scoreboard.ui.views.CountIndicatorView
import com.mlb.scoreboard.ui.views.StrikeZoneView
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class GamedayActivity : AppCompatActivity() {

    private lateinit var config: ScoreboardConfig
    private lateinit var repository: MlbRepository

    // Views
    private lateinit var awayTeamName: TextView
    private lateinit var awayScore: TextView
    private lateinit var homeTeamName: TextView
    private lateinit var homeScore: TextView
    private lateinit var inningLabel: TextView
    private lateinit var inningNumber: TextView
    private lateinit var strikeZoneView: StrikeZoneView
    private lateinit var baseDiamondView: BaseDiamondView
    private lateinit var countIndicator: CountIndicatorView
    private lateinit var batterName: TextView
    private lateinit var pitcherName: TextView
    private lateinit var batterLabel: TextView
    private lateinit var pitcherLabel: TextView
    private lateinit var playFeedRecycler: RecyclerView
    private lateinit var statusOverlay: TextView
    private lateinit var scoreboardBar: View

    private val playAdapter = PlayFeedAdapter()

    // State
    private var games: List<ScheduleGame> = emptyList()
    private var currentGame: ScheduleGame? = null
    private var liveFeed: LiveFeedResponse? = null
    private var isRunning = false
    private var lastPlayEvent: String? = null

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val dataScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastScheduleRefresh = 0L
    private var lastDataRefresh = 0L

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gameday)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        config = ScoreboardConfig(this)
        repository = MlbRepository.getInstance()

        bindViews()
        setupRecyclerView()
        setupGestures()
        hideSystemUI()
    }

    private fun bindViews() {
        awayTeamName = findViewById(R.id.awayTeamName)
        awayScore = findViewById(R.id.awayScore)
        homeTeamName = findViewById(R.id.homeTeamName)
        homeScore = findViewById(R.id.homeScore)
        inningLabel = findViewById(R.id.inningLabel)
        inningNumber = findViewById(R.id.inningNumber)
        strikeZoneView = findViewById(R.id.strikeZoneView)
        baseDiamondView = findViewById(R.id.baseDiamondView)
        countIndicator = findViewById(R.id.countIndicator)
        batterName = findViewById(R.id.batterName)
        pitcherName = findViewById(R.id.pitcherName)
        batterLabel = findViewById(R.id.batterLabel)
        pitcherLabel = findViewById(R.id.pitcherLabel)
        playFeedRecycler = findViewById(R.id.playFeedRecycler)
        statusOverlay = findViewById(R.id.statusOverlay)
        scoreboardBar = findViewById(R.id.scoreboardBar)
    }

    private fun setupRecyclerView() {
        playFeedRecycler.layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = true
            stackFromEnd = true
        }
        playFeedRecycler.adapter = playAdapter
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                startActivity(Intent(this@GamedayActivity, SettingsActivity::class.java))
                return true
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        isRunning = true
        hideSystemUI()
        config = ScoreboardConfig(this)
        applyNightMode()
        startDataLoop()
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

    private fun startDataLoop() {
        dataScope.launch {
            while (isRunning) {
                val now = System.currentTimeMillis()

                if (now - lastScheduleRefresh > 360_000 || lastScheduleRefresh == 0L) {
                    refreshSchedule()
                    lastScheduleRefresh = now
                }

                if (now - lastDataRefresh > 8_000 || lastDataRefresh == 0L) {
                    refreshGameData()
                    lastDataRefresh = now
                }

                delay(4000)
            }
        }
    }

    private suspend fun refreshSchedule() {
        val allGames = repository.getTodaySchedule()
        withContext(Dispatchers.Main) {
            games = allGames
            currentGame = findPreferredGame(allGames)
            if (currentGame == null) {
                showNoGame()
            }
        }
    }

    private suspend fun refreshGameData() {
        val game = currentGame ?: return
        val state = game.status?.abstractGameState

        if (state == "Live" || state == "Preview") {
            val feed = repository.getLiveFeed(game.gamePk)
            withContext(Dispatchers.Main) {
                if (feed != null) {
                    liveFeed = feed
                    updateUI(game, feed)
                }
            }
        } else if (state == "Final") {
            // Fetch once for final stats
            if (liveFeed == null) {
                val feed = repository.getLiveFeed(game.gamePk)
                withContext(Dispatchers.Main) {
                    if (feed != null) {
                        liveFeed = feed
                        updateUI(game, feed)
                    }
                }
            }
        }
    }

    private fun findPreferredGame(allGames: List<ScheduleGame>): ScheduleGame? {
        val preferred = config.preferredTeams
        if (preferred.isEmpty()) return allGames.firstOrNull()

        // Find game involving a preferred team, prioritize live games
        val sorted = allGames.sortedBy {
            when (it.status?.abstractGameState) {
                "Live" -> 0
                "Preview" -> 1
                "Final" -> 2
                else -> 3
            }
        }

        return sorted.firstOrNull { game ->
            val awayId = game.teams?.away?.team?.id ?: 0
            val homeId = game.teams?.home?.team?.id ?: 0
            val awayAbbrev = ScoreboardConfig.teamAbbrev(awayId)
            val homeAbbrev = ScoreboardConfig.teamAbbrev(homeId)
            preferred.contains(awayAbbrev) || preferred.contains(homeAbbrev)
        } ?: sorted.firstOrNull()
    }

    private fun updateUI(game: ScheduleGame, feed: LiveFeedResponse) {
        val linescore = feed.liveData?.linescore
        val plays = feed.liveData?.plays
        val currentPlay = plays?.currentPlay
        val state = feed.gameData?.status?.abstractGameState ?: game.status?.abstractGameState

        // Update scoreboard
        val awayTeam = feed.gameData?.teams?.away
        val homeTeam = feed.gameData?.teams?.home
        awayTeamName.text = awayTeam?.abbreviation ?: "AWY"
        homeTeamName.text = homeTeam?.abbreviation ?: "HME"
        awayScore.text = "${linescore?.teams?.away?.runs ?: 0}"
        homeScore.text = "${linescore?.teams?.home?.runs ?: 0}"

        // Apply team colors to names
        val awayAbbrev = awayTeam?.abbreviation?.lowercase() ?: "default"
        val homeAbbrev = homeTeam?.abbreviation?.lowercase() ?: "default"
        awayTeamName.setTextColor(config.getTeamColor(awayAbbrev, "home"))
        homeTeamName.setTextColor(config.getTeamColor(homeAbbrev, "home"))

        when (state) {
            "Live" -> updateLiveState(linescore, currentPlay)
            "Preview" -> showPregame(game, feed)
            "Final" -> showFinal(feed)
            else -> showStatus(game.status?.detailedState ?: "")
        }

        // Update play-by-play feed
        updatePlayFeed(plays)
    }

    private fun updateLiveState(linescore: Linescore?, currentPlay: CurrentPlay?) {
        statusOverlay.visibility = View.GONE
        playFeedRecycler.visibility = View.VISIBLE
        strikeZoneView.visibility = View.VISIBLE

        // Inning
        val isTop = linescore?.isTopInning ?: true
        inningLabel.text = if (isTop) "TOP" else "BOT"
        inningNumber.text = "${linescore?.currentInning ?: 1}"

        // Count
        val balls = linescore?.balls ?: currentPlay?.count?.balls ?: 0
        val strikes = linescore?.strikes ?: currentPlay?.count?.strikes ?: 0
        val outs = linescore?.outs ?: currentPlay?.count?.outs ?: 0
        countIndicator.setCount(balls, strikes, outs)

        // Base runners
        val offense = linescore?.offense
        baseDiamondView.setRunners(
            first = offense?.first != null,
            second = offense?.second != null,
            third = offense?.third != null
        )

        // Matchup
        val matchup = currentPlay?.matchup
        batterName.text = matchup?.batter?.lastName ?: matchup?.batter?.fullName ?: ""
        pitcherName.text = matchup?.pitcher?.lastName ?: matchup?.pitcher?.fullName ?: ""
        batterLabel.visibility = View.VISIBLE
        pitcherLabel.visibility = View.VISIBLE

        // Strike zone pitches
        val pitchEvents = currentPlay?.playEvents?.filter { it.isPitch } ?: emptyList()
        if (pitchEvents.isNotEmpty()) {
            val szTop = pitchEvents.lastOrNull()?.pitchData?.strikeZoneTop ?: 3.5
            val szBot = pitchEvents.lastOrNull()?.pitchData?.strikeZoneBottom ?: 1.5
            val batSide = matchup?.batSide?.code ?: "R"
            strikeZoneView.setPitchData(pitchEvents, batSide, szTop, szBot)
        } else {
            strikeZoneView.clearPitches()
        }
    }

    private fun showPregame(game: ScheduleGame, feed: LiveFeedResponse) {
        statusOverlay.visibility = View.VISIBLE
        playFeedRecycler.visibility = View.GONE

        val gameTime = feed.gameData?.datetime?.time ?: ""
        val ampm = feed.gameData?.datetime?.ampm ?: ""
        statusOverlay.text = "First pitch at $gameTime $ampm"

        inningLabel.text = ""
        inningNumber.text = ""
        awayScore.text = "0"
        homeScore.text = "0"
        countIndicator.setCount(0, 0, 0)
        baseDiamondView.setRunners(first = false, second = false, third = false)
        strikeZoneView.clearPitches()

        // Show probable pitchers
        val awayPitcher = feed.gameData?.probablePitchers?.away?.fullName ?: "TBD"
        val homePitcher = feed.gameData?.probablePitchers?.home?.fullName ?: "TBD"
        batterName.text = awayPitcher
        pitcherName.text = homePitcher
        batterLabel.text = "AWAY SP"
        pitcherLabel.text = "HOME SP"
        batterLabel.visibility = View.VISIBLE
        pitcherLabel.visibility = View.VISIBLE
    }

    private fun showFinal(feed: LiveFeedResponse) {
        statusOverlay.visibility = View.GONE
        playFeedRecycler.visibility = View.VISIBLE

        inningLabel.text = ""
        inningNumber.text = "F"
        inningNumber.setTextColor(Color.rgb(229, 57, 53))

        countIndicator.setCount(0, 0, 0)
        baseDiamondView.setRunners(first = false, second = false, third = false)
        strikeZoneView.clearPitches()

        val decisions = feed.liveData?.decisions
        batterLabel.text = "WIN"
        pitcherLabel.text = "LOSS"
        batterName.text = decisions?.winner?.fullName ?: ""
        pitcherName.text = decisions?.loser?.fullName ?: ""
        batterLabel.visibility = View.VISIBLE
        pitcherLabel.visibility = View.VISIBLE
    }

    private fun showStatus(status: String) {
        statusOverlay.visibility = View.VISIBLE
        statusOverlay.text = status
        playFeedRecycler.visibility = View.GONE
    }

    private fun showNoGame() {
        statusOverlay.visibility = View.VISIBLE
        statusOverlay.text = getString(R.string.no_game_today)
        playFeedRecycler.visibility = View.GONE
        awayTeamName.text = ""
        homeTeamName.text = ""
        awayScore.text = ""
        homeScore.text = ""
        inningLabel.text = ""
        inningNumber.text = ""
        batterName.text = ""
        pitcherName.text = ""
        batterLabel.visibility = View.GONE
        pitcherLabel.visibility = View.GONE
        strikeZoneView.clearPitches()
        countIndicator.setCount(0, 0, 0)
        baseDiamondView.setRunners(first = false, second = false, third = false)
    }

    private fun updatePlayFeed(plays: Plays?) {
        val allPlays = plays?.allPlays ?: return
        val completedPlays = allPlays.filter { it.about?.isComplete == true }
        val items = completedPlays.mapIndexed { index, play ->
            val event = play.result?.event ?: "Play"
            val desc = play.result?.description ?: ""
            val rbi = play.result?.rbi ?: 0
            val isHR = event.contains("Home Run", ignoreCase = true)
            val isScoring = rbi > 0 || event.contains("scores", ignoreCase = true)
            PlayItem(
                id = "$index",
                event = shortenEvent(event),
                description = desc,
                isScoring = isScoring,
                isHomeRun = isHR
            )
        }
        playAdapter.submitList(items) {
            if (items.isNotEmpty()) {
                playFeedRecycler.scrollToPosition(items.size - 1)
            }
        }
    }

    private fun shortenEvent(event: String): String {
        return when {
            event.contains("Home Run", true) -> "HR"
            event.contains("Strikeout", true) -> "K"
            event.contains("Walk", true) -> "BB"
            event.contains("Single", true) -> "1B"
            event.contains("Double", true) -> "2B"
            event.contains("Triple", true) -> "3B"
            event.contains("Flyout", true) -> "FO"
            event.contains("Groundout", true) -> "GO"
            event.contains("Lineout", true) -> "LO"
            event.contains("Pop Out", true) -> "PO"
            event.contains("Sac Fly", true) -> "SF"
            event.contains("Sac Bunt", true) -> "SAC"
            event.contains("Field Error", true) -> "E"
            event.contains("Hit By Pitch", true) -> "HBP"
            event.contains("Grounded Into", true) -> "GIDP"
            event.contains("Force Out", true) -> "FC"
            else -> event.take(6)
        }
    }

    private fun applyNightMode() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val autoNight = prefs.getBoolean("night_mode_auto", false)
        if (!autoNight) {
            window.attributes = window.attributes.apply { screenBrightness = -1f }
            return
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 20 || hour < 6) {
            val brightness = prefs.getString("night_mode_brightness", "30")?.toIntOrNull() ?: 30
            window.attributes = window.attributes.apply {
                screenBrightness = brightness / 100f
            }
        } else {
            window.attributes = window.attributes.apply { screenBrightness = -1f }
        }
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
}
