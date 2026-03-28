package com.mlb.scoreboard.config

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Configuration manager that loads colors and coordinates from JSON assets
 * and user preferences from SharedPreferences.
 */
class ScoreboardConfig(private val context: Context) {

    private val gson = Gson()
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // Parsed color data
    val scoreboardColors: Map<String, Any> by lazy { loadJsonMap("colors/scoreboard.json") }
    val teamColors: Map<String, Any> by lazy { loadJsonMap("colors/teams.json") }
    val layout: Map<String, Any> by lazy { loadJsonMap("coordinates/w64h32.json") }

    // ---- User Preferences ----

    val preferredTeams: List<String>
        get() = prefs.getString("preferred_teams", "")
            ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }
            ?: emptyList()

    val rotationEnabled: Boolean
        get() = prefs.getBoolean("rotation_enabled", true)

    val rotationRate: Float
        get() = prefs.getFloat("rotation_rate", 15f)

    val showStandings: Boolean
        get() = prefs.getBoolean("show_standings", true)

    val scrollingSpeed: Int
        get() = prefs.getInt("scrolling_speed", 2)

    val timeFormat: String
        get() = prefs.getString("time_format", "12h") ?: "12h"

    // ---- Color Helpers ----

    /**
     * Get a color by dot-separated keypath from scoreboard colors.
     * e.g., "bases.1B" -> {r, g, b}
     */
    fun getColor(keypath: String): Int {
        val parts = keypath.split(".")
        var current: Any? = scoreboardColors
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> null
            }
        }
        return parseColorMap(current) ?: Color.WHITE
    }

    /**
     * Get a team's color by abbreviation and type (home, text, accent).
     */
    fun getTeamColor(teamAbbrev: String, type: String): Int {
        val team = teamColors[teamAbbrev.lowercase()] as? Map<*, *>
            ?: teamColors["default"] as? Map<*, *>
            ?: return Color.WHITE
        val colorMap = team[type]
        return parseColorMap(colorMap) ?: Color.WHITE
    }

    /**
     * Get background color.
     */
    fun getBackgroundColor(): Int {
        return getColor("default.background")
    }

    /**
     * Get a layout coordinate value by dot-separated keypath.
     */
    fun getLayoutInt(keypath: String): Int {
        val parts = keypath.split(".")
        var current: Any? = layout
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> null
            }
        }
        return when (current) {
            is Number -> current.toInt()
            else -> 0
        }
    }

    fun getLayoutBool(keypath: String): Boolean {
        val parts = keypath.split(".")
        var current: Any? = layout
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> null
            }
        }
        return current as? Boolean ?: false
    }

    fun getLayoutString(keypath: String): String {
        val parts = keypath.split(".")
        var current: Any? = layout
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> null
            }
        }
        return current?.toString() ?: ""
    }

    private fun parseColorMap(obj: Any?): Int? {
        val map = obj as? Map<*, *> ?: return null
        val r = (map["r"] as? Number)?.toInt() ?: return null
        val g = (map["g"] as? Number)?.toInt() ?: return null
        val b = (map["b"] as? Number)?.toInt() ?: return null
        return Color.rgb(r, g, b)
    }

    private fun loadJsonMap(assetPath: String): Map<String, Any> {
        return try {
            val json = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ---- Team Abbreviation Mapping ----

    companion object {
        val TEAM_ABBREVS = mapOf(
            108 to "laa", 109 to "az", 110 to "bal", 111 to "bos",
            112 to "chc", 113 to "cin", 114 to "cle", 115 to "col",
            116 to "det", 117 to "hou", 118 to "kc", 119 to "lad",
            120 to "wsh", 121 to "nym", 133 to "ath", 134 to "pit",
            135 to "sd", 136 to "sea", 137 to "sf", 138 to "stl",
            139 to "tb", 140 to "tex", 141 to "tor", 142 to "min",
            143 to "phi", 144 to "atl", 145 to "cws", 146 to "mia",
            147 to "nyy", 158 to "mil"
        )

        val TEAM_DISPLAY_NAMES = mapOf(
            "laa" to "LAA", "az" to "AZ", "bal" to "BAL", "bos" to "BOS",
            "chc" to "CHC", "cin" to "CIN", "cle" to "CLE", "col" to "COL",
            "det" to "DET", "hou" to "HOU", "kc" to "KC", "lad" to "LAD",
            "wsh" to "WSH", "nym" to "NYM", "ath" to "ATH", "pit" to "PIT",
            "sd" to "SD", "sea" to "SEA", "sf" to "SF", "stl" to "STL",
            "tb" to "TB", "tex" to "TEX", "tor" to "TOR", "min" to "MIN",
            "phi" to "PHI", "atl" to "ATL", "cws" to "CWS", "mia" to "MIA",
            "nyy" to "NYY", "mil" to "MIL"
        )

        fun teamAbbrev(teamId: Int): String = TEAM_ABBREVS[teamId] ?: "default"
        fun teamDisplayName(teamId: Int): String {
            val abbrev = teamAbbrev(teamId)
            return TEAM_DISPLAY_NAMES[abbrev] ?: abbrev.uppercase()
        }
    }
}
